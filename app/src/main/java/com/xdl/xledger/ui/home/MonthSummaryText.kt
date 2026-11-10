package com.xdl.xledger.ui.home

import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat
import java.math.BigDecimal

/** 整月账单不受列表分类筛选影响；隐藏收入时，明细与合计都不包含收入。 */
internal fun buildMonthSummaryText(
    year: Int,
    month: Int,
    transactions: List<TransactionEntity>,
    categoryById: Map<Int, CategoryEntity>,
    includeIncome: Boolean,
): String {
    val records = transactions.filter { transaction ->
        MoneyFormat.toCents(transaction.amount) > 0L &&
            (includeIncome || transaction.transactionType != TransactionEntity.TYPE_INCOME)
    }.sortedByDescending { it.occurredAt }
    var expenseCents = 0L
    var incomeCents = 0L

    return buildString {
        appendLine("${DateTimeFormat.formatMonthLabel(year, month)}账单")
        records.groupBy { DateTimeFormat.formatDateKey(it.occurredAt) }.forEach { (day, dailyRecords) ->
            appendLine()
            appendLine(day)
            dailyRecords.forEach { transaction ->
                val cents = MoneyFormat.toCents(transaction.amount)
                if (transaction.transactionType == TransactionEntity.TYPE_INCOME) {
                    incomeCents += cents
                } else {
                    expenseCents += cents
                }
                val category = transaction.categoryId?.let(categoryById::get)?.categoryName ?: "未分类"
                appendLine(
                    "${DateTimeFormat.formatTime(transaction.occurredAt)} " +
                        "${transaction.description ?: "未命名记录"} $category " +
                        MoneyFormat.formatSigned(transaction.amount, transaction.transactionType),
                )
            }
        }
        appendLine()
        append("支出合计：¥${BigDecimal.valueOf(expenseCents, 2).toPlainString()}")
        if (includeIncome) {
            append(" 收入合计：¥${BigDecimal.valueOf(incomeCents, 2).toPlainString()}")
            append(" 结余：¥${BigDecimal.valueOf(incomeCents - expenseCents, 2).toPlainString()}")
        }
    }
}
