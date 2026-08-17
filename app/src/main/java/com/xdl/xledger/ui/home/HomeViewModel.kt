package com.xdl.xledger.ui.home

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
import com.xdl.xledger.util.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** 列表筛选。 */
enum class TransactionFilter(val label: String) {
    All("全部"),
    Expense("支出"),
    Income("收入"),
    Uncategorized("未分类"),
}

/** 编辑表单（本地时区展示字段）。 */
data class EditTransactionForm(
    val transactionId: Int,
    val description: String,
    val amount: String,
    val occurredAt: String,
    val transactionType: Int,
    val categoryId: Int?,
)

/** 账本主页 UI 状态。 */
data class HomeUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val filter: TransactionFilter = TransactionFilter.All,
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editForm: EditTransactionForm? = null,
    val isCreatingCategory: Boolean = false,
    val editCategoryName: String = "",
    val isSavingEdit: Boolean = false,
    val editMessage: String? = null,
)

class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 重新加载当前月份交易与全部分类。 */
    fun refresh() {
        loadTransactions()
        loadCategories()
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val target = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        _uiState.update { it.copy(year = target.year, month = target.monthValue) }
        loadTransactions()
    }

    fun setFilter(filter: TransactionFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    // ---- 编辑 / 删除 ----

    fun openEdit(transaction: TransactionEntity) {
        _uiState.update {
            it.copy(
                editForm = EditTransactionForm(
                    transactionId = transaction.transactionId,
                    description = transaction.description.orEmpty(),
                    amount = transaction.amount,
                    occurredAt = TimeFormat.toLocalDateTimeInput(
                        Instant.parse(transaction.occurredAt),
                    ),
                    transactionType = transaction.transactionType,
                    categoryId = transaction.categoryId,
                ),
                isCreatingCategory = false,
                editCategoryName = "",
                editMessage = null,
            )
        }
    }

    fun closeEdit() {
        _uiState.update {
            it.copy(
                editForm = null,
                isCreatingCategory = false,
                editCategoryName = "",
                isSavingEdit = false,
                editMessage = null,
            )
        }
    }

    fun onEditDescriptionChange(value: String) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(description = value))
        }
    }

    fun onEditAmountChange(value: String) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(amount = value))
        }
    }

    fun onEditOccurredAtChange(value: String) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(occurredAt = value))
        }
    }

    fun onEditTypeChange(transactionType: Int) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(transactionType = transactionType))
        }
    }

    fun onEditCategoryChange(categoryId: Int?) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(categoryId = categoryId))
        }
    }

    fun startCreateCategory() {
        _uiState.update { it.copy(isCreatingCategory = true, editCategoryName = "") }
    }

    fun cancelCreateCategory() {
        _uiState.update { it.copy(isCreatingCategory = false, editCategoryName = "") }
    }

    fun onEditCategoryNameChange(value: String) {
        _uiState.update { it.copy(editCategoryName = value) }
    }

    /** 编辑弹窗内新建分类并自动选中。 */
    fun createEditCategory() {
        val name = _uiState.value.editCategoryName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(editMessage = "分类名称不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                val category = categoryRepository.create(name)
                _uiState.update { state ->
                    state.copy(
                        categories = listOf(category) + state.categories,
                        editForm = state.editForm?.copy(categoryId = category.categoryId),
                        isCreatingCategory = false,
                        editCategoryName = "",
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "新增分类失败", error)
                _uiState.update { it.copy(editMessage = error.message ?: "无法新增分类") }
            }
        }
    }

    /** 保存编辑：校验金额 / 时间 / 类型后更新并重载。 */
    fun saveEdit() {
        val form = _uiState.value.editForm ?: return
        val amount = abs(form.amount.toDoubleOrNull() ?: 0.0)
        if (amount <= 0) {
            _uiState.update { it.copy(editMessage = "金额无效，请输入大于 0 的金额") }
            return
        }
        val occurredAt = TimeFormat.parseLocalDateTime(form.occurredAt)
        if (occurredAt == null) {
            _uiState.update { it.copy(editMessage = "时间无效，请输入类似 2026-05-04 12:30 的时间") }
            return
        }
        if (form.transactionType != TransactionEntity.TYPE_EXPENSE &&
            form.transactionType != TransactionEntity.TYPE_INCOME
        ) {
            _uiState.update { it.copy(editMessage = "类型无效，请选择收入或支出") }
            return
        }

        _uiState.update { it.copy(isSavingEdit = true, editMessage = null) }
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(
                    form.transactionId,
                    TransactionRepository.TransactionPatch(
                        description = form.description.trim().ifEmpty { null },
                        amount = "%.2f".format(amount),
                        occurredAt = TimeFormat.toIsoString(occurredAt),
                        transactionType = form.transactionType,
                        categoryId = form.categoryId,
                    ),
                )
                closeEdit()
                loadTransactions()
            } catch (error: Exception) {
                Log.w(TAG, "保存交易失败", error)
                _uiState.update {
                    it.copy(
                        isSavingEdit = false,
                        editMessage = error.message ?: "无法保存记录",
                    )
                }
            }
        }
    }

    /** 删除当前编辑的交易并重载。 */
    fun deleteEditTransaction() {
        val form = _uiState.value.editForm ?: return
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(form.transactionId)
                closeEdit()
                loadTransactions()
            } catch (error: Exception) {
                Log.w(TAG, "删除交易失败", error)
                _uiState.update {
                    it.copy(
                        isSavingEdit = false,
                        editMessage = error.message ?: "无法删除记录",
                    )
                }
            }
        }
    }

    fun consumeEditMessage() {
        _uiState.update { it.copy(editMessage = null) }
    }

    // ---- 数据加载 ----

    private fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            try {
                val transactions = transactionRepository.getByMonth(state.year, state.month)
                _uiState.update { it.copy(transactions = transactions, isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载账本失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取账本数据")
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = categoryRepository.getAll()
                _uiState.update { it.copy(categories = categories) }
            } catch (error: Exception) {
                Log.w(TAG, "加载分类失败", error)
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                HomeViewModel(
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                )
            }
        }
    }
}
