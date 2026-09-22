package com.xdl.xledger.ui.categories

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import kotlinx.coroutines.launch

/**
 * 分类管理页：按支出 / 收入 Tab 管理两级分类。
 *
 * - 一级分类可展开 / 收起其下二级分类
 * - 顶部输入框新增一级分类；一级分类行的“+”内联输入新增二级分类
 * - 删除一级分类会连同其下全部二级分类一起删除，相关交易变为未分类
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val colors = MaterialTheme.xledgerColors
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var newCategoryName by remember { mutableStateOf("") }
    var expandedParents by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var addingChildFor by remember { mutableStateOf<Int?>(null) }
    var childName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    // 当前类型下的一级分类与二级分类映射
    val parents = remember(uiState.categories) {
        uiState.categories.filter { it.parentId == null }
    }
    val childrenByParent = remember(uiState.categories) {
        uiState.categories.filter { it.parentId != null }.groupBy { it.parentId!! }
    }

    SubPageScaffold(
        title = "分类",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            TypeTabRow(
                selected = uiState.selectedType,
                onSelect = viewModel::switchType,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("新增一级分类") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                )
                Box(
                    modifier = Modifier
                        .background(colors.textPrimary, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) {
                            scope.launch {
                                if (viewModel.createCategory(newCategoryName)) {
                                    newCategoryName = ""
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "新增",
                        fontWeight = FontWeight.Bold,
                        color = colors.card,
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
                else -> {
                    if (parents.isEmpty()) {
                        EmptyHint(
                            text = "还没有${if (uiState.selectedType == CategoryEntity.TYPE_EXPENSE) "支出" else "收入"}分类",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            parents.forEach { parent ->
                                item(key = "parent_${parent.categoryId}") {
                                    ParentRow(
                                        category = parent,
                                        expanded = expandedParents.contains(parent.categoryId),
                                        childCount = childrenByParent[parent.categoryId].orEmpty().size,
                                        enabled = !isSaving,
                                        onToggle = {
                                            expandedParents = if (it in expandedParents) {
                                                expandedParents - it
                                            } else {
                                                expandedParents + it
                                            }
                                        },
                                        onAddChild = {
                                            addingChildFor = parent.categoryId
                                            childName = ""
                                        },
                                        onDelete = { pendingDelete = parent },
                                    )
                                }
                                if (expandedParents.contains(parent.categoryId)) {
                                    childrenByParent[parent.categoryId].orEmpty().forEach { child ->
                                        item(key = "child_${child.categoryId}") {
                                            ChildRow(
                                                category = child,
                                                enabled = !isSaving,
                                                onDelete = { pendingDelete = child },
                                            )
                                        }
                                    }
                                    if (addingChildFor == parent.categoryId) {
                                        item(key = "child_input_${parent.categoryId}") {
                                            ChildAddRow(
                                                value = childName,
                                                enabled = !isSaving,
                                                onValueChange = { childName = it },
                                                onConfirm = {
                                                    scope.launch {
                                                        if (viewModel.createCategory(childName, parent.categoryId)) {
                                                            childName = ""
                                                            addingChildFor = null
                                                        }
                                                    }
                                                },
                                                onCancel = { addingChildFor = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { category ->
        val isParent = category.parentId == null
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (isParent) "删除分类及子分类" else "删除分类") },
            text = {
                Text(
                    if (isParent) {
                        "删除「${category.categoryName}」及其全部子分类后，相关交易将变为未分类。此操作不可撤销。"
                    } else {
                        "删除「${category.categoryName}」后，相关交易将变为未分类。此操作不可撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteCategory(category.categoryId)
                        }
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 支出 / 收入 切换。 */
@Composable
private fun TypeTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TypeTabItem(
            label = "支出",
            active = selected == CategoryEntity.TYPE_EXPENSE,
            onClick = { onSelect(CategoryEntity.TYPE_EXPENSE) },
            modifier = Modifier.weight(1f),
        )
        TypeTabItem(
            label = "收入",
            active = selected == CategoryEntity.TYPE_INCOME,
            onClick = { onSelect(CategoryEntity.TYPE_INCOME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TypeTabItem(
    label: String,
    active: Boolean,
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
            .clickable(onClick = onClick)
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

/** 一级分类行：展开箭头 + 名称 + 子分类数 + 添加二级 + 删除。 */
@Composable
private fun ParentRow(
    category: CategoryEntity,
    expanded: Boolean,
    childCount: Int,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = { onToggle(category.categoryId) })
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(
                if (expanded) LucideR.drawable.lucide_ic_chevron_down
                else LucideR.drawable.lucide_ic_chevron_right,
            ),
            contentDescription = if (expanded) "收起" else "展开",
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (childCount > 0) {
            Text(
                text = "$childCount 个",
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_plus),
            contentDescription = "添加子分类",
            tint = colors.accent,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onAddChild)
                .padding(6.dp)
                .size(18.dp),
        )
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_trash_2),
            contentDescription = "删除",
            tint = colors.expense,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onDelete)
                .padding(6.dp)
                .size(18.dp),
        )
    }
}

/** 二级分类行：缩进展示 + 删除。 */
@Composable
private fun ChildRow(
    category: CategoryEntity,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp)
            .background(colors.background, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_trash_2),
            contentDescription = "删除",
            tint = colors.expense,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onDelete)
                .padding(6.dp)
                .size(18.dp),
        )
    }
}

/** 行内新增二级分类输入。 */
@Composable
private fun ChildAddRow(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("子分类名称") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
        )
        Box(
            modifier = Modifier
                .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onConfirm)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "确定",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
        Box(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onCancel)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "取消",
                fontSize = 13.sp,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.xledgerColors
    Column(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "分类区分支出 / 收入，一级分类下可添加子分类。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
