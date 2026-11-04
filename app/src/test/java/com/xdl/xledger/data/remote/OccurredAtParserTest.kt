package com.xdl.xledger.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OccurredAtParserTest {

    // 固定东八区，结果按本地时间换算成 UTC 后断言，避免依赖运行机器的时区
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 4)

    private fun parse(text: String?, now: LocalDate? = today) = OccurredAtParser.parse(text, now, zone)

    @Test
    fun `微信 月日加时间 补今年`() {
        assertEquals("2026-11-03T04:30:00.000Z", parse("11月3日 12:30"))
    }

    @Test
    fun `支付宝 昨天和前天按今天推算`() {
        assertEquals("2026-11-03T12:54:00.000Z", parse("昨天 20:54"))
        assertEquals("2026-11-02T01:05:00.000Z", parse("前天 9:05"))
        assertEquals("2026-11-04T02:00:00.000Z", parse("今天10:00"))
    }

    @Test
    fun `昨天跨月跨年`() {
        assertEquals("2026-10-31T04:00:00.000Z", parse("昨天 12:00", LocalDate.of(2026, 11, 1)))
        assertEquals("2025-12-31T04:00:00.000Z", parse("昨天 12:00", LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `月日晚于今天说明是去年`() {
        // 今天是 1 月 5 日，截图里的 12 月 30 日只能是去年
        assertEquals("2025-12-30T04:00:00.000Z", parse("12月30日 12:00", LocalDate.of(2026, 1, 5)))
        // 今天当天不算未来
        assertEquals("2026-11-04T04:00:00.000Z", parse("11月4日 12:00"))
    }

    @Test
    fun `数字月日 单位数日期和斜杠`() {
        assertEquals("2026-11-03T04:30:00.000Z", parse("11-03 12:30"))
        assertEquals("2026-11-03T04:30:00.000Z", parse("11/3 12:30"))
        assertEquals("2026-10-05T16:00:00.000Z", parse("10-6"))
    }

    @Test
    fun `完整日期保持原样`() {
        assertEquals("2026-11-03T04:30:05.000Z", parse("2026-11-03 12:30:05"))
        assertEquals("2025-06-01T04:00:00.000Z", parse("2025年6月1日 12:00"))
        assertEquals("2026-11-03T04:30:00.000Z", parse("2026-11-03T12:30:00"))
        assertEquals("2026-11-03T04:30:00.000Z", parse("2026-11-03T12:30:00+08:00"))
    }

    @Test
    fun `没有时间时取零点`() {
        assertEquals("2026-11-02T16:00:00.000Z", parse("11月3日"))
        assertEquals("2026-11-02T16:00:00.000Z", parse("昨天"))
    }

    @Test
    fun `无法识别返回 null`() {
        assertNull(parse(null))
        assertNull(parse("  "))
        assertNull(parse("上周五 12:30"))
        assertNull(parse("2月30日 12:00"))
        assertNull(parse("11月3日 25:99"))
    }

    @Test
    fun `缺少今天时只能解析完整日期`() {
        assertNull(parse("昨天 12:30", now = null))
        assertEquals("2026-11-03T04:30:00.000Z", parse("2026-11-03 12:30", now = null))
    }
}
