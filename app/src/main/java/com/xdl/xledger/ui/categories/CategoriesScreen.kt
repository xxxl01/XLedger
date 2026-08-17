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
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import kotlinx.coroutines.launch

/**
 * 分类管理页：新增 / 删除自定义分类，删除时自动解除相关交易关联。
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
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(uiState.errorMessage) {
        val text = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("分类名称") },
                    singleLine = true,
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
                else -> CategoryList(
                    categories = uiState.categories,
                    onDelete = { pendingDelete = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类") },
            text = {
                Text("删除「${category.categoryName}」后，相关交易将变为未分类。此操作不可撤销。")
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

@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    onDelete: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors

    if (categories.isEmpty()) {
        Column(
            modifier = modifier
                .padding(top = 16.dp)
                .background(colors.card, RoundedCornerShape(8.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有分类",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "新增后可在编辑记录时选择。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.categoryId }) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clickable { onDelete(category) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_trash_2),
                        contentDescription = "删除",
                        tint = colors.expense,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
