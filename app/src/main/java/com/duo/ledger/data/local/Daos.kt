package com.duo.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun active(): List<MemberEntity>

    /** 含已归档成员：导出与按人汇总都要能查到历史名字。 */
    @Query("SELECT * FROM members ORDER BY sortOrder, id")
    suspend fun all(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun byId(id: Long): MemberEntity?

    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Update
    suspend fun update(member: MemberEntity)

    /**
     * 「删除成员」只置不可用标记，不物理删除：
     * records.memberId 是外键 RESTRICT，流水（哪怕已软删）还在表里，直接 DELETE 会被拒绝。
     */
    @Query("UPDATE members SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    /** 撤销删除：恢复为可用成员。 */
    @Query("UPDATE members SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)

    /** 含不可用成员：流水页显示历史名字用；chips 仍用 observeActive。 */
    @Query("SELECT * FROM members ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<MemberEntity>>

    // 覆盖式恢复用：按原始 id 批量写回，引用关系才不会错位。
    @Insert
    suspend fun insertAll(items: List<MemberEntity>)

    @Query("DELETE FROM members")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun active(): List<CategoryEntity>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    /** 含归档分类：备份要能原样恢复全部分类。 */
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun all(): List<CategoryEntity>

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}

data class MemberSum(
    val memberId: Long,
    val name: String,
    val totalCents: Long,
    val archived: Boolean
)

data class CategorySum(val categoryId: Long, val name: String, val totalCents: Long)

/** 删除成员前的二次确认要显示的影响范围；只统计有效流水，不混入此前已删的。 */
data class MemberImpact(
    val recordCount: Int,
    val expenseCents: Long,
    val incomeCents: Long
)

/** 一条 SQL 取全类型合计，避免把太多 Flow 塞进 combine。 */
data class TotalsRow(
    val expenseCents: Long,
    val incomeCents: Long,
    val transferOutCents: Long,
    val transferInnerCents: Long,
    val transferInnerCount: Int,
    val transferOutCount: Int
) {
    companion object {
        val ZERO = TotalsRow(0, 0, 0, 0, 0, 0)
    }
}

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("UPDATE records SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE records SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun byId(id: Long): RecordEntity

    @Query(
        """
        SELECT * FROM active_records
        WHERE day BETWEEN :start AND :end
          AND (:memberId IS NULL OR memberId = :memberId)
          AND (:typeFilter IS NULL OR type = :typeFilter)
        ORDER BY day DESC, createdAt DESC
        """
    )
    fun observeBetween(
        start: Long,
        end: Long,
        memberId: Long?,
        typeFilter: String?
    ): Flow<List<ActiveRecord>>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents,
          COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
          COALESCE(SUM(CASE WHEN type = 'TRANSFER_OUT' THEN amountCents ELSE 0 END), 0) AS transferOutCents,
          COALESCE(SUM(CASE WHEN type = 'TRANSFER' THEN amountCents ELSE 0 END), 0) AS transferInnerCents,
          COALESCE(SUM(CASE WHEN type = 'TRANSFER' THEN 1 ELSE 0 END), 0) AS transferInnerCount,
          COALESCE(SUM(CASE WHEN type = 'TRANSFER_OUT' THEN 1 ELSE 0 END), 0) AS transferOutCount
        FROM active_records
        WHERE day BETWEEN :start AND :end
          AND (:memberId IS NULL OR memberId = :memberId)
        """
    )
    fun observeTotals(start: Long, end: Long, memberId: Long?): Flow<TotalsRow>

    /** kind 传 'EXPENSE' 或 'INCOME'；归档成员也进，Repository 侧再隐藏无当期数据的。 */
    @Query(
        """
        SELECT m.id AS memberId, m.name AS name, m.archived AS archived,
               COALESCE(SUM(r.amountCents), 0) AS totalCents
        FROM members m
        LEFT JOIN active_records r
          ON r.memberId = m.id AND r.type = :kind AND r.day BETWEEN :start AND :end
        WHERE (:memberId IS NULL OR m.id = :memberId)
        GROUP BY m.id, m.name, m.archived, m.sortOrder
        ORDER BY m.sortOrder, m.id
        """
    )
    fun observeByMember(
        start: Long,
        end: Long,
        memberId: Long?,
        kind: String
    ): Flow<List<MemberSum>>

    /** kind 传 'EXPENSE' 或 'INCOME'；左连接保证「未分类」也进汇总。 */
    @Query(
        """
        SELECT COALESCE(r.categoryId, 0) AS categoryId,
               COALESCE(c.name, '未分类') AS name,
               SUM(r.amountCents) AS totalCents
        FROM active_records r
        LEFT JOIN categories c ON c.id = r.categoryId
        WHERE r.type = :kind AND r.day BETWEEN :start AND :end
          AND (:memberId IS NULL OR r.memberId = :memberId)
        GROUP BY COALESCE(r.categoryId, 0), c.name, c.sortOrder
        ORDER BY SUM(r.amountCents) DESC, c.sortOrder
        """
    )
    fun observeByCategory(
        start: Long,
        end: Long,
        memberId: Long?,
        kind: String
    ): Flow<List<CategorySum>>

    @Query("SELECT * FROM active_records ORDER BY day, createdAt")
    suspend fun allForExport(): List<ActiveRecord>

    /** 周期导出：与页面统计用同一套区间和成员筛选口径。 */
    @Query(
        """
        SELECT * FROM active_records
        WHERE day BETWEEN :start AND :end
          AND (:memberId IS NULL OR memberId = :memberId)
        ORDER BY day, createdAt
        """
    )
    suspend fun exportBetween(start: Long, end: Long, memberId: Long?): List<ActiveRecord>

    /** 备份要含软删除记录，否则恢复后「已删除」的账会复活成有效流水。 */
    @Query("SELECT * FROM records ORDER BY id")
    suspend fun allForBackup(): List<RecordEntity>

    @Insert
    suspend fun insertAll(items: List<RecordEntity>)

    @Query("DELETE FROM records")
    suspend fun clearAll()

    /** 二次确认用：该成员名下还有多少有效流水、多少钱（支出/收入分开，不做无意义的混合求和）。 */
    @Query(
        """
        SELECT COUNT(*) AS recordCount,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents
        FROM active_records WHERE memberId = :memberId
        """
    )
    suspend fun memberImpact(memberId: Long): MemberImpact

    /** 只取「本次之前仍有效」的流水 id；撤销时精确恢复这些，绝不复活用户先前手动删掉的账。 */
    @Query("SELECT id FROM active_records WHERE memberId = :memberId")
    suspend fun activeIdsByMember(memberId: Long): List<Long>

    @Query("UPDATE records SET deletedAt = :at WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDeleteIds(ids: List<Long>, at: Long)

    @Query("UPDATE records SET deletedAt = NULL WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun restoreIds(ids: List<Long>)
}
