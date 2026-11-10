package com.xdl.xledger.ui.home

import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class MonthSummaryTextTest {
    private val categories = mapOf(
        1 to CategoryEntity(1, "餐饮", TransactionEntity.TYPE_EXPENSE, null, 0),
    )

    private fun transaction(
        amount: String,
        description: String? = "午餐",
        type: Int = TransactionEntity.TYPE_EXPENSE,
        occurredAt: String = "2026-05-04T04:30:00.000Z",
        categoryId: Int? = 1,
    ) = TransactionEntity(
        transactionType = type,
        amount = amount,
        occurredAt = occurredAt,
        description = description,
        categoryId = categoryId,
        dedupeKey = "test",
    )

    @Test
    fun includesMonthDetailsAndExactTotals() {
        val text = buildMonthSummaryText(
            2026, 5,
            listOf(
                transaction("0.10"),
                transaction("0.20", "晚餐"),
                transaction("0.10", "退款", TransactionEntity.TYPE_INCOME),
            ),
            categories,
            includeIncome = true,
        )

        assertTrue(text.startsWith("2026年5月账单\n"))
        assertTrue(text.contains("午餐 餐饮 -¥0.10"))
        assertTrue(text.contains("晚餐 餐饮 -¥0.20"))
        assertTrue(text.contains("退款 餐饮 +¥0.10"))
        assertTrue(text.endsWith("支出合计：¥0.30 收入合计：¥0.10 结余：¥-0.20"))
    }

    @Test
    fun hidingIncomeOmitsIncomeDetailsTotalsAndBalance() {
        val text = buildMonthSummaryText(
            2026, 5,
            listOf(transaction("10.00"), transaction("1000.00", "工资", TransactionEntity.TYPE_INCOME)),
            categories,
            includeIncome = false,
        )

        assertTrue(text.endsWith("支出合计：¥10.00"))
        listOf("工资", "1000", "收入", "结余", "+¥").forEach { assertFalse(text.contains(it)) }
    }

    @Test
    fun skipsFullyOffsetRecordsAndUsesFallbackLabels() {
        val text = buildMonthSummaryText(
            2026, 5,
            listOf(transaction("0.00", "已抵消的午餐"), transaction("5.00", null, categoryId = 999)),
            categories,
            includeIncome = true,
        )

        assertFalse(text.contains("已抵消的午餐"))
        assertTrue(text.contains("未命名记录 未分类 -¥5.00"))
        assertTrue(text.endsWith("支出合计：¥5.00 收入合计：¥0.00 结余：¥-5.00"))
    }

    @Test
    fun groupsAndSortsByLocalDateAcrossUtcDayBoundary() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val text = buildMonthSummaryText(
                2026, 5,
                listOf(
                    transaction("1.00", "早一笔", occurredAt = "2026-05-03T15:30:00.000Z"),
                    transaction("2.00", "晚一笔", occurredAt = "2026-05-03T16:30:00.000Z"),
                ),
                categories,
                includeIncome = true,
            )

            assertTrue(text.contains("2026-05-04\n00:30 晚一笔"))
            assertTrue(text.contains("2026-05-03\n23:30 早一笔"))
            assertTrue(text.indexOf("晚一笔") < text.indexOf("早一笔"))
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun emptyMonthProducesZeroTotals() {
        assertEquals(
            "2026年5月账单\n\n支出合计：¥0.00 收入合计：¥0.00 结余：¥0.00",
            buildMonthSummaryText(2026, 5, emptyList(), categories, includeIncome = true),
        )
    }
}
