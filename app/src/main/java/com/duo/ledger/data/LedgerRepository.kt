package com.duo.ledger.data

import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.AppDatabase
import com.duo.ledger.data.local.CategoryEntity
import com.duo.ledger.data.local.CategorySum
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.data.local.MemberEntity
import com.duo.ledger.data.local.MemberImpact
import com.duo.ledger.data.local.MemberSum
import com.duo.ledger.data.local.RecordEntity
import com.duo.ledger.data.local.TotalsRow
import androidx.room.withTransaction
import com.duo.ledger.util.buildCsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class LedgerSummary(
    val totals: TotalsRow,
    val byMember: List<MemberSum>,
    val incomeByMember: List<MemberSum>,
    val expenseByCategory: List<CategorySum>,
    val incomeByCategory: List<CategorySum>
) {
    val expenseTotalCents: Long get() = totals.expenseCents
    val incomeTotalCents: Long get() = totals.incomeCents
    val transferOutTotalCents: Long get() = totals.transferOutCents
    val transferOutCount: Int get() = totals.transferOutCount
    val transferTotalCents: Long get() = totals.transferInnerCents
    val transferCount: Int get() = totals.transferInnerCount

    /**
     * 结余是「我们」这个主体的净现金流：外部流入减消费支出减对外转出。
     * 内部划转（TRANSFER）钱没离开主体，完全不参与。
     */
    val balanceCents: Long
        get() = incomeTotalCents - expenseTotalCents - transferOutTotalCents

    companion object {
        val EMPTY = LedgerSummary(TotalsRow.ZERO, emptyList(), emptyList(), emptyList(), emptyList())
    }
}

class LedgerRepository(
    private val db: AppDatabase,
    val preferences: LedgerPreferences
) {
    val members: Flow<List<MemberEntity>> = db.memberDao().observeActive()
    /** 含已删除成员：流水页显示历史名字用；筛选 chips 仍用 members。 */
    val allMembers: Flow<List<MemberEntity>> = db.memberDao().observeAll()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeActive()

    fun records(range: PeriodRange, memberId: Long?, type: EntryType?): Flow<List<ActiveRecord>> =
        db.recordDao().observeBetween(range.startDay, range.endDay, memberId, type?.name)

    fun summary(range: PeriodRange, memberId: Long?): Flow<LedgerSummary> {
        val dao = db.recordDao()
        return combine(
            dao.observeTotals(range.startDay, range.endDay, memberId),
            dao.observeByMember(range.startDay, range.endDay, memberId, EntryType.EXPENSE.name),
            dao.observeByMember(range.startDay, range.endDay, memberId, EntryType.INCOME.name),
            dao.observeByCategory(range.startDay, range.endDay, memberId, EntryType.EXPENSE.name),
            dao.observeByCategory(range.startDay, range.endDay, memberId, EntryType.INCOME.name)
        ) { totals, expenseMembers, incomeMembers, expenseCats, incomeCats ->
            // 已删除成员若仍有有效流水（例如从旧备份恢复）就显示，避免合计与按人汇总对不上；
            // 无数据的已删除成员不打扰界面。
            fun visible(list: List<MemberSum>) = list.filter { !it.archived || it.totalCents != 0L }
            LedgerSummary(totals, visible(expenseMembers), visible(incomeMembers), expenseCats, incomeCats)
        }
    }

    suspend fun addRecord(
        type: EntryType,
        amountCents: Long,
        day: Long,
        memberId: Long,
        categoryId: Long?,
        purpose: String,
        fromParty: String? = null,
        toParty: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        return db.recordDao().insert(
            RecordEntity(
                type = type,
                amountCents = amountCents,
                day = day,
                memberId = memberId,
                categoryId = categoryId,
                purpose = purpose,
                fromParty = fromParty,
                toParty = toParty,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 编辑已有流水：保留 id / createdAt / deletedAt，只刷新 updatedAt，避免打乱流水排序。 */
    suspend fun updateRecord(
        id: Long,
        type: EntryType,
        amountCents: Long,
        day: Long,
        memberId: Long,
        categoryId: Long?,
        purpose: String,
        fromParty: String?,
        toParty: String?
    ) {
        val original = db.recordDao().byId(id)
        db.recordDao().update(
            original.copy(
                type = type,
                amountCents = amountCents,
                day = day,
                memberId = memberId,
                categoryId = categoryId,
                purpose = purpose,
                fromParty = fromParty,
                toParty = toParty,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addCategory(name: String, kind: EntryType): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val maxOrder = db.categoryDao().active().maxOfOrNull { it.sortOrder } ?: -1
        return db.categoryDao().insert(
            CategoryEntity(name = trimmed, sortOrder = maxOrder + 1, builtIn = false, kind = kind)
        )
    }

    suspend fun addMember(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val maxOrder = db.memberDao().all().maxOfOrNull { it.sortOrder } ?: -1
        return db.memberDao().insert(
            MemberEntity(name = trimmed, sortOrder = maxOrder + 1)
        )
    }

    /** 改名只改显示名，历史流水仍按 memberId 归属。 */
    suspend fun renameMember(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val member = db.memberDao().byId(id) ?: return
        db.memberDao().update(member.copy(name = trimmed))
    }

    suspend fun memberImpact(memberId: Long): MemberImpact =
        db.recordDao().memberImpact(memberId)

    /**
     * 删除成员：一个事务里软删其名下所有有效流水，并把成员置为不可用。
     * 返回本次新打标记的流水 id，撤销时只恢复这些，不会复活用户先前手动删掉的账；
     * 返回 null 表示被「至少保留一个成员」规则拒绝。
     * 全程不物理删除任何行，撤销与 JSON 备份才有后悔药。
     */
    suspend fun deleteMember(memberId: Long): List<Long>? {
        if (db.memberDao().active().size <= 1) return null
        val ids = db.recordDao().activeIdsByMember(memberId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            // Room 会把 IN (:ids) 展开成占位符，SQLite 变量数上限 999；分批避免成员流水多时炸掉。
            ids.chunked(IN_BATCH).forEach { batch -> db.recordDao().softDeleteIds(batch, now) }
            db.memberDao().archive(memberId)
        }
        return ids
    }

    suspend fun undoDeleteMember(memberId: Long, recordIds: List<Long>) {
        db.withTransaction {
            db.memberDao().unarchive(memberId)
            recordIds.chunked(IN_BATCH).forEach { batch -> db.recordDao().restoreIds(batch) }
        }
    }

    /**
     * 把已删除成员恢复为可用，不改动任何流水。
     * 撤销窗口过后、或旧版遗留的停用成员，都走这里找回。
     */
    suspend fun restoreMember(memberId: Long) {
        db.memberDao().unarchive(memberId)
    }

    private companion object {
        const val IN_BATCH = 400
    }

    suspend fun delete(id: Long) {
        db.recordDao().softDelete(id, System.currentTimeMillis())
    }

    suspend fun restore(id: Long) {
        db.recordDao().restore(id)
    }

    /** range 为 null 表示全量：无视周期与成员筛选。 */
    suspend fun exportCsv(range: PeriodRange?, memberId: Long?): String {
        val rows = if (range == null) {
            db.recordDao().allForExport()
        } else {
            db.recordDao().exportBetween(range.startDay, range.endDay, memberId)
        }
        val memberNames = db.memberDao().all().associate { it.id to it.name }
        val categoryNames = db.categoryDao().active().associate { it.id to it.name }
        return buildCsv(rows, memberNames, categoryNames)
    }

    /** 全量快照：含软删除流水、归档成员与分类；偏好刻意不含 API Key。 */
    suspend fun exportBackup(): String {
        val p = preferences
        return buildBackupJson(
            BackupData(
                exportedAtMillis = System.currentTimeMillis(),
                members = db.memberDao().all(),
                categories = db.categoryDao().all(),
                records = db.recordDao().allForBackup(),
                preferences = BackupPreferences(
                    lastMemberId = p.lastMemberId.first(),
                    lastCategoryId = p.lastCategoryId.first(),
                    weekStartsMonday = p.weekStartsMonday.first(),
                    aiModel = p.aiModel.first(),
                    aiBaseUrl = p.aiBaseUrl.first(),
                    aiEnabled = p.aiEnabled.first()
                )
            )
        )
    }

    /**
     * 覆盖式恢复：校验通过后才动数据，三张表在一个事务里整体替换，
     * 中途失败不会留下「清了库但没插回去」的半套状态。
     */
    suspend fun restoreBackup(data: BackupData, reviveDeleted: Boolean = false) {
        validateBackup(data)
        // 勾选「完整回滚」时，把备份里已删流水与已停用成员一并恢复为有效状态。
        val snapshot = if (reviveDeleted) data.reviveDeleted() else data
        db.withTransaction {
            // records 有指向 members/categories 的外键，先清子表、后插父表。
            db.recordDao().clearAll()
            db.categoryDao().clearAll()
            db.memberDao().clearAll()
            db.memberDao().insertAll(snapshot.members)
            db.categoryDao().insertAll(snapshot.categories)
            db.recordDao().insertAll(snapshot.records)
        }
        preferences.restoreFrom(snapshot.preferences)
    }
}
