package com.xdl.xledger.ui.import

import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.remote.LlmApiService
import com.xdl.xledger.data.remote.LlmParseDebugResult
import com.xdl.xledger.data.remote.ParsedTransaction
import com.xdl.xledger.data.repository.CategoryRepository
import com.xdl.xledger.data.repository.TransactionRepository
import com.xdl.xledger.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 导入流程步骤。 */
enum class UploadStep { Pick, Ready, Compress, Llm, Review, Saving }

/** 已选图片。 */
data class SelectedImage(val uri: String)

    /** 识别候选（带“可能重复”提示标记，仅为提示不阻止入库）。 */
data class ImportCandidate(
    val occurredAt: String,
    val transactionType: Int,
    val amount: String,
    val description: String?,
    /** 按名称从库里同名历史交易带出的分类 id，找不到则为 null（未分类）。 */
    val categoryId: Int? = null,
    /** 带出的分类名，仅用于展示。 */
    val categoryName: String? = null,
    val isDuplicate: Boolean,
)

/** 导入页 UI 状态。 */
data class UploadUiState(
    val step: UploadStep = UploadStep.Pick,
    val isWorking: Boolean = false,
    /** “今天是哪天”：截图中的相对日期（今天/昨天）与月日按此日期补全。 */
    val todayDate: LocalDate = LocalDate.now(),
    val selectedImages: List<SelectedImage> = emptyList(),
    val candidates: List<ImportCandidate> = emptyList(),
    val selectedIndexes: Set<Int> = emptySet(),
    val message: String? = null,
    /** 最近一次识别的 LLM 原始返回（调试用）。 */
    val llmDebug: LlmParseDebugResult? = null,
)

class UploadViewModel(
    application: XLedgerApplication,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val llmApiService: LlmApiService,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun setTodayDate(date: LocalDate) {
        _uiState.update { it.copy(todayDate = date) }
    }

    /** 选择图片后进入 ready 步骤，并清空上次识别结果。 */
    fun addImages(uris: List<String>) {
        if (uris.isEmpty()) return
        resetRecognitionResult()
        _uiState.update {
            it.copy(
                selectedImages = uris.map { SelectedImage(it) },
                step = UploadStep.Ready,
                llmDebug = null,
            )
        }
    }

    fun removeImage(uri: String) {
        _uiState.update { state ->
            val next = state.selectedImages.filterNot { it.uri == uri }
            state.copy(
                selectedImages = next,
                step = if (next.isEmpty()) UploadStep.Pick else UploadStep.Ready,
            )
        }
        resetRecognitionResult()
    }

    /** 开始识别：压缩 → LLM 解析 → 标记可能重复 → 进入筛选。 */
    fun startRecognition() {
        val state = _uiState.value
        if (state.selectedImages.isEmpty()) {
            _uiState.update { it.copy(message = "未选择图片，请先选择支付记录截图") }
            return
        }

        resetRecognitionResult()
        _uiState.update { it.copy(isWorking = true, message = null, step = UploadStep.Compress) }
        viewModelScope.launch {
            try {
                val application = getApplication<XLedgerApplication>()
                val compressed = withContext(Dispatchers.IO) {
                    state.selectedImages.map { image ->
                        ImageCompressor.compress(application, Uri.parse(image.uri))
                    }
                }

                _uiState.update { it.copy(step = UploadStep.Llm) }
                // 传“今天是哪天”，让模型把截图中的相对日期与月日补全为具体日期；
                // 分类不交给模型，识别后由程序按名称到库里找同名交易带出
                val debugResult = llmApiService.parseTransactionsFromImagesWithDebug(
                    compressed,
                    state.todayDate.toString(),
                )
                _uiState.update { it.copy(llmDebug = debugResult) }

                if (debugResult.parseError != null) {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            step = UploadStep.Ready,
                            message = "LLM 返回无法解析，已展示原始返回供排查",
                        )
                    }
                    return@launch
                }

                val candidates = markDuplicates(debugResult.transactions)

                _uiState.update {
                    it.copy(
                        candidates = candidates,
                        selectedIndexes = candidates.indices.toSet(),
                        step = UploadStep.Review,
                        isWorking = false,
                    )
                }
                if (candidates.isEmpty()) {
                    _uiState.update { it.copy(message = "没有可导入记录，这些截图没有识别出有效支付记录") }
                }
            } catch (error: Exception) {
                Log.w(TAG, "识别失败", error)
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        step = UploadStep.Ready,
                        message = error.message ?: "无法识别这些截图",
                    )
                }
            }
        }
    }

    fun toggleCandidate(index: Int) {
        _uiState.update { state ->
            val next = state.selectedIndexes.toMutableSet()
            if (!next.add(index)) {
                next.remove(index)
            }
            state.copy(selectedIndexes = next)
        }
    }

    fun toggleDay(indexes: List<Int>) {
        _uiState.update { state ->
            val next = state.selectedIndexes.toMutableSet()
            val allSelected = indexes.all { next.contains(it) }
            if (allSelected) {
                indexes.forEach(next::remove)
            } else {
                indexes.forEach(next::add)
            }
            state.copy(selectedIndexes = next)
        }
    }

    /** 勾选候选入库；成功后清空候选回到 ready，避免重复导入。 */
    fun confirmImport() {
        val state = _uiState.value
        val selected = state.candidates.filterIndexed { index, _ ->
            state.selectedIndexes.contains(index)
        }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(message = "未选择记录，请至少选择一条记录再入库") }
            return
        }

        _uiState.update { it.copy(isWorking = true, message = null, step = UploadStep.Saving) }
        viewModelScope.launch {
            try {
                val items = selected.map {
                    TransactionRepository.ImportCandidate(
                        transactionType = it.transactionType,
                        amount = it.amount,
                        occurredAt = it.occurredAt,
                        description = it.description,
                        categoryId = it.categoryId,
                    )
                }
                val created = transactionRepository.createSelectedTransactions(items)
                resetRecognitionResult()
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        step = UploadStep.Ready,
                        message = "已导入 ${created.size} / ${selected.size} 条",
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "入库失败", error)
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        step = UploadStep.Review,
                        message = error.message ?: "无法保存记录",
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun resetRecognitionResult() {
        _uiState.update { it.copy(candidates = emptyList(), selectedIndexes = emptySet()) }
    }

    /** 清除调试结果（重新选图时）。 */
    fun clearDebugResult() {
        _uiState.update { it.copy(llmDebug = null) }
    }

    private suspend fun markDuplicates(
        parsed: List<ParsedTransaction>,
    ): List<ImportCandidate> {
        val inputs = parsed.map {
            TransactionRepository.CreateTransactionInput(
                transactionType = it.transactionType,
                amount = it.amount,
                occurredAt = it.occurredAt,
                description = it.description,
                categoryId = null,
            )
        }
        val existingKeys = transactionRepository.getExistingDedupeKeys(inputs)
        val seenKeys = mutableSetOf<String>()

        // 按名称到库里找同名历史交易的分类；同名但分类已被删除时取不到名字，按未分类处理
        val categoryById = categoryRepository.getAll().associateBy { it.categoryId }
        val categoryIdByName = transactionRepository.findCategoryIdsByDescription(
            parsed.mapNotNull { t -> t.description?.let { it to t.transactionType } },
        )

        return parsed.map { transaction ->
            val key = transactionRepository.createDedupeKey(
                transaction.occurredAt,
                transaction.amount,
                transaction.transactionType,
            )
            val isDuplicate = existingKeys.contains(key) || !seenKeys.add(key)
            val category = transaction.description
                ?.let { categoryIdByName[it to transaction.transactionType] }
                ?.let(categoryById::get)
            ImportCandidate(
                occurredAt = transaction.occurredAt,
                transactionType = transaction.transactionType,
                amount = transaction.amount,
                description = transaction.description,
                categoryId = category?.categoryId,
                categoryName = category?.categoryName,
                isDuplicate = isDuplicate,
            )
        }
    }

    companion object {
        private const val TAG = "UploadViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                UploadViewModel(
                    application = application,
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                    llmApiService = application.llmApiService,
                )
            }
        }
    }
}
