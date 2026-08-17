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

/** 单个分类的统计行。 */
data class CategoryStat(
    val key: String,
    val name: String,
    val amount: Double,
    val percent: Double,
)

/** 分类统计页 UI 状态。 */
data class MonthlyCategoryStatsUiState(
    val year: Int,
    val month: Int,
    val expenseStats: List<CategoryStat> = emptyList(),
    val incomeStats: List<CategoryStat> = emptyList(),
    val expenseTotal: Double = 0.0,
    val incomeTotal: Double = 0.0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class MonthlyCategoryStatsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val year: Int,
    private val month: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyCategoryStatsUiState(year = year, month = month))
    val uiState: StateFlow<MonthlyCategoryStatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val transactions = transactionRepository.getByMonth(year, month)
                val categories = categoryRepository.getAll()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        expenseStats = buildStats(transactions, categories, TransactionEntity.TYPE_EXPENSE),
                        incomeStats = buildStats(transactions, categories, TransactionEntity.TYPE_INCOME),
                        expenseTotal = sumOf(transactions, TransactionEntity.TYPE_EXPENSE),
                        incomeTotal = sumOf(transactions, TransactionEntity.TYPE_INCOME),
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载分类统计失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取分类统计")
                }
            }
        }
    }

    private fun buildStats(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        transactionType: Int,
    ): List<CategoryStat> {
        val categoryById = categories.associateBy { it.categoryId }
        val amounts = LinkedHashMap<String, Pair<String, Double>>()

        for (transaction in transactions) {
            if (transaction.transactionType != transactionType) continue
            val key = transaction.categoryId?.toString() ?: "uncategorized"
            val name = transaction.categoryId?.let(categoryById::get)?.categoryName ?: "未分类"
            val current = amounts[key] ?: (name to 0.0)
            amounts[key] = current.first to (current.second + (transaction.amount.toDoubleOrNull() ?: 0.0))
        }

        val rows = amounts.map { (key, value) -> CategoryStat(key, value.first, value.second, 0.0) }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }
        val total = rows.sumOf { it.amount }

        return rows.map { stat ->
            stat.copy(percent = if (total > 0) stat.amount / total else 0.0)
        }
    }

    private fun sumOf(transactions: List<TransactionEntity>, transactionType: Int): Double =
        transactions
            .filter { it.transactionType == transactionType }
            .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    companion object {
        private const val TAG = "MonthlyCategoryStatsViewModel"

        fun factory(year: Int, month: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                MonthlyCategoryStatsViewModel(
                    transactionRepository = application.transactionRepository,
                    categoryRepository = application.categoryRepository,
                    year = year,
                    month = month,
                )
            }
        }
    }
}
