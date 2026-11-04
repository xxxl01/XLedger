package com.xdl.xledger.data.remote

import com.xdl.xledger.util.TimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 把 LLM 从截图里原样抄下来的日期时间文字解析成存储用的 UTC ISO 字符串。
 *
 * 截图上的日期形态因应用而异：微信是“11月3日 12:30”，支付宝是“昨天 12:30”“前天 12:30”，
 * 也可能是“11-03 12:30”或完整的“2026-11-03 12:30:00”。模型只负责抄写，
 * 相对日期与缺失的年份都在这里按“今天是哪天”推算，不依赖模型算日期。
 */
object OccurredAtParser {

    /** 截图文字没有时间时的默认时间。 */
    private val DEFAULT_TIME = LocalTime.MIDNIGHT

    private val YEAR_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    )

    private const val TIME = "(\\d{1,2}:\\d{2}(?::\\d{2})?)"

    /** 相对日期（今天/昨天/前天）后跟可选时间，如 “昨天 12:30”。 */
    private val RELATIVE_DAY_REGEX = Regex("(今天|昨天|前天)\\s*$TIME?")

    /** 中文年月日 / 月日：2026年11月3日 12:30、11月3日 12:30（年份可省）。 */
    private val CHINESE_DATE_REGEX = Regex("(?:(\\d{4})\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*$TIME?")

    /** 数字年月日 / 月日：2026-11-3、11-03 12:30、11/3 12:30（年份可省）。 */
    private val NUMERIC_DATE_REGEX = Regex("^(?:(\\d{4})[-/.])?(\\d{1,2})[-/.](\\d{1,2})(?:\\s+$TIME)?$")

    /**
     * @param value 模型输出的日期时间文字
     * @param today “今天是哪天”，用于推算相对日期与补全年份
     * @return 存储用的 UTC ISO 字符串；无法识别返回 null
     */
    fun parse(
        value: String?,
        today: LocalDate?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()

        // ISO 带时区
        runCatching { ZonedDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }
        runCatching { OffsetDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }

        // 无时区的日期时间（ISO 本地格式或 yyyy-MM-dd HH:mm 等），按本地时区解释
        runCatching { LocalDateTime.parse(raw).atZone(zone).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }
        YEAR_FORMATS.forEach { formatter ->
            runCatching { LocalDateTime.parse(raw, formatter).atZone(zone).toInstant() }
                .getOrNull()?.let { return TimeFormat.toIsoString(it) }
        }

        // 只有日期（2026-11-03）：时间取 00:00
        runCatching { LocalDate.parse(raw.replace('/', '-')).atTime(DEFAULT_TIME).atZone(zone).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }

        // 以下都需要“今天是哪天”
        val todayDate = today ?: return null

        // 相对日期：今天 / 昨天 / 前天
        RELATIVE_DAY_REGEX.find(raw)?.let { match ->
            val dayOffset = when (match.groupValues[1]) {
                "今天" -> 0L
                "昨天" -> 1L
                else -> 2L
            }
            return toIso(todayDate.minusDays(dayOffset), match.groupValues[2], zone)
        }

        // 中文年月日 / 月日，数字月日
        val parts = CHINESE_DATE_REGEX.find(raw)?.destructured?.toList()
            ?: NUMERIC_DATE_REGEX.find(raw)?.destructured?.toList()
            ?: return null
        val (yearText, monthText, dayText, timeText) = parts
        val month = monthText.toInt()
        val day = dayText.toInt()
        val date = if (yearText.isNotEmpty()) {
            runCatching { LocalDate.of(yearText.toInt(), month, day) }.getOrNull()
        } else {
            resolveYearless(month, day, todayDate)
        } ?: return null
        return toIso(date, timeText, zone)
    }

    /**
     * 补全缺失的年份：先用今天所在的年份，若得到的日期晚于今天（如今天 1 月、截图里是 12 月），
     * 说明是去年的记录，退回一年。支付记录不会发生在未来。
     */
    private fun resolveYearless(month: Int, day: Int, today: LocalDate): LocalDate? {
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isAfter(today)) return thisYear
        // 今年的这一天不存在（如 2 月 29 日）或在未来：用去年
        return runCatching { LocalDate.of(today.year - 1, month, day) }.getOrNull()
    }

    private fun toIso(date: LocalDate, timeText: String, zone: ZoneId): String? {
        val time = if (timeText.isEmpty()) DEFAULT_TIME else parseTime(timeText) ?: return null
        return TimeFormat.toIsoString(LocalDateTime.of(date, time).atZone(zone).toInstant())
    }

    /** 解析 H:mm / HH:mm / HH:mm:ss，小时不足两位也接受（LocalTime.parse 要求两位）。 */
    private fun parseTime(text: String): LocalTime? {
        val fields = text.split(':').map { it.toIntOrNull() ?: return null }
        return runCatching {
            LocalTime.of(fields[0], fields[1], fields.getOrElse(2) { 0 })
        }.getOrNull()
    }
}
