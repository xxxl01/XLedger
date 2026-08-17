package com.xdl.xledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.xledger.data.remote.DEFAULT_LLM_BASE_URL
import com.xdl.xledger.data.remote.DEFAULT_LLM_MODEL
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import kotlinx.coroutines.launch

/**
 * LLM 配置页：设置支付记录识别的 Base URL、API Key 和模型。
 */
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    viewModel: LlmSettingsViewModel = viewModel(factory = LlmSettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.xledgerColors
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.savedMessage) {
        val text = uiState.savedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeSavedMessage()
    }
    LaunchedEffect(uiState.errorMessage) {
        val text = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeError()
    }

    SubPageScaffold(
        title = "LLM 配置",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "支付记录截图识别使用的 API 配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )

                ConfigField(
                    label = "Base URL",
                    value = uiState.baseUrl,
                    placeholder = DEFAULT_LLM_BASE_URL,
                    enabled = !uiState.isSaving,
                    onValueChange = viewModel::onBaseUrlChange,
                )
                ConfigField(
                    label = "API Key",
                    value = uiState.apiKey,
                    placeholder = "sk-...",
                    enabled = !uiState.isSaving,
                    onValueChange = viewModel::onApiKeyChange,
                    isPassword = true,
                )
                ConfigField(
                    label = "模型",
                    value = uiState.model,
                    placeholder = DEFAULT_LLM_MODEL,
                    enabled = !uiState.isSaving,
                    onValueChange = viewModel::onModelChange,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(colors.textPrimary, RoundedCornerShape(8.dp))
                        .clickable(enabled = !uiState.isSaving) {
                            scope.launch { viewModel.save() }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "保存",
                        fontWeight = FontWeight.Bold,
                        color = colors.card,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    val colors = MaterialTheme.xledgerColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = if (isPassword) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )
    }
}
