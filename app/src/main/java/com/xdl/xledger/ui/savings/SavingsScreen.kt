package com.xdl.xledger.ui.savings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.SavingEntity
import com.xdl.xledger.ui.home.IconButton
import com.xdl.xledger.ui.home.PrimaryIconButton
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.DateTimeFormat

/**
 * 省钱页：按月记录“本来要花多少、实际花了多少”，统计省下的金额。
 */
@Composable
fun SavingsScreen(
    viewModel: SavingsViewModel = viewModel(factory = SavingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors

    // 本月省下合计
    val monthSaved = remember(uiState.savings) {
        uiState.savings.sumOf { it.savedAmount }
    }
    // 按日期分组（本地时区），组内沿用 DAO 的时间倒序
    val groupedByDay = remember(uiState.savings) {
        uiState.savings.groupBy { DateTimeFormat.formatDateKey(it.occurredAt) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
    ) {
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
                onClick = { viewModel.shiftMonth(-1) },
            )
            Text(
                text = DateTimeFormat.formatMonthLabel(uiState.year, uiState.month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                iconRes = LucideR.drawable.lucide_ic_chevron_right,
                contentDescription = "下个月",
                onClick = { viewModel.shiftMonth(1) },
            )
            PrimaryIconButton(
                iconRes = LucideR.drawable.lucide_ic_plus,
                contentDescription = "新增省钱记录",
                onClick = viewModel::openCreate,
            )
        }

        // 汇总卡片：左边本月省下，右边累计省下
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(8.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本月省下",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = "¥%.2f".format(monthSaved),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.income,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "累计省下",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = "¥%.2f".format(uiState.totalSaved),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.income,
                )
            }
        }

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
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.expense,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            uiState.savings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.TopCenter,
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
                            text = "本月还没有省钱记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "点右上角 + 记一笔：填项目名称、原价和实际花费。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    groupedByDay.forEach { (day, daySavings) ->
                        item(key = "day_$day") {
                            // 日期分组标题：日期 + 当天省下合计
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = DateTimeFormat.formatMonthDay(
                                        daySavings.first().occurredAt,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "省下 ¥%.2f".format(daySavings.sumOf { it.savedAmount }),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.income,
                                )
                            }
                        }
                        items(daySavings, key = { it.savingId }) { saving ->
                            SavingRow(saving = saving, onClick = { viewModel.openEdit(saving) })
                        }
                    }
                }
            }
        }
    }

    uiState.editForm?.let { form ->
        EditSavingDialog(
            form = form,
            isSaving = uiState.isSavingEdit,
            editMessage = uiState.editMessage,
            onItemNameChange = viewModel::onEditItemNameChange,
            onOriginalAmountChange = viewModel::onEditOriginalAmountChange,
            onActualAmountChange = viewModel::onEditActualAmountChange,
            onDateChange = viewModel::onEditDateChange,
            onNoteChange = viewModel::onEditNoteChange,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteEditSaving,
            onClose = viewModel::closeEdit,
        )
    }
}

@Composable
private fun SavingRow(saving: SavingEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.xledgerColors

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
                text = saving.itemName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("原价 ¥%.2f".format(saving.originalAmount.toDoubleOrNull() ?: 0.0))
                    append(" → 实付 ¥%.2f".format(saving.actualAmount.toDoubleOrNull() ?: 0.0))
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!saving.note.isNullOrBlank()) {
                Text(
                    text = saving.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "省 ¥%.2f".format(saving.savedAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (saving.savedAmount >= 0) colors.income else colors.expense,
        )
    }
}
