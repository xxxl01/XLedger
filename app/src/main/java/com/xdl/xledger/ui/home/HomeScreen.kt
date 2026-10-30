package com.xdl.xledger.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat

/**
 * 账本主页：按月查看收支流水，支持筛选；汇总卡片与操作入口。
 */
@Composable
fun HomeScreen(
    onOpenUpload: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTransactionEdit: (Int) -> Unit,
    onOpenMonthlyCategoryStats: (year: Int, month: Int) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从上传页、分类页或编辑页返回时，主页仍复用原来的 ViewModel，需要主动重新读取数据库。
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeContent(
        uiState = uiState,
        onShiftMonth = viewModel::shiftMonth,
        onSelectFilter = viewModel::setFilter,
        onToggleHideIncome = viewModel::toggleHideIncome,
        onOpenEdit = onOpenTransactionEdit,
        onOpenUpload = onOpenUpload,
        onOpenCategories = onOpenCategories,
        onOpenMonthlyCategoryStats = {
            onOpenMonthlyCategoryStats(uiState.year, uiState.month)
        },
        onRetry = viewModel::refresh,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onShiftMonth: (Int) -> Unit,
    onSelectFilter: (TransactionFilter) -> Unit,
    onToggleHideIncome: () -> Unit,
    onOpenEdit: (Int) -> Unit,
    onOpenUpload: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenMonthlyCategoryStats: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val hideIncome = uiState.hideIncome
    // 隐藏收入时，收入记录整体不进入列表；汇总里的收入和结余（结余会暴露收入）单独遮住
    val visibleTransactions = remember(uiState.transactions, hideIncome) {
        if (hideIncome) {
            uiState.transactions.filter { it.transactionType != TransactionEntity.TYPE_INCOME }
        } else {
            uiState.transactions
        }
    }
    // 隐藏收入时“收入”筛选没有意义，回落到“全部”
    val filter = if (hideIncome && uiState.filter == TransactionFilter.Income) {
        TransactionFilter.All
    } else {
        uiState.filter
    }
    val summary = remember(uiState.transactions) {
        var expense = 0.0
        var income = 0.0
        for (transaction in uiState.transactions) {
            val amount = transaction.amount.toDoubleOrNull() ?: 0.0
            if (transaction.transactionType == TransactionEntity.TYPE_INCOME) {
                income += amount
            } else {
                expense += amount
            }
        }
        Triple(expense, income, income - expense)
    }
    val categoryById = remember(uiState.categories) {
        uiState.categories.associateBy { it.categoryId }
    }
    val filteredTransactions = remember(visibleTransactions, filter) {
        visibleTransactions.filter { transaction ->
            when (filter) {
                TransactionFilter.All -> true
                TransactionFilter.Expense ->
                    transaction.transactionType == TransactionEntity.TYPE_EXPENSE
                TransactionFilter.Income ->
                    transaction.transactionType == TransactionEntity.TYPE_INCOME
                TransactionFilter.Uncategorized -> transaction.categoryId == null
            }
        }
    }
    val groupedByDay = remember(filteredTransactions) {
        filteredTransactions.groupBy { DateTimeFormat.formatDateKey(it.occurredAt) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
    ) {
        HomeHeader(
            year = uiState.year,
            month = uiState.month,
            onShiftMonth = onShiftMonth,
            hideIncome = hideIncome,
            onToggleHideIncome = onToggleHideIncome,
            onOpenCategories = onOpenCategories,
            onOpenUpload = onOpenUpload,
        )

        SummaryCard(
            expense = summary.first,
            income = summary.second,
            balance = summary.third,
            hideIncome = hideIncome,
            onClick = onOpenMonthlyCategoryStats,
        )

        FilterRow(
            selected = filter,
            hideIncome = hideIncome,
            onSelect = onSelectFilter,
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            uiState.errorMessage != null -> {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            else -> {
                TransactionList(
                    groupedByDay = groupedByDay,
                    categoryById = categoryById,
                    hideIncome = hideIncome,
                    onOpenEdit = onOpenEdit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    year: Int,
    month: Int,
    onShiftMonth: (Int) -> Unit,
    hideIncome: Boolean,
    onToggleHideIncome: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenUpload: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(
            iconRes = LucideR.drawable.lucide_ic_chevron_left,
            contentDescription = "上个月",
            onClick = { onShiftMonth(-1) },
        )
        Text(
            text = DateTimeFormat.formatMonthLabel(year, month),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            iconRes = LucideR.drawable.lucide_ic_chevron_right,
            contentDescription = "下个月",
            onClick = { onShiftMonth(1) },
        )
        IconButton(
            iconRes = if (hideIncome) {
                LucideR.drawable.lucide_ic_eye_off
            } else {
                LucideR.drawable.lucide_ic_eye
            },
            contentDescription = if (hideIncome) "显示收入" else "隐藏收入",
            onClick = onToggleHideIncome,
        )
        SecondaryTextButton(text = "分类", onClick = onOpenCategories)
        PrimaryIconButton(
            iconRes = LucideR.drawable.lucide_ic_upload,
            contentDescription = "导入截图",
            onClick = onOpenUpload,
        )
    }
}

@Composable
private fun SummaryCard(
    expense: Double,
    income: Double,
    balance: Double,
    hideIncome: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        SummaryItem(label = "支出", amount = expense, modifier = Modifier.weight(1f))
        SummaryItem(label = "收入", amount = income, masked = hideIncome, modifier = Modifier.weight(1f))
        SummaryItem(label = "结余", amount = balance, masked = hideIncome, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
) {
    val colors = MaterialTheme.xledgerColors
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            text = if (masked) "¥****" else "¥%.2f".format(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun FilterRow(
    selected: TransactionFilter,
    hideIncome: Boolean,
    onSelect: (TransactionFilter) -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TransactionFilter.entries.filter { !hideIncome || it != TransactionFilter.Income }.forEach { filter ->
            val active = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (active) colors.accent else colors.inputBorder,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(
                        color = if (active) colors.accentContainer else colors.card,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = filter.label,
                    fontSize = 12.sp,
                    color = if (active) colors.accent else colors.textSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun TransactionList(
    groupedByDay: Map<String, List<TransactionEntity>>,
    categoryById: Map<Int, CategoryEntity>,
    hideIncome: Boolean,
    onOpenEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors

    if (groupedByDay.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                    .padding(24.dp),
            ) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "上传支付记录截图并确认后，交易会进入这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        groupedByDay.forEach { (day, dayTransactions) ->
            item(key = "day_$day") {
                DayHeader(
                    transactions = dayTransactions,
                    categoryById = categoryById,
                    hideIncome = hideIncome,
                )
            }
            items(dayTransactions, key = { it.transactionId }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    categoryName = transaction.categoryId?.let(categoryById::get)?.categoryName,
                    onClick = { onOpenEdit(transaction.transactionId) },
                )
            }
        }
    }
}

/** 日期分组标题：显示日期与当天收支合计，右侧提供“复制”按钮，把当天支出情况复制为文本。 */
@Composable
private fun DayHeader(
    transactions: List<TransactionEntity>,
    categoryById: Map<Int, CategoryEntity>,
    hideIncome: Boolean,
) {
    val colors = MaterialTheme.xledgerColors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // 当天收支合计
    val daySummary = remember(transactions) {
        var expense = 0.0
        var income = 0.0
        for (transaction in transactions) {
            val amount = transaction.amount.toDoubleOrNull() ?: 0.0
            if (transaction.transactionType == TransactionEntity.TYPE_INCOME) {
                income += amount
            } else {
                expense += amount
            }
        }
        expense to income
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = DateTimeFormat.formatMonthDay(transactions.first().occurredAt),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (daySummary.first > 0.0) {
            Text(
                text = "支出 ¥%.2f".format(daySummary.first),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.expense,
            )
        }
        if (daySummary.second > 0.0) {
            Text(
                text = "收入 ¥%.2f".format(daySummary.second),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.income,
            )
        }
        Box(
            modifier = Modifier
                .border(1.dp, colors.inputBorder, RoundedCornerShape(6.dp))
                .clickable {
                    clipboard.setText(AnnotatedString(buildDaySummaryText(transactions, categoryById, includeIncome = !hideIncome)))
                    Toast.makeText(context, "已复制当天支出情况", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_copy),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "复制",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** 构建当天支出摘要文本：日期标题 + 每条记录（时间/描述/分类/金额）+ 支出与收入合计。 */
private fun buildDaySummaryText(
    transactions: List<TransactionEntity>,
    categoryById: Map<Int, CategoryEntity>,
    includeIncome: Boolean,
): String {
    var expense = 0.0
    var income = 0.0
    return buildString {
        appendLine("${DateTimeFormat.formatMonthDay(transactions.first().occurredAt)} 支出情况")
        transactions.forEach { transaction ->
            val amount = transaction.amount.toDoubleOrNull() ?: 0.0
            // 已被完全抵消的记录金额为 0，复制出去没有意义
            if (amount <= 0) return@forEach
            if (transaction.transactionType == TransactionEntity.TYPE_INCOME) {
                income += amount
            } else {
                expense += amount
            }
            val category = transaction.categoryId?.let(categoryById::get)?.categoryName ?: "未分类"
            appendLine(
                "${DateTimeFormat.formatTime(transaction.occurredAt)} " +
                    "${transaction.description ?: "未命名记录"} $category " +
                    MoneyFormat.formatSigned(transaction.amount, transaction.transactionType),
            )
        }
        append("支出合计：¥%.2f".format(expense))
        if (includeIncome) append(" 收入合计：¥%.2f".format(income))
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    categoryName: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val isIncome = transaction.transactionType == TransactionEntity.TYPE_INCOME
    // 被完全抵消的记录保留在列表里，金额为 0，弱化显示避免看着像脏数据
    val isFullyOffset = MoneyFormat.toCents(transaction.amount) == 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description ?: "未命名记录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFullyOffset) colors.disabled else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(DateTimeFormat.formatTime(transaction.occurredAt))
                    append(" · ")
                    append(categoryName ?: "未分类")
                    if (isFullyOffset) append(" · 已抵消")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = MoneyFormat.formatSigned(transaction.amount, transaction.transactionType),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                isFullyOffset -> colors.disabled
                isIncome -> colors.income
                else -> colors.expense
            },
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    Column(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.expenseBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.expense,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        SecondaryTextButton(text = "重试", onClick = onRetry)
    }
}

// ---- 通用小组件 ----

@Composable
internal fun IconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun PrimaryIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.textPrimary, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colors.card,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
internal fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
    }
}
