package com.xdl.xledger.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间工具。
 *
 * 存储格式与旧版一致：JS `Date.toISOString()` 输出 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`（UTC，固定三位毫秒）。
 * 字符串字典序即时间序，保证数据库范围查询正确。
 */
object TimeFormat {

    private val STORAGE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"))

    /** 与 JS `toISOString()` 输出一致的 UTC 字符串。 */
    fun toIsoString(instant: Instant): String = STORAGE_FORMAT.format(instant)

    /** 本地时区 yyyy-MM-dd HH:mm 输入 → 存储字符串（见 [toIsoString]）。 */
    fun parseLocalDateTime(value: String): Instant? {
        val normalized = value.trim().replace(' ', 'T')
        return try {
            LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    /** 存储字符串 → 本地时区 "yyyy-MM-dd HH:mm" 输入框格式。 */
    fun toLocalDateTimeInput(instant: Instant): String {
        val zoned = instant.atZone(ZoneId.systemDefault())
        return "%04d-%02d-%02d %02d:%02d".format(
            zoned.year,
            zoned.monthValue,
            zoned.dayOfMonth,
            zoned.hour,
            zoned.minute,
        )
    }

    /** 本地时区某月起始时刻（UTC 存储字符串），用于月份范围查询。 */
    fun monthStartIsoString(year: Int, month: Int): String {
        val start = LocalDateTime.of(year, month, 1, 0, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        return toIsoString(start)
    }

    /** 本地时区某月之后一个月的起始时刻（UTC 存储字符串），半开区间右端。 */
    fun monthEndIsoString(year: Int, month: Int): String {
        val end = LocalDateTime.of(year, month, 1, 0, 0)
            .plusMonths(1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        return toIsoString(end)
    }
}
