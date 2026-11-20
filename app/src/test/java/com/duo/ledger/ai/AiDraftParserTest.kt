package com.duo.ledger.ai

import com.duo.ledger.data.local.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AiDraftParserTest {
    private val members = listOf(1L to "我", 2L to "对象")
    private val expenseCats = listOf(10L to "餐饮", 11L to "交通")
    private val incomeCats = listOf(20L to "工资", 21L to "红包")
    private val today = LocalDate.of(2026, 11, 10)

    private fun validate(raw: AiRawDraft) =
        validateAiDraft(raw, members, expenseCats, incomeCats, 1L, today)

    @Test
    fun normalExpense() {
        val draft = validate(
            AiRawDraft(type = "EXPENSE", amount = "35.5", purpose = "午饭", member = "对象", category = "餐饮")
        )
        assertNotNull(draft)
        assertEquals(EntryType.EXPENSE, draft!!.type)
        assertEquals("35.50", draft.amountText)
        assertEquals("午饭", draft.purpose)
        assertEquals(2L, draft.memberId)
        assertEquals(10L, draft.categoryId)
    }

    @Test
    fun unknownTypeFallsBackToExpense() {
        val draft = validate(AiRawDraft(type = "SOMETHING", amount = "12", category = "交通"))
        assertEquals(EntryType.EXPENSE, draft?.type)
        assertEquals(11L, draft?.categoryId)
    }

    @Test
    fun missingTypeFallsBackToExpense() {
        assertEquals(EntryType.EXPENSE, validate(AiRawDraft(amount = "12"))?.type)
    }

    @Test
    fun invalidAmountRejected() {
        assertNull(validate(AiRawDraft(amount = "")))
        assertNull(validate(AiRawDraft(amount = "abc")))
        assertNull(validate(AiRawDraft(amount = "12.345")))
        assertNull(validate(AiRawDraft(amount = "0")))
    }

    @Test
    fun negativeOnlyAllowedForExpense() {
        assertEquals("-20.00", validate(AiRawDraft(type = "EXPENSE", amount = "-20"))?.amountText)
        assertNull(validate(AiRawDraft(type = "INCOME", amount = "-20")))
        assertNull(validate(AiRawDraft(type = "TRANSFER_OUT", amount = "-20")))
    }

    @Test
    fun unknownMemberFallsBackToDefault() {
        assertEquals(1L, validate(AiRawDraft(amount = "12", member = "陌生人"))?.memberId)
    }

    @Test
    fun unknownCategoryFallsBackToFirstOfPool() {
        assertEquals(10L, validate(AiRawDraft(type = "EXPENSE", amount = "12", category = "不存在"))?.categoryId)
    }

    @Test
    fun incomeUsesIncomeCategoryPool() {
        val draft = validate(AiRawDraft(type = "INCOME", amount = "5000", category = "工资"))
        assertEquals(EntryType.INCOME, draft?.type)
        assertEquals(20L, draft?.categoryId)
    }

    @Test
    fun transferTypesHaveNoCategory() {
        assertNull(validate(AiRawDraft(type = "TRANSFER_OUT", amount = "100", category = "餐饮"))?.categoryId)
        assertNull(validate(AiRawDraft(type = "TRANSFER", amount = "100", category = "餐饮"))?.categoryId)
    }

    @Test
    fun textIsSanitized() {
        val draft = validate(
            AiRawDraft(amount = "12", purpose = "   ", counterparty = "  朋友  ")
        )
        assertEquals("未填用途", draft?.purpose)
        assertEquals("朋友", draft?.counterparty)
    }

    @Test
    fun missingDateFallsBackToToday() {
        assertEquals(today, validate(AiRawDraft(amount = "12"))?.date)
        assertEquals(today, validate(AiRawDraft(amount = "12", date = "   "))?.date)
    }

    @Test
    fun isoDateIsUsed() {
        val draft = validate(AiRawDraft(amount = "12", date = "2025-03-15"))
        assertEquals(LocalDate.of(2025, 3, 15), draft?.date)
    }

    @Test
    fun malformedDateFallsBackToToday() {
        assertEquals(today, validate(AiRawDraft(amount = "12", date = "昨天"))?.date)
        assertEquals(today, validate(AiRawDraft(amount = "12", date = "2025/03/15"))?.date)
        assertEquals(today, validate(AiRawDraft(amount = "12", date = "2025-02-30"))?.date)
    }
}
