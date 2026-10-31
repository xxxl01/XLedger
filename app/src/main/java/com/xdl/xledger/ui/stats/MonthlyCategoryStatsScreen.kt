package com.xdl.xledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import java.util.Locale

/**
 * 分类统计页：按月查看支出 / 收入按一级分类聚合的占比，二级分类默认展开；
 * 点击一级或二级分类进入明细页，按金额从大到小查看该分类的每条记录。
 */
@Composable
fun MonthlyCategoryStatsScreen(
    year: Int,
    month: Int,
    onBack: () -> Unit,
    onOpenCategoryTransactions: (transactionType: Int, categoryId: Int?, includeChildren: Boolean) -> Unit,
    viewModel: MonthlyCategoryStatsViewModel = viewModel(
        factory = MonthlyCategoryStatsViewModel.factory(year, month),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从明细页改了记录后返回，静默刷新统计
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load(showLoading = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SubPageScaffold(
        title = "分类统计",
        subtitle = DateTimeFormat.formatMonthLabel(year, month),
        onBack = onBack,
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            uiState.errorMessage != null -> {
                val error = uiState.errorMessage
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.expense,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TotalRow(
                        expenseTotal = uiState.expenseTotal,
                        incomeTotal = uiState.incomeTotal,
                    )
                    StatPanel(
                        title = "支出分类",
                        groups = uiState.expenseStats,
                        onOpen = { categoryId, includeChildren ->
                            onOpenCategoryTransactions(TransactionEntity.TYPE_EXPENSE, categoryId, includeChildren)
                        },
                    )
                    StatPanel(
                        title = "收入分类",
                        groups = uiState.incomeStats,
                        onOpen = { categoryId, includeChildren ->
                            onOpenCategoryTransactions(TransactionEntity.TYPE_INCOME, categoryId, includeChildren)
                        },
                    )
                    Box(modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun TotalRow(expenseTotal: Double, incomeTotal: Double) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TotalItem("支出", expenseTotal, colors.expense, Modifier.weight(1f))
        TotalItem("收入", incomeTotal, colors.income, Modifier.weight(1f))
    }
}

@Composable
private fun TotalItem(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = MaterialTheme.xledgerColors
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            text = "¥%.2f".format(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
    }
}

@Composable
private fun StatPanel(
    title: String,
    groups: List<CategoryGroup>,
    onOpen: (categoryId: Int?, includeChildren: Boolean) -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        if (groups.isEmpty()) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            groups.forEach { group ->
                // 一级行：查看该一级分类（含其下所有二级分类）的全部记录
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(group.categoryId, true) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", group.percent * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = "¥%.2f".format(group.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
                // 二级行默认展开：点击只看该二级分类的记录
                if (group.children.isNotEmpty()) {
                    group.children.forEach { child ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(child.categoryId, false) }
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = child.name,
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", child.percent * 100),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(
                                text = "¥%.2f".format(child.amount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
