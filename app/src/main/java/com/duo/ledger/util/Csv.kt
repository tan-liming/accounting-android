package com.duo.ledger.util

import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.EntryType
import java.time.LocalDate

/** 导出流水明细。带 UTF-8 BOM，Excel 直接打开中文不乱码。 */
fun buildCsv(
    rows: List<ActiveRecord>,
    memberNames: Map<Long, String>,
    categoryNames: Map<Long, String>
): String {
    val sb = StringBuilder()
    sb.append('\uFEFF')
    sb.append("日期,类型,成员,分类,用途,转出方,转入方,金额(元)\n")
    rows.forEach { r ->
        val cells = listOf(
            LocalDate.ofEpochDay(r.day).toString(),
            when (r.type) {
                EntryType.EXPENSE -> "支出"
                EntryType.INCOME -> "收入"
                EntryType.TRANSFER_OUT -> "对外转出"
                EntryType.TRANSFER -> "内部划转"
            },
            memberNames[r.memberId].orEmpty(),
            r.categoryId?.let { categoryNames[it] }.orEmpty(),
            r.purpose,
            r.fromParty.orEmpty(),
            r.toParty.orEmpty(),
            r.amountCents.centsToYuan()
        )
        sb.append(cells.joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" })
        sb.append('\n')
    }
    return sb.toString()
}
