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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 列表筛选。 */
enum class TransactionFilter(val label: String) {
    All("全部"),
    Expense("支出"),
    Income("收入"),
    Uncategorized("未分类"),
}

/** 账本主页 UI 状态。 */
data class HomeUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val filter: TransactionFilter = TransactionFilter.All,
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 账本主页 ViewModel：只负责列表、月份、筛选与分类数据。
 *
 * 单条记录的编辑（含抵消）在 [com.xdl.xledger.ui.edit.TransactionEditViewModel] 里。
 */
class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新加载当前月份交易与全部分类。
     *
     * 已有列表时静默刷新：不切到 loading、不把列表移出组合，
     * 这样从编辑页返回时 LazyColumn 的滚动位置不会被重置。
     */
    fun refresh() {
        loadTransactions(showLoading = false)
        loadCategories()
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val target = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        _uiState.update { it.copy(year = target.year, month = target.monthValue) }
        // 换月份是全新数据，从顶部开始，正常显示加载态
        loadTransactions(showLoading = true)
    }

    fun setFilter(filter: TransactionFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    // ---- 数据加载 ----

    private fun loadTransactions(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading || _uiState.value.transactions.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(errorMessage = null) }
            }
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
