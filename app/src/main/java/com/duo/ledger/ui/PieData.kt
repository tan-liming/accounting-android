package com.duo.ledger.ui

import kotlin.math.floor

private const val TOP_N = 6

data class PieSlice(val label: String, val cents: Long, val percent: Int)

data class PieData(
    val slices: List<PieSlice>,
    /** 入图扇区之和；有负净额被排除时会小于页面合计，UI 必须标注。 */
    val totalCents: Long,
    val excludedCount: Int
)

/**
 * 最大余数法分配整数百分比，保证显示出来的占比合计恰好 100%。
 * 先各自向下取整，再把差额按小数部分从大到小补 1；
 * 单纯用 cents * 100 / total 会一路向下取整，出现 33+33+33=99 这种「加起来不是 100%」。
 * 这里只用于标签展示，金额口径全程仍是 Long 整数分。
 */
fun distributePercent(amounts: List<Long>, total: Long): List<Int> {
    if (amounts.isEmpty() || total <= 0L) return amounts.map { 0 }
    val exact = amounts.map { it.toDouble() * 100.0 / total.toDouble() }
    val result = exact.map { floor(it).toInt() }.toMutableList()
    var left = 100 - result.sum()
    val byRemainder = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
    var i = 0
    while (left > 0) {
        result[byRemainder[i % byRemainder.size]] += 1
        left--
        i++
    }
    return result
}

/**
 * Top N 之后合并成「其他」；净额 ≤ 0 的分类排除并计数。
 * 绝不对负数取绝对值——那会把占比画歪。
 */
fun preparePieData(rows: List<Pair<String, Long>>): PieData {
    val excluded = rows.count { it.second <= 0 }
    val positive = rows.filter { it.second > 0 }.sortedByDescending { it.second }
    val head = positive.take(TOP_N).map { PieSlice(it.first, it.second, 0) }
    val rest = positive.drop(TOP_N).sumOf { it.second }
    val raw = head + if (rest > 0) listOf(PieSlice("其他", rest, 0)) else emptyList()
    val total = raw.sumOf { it.cents }
    val percents = distributePercent(raw.map { it.cents }, total)
    val slices = raw.mapIndexed { i, slice -> slice.copy(percent = percents[i]) }
    return PieData(slices, total, excluded)
}
