package com.xdl.xledger.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.ui.common.SubPageScaffold
import com.xdl.xledger.ui.theme.xledgerColors
import com.xdl.xledger.util.BitmapLoader
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 截图导入页：选择支付记录截图 → 压缩 → LLM 识别 → 勾选入库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onBack: () -> Unit,
    viewModel: UploadViewModel = viewModel(factory = UploadViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        viewModel.addImages(uris.map { it.toString() })
    }

    LaunchedEffect(uiState.message) {
        val text = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
        // 解析失败时自动弹出原始返回，便于调试
        if (uiState.llmDebug?.parseError != null) {
            showDebugDialog = true
        }
    }

    SubPageScaffold(
        title = "导入",
        subtitle = "选择支付记录截图，确认后再识别入库",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            StepProgress(
                step = uiState.step,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "images") {
                    ImagePanel(
                        state = uiState,
                        onTodayDateChange = viewModel::setTodayDate,
                        onPickImages = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRemoveImage = viewModel::removeImage,
                    )
                }

                if (uiState.isWorking && uiState.step != UploadStep.Review) {
                    item(key = "working") {
                        WorkingPanel(step = uiState.step)
                    }
                }

                if (uiState.step == UploadStep.Review) {
                    item(key = "review_header") {
                        ReviewHeader(
                            state = uiState,
                            onShowDebug = { showDebugDialog = true },
                        )
                    }
                    itemsIndexed(uiState.candidates) { index, candidate ->
                        CandidateRow(
                            candidate = candidate,
                            selected = uiState.selectedIndexes.contains(index),
                            onClick = { viewModel.toggleCandidate(index) },
                        )
                    }
                }
            }

            // 底部操作按钮
            when (uiState.step) {
                UploadStep.Ready -> BottomActionButton(
                    text = "开始识别",
                    enabled = !uiState.isWorking,
                    iconRes = LucideR.drawable.lucide_ic_loader_circle,
                    onClick = viewModel::startRecognition,
                )
                UploadStep.Review -> BottomActionButton(
                    text = "确定入库",
                    enabled = !uiState.isWorking,
                    iconRes = LucideR.drawable.lucide_ic_check,
                    onClick = viewModel::confirmImport,
                )
                else -> Unit
            }
        }
    }

    // LLM 原始返回调试对话框
    if (showDebugDialog) {
        uiState.llmDebug?.let { debug ->
            LlmDebugDialog(
                debug = debug,
                onDismiss = { showDebugDialog = false },
            )
        }
    }
}

// ---- 步骤条 ----

private data class StepItem(val key: UploadStep, val label: String)

private val STEPS = listOf(
    StepItem(UploadStep.Pick, "选择截图"),
    StepItem(UploadStep.Compress, "压缩图片"),
    StepItem(UploadStep.Llm, "整理记录"),
    StepItem(UploadStep.Review, "筛选结果"),
)

@Composable
private fun StepProgress(step: UploadStep, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.xledgerColors

    fun status(item: UploadStep): StepStatus {
        if (step == UploadStep.Ready) {
            return if (item == UploadStep.Pick) StepStatus.Done else StepStatus.Pending
        }
        val currentIndex = STEPS.indexOfFirst { it.key == step }
        val itemIndex = STEPS.indexOfFirst { it.key == item }
        return when {
            itemIndex < currentIndex -> StepStatus.Done
            itemIndex == currentIndex -> StepStatus.Active
            else -> StepStatus.Pending
        }
    }

    Column(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        STEPS.forEach { stepItem ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (status(stepItem.key)) {
                    StepStatus.Done -> Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_check),
                        contentDescription = null,
                        tint = colors.income,
                        modifier = Modifier.size(18.dp),
                    )
                    StepStatus.Active -> Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_loader_circle),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    StepStatus.Pending -> Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_circle),
                        contentDescription = null,
                        tint = colors.disabled,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = stepItem.label,
                    fontSize = 14.sp,
                    color = if (status(stepItem.key) == StepStatus.Active) {
                        colors.accent
                    } else {
                        colors.textSecondary
                    },
                    fontWeight = if (status(stepItem.key) == StepStatus.Active) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                )
            }
        }
    }
}

private enum class StepStatus { Pending, Active, Done }

// ---- 图片面板 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePanel(
    state: UploadUiState,
    onTodayDateChange: (LocalDate) -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    var showDatePicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.selectedImages.isNotEmpty()) {
                        "已选择 ${state.selectedImages.size} 张截图"
                    } else {
                        "支付记录截图"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "今天是哪天",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
            }
            // “今天是哪天”日期选择框：支付宝截图里最新日期常为“今天”这类相对日期
            Box(
                modifier = Modifier
                    .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.isWorking, onClick = { showDatePicker = true })
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_calendar),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = state.todayDate.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
            }
            if (state.selectedImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.isWorking, onClick = onPickImages)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "重新选择",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
            }
        }

        if (state.selectedImages.isNotEmpty()) {
            ImageGrid(
                images = state.selectedImages,
                onRemove = onRemoveImage,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.isWorking, onClick = onPickImages)
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_image_plus),
                        contentDescription = null,
                        tint = colors.disabled,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "选择支付记录截图",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        // DatePicker 的选中毫秒按 UTC 零点解释，避免时区偏移导致日期错一天
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.todayDate
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
                            onTodayDateChange(
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
}

@Composable
private fun ImageGrid(
    images: List<SelectedImage>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.take(3).forEach { image ->
            SelectedImageThumbnail(
                uri = image.uri,
                onRemove = { onRemove(image.uri) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectedImageThumbnail(
    uri: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.xledgerColors
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = BitmapLoader.loadThumbnail(context, android.net.Uri.parse(uri))
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .background(colors.card, RoundedCornerShape(8.dp)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .background(colors.card.copy(alpha = 0.92f), RoundedCornerShape(13.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_x),
                contentDescription = "移除图片",
                tint = colors.textPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ---- 工作进度 ----

@Composable
private fun WorkingPanel(step: UploadStep) {
    val colors = MaterialTheme.xledgerColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = colors.accent)
        Text(
            text = when (step) {
                UploadStep.Compress -> "正在压缩图片"
                UploadStep.Llm -> "正在识别，请稍候"
                UploadStep.Saving -> "正在写入账本"
                else -> "处理中"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

// ---- 候选列表 ----

@Composable
private fun ReviewHeader(
    state: UploadUiState,
    onShowDebug: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val duplicateCount = state.candidates.count { it.isDuplicate }
    val selectedDuplicateCount = state.candidates.filterIndexed { index, c ->
        c.isDuplicate && state.selectedIndexes.contains(index)
    }.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已选 ${state.selectedIndexes.size} / ${state.candidates.size} 条",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (duplicateCount > 0) {
            Text(
                text = "可能重复 $selectedDuplicateCount / $duplicateCount 条",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.duplicate,
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .border(1.dp, colors.inputBorder, RoundedCornerShape(6.dp))
                .clickable(onClick = onShowDebug)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_eye),
                contentDescription = "查看 LLM 原始返回",
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    val isIncome = candidate.transactionType == TransactionEntity.TYPE_INCOME
    val textColor = if (candidate.isDuplicate) colors.duplicate else colors.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = when {
                    candidate.isDuplicate -> colors.duplicateBorder
                    selected -> colors.accent
                    else -> colors.cardBorder
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(
                if (selected) {
                    LucideR.drawable.lucide_ic_square_check_big
                } else {
                    LucideR.drawable.lucide_ic_square
                },
            ),
            contentDescription = null,
            tint = if (candidate.isDuplicate) colors.duplicate else {
                if (selected) colors.accent else colors.disabled
            },
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.description ?: "未命名记录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(DateTimeFormat.formatTime(candidate.occurredAt))
                    candidate.categoryName?.let { append(" · $it") }
                    if (candidate.isDuplicate) append(" · 可能重复")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = MoneyFormat.formatSigned(candidate.amount, candidate.transactionType),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isIncome) colors.income else colors.expense,
        )
    }
}

// ---- 底部按钮 ----

@Composable
private fun BottomActionButton(
    text: String,
    enabled: Boolean,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.xledgerColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.textPrimary, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.card,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = colors.card,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
