package com.xdl.xledger.ui.offsets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.data.db.dao.OffsetDetail
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat

/**
 * 抵消记录页：查看历史抵消台账。
 *
 * 每条抵消展示收入方与支出方的当前信息；两侧金额是**当前剩余金额**，
 * 因为记录本身可被后续再抵消或编辑，只有「抵消金额」是这次操作的定值。
 */
@Composable
fun OffsetsScreen(
    onBack: () -> Unit,
    viewModel: OffsetsViewModel = viewModel(factory = OffsetsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors

    SubPageScaffold(
        title = "抵消记录",
        subtitle = "共 ${uiState.offsets.size} 次抵消",
        onBack = onBack,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.expense,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                uiState.offsets.isEmpty() -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(colors.card, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                            .padding(20.dp),
                    ) {
                        Text(
                            text = "暂无抵消记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "在账本里点开一笔收入，选择「抵消支出」即可抵消对应的支出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.offsets, key = { it.offsetId }) { offset ->
                            OffsetCard(offset = offset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetCard(offset: OffsetDetail) {
    val colors = MaterialTheme.xledgerColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DateTimeFormat.formatMonthDay(offset.createdAt) + " " +
                    DateTimeFormat.formatTime(offset.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "抵消 " + MoneyFormat.formatPlain(offset.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.accent,
            )
        }
        OffsetSide(
            label = "收入",
            description = offset.incomeDescription,
            amount = offset.incomeAmount,
            occurredAt = offset.incomeOccurredAt,
            labelColor = colors.income,
        )
        OffsetSide(
            label = "支出",
            description = offset.expenseDescription,
            amount = offset.expenseAmount,
            occurredAt = offset.expenseOccurredAt,
            labelColor = colors.expense,
        )
    }
}

/** 抵消的一侧：金额为 null 说明该记录已被用户手动删除。 */
@Composable
private fun OffsetSide(
    label: String,
    description: String?,
    amount: String?,
    occurredAt: String?,
    labelColor: androidx.compose.ui.graphics.Color,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
        )
        if (amount == null) {
            Text(
                text = "记录已删除",
                style = MaterialTheme.typography.labelSmall,
                color = colors.disabled,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }
        Text(
            text = description ?: "未命名记录",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = occurredAt?.let(DateTimeFormat::formatMonthDay).orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            text = "剩余 " + MoneyFormat.formatPlain(amount),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
        )
    }
}
