package com.xdl.xledger.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.ui.theme.xledgerColors

/**
 * 全屏子页面骨架：顶部栏（返回按钮 + 标题，可选副标题）+ 内容区。
 * 上传 / 分类 / LLM 配置 / 分类统计等覆盖 Tab 栏的页面统一使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    snackbarHost: @Composable () -> Unit = { },
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = MaterialTheme.xledgerColors

    Scaffold(
        containerColor = colors.background,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_arrow_left),
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        content(innerPadding)
    }
}

/** 占位内容：功能尚未实现的空态提示。 */
@Composable
fun PlaceholderContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_info),
                contentDescription = null,
                tint = colors.disabled,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
