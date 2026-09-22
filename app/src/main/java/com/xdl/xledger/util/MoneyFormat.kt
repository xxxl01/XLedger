package com.xdl.xledger.util

import com.xdl.xledger.data.db.entity.TransactionEntity
import java.util.Locale
import kotlin.math.roundToLong

/** 金额格式化，对齐旧版 "-¥28.50" / "+¥28.50" 风格。 */
object MoneyFormat {

    private fun toDouble(amount: String): Double = amount.toDoubleOrNull() ?: 0.0

    /** 带收支符号：支出 "-¥28.50"，收入 "+¥28.50"。 */
    fun formatSigned(amount: String, transactionType: Int): String {
        val prefix = if (transactionType == TransactionEntity.TYPE_INCOME) "+" else "-"
        return String.format(Locale.US, "%s¥%.2f", prefix, toDouble(amount))
    }

    /** 无符号："¥28.50"。 */
    fun formatPlain(amount: String): String =
        String.format(Locale.US, "¥%.2f", toDouble(amount))

    /** 无符号纯数字："28.50"。 */
    fun toPlain(amount: String): String =
        String.format(Locale.US, "%.2f", toDouble(amount))

    /** 金额字符串 → 分。抵消计算全程用分做整数运算，避免浮点减法出现 0.009999 之类的余额。 */
    fun toCents(amount: String): Long = ((toDouble(amount)) * 100).roundToLong()

    /** 分 → 存储用的两位小数字符串。不走浮点格式化，避免 locale 小数点差异。 */
    fun fromCents(cents: Long): String =
        String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
}
