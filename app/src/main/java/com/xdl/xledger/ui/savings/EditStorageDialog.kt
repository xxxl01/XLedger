package com.xdl.xledger.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xdl.xledger.ui.common.CompactTextField
import com.xdl.xledger.ui.theme.xledgerColors

@Composable
fun EditStorageDialog(
    form: EditStorageForm,
    isSaving: Boolean,
    editMessage: String?,
    onItemNameChange: (String) -> Unit,
    onTotalAmountChange: (String) -> Unit,
    onRemainingAmountChange: (String) -> Unit,
    onDeductionAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    var pendingDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (form.storageId == null) "新增储藏" else "编辑储藏",
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
                placeholder = "物品名称（如大米、纸巾）",
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactTextField(
                    value = form.totalAmount,
                    onValueChange = onTotalAmountChange,
                    placeholder = "囤入总金额",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                CompactTextField(
                    value = form.deductionAmount,
                    onValueChange = onDeductionAmountChange,
                    placeholder = "每次扣除",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            CompactTextField(
                value = form.remainingAmount,
                onValueChange = onRemainingAmountChange,
                placeholder = "当前剩余金额",
                enabled = !isSaving,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (form.storageId == null) {
                    "新建后剩余金额会自动等于囤入总金额"
                } else {
                    "可手动修正剩余金额，用于补录或纠正库存"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            CompactTextField(
                value = form.note,
                onValueChange = onNoteChange,
                placeholder = "备注（可选）",
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (editMessage != null) {
                Text(editMessage, style = MaterialTheme.typography.bodySmall, color = colors.expense)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
                if (form.storageId != null) {
                    TextButton(
                        onClick = { pendingDelete = true },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删除", color = colors.expense, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("删除储藏记录") },
            text = { Text("删除后扣除记录也无法恢复，确定删除吗？") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = false; onDelete() }) {
                    Text("删除", color = colors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
fun DeductStorageDialog(
    item: com.xdl.xledger.data.db.entity.StorageEntity,
    amount: String,
    isDeducting: Boolean,
    message: String?,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("扣除储藏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "${item.itemName} · 当前剩余 ¥${item.remainingAmount}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            CompactTextField(
                value = amount,
                onValueChange = onAmountChange,
                placeholder = "本次扣除金额",
                enabled = !isDeducting,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.expense)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose, enabled = !isDeducting) { Text("取消") }
                TextButton(onClick = onConfirm, enabled = !isDeducting) { Text("确认扣除") }
            }
        }
    }
}
