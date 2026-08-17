package com.xdl.xledger.ui.assets

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.data.db.entity.AssetEntity
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.MoneyFormat
import kotlinx.coroutines.launch

/**
 * 资产页：查看 / 新增 / 编辑资产账户。
 */
@Composable
fun AssetsScreen(
    viewModel: AssetsViewModel = viewModel(factory = AssetsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isAdding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftAmount by rememberSaveable { mutableStateOf("") }

    val total = remember(uiState.assets) {
        uiState.assets.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    fun startAdd() {
        draftName = ""
        draftAmount = ""
        editingId = null
        isAdding = true
    }

    fun startEdit(asset: AssetEntity) {
        isAdding = false
        editingId = asset.assetId
        draftName = asset.accountName
        draftAmount = asset.amount
    }

    fun cancelEditing() {
        isAdding = false
        editingId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "资产",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(8.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "总资产",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = MoneyFormat.formatPlain(total.toString()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
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
                val error = uiState.errorMessage
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.expense,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    AssetFormRow(
                        isVisible = isAdding,
                        name = draftName,
                        amount = draftAmount,
                        enabled = !isSaving,
                        onNameChange = { draftName = it },
                        onAmountChange = { draftAmount = it },
                        onSave = {
                            scope.launch {
                                if (viewModel.createAsset(draftName, draftAmount)) {
                                    cancelEditing()
                                }
                            }
                        },
                        onCancel = ::cancelEditing,
                        showAddButton = !isAdding && editingId == null,
                        onAdd = ::startAdd,
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    ) {
                        items(uiState.assets, key = { it.assetId }) { asset ->
                            AssetRow(
                                asset = asset,
                                isEditing = editingId == asset.assetId,
                                name = draftName,
                                amount = draftAmount,
                                enabled = !isSaving,
                                onNameChange = { draftName = it },
                                onAmountChange = { draftAmount = it },
                                onSave = {
                                    scope.launch {
                                        if (viewModel.updateAsset(asset.assetId, draftName, draftAmount)) {
                                            cancelEditing()
                                        }
                                    }
                                },
                                onCancel = ::cancelEditing,
                                onEdit = { startEdit(asset) },
                            )
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AssetFormRow(
    isVisible: Boolean,
    name: String,
    amount: String,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    showAddButton: Boolean,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    if (showAddButton) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+ 新增账户",
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
        return
    }

    if (!isVisible) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("账户") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            placeholder = { Text("金额") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(0.7f),
            shape = RoundedCornerShape(8.dp),
        )
        SaveButton(text = "保存", enabled = enabled, onClick = onSave)
        CancelButton(onClick = onCancel)
    }
}

@Composable
private fun AssetRow(
    asset: AssetEntity,
    isEditing: Boolean,
    name: String,
    amount: String,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Column {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(8.dp),
                )
                SaveButton(text = "保存", enabled = enabled, onClick = onSave)
                CancelButton(onClick = onCancel)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = asset.accountName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = MoneyFormat.formatPlain(asset.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "编辑",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun SaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .background(colors.textPrimary, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = colors.card,
        )
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "取消",
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
        )
    }
}
