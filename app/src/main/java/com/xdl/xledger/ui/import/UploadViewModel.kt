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
import com.xdl.xledger.data.remote.ParsedTransaction
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

/** 识别候选（带重复标记）。 */
data class ImportCandidate(
    val occurredAt: String,
    val transactionType: Int,
    val amount: String,
    val description: String?,
    val isDuplicate: Boolean,
)

/** 导入页 UI 状态。 */
data class UploadUiState(
    val step: UploadStep = UploadStep.Pick,
    val isWorking: Boolean = false,
    val transactionYear: String = LocalDate.now().year.toString(),
    val selectedImages: List<SelectedImage> = emptyList(),
    val candidates: List<ImportCandidate> = emptyList(),
    val selectedIndexes: Set<Int> = emptySet(),
    val message: String? = null,
)

class UploadViewModel(
    application: XLedgerApplication,
    private val transactionRepository: TransactionRepository,
    private val llmApiService: LlmApiService,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun setTransactionYear(value: String) {
        _uiState.update { it.copy(transactionYear = value) }
    }

    /** 选择图片后进入 ready 步骤，并清空上次识别结果。 */
    fun addImages(uris: List<String>) {
        if (uris.isEmpty()) return
        resetRecognitionResult()
        _uiState.update {
            it.copy(
                selectedImages = uris.map { SelectedImage(it) },
                step = UploadStep.Ready,
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

    /** 开始识别：压缩 → LLM 解析 → 标记重复 → 进入筛选。 */
    fun startRecognition() {
        val state = _uiState.value
        val year = state.transactionYear.trim()
        if (!Regex("^\\d{4}$").matches(year)) {
            _uiState.update { it.copy(message = "年份无效，请输入 4 位年份") }
            return
        }
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
                val parsed = llmApiService.parseTransactionsFromImages(compressed, year)
                val candidates = markDuplicates(parsed)

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
                        isDuplicate = it.isDuplicate,
                    )
                }
                val created = transactionRepository.createSelectedTransactionsAllowingDuplicates(items)
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

    private suspend fun markDuplicates(parsed: List<ParsedTransaction>): List<ImportCandidate> {
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

        return parsed.map { transaction ->
            val key = transactionRepository.createDedupeKey(
                transaction.occurredAt,
                transaction.amount,
                transaction.transactionType,
            )
            val isDuplicate = existingKeys.contains(key) || !seenKeys.add(key)
            ImportCandidate(
                occurredAt = transaction.occurredAt,
                transactionType = transaction.transactionType,
                amount = transaction.amount,
                description = transaction.description,
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
                    llmApiService = application.llmApiService,
                )
            }
        }
    }
}
