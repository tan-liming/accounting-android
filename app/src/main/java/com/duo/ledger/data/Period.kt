package com.duo.ledger.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class Period(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

/** 周期的闭区间，用 epochDay 表示，可直接下推到 SQL 的 BETWEEN。 */
data class PeriodRange(
    val startDay: Long,
    val endDay: Long,
    val label: String
)

fun Period.rangeOf(anchor: LocalDate, weekStartsMonday: Boolean = true): PeriodRange {
    val start: LocalDate
    val end: LocalDate
    val text: String

    when (this) {
        Period.DAY -> {
            start = anchor
            end = anchor
            text = "${anchor.year}年${anchor.monthValue}月${anchor.dayOfMonth}日"
        }
        Period.WEEK -> {
            val firstDay = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            start = anchor.with(TemporalAdjusters.previousOrSame(firstDay))
            end = start.plusDays(6)
            text = "${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
        }
        Period.MONTH -> {
            val ym = YearMonth.from(anchor)
            start = ym.atDay(1)
            end = ym.atEndOfMonth()
            text = "${ym.year}年${ym.monthValue}月"
        }
        Period.YEAR -> {
            start = LocalDate.of(anchor.year, 1, 1)
            end = LocalDate.of(anchor.year, 12, 31)
            text = "${anchor.year}年"
        }
    }

    return PeriodRange(start.toEpochDay(), end.toEpochDay(), text)
}

fun Period.shift(anchor: LocalDate, delta: Int): LocalDate = when (this) {
    Period.DAY -> anchor.plusDays(delta.toLong())
    Period.WEEK -> anchor.plusWeeks(delta.toLong())
    Period.MONTH -> anchor.plusMonths(delta.toLong())
    Period.YEAR -> anchor.plusYears(delta.toLong())
}
