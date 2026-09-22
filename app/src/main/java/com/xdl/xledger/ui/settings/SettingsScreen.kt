package com.xdl.xledger.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.ui.theme.xledgerColors

/** 设置页：应用配置入口列表。 */
@Composable
fun SettingsScreen(
    onOpenLlmSettings: () -> Unit,
    onOpenOffsets: () -> Unit,
    dataTransferViewModel: DataTransferViewModel = viewModel(factory = DataTransferViewModel.Factory),
) {
    val colors = MaterialTheme.xledgerColors
    val transferState by dataTransferViewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDataTransferDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) dataTransferViewModel.exportData(context.contentResolver, it)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingImportUri = it
    }

    LaunchedEffect(transferState.message) {
        val message = transferState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        dataTransferViewModel.consumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 14.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_settings),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsItem(
                title = "LLM 配置",
                description = "设置支付记录识别使用的 API 地址、Key 和模型",
                onClick = onOpenLlmSettings,
            )
            SettingsItem(
                title = "抵消记录",
                description = "查看用收入抵消支出的历史记录",
                onClick = onOpenOffsets,
            )
            SettingsItem(
                title = "数据导入导出",
                description = "备份或恢复全部分类和账单数据",
                onClick = { showDataTransferDialog = true },
            )
        }
    }

    if (showDataTransferDialog) {
        AlertDialog(
            onDismissRequest = { showDataTransferDialog = false },
            title = { Text("数据导入导出") },
            text = { Text("导出会生成包含全部分类和账单的备份文件；导入会覆盖当前全部分类和账单。") },
            confirmButton = {
                TextButton(
                    enabled = !transferState.isWorking,
                    onClick = {
                        showDataTransferDialog = false
                        exportLauncher.launch("xledger-backup.json")
                    },
                ) { Text("导出数据") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !transferState.isWorking,
                        onClick = {
                            showDataTransferDialog = false
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                    ) { Text("导入数据") }
                    TextButton(onClick = { showDataTransferDialog = false }) { Text("取消") }
                }
            },
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("确认导入全部数据") },
            text = { Text("导入会清空当前全部分类和账单，并替换为备份文件中的数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dataTransferViewModel.importData(context.contentResolver, uri)
                        pendingImportUri = null
                    },
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.accentContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_bot),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_chevron_right),
            contentDescription = null,
            tint = colors.disabled,
            modifier = Modifier.size(20.dp),
        )
    }
}
