package com.xdl.xledger.ui.edit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.data.repository.CategoryRepository
import com.xdl.xledger.data.repository.TransactionRepository
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * 抵消弹窗状态：用当前收入去抵消一笔支出。
 *
 * 候选支出按月加载（[year] / [month] 可翻页），不一次性查询全部历史记录；
 * [keyword] 只在已加载的当月候选里按描述 / 金额过滤。
 */
data class OffsetPickerState(
    val incomeId: Int,
    val incomeDescription: String,
    val incomeAmount: String,
    val year: Int,
    val month: Int,
    val keyword: String = "",
    val expenses: List<TransactionEntity> = emptyList(),
    val selectedExpenseId: Int? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
)

/** 交易编辑页 UI 状态。 */
data class TransactionEditUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val description: String = "",
    val amount: String = "",
    val occurredAt: LocalDateTime = LocalDateTime.now(),
    val transactionType: Int = TransactionEntity.TYPE_EXPENSE,
    val categoryId: Int? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
    /** 保存 / 删除成功后置位，页面据此返回上一页。 */
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val offsetPicker: OffsetPickerState? = null,
)

/**
 * 单条交易的全屏编辑页 ViewModel。
 *
 * 独立于 [com.xdl.xledger.ui.home.HomeViewModel]，按 [transactionId] 加载记录与分类；
 * 抵消入口只在这里，抵消相关状态与方法也一并放在这里。
 */
class TransactionEditViewModel(
    private val transactionId: Int,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionEditUiState())
    val uiState: StateFlow<TransactionEditUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 重新加载记录与分类（抵消后金额会变，需要刷新）。 */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            try {
                val transaction = transactionRepository.getById(transactionId)
                    ?: error("记录不存在或已被删除")
                val categories = categoryRepository.getAll()
                val occurredAt = DateTimeFormat.parseToLocal(transaction.occurredAt)
                    ?.toLocalDateTime() ?: LocalDateTime.now()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        description = transaction.description.orEmpty(),
                        amount = transaction.amount,
                        occurredAt = occurredAt,
                        transactionType = transaction.transactionType,
                        categoryId = transaction.categoryId,
                        categories = categories,
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载交易失败", error)
                _uiState.update {
                    it.copy(isLoading = false, loadError = error.message ?: "无法读取记录")
                }
            }
        }
    }

    // ---- 表单编辑 ----

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(occurredAt = it.occurredAt.with(date)) }
    }

    fun onTimeChange(time: LocalTime) {
        _uiState.update { it.copy(occurredAt = it.occurredAt.with(time)) }
    }

    fun onTypeChange(transactionType: Int) {
        _uiState.update { it.copy(transactionType = transactionType) }
    }

    fun onCategoryChange(categoryId: Int?) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    // ---- 保存 / 删除 ----

    /** 校验金额 / 类型后更新并置 saved，由页面负责返回。 */
    fun save() {
        val state = _uiState.value
        val amount = abs(state.amount.toDoubleOrNull() ?: 0.0)
        if (amount <= 0) {
            _uiState.update { it.copy(message = "金额无效，请输入大于 0 的金额") }
            return
        }
        if (state.transactionType != TransactionEntity.TYPE_EXPENSE &&
            state.transactionType != TransactionEntity.TYPE_INCOME
        ) {
            _uiState.update { it.copy(message = "类型无效，请选择收入或支出") }
            return
        }

        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(
                    transactionId,
                    TransactionRepository.TransactionPatch(
                        description = state.description.trim().ifEmpty { null },
                        amount = "%.2f".format(amount),
                        occurredAt = TimeFormat.toIsoString(
                            state.occurredAt.atZone(ZoneId.systemDefault()).toInstant(),
                        ),
                        transactionType = state.transactionType,
                        categoryId = state.categoryId,
                    ),
                )
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (error: Exception) {
                Log.w(TAG, "保存交易失败", error)
                _uiState.update {
                    it.copy(isSaving = false, message = error.message ?: "无法保存记录")
                }
            }
        }
    }

    /** 删除记录并置 deleted，由页面负责返回。 */
    fun delete() {
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transactionId)
                _uiState.update { it.copy(isSaving = false, deleted = true) }
            } catch (error: Exception) {
                Log.w(TAG, "删除交易失败", error)
                _uiState.update {
                    it.copy(isSaving = false, message = error.message ?: "无法删除记录")
                }
            }
        }
    }

    // ---- 抵消 ----

    /** 打开抵消弹窗：候选支出默认加载这笔收入所在月份。 */
    fun openOffsetPicker() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                offsetPicker = OffsetPickerState(
                    incomeId = transactionId,
                    incomeDescription = state.description.ifBlank { "未命名记录" },
                    incomeAmount = state.amount,
                    year = state.occurredAt.year,
                    month = state.occurredAt.monthValue,
                ),
            )
        }
        loadOffsetExpenses()
    }

    fun closeOffsetPicker() {
        _uiState.update { it.copy(offsetPicker = null) }
    }

    fun shiftOffsetMonth(delta: Int) {
        val picker = _uiState.value.offsetPicker ?: return
        val target = YearMonth.of(picker.year, picker.month).plusMonths(delta.toLong())
        _uiState.update {
            it.copy(
                offsetPicker = picker.copy(
                    year = target.year,
                    month = target.monthValue,
                    selectedExpenseId = null,
                    message = null,
                ),
            )
        }
        loadOffsetExpenses()
    }

    fun onOffsetKeywordChange(value: String) {
        _uiState.update { state ->
            state.copy(offsetPicker = state.offsetPicker?.copy(keyword = value))
        }
    }

    fun onOffsetExpenseSelect(expenseId: Int) {
        _uiState.update { state ->
            state.copy(
                offsetPicker = state.offsetPicker?.copy(
                    selectedExpenseId = expenseId,
                    message = null,
                ),
            )
        }
    }

    /** 执行抵消：两边金额各扣减 min(收入剩余, 支出剩余)，记录都保留。 */
    fun confirmOffset() {
        val picker = _uiState.value.offsetPicker ?: return
        val expenseId = picker.selectedExpenseId
        if (expenseId == null) {
            _uiState.update { it.copy(offsetPicker = picker.copy(message = "请先选择要抵消的支出")) }
            return
        }

        _uiState.update { it.copy(offsetPicker = picker.copy(isSubmitting = true, message = null)) }
        viewModelScope.launch {
            try {
                transactionRepository.offsetIncomeWithExpense(picker.incomeId, expenseId)
                _uiState.update { it.copy(offsetPicker = null) }
                // 抵消后这笔收入的剩余金额变了，重新加载表单
                load()
            } catch (error: Exception) {
                Log.w(TAG, "抵消失败", error)
                _uiState.update { state ->
                    state.copy(
                        offsetPicker = state.offsetPicker?.copy(
                            isSubmitting = false,
                            message = error.message ?: "抵消失败",
                        ),
                    )
                }
            }
        }
    }

    private fun loadOffsetExpenses() {
        val picker = _uiState.value.offsetPicker ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(offsetPicker = state.offsetPicker?.copy(isLoading = true))
            }
            try {
                val expenses = transactionRepository.getOffsettableExpensesByMonth(
                    picker.year,
                    picker.month,
                )
                _uiState.update { state ->
                    state.copy(
                        offsetPicker = state.offsetPicker?.copy(
                            expenses = expenses,
                            isLoading = false,
                        ),
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载可抵消支出失败", error)
                _uiState.update { state ->
                    state.copy(
                        offsetPicker = state.offsetPicker?.copy(
                            isLoading = false,
                            message = error.message ?: "无法读取支出记录",
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TransactionEditViewModel"

        fun factory(transactionId: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                TransactionEditViewModel(
                    transactionId = transactionId,
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                )
            }
        }
    }
}
