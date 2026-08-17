package com.xdl.xledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import java.util.Locale

/**
 * 分类统计页：按月查看支出 / 收入在各分类的占比。
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
                        stats = uiState.expenseStats,
                    )
                    StatPanel(
                        title = "收入分类",
                        stats = uiState.incomeStats,
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
private fun StatPanel(title: String, stats: List<CategoryStat>) {
    val colors = MaterialTheme.xledgerColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        if (stats.isEmpty()) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            stats.forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stat.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stat.percent * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = "¥%.2f".format(stat.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
