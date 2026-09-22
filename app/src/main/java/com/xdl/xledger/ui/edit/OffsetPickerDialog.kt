package com.xdl.xledger.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.home.IconButton
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat

/**
 * 抵消弹窗：用当前收入抵消一笔支出。
 *
 * 候选支出按月加载，先翻到目标月份再选，不一次性查询全部历史记录；
 * 搜索框只在当月候选里按描述 / 金额过滤。
 */
@Composable
fun OffsetPickerDialog(
    state: OffsetPickerState,
    onShiftMonth: (Int) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSelectExpense: (Int) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val incomeCents = MoneyFormat.toCents(state.incomeAmount)
    val keyword = state.keyword.trim()
    val visibleExpenses = remember(state.expenses, keyword) {
        if (keyword.isEmpty()) {
            state.expenses
        } else {
            state.expenses.filter { expense ->
                expense.description?.contains(keyword, ignoreCase = true) == true ||
                    expense.amount.contains(keyword)
            }
        }
    }
    val selectedExpense = remember(visibleExpenses, state.selectedExpenseId) {
        visibleExpenses.firstOrNull { it.transactionId == state.selectedExpenseId }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "抵消支出",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }

            // 当前收入信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = state.incomeDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "可抵消金额 ${MoneyFormat.formatPlain(state.incomeAmount)}",
                    fontSize = 12.sp,
                    color = colors.income,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 月份翻页：候选支出按月加载
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    iconRes = LucideR.drawable.lucide_ic_chevron_left,
                    contentDescription = "上个月",
                    onClick = { onShiftMonth(-1) },
                )
                Text(
                    text = DateTimeFormat.formatMonthLabel(state.year, state.month),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    iconRes = LucideR.drawable.lucide_ic_chevron_right,
                    contentDescription = "下个月",
                    onClick = { onShiftMonth(1) },
                )
            }

            OutlinedTextField(
                value = state.keyword,
                onValueChange = onKeywordChange,
                placeholder = { Text("按描述或金额筛选") },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                visibleExpenses.isEmpty() -> {
                    Text(
                        text = if (state.expenses.isEmpty()) {
                            "这个月没有可抵消的支出，换个月份看看。"
                        } else {
                            "没有匹配的支出。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(visibleExpenses, key = { it.transactionId }) { expense ->
                            ExpenseCandidateRow(
                                expense = expense,
                                selected = expense.transactionId == state.selectedExpenseId,
                                enabled = !state.isSubmitting,
                                onClick = { onSelectExpense(expense.transactionId) },
                            )
                        }
                    }
                }
            }

            // 抵消结果预览
            if (selectedExpense != null) {
                val expenseCents = MoneyFormat.toCents(selectedExpense.amount)
                val offsetCents = minOf(incomeCents, expenseCents)
                Text(
                    text = buildString {
                        append("抵消 ")
                        append(MoneyFormat.formatPlain(MoneyFormat.fromCents(offsetCents)))
                        append("，收入剩余 ")
                        append(MoneyFormat.formatPlain(MoneyFormat.fromCents(incomeCents - offsetCents)))
                        append("，支出剩余 ")
                        append(MoneyFormat.formatPlain(MoneyFormat.fromCents(expenseCents - offsetCents)))
                        append("。两条记录都会保留。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.expense,
                )
            }

            OffsetConfirmButton(
                enabled = !state.isSubmitting && selectedExpense != null,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ExpenseCandidateRow(
    expense: TransactionEntity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else colors.inputBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                color = if (selected) colors.accentContainer else colors.card,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.description ?: "未命名记录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DateTimeFormat.formatMonthDay(expense.occurredAt) + " " +
                    DateTimeFormat.formatTime(expense.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = MoneyFormat.formatPlain(expense.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.expense,
        )
    }
}

@Composable
private fun OffsetConfirmButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (enabled) colors.textPrimary else colors.disabled,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "确认抵消",
            fontWeight = FontWeight.Bold,
            color = colors.card,
        )
    }
}
