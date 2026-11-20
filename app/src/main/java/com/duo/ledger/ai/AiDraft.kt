package com.duo.ledger.ai

import com.duo.ledger.data.local.EntryType
import com.duo.ledger.util.centsToYuan
import com.duo.ledger.util.yuanToCents
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 模型返回的原始字段，一律按不可信的字符串处理。 */
data class AiRawDraft(
    val type: String? = null,
    val amount: String? = null,
    val purpose: String? = null,
    val member: String? = null,
    val category: String? = null,
    val counterparty: String? = null,
    val date: String? = null
)

/** 已通过本地校验、可填进表单的草稿；仍然要人工点保存才入库。 */
data class AiDraft(
    val type: EntryType,
    val amountText: String,
    val purpose: String,
    val memberId: Long?,
    val categoryId: Long?,
    val counterparty: String,
    val date: LocalDate
)

private const val MAX_TEXT = 40

/**
 * 信任边界：模型输出绝不直接写库。
 * - 金额走 yuanToCents（BigDecimal、最多两位小数），非法即判识别失败
 * - 类型不在枚举内回落 EXPENSE（默认支出）
 * - 成员/分类只接受名单内的名字，否则回落默认值
 * - 日期只接受 ISO yyyy-MM-dd；缺失、空白或解析失败一律回落 today
 */
fun validateAiDraft(
    raw: AiRawDraft,
    members: List<Pair<Long, String>>,
    expenseCategories: List<Pair<Long, String>>,
    incomeCategories: List<Pair<Long, String>>,
    defaultMemberId: Long?,
    today: LocalDate
): AiDraft? {
    val type = EntryType.entries.firstOrNull {
        it.name.equals(raw.type?.trim(), ignoreCase = true)
    } ?: EntryType.EXPENSE

    val cents = raw.amount?.trim()?.yuanToCents() ?: return null
    if (cents == 0L) return null
    if (type != EntryType.EXPENSE && cents < 0L) return null

    val memberId = raw.member?.trim()?.let { name ->
        members.firstOrNull { it.second == name }?.first
    } ?: defaultMemberId

    val categoryId = if (type == EntryType.EXPENSE || type == EntryType.INCOME) {
        val pool = if (type == EntryType.INCOME) incomeCategories else expenseCategories
        raw.category?.trim()?.let { name -> pool.firstOrNull { it.second == name }?.first }
            ?: pool.firstOrNull()?.first
    } else {
        null
    }

    // 往返比较可挡掉 SMART 解析把 2025-02-30 悄悄改成 2025-02-28 这类幻觉日期。
    val date = raw.date?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        try {
            LocalDate.parse(value).takeIf { it.toString() == value }
        } catch (e: DateTimeParseException) {
            null
        }
    } ?: today

    val purpose = raw.purpose?.trim()?.take(MAX_TEXT).orEmpty().ifEmpty { "未填用途" }
    val counterparty = raw.counterparty?.trim()?.take(MAX_TEXT).orEmpty()
    return AiDraft(type, cents.centsToYuan(), purpose, memberId, categoryId, counterparty, date)
}
