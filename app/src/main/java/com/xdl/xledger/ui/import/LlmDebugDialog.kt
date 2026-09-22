package com.xdl.xledger.ui.import

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xdl.xledger.data.remote.LlmParseDebugResult
import com.xdl.xledger.ui.theme.xledgerColors

/**
 * LLM 原始返回调试对话框：展示模型、请求地址、解析结果、思考过程（推理内容）与原始响应文本。
 */
@Composable
fun LlmDebugDialog(
    debug: LlmParseDebugResult,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    // 限制对话框最大高度，避免内容超出屏幕底部导致窗口阴影/内容被裁
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .background(colors.card, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LLM 原始返回",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "复制返回",
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(debug.responseContent))
                    },
                )
                Text(
                    text = "  关闭",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(onClick = onDismiss),
                )
            }

            DebugInfoRow(label = "模型", value = debug.model)
            DebugInfoRow(label = "API", value = debug.apiUrl)

            if (debug.parseError != null) {
                Text(
                    text = "解析失败：${debug.parseError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.expense,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "解析出 ${debug.transactions.size} 条交易",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.income,
                    fontWeight = FontWeight.Bold,
                )
            }

            debug.reasoningContent?.let { reasoning ->
                SectionHeader(
                    title = "思考过程",
                    onCopy = { clipboard.setText(AnnotatedString(reasoning)) },
                )
                MonoTextBlock(
                    text = reasoning,
                    background = colors.accentContainer,
                )
            }

            SectionHeader(
                title = "原始返回",
                onCopy = { clipboard.setText(AnnotatedString(debug.responseContent)) },
            )
            MonoTextBlock(
                text = debug.responseContent,
                background = colors.background,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onCopy: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "复制",
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onCopy),
        )
    }
}

@Composable
private fun MonoTextBlock(
    text: String,
    background: androidx.compose.ui.graphics.Color,
) {
    val colors = MaterialTheme.xledgerColors
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        ),
        color = colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    )
}

@Composable
private fun DebugInfoRow(label: String, value: String) {
    val colors = MaterialTheme.xledgerColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
