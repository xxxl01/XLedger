package com.xdl.xledger.ui.stats

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

/** 明细行：一条记录及其分类名（二级分类显示为“一级 / 二级”）。 */
data class CategoryTransactionItem(
    val transaction: TransactionEntity,
    val categoryLabel: String,
)

/** 分类明细页 UI 状态。 */
data class CategoryTransactionsUiState(
    val title: String = "",
    val items: List<CategoryTransactionItem> = emptyList(),
    val total: Double = 0.0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 某月某分类的明细：与分类统计页同口径（同收支类型、金额大于 0），按金额从大到小排序。
 *
 * [categoryId] 为 null 表示“未分类”（无分类或分类已被删除）；
 * [includeChildren] 为 true 时同时包含该一级分类下所有二级分类的记录。
 */
class CategoryTransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val year: Int,
    private val month: Int,
    private val transactionType: Int,
    private val categoryId: Int?,
    private val includeChildren: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryTransactionsUiState())
    val uiState: StateFlow<CategoryTransactionsUiState> = _uiState.asStateFlow()

    init {
        load(showLoading = true)
    }

    /** [showLoading] 为 false 时静默刷新（从编辑页返回），保留滚动位置。 */
    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showLoading, errorMessage = null) }
            try {
                val categories = categoryRepository.getAll()
                val transactions = transactionRepository.getByMonth(year, month)
                _uiState.update { build(transactions, categories) }
            } catch (error: Exception) {
                Log.w(TAG, "加载分类明细失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取分类明细")
                }
            }
        }
    }

    private fun build(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): CategoryTransactionsUiState {
        val categoryById = categories.associateBy { it.categoryId }
        val selected = categoryById[categoryId]
        // 未分类：categoryId 为 null，或所属分类已不存在
        val matchIds: Set<Int>? = categoryId?.let { id ->
            buildSet {
                add(id)
                if (includeChildren) {
                    categories.filter { it.parentId == id }.forEach { add(it.categoryId) }
                }
            }
        }

        val items = transactions
            .filter { it.transactionType == transactionType }
            .filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
            .filter { transaction ->
                val id = transaction.categoryId?.takeIf { it in categoryById }
                if (matchIds == null) id == null else id in matchIds
            }
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
            .sortedWith(
                compareByDescending<CategoryTransactionItem> { it.transaction.amount.toDoubleOrNull() ?: 0.0 }
                    .thenByDescending { it.transaction.occurredAt },
            )

        return CategoryTransactionsUiState(
            title = if (categoryId == null) "未分类" else selected?.categoryName ?: "分类",
            items = items,
            total = items.sumOf { it.transaction.amount.toDoubleOrNull() ?: 0.0 },
        )
    }

    companion object {
        private const val TAG = "CategoryTransactionsViewModel"

        fun factory(
            year: Int,
            month: Int,
            transactionType: Int,
            categoryId: Int?,
            includeChildren: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                CategoryTransactionsViewModel(
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                    year = year,
                    month = month,
                    transactionType = transactionType,
                    categoryId = categoryId,
                    includeChildren = includeChildren,
                )
            }
        }
    }
}
