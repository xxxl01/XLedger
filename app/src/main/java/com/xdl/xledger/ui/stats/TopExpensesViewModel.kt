package com.xdl.xledger.ui.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.data.repository.CategoryRepository
import com.xdl.xledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 某月单笔金额最大的 [LIMIT] 笔支出，按金额从大到小排序。
 *
 * 已被完全抵消（金额 0）的支出不参与；金额是抵消后的剩余金额，与账本里显示的一致。
 * 复用 [CategoryTransactionsUiState]，列表展示与分类明细页一致。
 */
class TopExpensesViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val year: Int,
    private val month: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryTransactionsUiState(title = "大额支出"))
    val uiState: StateFlow<CategoryTransactionsUiState> = _uiState.asStateFlow()

    init {
        load(showLoading = true)
    }

    /** [showLoading] 为 false 时静默刷新（从编辑页返回），保留滚动位置。 */
    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showLoading, errorMessage = null) }
            try {
                val categoryById = categoryRepository.getAll().associateBy { it.categoryId }
                val items = transactionRepository.getByMonth(year, month)
                    .filter { it.transactionType == TransactionEntity.TYPE_EXPENSE }
                    .filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
                    .sortedWith(
                        compareByDescending<TransactionEntity> { it.amount.toDoubleOrNull() ?: 0.0 }
                            .thenByDescending { it.occurredAt },
                    )
                    .take(LIMIT)
                    .map { transaction ->
                        val category = transaction.categoryId?.let(categoryById::get)
                        val parent = category?.parentId?.let(categoryById::get)
                        CategoryTransactionItem(
                            transaction = transaction,
                            categoryLabel = when {
                                category == null -> "未分类"
                                parent != null -> "${parent.categoryName} / ${category.categoryName}"
                                else -> category.categoryName
                            },
                        )
                    }
                _uiState.update {
                    CategoryTransactionsUiState(
                        title = "大额支出",
                        items = items,
                        total = items.sumOf { it.transaction.amount.toDoubleOrNull() ?: 0.0 },
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载大额支出失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取大额支出")
                }
            }
        }
    }

    companion object {
        private const val TAG = "TopExpensesViewModel"
        private const val LIMIT = 20

        fun factory(year: Int, month: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                TopExpensesViewModel(
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                    year = year,
                    month = month,
                )
            }
        }
    }
}
