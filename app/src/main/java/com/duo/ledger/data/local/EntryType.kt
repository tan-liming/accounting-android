package com.duo.ledger.data.local

/**
 * 流水类型，按资金是否跨过「我们」这个主体边界区分：
 * - EXPENSE / INCOME：对外的消费支出 / 外部流入（工资、别人给的）
 * - TRANSFER_OUT：我们给外部的钱，单列展示，不进支出合计，但计入结余减项
 * - TRANSFER：主体内部划转（我↔对象、账户互转），仅留痕，不进任何汇总
 *
 * 结余 = INCOME - EXPENSE - TRANSFER_OUT；TRANSFER 完全不参与。
 */
enum class EntryType {
    EXPENSE,
    INCOME,
    TRANSFER_OUT,
    TRANSFER
}
