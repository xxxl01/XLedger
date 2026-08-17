package com.xdl.xledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.theme.xledgerColors

/**
 * 编辑 / 删除交易弹窗（无状态组件，状态由 HomeViewModel 持有）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTransactionDialog(
    form: EditTransactionForm,
    categories: List<CategoryEntity>,
    isCreatingCategory: Boolean,
    editCategoryName: String,
    isSaving: Boolean,
    editMessage: String?,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onOccurredAtChange: (String) -> Unit,
    onTypeChange: (Int) -> Unit,
    onCategoryChange: (Int?) -> Unit,
    onStartCreateCategory: () -> Unit,
    onCancelCreateCategory: () -> Unit,
    onCategoryNameChange: (String) -> Unit,
    onCreateCategory: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "编辑记录",
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

            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                placeholder = { Text("描述") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = onAmountChange,
                    placeholder = { Text("金额") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.36f),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = form.occurredAt,
                    onValueChange = onOccurredAtChange,
                    placeholder = { Text("2026-05-04 12:30") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.weight(0.64f),
                    shape = RoundedCornerShape(8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TypeSegment(
                    label = "支出",
                    active = form.transactionType == TransactionEntity.TYPE_EXPENSE,
                    enabled = !isSaving,
                    onClick = { onTypeChange(TransactionEntity.TYPE_EXPENSE) },
                    modifier = Modifier.weight(1f),
                )
                TypeSegment(
                    label = "收入",
                    active = form.transactionType == TransactionEntity.TYPE_INCOME,
                    enabled = !isSaving,
                    onClick = { onTypeChange(TransactionEntity.TYPE_INCOME) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (isCreatingCategory) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = editCategoryName,
                        onValueChange = onCategoryNameChange,
                        placeholder = { Text("新分类") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    InlineButton(
                        text = "新增",
                        filled = true,
                        enabled = !isSaving,
                        onClick = onCreateCategory,
                    )
                    InlineButton(
                        text = "取消",
                        filled = false,
                        enabled = !isSaving,
                        onClick = onCancelCreateCategory,
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryChip(
                        label = "未分类",
                        active = form.categoryId == null,
                        enabled = !isSaving,
                        onClick = { onCategoryChange(null) },
                    )
                    categories.forEach { category ->
                        CategoryChip(
                            label = category.categoryName,
                            active = form.categoryId == category.categoryId,
                            enabled = !isSaving,
                            onClick = { onCategoryChange(category.categoryId) },
                        )
                    }
                    CategoryChip(
                        label = "+ 分类",
                        active = false,
                        enabled = !isSaving,
                        onClick = onStartCreateCategory,
                    )
                }
            }

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
                InlineButton(
                    text = "保存",
                    filled = true,
                    enabled = !isSaving,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.expenseBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) { pendingDelete = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "删除",
                        fontWeight = FontWeight.Bold,
                        color = colors.expense,
                    )
                }
            }
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("删除记录") },
            text = { Text("删除后不可恢复，确定删除这条记录吗？") },
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

@Composable
private fun TypeSegment(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (active) colors.accent else colors.inputBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                color = if (active) colors.accentContainer else colors.card,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (active) colors.accent else colors.inputBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                color = if (active) colors.accentContainer else colors.card,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            maxLines = 1,
            color = if (active) colors.accent else colors.textPrimary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun InlineButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = modifier
            .then(
                if (filled) {
                    Modifier.background(colors.textPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier.border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = if (filled) colors.card else colors.textPrimary,
        )
    }
}
