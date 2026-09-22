package com.xdl.xledger.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.ui.common.CompactTextField
import com.xdl.xledger.ui.theme.xledgerColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 新增 / 编辑省钱记录弹窗（无状态组件，状态由 SavingsViewModel 持有）。
 *
 * 只记到日期，用日期选择组件选日子；省下金额 = 原价 - 实付，实时显示，不需要用户填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSavingDialog(
    form: EditSavingForm,
    isSaving: Boolean,
    editMessage: String?,
    onItemNameChange: (String) -> Unit,
    onOriginalAmountChange: (String) -> Unit,
    onActualAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    var pendingDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val saved = (form.originalAmount.trim().toDoubleOrNull() ?: 0.0) -
        (form.actualAmount.trim().toDoubleOrNull() ?: 0.0)

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (form.savingId == null) "记一笔省钱" else "编辑省钱记录",
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

            CompactTextField(
                value = form.itemName,
                onValueChange = onItemNameChange,
                placeholder = "项目名称",
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactTextField(
                    value = form.originalAmount,
                    onValueChange = onOriginalAmountChange,
                    placeholder = "原价",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                CompactTextField(
                    value = form.actualAmount,
                    onValueChange = onActualAmountChange,
                    placeholder = "实付",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "本条省下 ¥%.2f".format(saved),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (saved >= 0) colors.income else colors.expense,
            )

            // 日期选择：点击弹系统日期选择器，只精确到天
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = !isSaving) { showDatePicker = true }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_calendar),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = form.date.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSaving) colors.disabled else colors.textPrimary,
                    )
                }
            }

            CompactTextField(
                value = form.note,
                onValueChange = onNoteChange,
                placeholder = "备注（可选，如怎么省下来的）",
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            if (editMessage != null) {
                Text(
                    text = editMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.expense,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.textPrimary, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving, onClick = onSave)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "保存", fontWeight = FontWeight.Bold, color = colors.card)
                }
                // 新增时没有可删除的记录，只在编辑已有记录时显示删除
                if (form.savingId != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, colors.expenseBorder, RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSaving) { pendingDelete = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "删除", fontWeight = FontWeight.Bold, color = colors.expense)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        // DatePicker 的选中毫秒按 UTC 零点解释，避免时区偏移导致日期错一天
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("删除记录") },
            text = { Text("删除后不可恢复，确定删除这条省钱记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}
