package com.xdl.xledger.ui.edit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.common.CompactTextField
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 单条交易的全屏编辑页。
 *
 * 相比原来的弹窗：表单可滚动、底部操作栏固定、日期时间用选择器、分类按一级 / 二级联动横向滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    transactionId: Int,
    onBack: () -> Unit,
    viewModel: TransactionEditViewModel = viewModel(
        factory = TransactionEditViewModel.factory(transactionId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 保存 / 删除成功后返回账本
    LaunchedEffect(uiState.saved, uiState.deleted) {
        if (uiState.saved || uiState.deleted) onBack()
    }

    SubPageScaffold(title = "编辑记录", onBack = onBack) { innerPadding ->
        val colors = MaterialTheme.xledgerColors
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
            uiState.loadError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = uiState.loadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.expense,
                    )
                    TextButton(onClick = viewModel::load) { Text("重试") }
                }
            }
            else -> {
                TransactionEditContent(
                    state = uiState,
                    innerPadding = innerPadding,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onAmountChange = viewModel::onAmountChange,
                    onDateChange = viewModel::onDateChange,
                    onTimeChange = viewModel::onTimeChange,
                    onTypeChange = viewModel::onTypeChange,
                    onCategoryChange = viewModel::onCategoryChange,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onOpenOffsetPicker = viewModel::openOffsetPicker,
                )
            }
        }
    }

    uiState.offsetPicker?.let { picker ->
        OffsetPickerDialog(
            state = picker,
            onShiftMonth = viewModel::shiftOffsetMonth,
            onKeywordChange = viewModel::onOffsetKeywordChange,
            onSelectExpense = viewModel::onOffsetExpenseSelect,
            onConfirm = viewModel::confirmOffset,
            onClose = viewModel::closeOffsetPicker,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditContent(
    state: TransactionEditUiState,
    innerPadding: PaddingValues,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onTypeChange: (Int) -> Unit,
    onCategoryChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onOpenOffsetPicker: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    // 树中展开的二级分类所在的一级分类 id
    var expandedParents by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 当前收支类型下的一级分类
    val typeCategories = remember(state.categories, state.transactionType) {
        state.categories.filter { it.transactionType == state.transactionType }
    }
    val parentCategories = remember(typeCategories) {
        typeCategories.filter { it.parentId == null }
    }

    // 打开即全展开：一级分类变化（含切换收支类型）时，展开其下所有二级分类
    LaunchedEffect(parentCategories) {
        expandedParents = parentCategories.map { it.categoryId }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 类型置顶：分类列表依赖它
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TypeSegment(
                    label = "支出",
                    active = state.transactionType == TransactionEntity.TYPE_EXPENSE,
                    enabled = !state.isSaving,
                    onClick = {
                        onTypeChange(TransactionEntity.TYPE_EXPENSE)
                        clearCategoryIfTypeMismatch(
                            state.categoryId,
                            state.categories,
                            TransactionEntity.TYPE_EXPENSE,
                            onCategoryChange,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                TypeSegment(
                    label = "收入",
                    active = state.transactionType == TransactionEntity.TYPE_INCOME,
                    enabled = !state.isSaving,
                    onClick = {
                        onTypeChange(TransactionEntity.TYPE_INCOME)
                        clearCategoryIfTypeMismatch(
                            state.categoryId,
                            state.categories,
                            TransactionEntity.TYPE_INCOME,
                            onCategoryChange,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            CompactTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                placeholder = "金额",
                enabled = !state.isSaving,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )

            CompactTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                placeholder = "描述（可选）",
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            // 日期 + 时间：分别点开对应选择器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DateTimeField(
                    iconRes = LucideR.drawable.lucide_ic_calendar,
                    text = state.occurredAt.toLocalDate().toString(),
                    enabled = !state.isSaving,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                DateTimeField(
                    iconRes = LucideR.drawable.lucide_ic_clock,
                    text = "%02d:%02d".format(state.occurredAt.hour, state.occurredAt.minute),
                    enabled = !state.isSaving,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "分类",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )

            // 分类树：一级 + 缩进的二级全部平铺展示，点哪行选哪行，一眼看全
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                    .background(colors.card, RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // “未分类”固定置顶
                CategoryTreeRow(
                    label = "未分类",
                    indent = 0,
                    active = state.categoryId == null,
                    enabled = !state.isSaving,
                    onClick = { onCategoryChange(null) },
                )
                parentCategories.forEach { parent ->
                    val children = typeCategories.filter { it.parentId == parent.categoryId }
                    // 点击一级分类：选中一级本身；再点一次展开 / 收起它的二级
                    CategoryTreeRow(
                        label = parent.categoryName,
                        indent = 0,
                        active = state.categoryId == parent.categoryId,
                        enabled = !state.isSaving,
                        expandable = children.isNotEmpty(),
                        expanded = expandedParents.contains(parent.categoryId),
                        onClick = {
                            // 已是选中一级时点击视为展开 / 收起子级
                            if (state.categoryId == parent.categoryId && children.isNotEmpty()) {
                                expandedParents = if (parent.categoryId in expandedParents) {
                                    expandedParents - parent.categoryId
                                } else {
                                    expandedParents + parent.categoryId
                                }
                            } else {
                                onCategoryChange(parent.categoryId)
                            }
                        },
                        onToggleExpand = {
                            expandedParents = if (parent.categoryId in expandedParents) {
                                expandedParents - parent.categoryId
                            } else {
                                expandedParents + parent.categoryId
                            }
                        },
                    )
                    // 展开后平铺二级分类（缩进展示）
                    if (expandedParents.contains(parent.categoryId)) {
                        children.forEach { child ->
                            CategoryTreeRow(
                                label = child.categoryName,
                                indent = 1,
                                active = state.categoryId == child.categoryId,
                                enabled = !state.isSaving,
                                onClick = { onCategoryChange(child.categoryId) },
                            )
                        }
                    }
                }
            }

            // 收入且仍有剩余金额时，可以选一笔支出来抵消
            if (state.transactionType == TransactionEntity.TYPE_INCOME &&
                MoneyFormat.toCents(state.amount) > 0
            ) {
                InlineButton(
                    text = "抵消支出",
                    filled = false,
                    enabled = !state.isSaving,
                    onClick = onOpenOffsetPicker,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.expense,
                )
            }
        }

        // 底部固定操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InlineButton(
                text = "保存",
                filled = true,
                enabled = !state.isSaving,
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, colors.expenseBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.isSaving) { pendingDelete = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "删除", fontWeight = FontWeight.Bold, color = colors.expense)
            }
        }
    }

    if (showDatePicker) {
        // DatePicker 的选中毫秒按 UTC 零点解释，避免时区偏移导致日期错一天
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt
                .toLocalDate()
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

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.occurredAt.hour,
            initialMinute = state.occurredAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
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

/** 切换收支类型后，若当前已选分类不属于新类型则清空分类。 */
private fun clearCategoryIfTypeMismatch(
    categoryId: Int?,
    categories: List<CategoryEntity>,
    targetType: Int,
    onCategoryChange: (Int?) -> Unit,
) {
    if (categoryId == null) return
    val category = categories.firstOrNull { it.categoryId == categoryId }
    if (category == null || category.transactionType != targetType) {
        onCategoryChange(null)
    }
}

@Composable
private fun DateTimeField(
    iconRes: Int,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = modifier
            .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) colors.textPrimary else colors.disabled,
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

/** 分类树中的一行：按缩进层级展示，点击选中当前行。 */
@Composable
private fun CategoryTreeRow(
    label: String,
    indent: Int,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) colors.accentContainer else colors.card,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .padding(start = (indent * 18).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (expandable) {
            // 展开 / 收起箭头，独立点击，避免与选中冲突
            Icon(
                painter = painterResource(
                    if (expanded) LucideR.drawable.lucide_ic_chevron_down
                    else LucideR.drawable.lucide_ic_chevron_right,
                ),
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(enabled = enabled, onClick = onToggleExpand),
            )
        } else {
            Box(modifier = Modifier.size(14.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            maxLines = 1,
            color = if (active) colors.accent else colors.textPrimary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_check),
                contentDescription = "已选中",
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
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
