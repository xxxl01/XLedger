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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import java.util.Locale

/**
 * 分类统计页：按月查看支出 / 收入按一级分类聚合的占比，
 * 点击一级分类可展开查看其下二级分类明细。
 */
@Composable
fun MonthlyCategoryStatsScreen(
    year: Int,
    month: Int,
    onBack: () -> Unit,
    viewModel: MonthlyCategoryStatsViewModel = viewModel(
        factory = MonthlyCategoryStatsViewModel.factory(year, month),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors

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
                    )
                    StatPanel(
                        title = "收入分类",
                        groups = uiState.incomeStats,
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
private fun StatPanel(title: String, groups: List<CategoryGroup>) {
    val colors = MaterialTheme.xledgerColors
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                val expanded = expandedKeys.contains(group.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedKeys = if (expanded) expandedKeys - group.name else expandedKeys + group.name
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (group.children.isNotEmpty()) {
                        Icon(
                            painter = painterResource(
                                if (expanded) LucideR.drawable.lucide_ic_chevron_down
                                else LucideR.drawable.lucide_ic_chevron_right,
                            ),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.padding(end = 2.dp).size(14.dp),
                        )
                    } else {
                        Box(modifier = Modifier.size(16.dp))
                    }
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
                if (expanded && group.children.isNotEmpty()) {
                    group.children.forEach { child ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
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
