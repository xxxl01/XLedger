package com.xdl.xledger.util

import com.xdl.xledger.data.db.entity.TransactionEntity
import java.util.Locale

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
}
