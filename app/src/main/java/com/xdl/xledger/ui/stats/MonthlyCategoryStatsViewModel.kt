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

/** 单个分类的统计行（一级聚合行或二级明细行）。 */
data class CategoryStat(
    val key: String,
    val name: String,
    val amount: Double,
    /** 占比：一级行相对该收支类型总额；二级行相对其一级分类金额。 */
    val percent: Double,
)

/** 一级分类聚合行，可展开查看二级明细。 */
data class CategoryGroup(
    val categoryId: Int?,
    val name: String,
    val amount: Double,
    val percent: Double,
    val children: List<CategoryStat>,
)

/** 分类统计页 UI 状态。 */
data class MonthlyCategoryStatsUiState(
    val year: Int,
    val month: Int,
    val expenseStats: List<CategoryGroup> = emptyList(),
    val incomeStats: List<CategoryGroup> = emptyList(),
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
                        expenseStats = buildGroups(transactions, categories, TransactionEntity.TYPE_EXPENSE),
                        incomeStats = buildGroups(transactions, categories, TransactionEntity.TYPE_INCOME),
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

    /**
     * 按一级分类聚合统计：
     * - 交易属于二级分类时归入其一级分类
     * - 一级分类的二级明细记录在 [CategoryGroup.children]
     * - 无分类或分类已删除的交易归入“未分类”
     */
    private fun buildGroups(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        transactionType: Int,
    ): List<CategoryGroup> {
        val categoryById = categories.associateBy { it.categoryId }
        data class Agg(
            val name: String,
            var amount: Double = 0.0,
            val children: LinkedHashMap<String, Double> = LinkedHashMap(),
        )
        val aggs = LinkedHashMap<String, Agg>()

        for (transaction in transactions) {
            if (transaction.transactionType != transactionType) continue
            val amount = transaction.amount.toDoubleOrNull() ?: 0.0
            if (amount <= 0) continue

            val category = transaction.categoryId?.let(categoryById::get)
            when {
                category == null -> {
                    val agg = aggs.getOrPut("uncategorized") { Agg("未分类") }
                    agg.amount += amount
                }
                category.parentId == null -> {
                    val agg = aggs.getOrPut("c${category.categoryId}") { Agg(category.categoryName) }
                    agg.amount += amount
                }
                else -> {
                    val parent = category.parentId?.let(categoryById::get)
                    if (parent != null) {
                        val agg = aggs.getOrPut("c${parent.categoryId}") { Agg(parent.categoryName) }
                        agg.amount += amount
                        agg.children[category.categoryName] =
                            (agg.children[category.categoryName] ?: 0.0) + amount
                    } else {
                        // 父分类缺失（正常删除流程会级联清理），按自身作为一级展示
                        val agg = aggs.getOrPut("c${category.categoryId}") { Agg(category.categoryName) }
                        agg.amount += amount
                    }
                }
            }
        }

        val rows = aggs.map { (key, agg) ->
            val children = agg.children
                .map { (name, childAmount) -> CategoryStat(key = "$key#$name", name = name, amount = childAmount, percent = 0.0) }
                .sortedByDescending { it.amount }
            CategoryGroup(
                categoryId = key.removePrefix("c").toIntOrNull(),
                name = agg.name,
                amount = agg.amount,
                percent = 0.0,
                children = children,
            )
        }.filter { it.amount > 0 }.sortedByDescending { it.amount }

        val total = rows.sumOf { it.amount }
        return rows.map { group ->
            group.copy(
                percent = if (total > 0) group.amount / total else 0.0,
                children = group.children.map { child ->
                    child.copy(percent = if (group.amount > 0) child.amount / group.amount else 0.0)
                },
            )
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
