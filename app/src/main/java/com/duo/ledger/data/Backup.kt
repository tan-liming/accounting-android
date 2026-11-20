package com.duo.ledger.data

import com.duo.ledger.ai.DEFAULT_AI_BASE_URL
import com.duo.ledger.data.local.CategoryEntity
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.data.local.MemberEntity
import com.duo.ledger.data.local.RecordEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val BACKUP_SCHEMA_VERSION = 1
const val BACKUP_APP_ID = "com.duo.ledger"

private const val MAX_NAME = 40
private const val MAX_TEXT = 500
private const val MAX_RECORDS = 200_000

/**
 * 恢复时同步的偏好。**刻意不含 API Key**：备份是明文 JSON，会被导到网盘/聊天里，
 * 把 Key 写进去等于泄露；恢复后用户到设置里重填一次即可。
 */
data class BackupPreferences(
    val lastMemberId: Long? = null,
    val lastCategoryId: Long? = null,
    val weekStartsMonday: Boolean = true,
    val aiModel: String = LedgerPreferences.DEFAULT_AI_MODEL,
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    val aiEnabled: Boolean = true
)

/**
 * 全量快照：含已软删除的流水（deletedAt 保留），恢复后统计口径与导出结果才一致。
 * 所有记录都带原始 id，memberId / categoryId 才能原样对上。
 */
data class BackupData(
    val exportedAtMillis: Long,
    val members: List<MemberEntity>,
    val categories: List<CategoryEntity>,
    val records: List<RecordEntity>,
    val preferences: BackupPreferences
) {
    val activeRecordCount: Int get() = records.count { it.deletedAt == null }
}

data class BackupInfo(
    val exportedAtMillis: Long,
    val memberCount: Int,
    val categoryCount: Int,
    val recordCount: Int,
    val activeRecordCount: Int,
    val deletedRecordCount: Int
)

fun BackupData.info(): BackupInfo = BackupInfo(
    exportedAtMillis,
    members.size,
    categories.size,
    records.size,
    activeRecordCount,
    records.size - activeRecordCount
)

/**
 * 完整回滚：把备份里已删除的流水、已停用的成员/分类恢复为有效状态。
 * 只在恢复确认框显式勾选时调用；默认恢复保留删除标记，避免删掉的账悄悄复活。
 */
fun BackupData.reviveDeleted(): BackupData = copy(
    members = members.map { it.copy(archived = false) },
    categories = categories.map { it.copy(archived = false) },
    records = records.map { it.copy(deletedAt = null) }
)

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.nullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun parseType(value: String): EntryType =
    EntryType.entries.firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("备份里有未知流水类型：$value")

private fun parseKind(value: String): EntryType = when (value) {
    "EXPENSE" -> EntryType.EXPENSE
    "INCOME" -> EntryType.INCOME
    else -> throw IllegalArgumentException("备份里有未知分类类型：$value")
}

private fun memberJson(m: MemberEntity): JSONObject = JSONObject()
    .put("id", m.id)
    .put("name", m.name)
    .put("sortOrder", m.sortOrder)
    .put("archived", m.archived)

private fun categoryJson(c: CategoryEntity): JSONObject = JSONObject()
    .put("id", c.id)
    .put("name", c.name)
    .put("sortOrder", c.sortOrder)
    .put("builtIn", c.builtIn)
    .put("archived", c.archived)
    .put("kind", c.kind.name)

private fun recordJson(r: RecordEntity): JSONObject = JSONObject()
    .put("id", r.id)
    .put("type", r.type.name)
    .put("amountCents", r.amountCents)
    .put("day", r.day)
    .put("memberId", r.memberId)
    .put("categoryId", r.categoryId ?: JSONObject.NULL)
    .put("purpose", r.purpose)
    .put("fromParty", r.fromParty ?: JSONObject.NULL)
    .put("toParty", r.toParty ?: JSONObject.NULL)
    .put("createdAt", r.createdAt)
    .put("updatedAt", r.updatedAt)
    .put("deletedAt", r.deletedAt ?: JSONObject.NULL)

/** 带缩进，方便用户自己用文本编辑器核对内容。 */
fun buildBackupJson(data: BackupData): String = JSONObject()
    .put("schemaVersion", BACKUP_SCHEMA_VERSION)
    .put("app", BACKUP_APP_ID)
    .put("exportedAtMillis", data.exportedAtMillis)
    .put("members", JSONArray().also { a -> data.members.forEach { a.put(memberJson(it)) } })
    .put("categories", JSONArray().also { a -> data.categories.forEach { a.put(categoryJson(it)) } })
    .put("records", JSONArray().also { a -> data.records.forEach { a.put(recordJson(it)) } })
    .put(
        "preferences", JSONObject()
            .put("lastMemberId", data.preferences.lastMemberId ?: JSONObject.NULL)
            .put("lastCategoryId", data.preferences.lastCategoryId ?: JSONObject.NULL)
            .put("weekStartsMonday", data.preferences.weekStartsMonday)
            .put("aiModel", data.preferences.aiModel)
            .put("aiBaseUrl", data.preferences.aiBaseUrl)
            .put("aiEnabled", data.preferences.aiEnabled)
    )
    .toString(2)

/** 信任边界：任何字段缺失、类型不对、引用悬空都拒绝，绝不能拿半截数据去清库。 */
fun parseBackupJson(text: String): BackupData {
    val root = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IllegalArgumentException("这不是合法的 JSON 备份文件")
    }
    val version = root.optInt("schemaVersion", -1)
    require(version == BACKUP_SCHEMA_VERSION) {
        "备份版本不兼容（本应用支持 v$BACKUP_SCHEMA_VERSION，文件是 v$version）"
    }
    require(root.optString("app") == BACKUP_APP_ID) { "这不是「记账」的备份文件" }

    return try {
        val members = root.getJSONArray("members").mapObjects {
            MemberEntity(it.getLong("id"), it.getString("name"), it.optInt("sortOrder"), it.optBoolean("archived"))
        }
        val categories = root.getJSONArray("categories").mapObjects {
            CategoryEntity(
                it.getLong("id"), it.getString("name"), it.optInt("sortOrder"),
                it.optBoolean("builtIn"), it.optBoolean("archived"), parseKind(it.optString("kind"))
            )
        }
        val records = root.getJSONArray("records").mapObjects {
            RecordEntity(
                id = it.getLong("id"),
                type = parseType(it.getString("type")),
                amountCents = it.getLong("amountCents"),
                day = it.getLong("day"),
                memberId = it.getLong("memberId"),
                categoryId = it.nullableLong("categoryId"),
                purpose = it.getString("purpose"),
                fromParty = it.nullableString("fromParty"),
                toParty = it.nullableString("toParty"),
                createdAt = it.getLong("createdAt"),
                updatedAt = it.getLong("updatedAt"),
                deletedAt = it.nullableLong("deletedAt")
            )
        }
        val p = root.optJSONObject("preferences") ?: JSONObject()
        BackupData(
            exportedAtMillis = root.optLong("exportedAtMillis", 0L),
            members = members,
            categories = categories,
            records = records,
            preferences = BackupPreferences(
                lastMemberId = p.nullableLong("lastMemberId"),
                lastCategoryId = p.nullableLong("lastCategoryId"),
                weekStartsMonday = p.optBoolean("weekStartsMonday", true),
                aiModel = p.optString("aiModel", LedgerPreferences.DEFAULT_AI_MODEL)
                    .ifBlank { LedgerPreferences.DEFAULT_AI_MODEL },
                aiBaseUrl = p.optString("aiBaseUrl", DEFAULT_AI_BASE_URL)
                    .ifBlank { DEFAULT_AI_BASE_URL },
                aiEnabled = p.optBoolean("aiEnabled", true)
            )
        )
    } catch (e: JSONException) {
        throw IllegalArgumentException("备份文件字段缺失或格式错误：${e.message}", e)
    }
}

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }

/** 结构之外的业务约束集中在这一处；通过后才允许进入覆盖式事务。 */
fun validateBackup(data: BackupData) {
    require(data.members.isNotEmpty()) { "备份里没有成员，无法恢复" }
    require(data.records.size <= MAX_RECORDS) { "流水条数超过上限 $MAX_RECORDS" }

    require(data.members.all { it.id > 0 }) { "存在非法的成员 ID" }
    require(data.categories.all { it.id > 0 }) { "存在非法的分类 ID" }
    require(data.records.all { it.id > 0 }) { "存在非法的流水 ID" }
    require(data.members.distinctBy { it.id }.size == data.members.size) { "成员 ID 重复" }
    require(data.categories.distinctBy { it.id }.size == data.categories.size) { "分类 ID 重复" }
    require(data.records.distinctBy { it.id }.size == data.records.size) { "流水 ID 重复" }

    require(data.members.all { it.name.isNotBlank() && it.name.length <= MAX_NAME }) {
        "成员名过长或为空"
    }
    require(data.categories.all { it.name.isNotBlank() && it.name.length <= MAX_NAME }) {
        "分类名过长或为空"
    }
    require(data.records.all {
        it.purpose.length <= MAX_TEXT &&
            (it.fromParty?.length ?: 0) <= MAX_TEXT &&
            (it.toParty?.length ?: 0) <= MAX_TEXT
    }) { "流水文本字段过长" }

    // records 有外键（memberId RESTRICT / categoryId SET_NULL），引用悬空会让整笔恢复失败。
    val memberIds = data.members.map { it.id }.toSet()
    val categoryIds = data.categories.map { it.id }.toSet()
    require(data.records.all { it.memberId in memberIds }) { "流水引用了不存在的成员" }
    require(data.records.all { record ->
        val cid = record.categoryId
        cid == null || cid in categoryIds
    }) { "流水引用了不存在的分类" }

    val lastMember = data.preferences.lastMemberId
    val lastCategory = data.preferences.lastCategoryId
    require(lastMember == null || lastMember in memberIds) { "上次成员偏好指向不存在的成员" }
    require(lastCategory == null || lastCategory in categoryIds) { "上次分类偏好指向不存在的分类" }
}
