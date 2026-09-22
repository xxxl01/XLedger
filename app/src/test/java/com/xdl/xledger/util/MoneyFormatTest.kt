package com.xdl.xledger.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 抵消计算的基础：金额字符串 ↔ 分的换算必须精确。
 * 抵消全程用分做整数运算，避免出现 0.009999 之类的残余余额。
 */
class MoneyFormatTest {

    @Test
    fun toCents_解析两位小数字符串() {
        assertEquals(2850L, MoneyFormat.toCents("28.50"))
        assertEquals(0L, MoneyFormat.toCents("0.00"))
        assertEquals(10000L, MoneyFormat.toCents("100.00"))
        assertEquals(1L, MoneyFormat.toCents("0.01"))
    }

    @Test
    fun toCents_非法输入按零处理() {
        assertEquals(0L, MoneyFormat.toCents(""))
        assertEquals(0L, MoneyFormat.toCents("abc"))
    }

    @Test
    fun fromCents_补齐两位小数() {
        assertEquals("28.50", MoneyFormat.fromCents(2850))
        assertEquals("0.00", MoneyFormat.fromCents(0))
        assertEquals("0.05", MoneyFormat.fromCents(5))
        assertEquals("100.00", MoneyFormat.fromCents(10000))
    }

    /** 完全抵消必须精确得到 0.00，浮点直接相减在 0.1/0.2 这类金额上会残留误差。 */
    @Test
    fun 抵消余额计算精确到分() {
        val income = MoneyFormat.toCents("0.30")
        val expense = MoneyFormat.toCents("0.10")
        val offset = minOf(income, expense)

        assertEquals("0.10", MoneyFormat.fromCents(offset))
        assertEquals("0.20", MoneyFormat.fromCents(income - offset))
        assertEquals("0.00", MoneyFormat.fromCents(expense - offset))
    }

    /** 支出 60 被 25 抵消后剩 35，收入清零。 */
    @Test
    fun 部分抵消保留剩余金额() {
        val income = MoneyFormat.toCents("25.00")
        val expense = MoneyFormat.toCents("60.00")
        val offset = minOf(income, expense)

        assertEquals("25.00", MoneyFormat.fromCents(offset))
        assertEquals("0.00", MoneyFormat.fromCents(income - offset))
        assertEquals("35.00", MoneyFormat.fromCents(expense - offset))
    }
}
