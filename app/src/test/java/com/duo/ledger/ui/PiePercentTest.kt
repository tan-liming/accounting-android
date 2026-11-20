package com.duo.ledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiePercentTest {
    @Test
    fun thirdsAddUpTo100() {
        val p = distributePercent(listOf(1L, 1L, 1L), 3L)
        assertEquals(100, p.sum())
        assertEquals(listOf(34, 33, 33), p)
    }

    @Test
    fun typicalTopSixPlusOther() {
        val rows = listOf(
            "餐饮" to 5000L, "居住" to 3000L, "交通" to 2000L,
            "日用" to 1500L, "娱乐" to 1200L, "医疗" to 900L,
            "人情" to 700L, "服饰" to 500L, "教育" to 300L,
            "通讯" to 200L, "退款" to -800L, "空" to 0L
        )
        val data = preparePieData(rows)
        assertEquals(7, data.slices.size)
        assertEquals("其他", data.slices.last().label)
        assertEquals(1700L, data.slices.last().cents)
        assertEquals(15300L, data.totalCents)
        assertEquals(2, data.excludedCount)
        assertEquals(100, data.slices.sumOf { it.percent })
        assertTrue(data.slices.all { it.percent >= 0 })
    }

    @Test
    fun sevenEqualSlicesStillSum100() {
        val p = distributePercent(List(7) { 1L }, 7L)
        assertEquals(100, p.sum())
        assertTrue(p.all { it in 14..15 })
    }

    @Test
    fun singleSliceIs100() {
        assertEquals(listOf(100), distributePercent(listOf(12345L), 12345L))
        assertEquals(100, preparePieData(listOf("唯一" to 500L)).slices.single().percent)
    }

    @Test
    fun emptyAndAllNegativeAreSafe() {
        assertTrue(distributePercent(emptyList(), 0L).isEmpty())
        assertEquals(listOf(0), distributePercent(listOf(0L), 0L))
        val data = preparePieData(listOf("退款" to -500L, "零" to 0L))
        assertTrue(data.slices.isEmpty())
        assertEquals(0L, data.totalCents)
        assertEquals(2, data.excludedCount)
    }

    @Test
    fun largeAmountsDoNotOverflowDisplayPercent() {
        val p = distributePercent(listOf(9_000_000_000_000L, 1L, 1L), 9_000_000_000_002L)
        assertEquals(100, p.sum())
        assertEquals(100, p[0])
    }
}
