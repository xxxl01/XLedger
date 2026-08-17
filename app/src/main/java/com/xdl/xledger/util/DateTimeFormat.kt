package com.xdl.xledger.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 展示层时间格式化。
 *
 * 存储为 UTC ISO-8601（如 "2026-05-04T04:30:00.000Z"），展示时一律转本地时区，
 * 与旧版 JS `new Date(iso)` 取本地时分的行为一致。
 */
object DateTimeFormat {

    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /** 解析存储字符串为本地时区的时刻；解析失败返回 null。 */
    fun parseToLocal(iso: String): ZonedDateTime? = try {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
    } catch (_: Exception) {
        null
    }

    /** "5月4日" */
    fun formatMonthDay(iso: String): String {
        val zoned = parseToLocal(iso) ?: return iso
        return "${zoned.monthValue}月${zoned.dayOfMonth}日"
    }

    /** "12:30" */
    fun formatTime(iso: String): String {
        val zoned = parseToLocal(iso) ?: return ""
        return TIME_FORMATTER.format(zoned)
    }

    /** "2026-05-04"（按本地时区） */
    fun formatDateKey(iso: String): String {
        val zoned = parseToLocal(iso) ?: return iso
        return "%04d-%02d-%02d".format(zoned.year, zoned.monthValue, zoned.dayOfMonth)
    }

    /** "2026年5月" */
    fun formatMonthLabel(year: Int, month: Int): String = "${year}年${month}月"
}
