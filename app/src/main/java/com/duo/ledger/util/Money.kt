package com.duo.ledger.util

import java.math.BigDecimal
import java.math.RoundingMode

private val HUNDRED = BigDecimal(100)

/** 分 -> "123.45"。负数为红冲（退款/报销）。 */
fun Long.centsToYuan(): String =
    BigDecimal.valueOf(this)
        .divide(HUNDRED, 2, RoundingMode.HALF_UP)
        .toPlainString()

/** "123.45" -> 12345。走 BigDecimal，不用 Double 解析金额。 */
fun String.yuanToCents(): Long? {
    val text = trim()
    if (text.isEmpty()) return null
    return try {
        val value = BigDecimal(text)
        if (value.scale() > 2) return null
        value.setScale(2, RoundingMode.UNNECESSARY)
            .multiply(HUNDRED)
            .longValueExact()
    } catch (e: NumberFormatException) {
        null
    } catch (e: ArithmeticException) {
        null
    }
}
