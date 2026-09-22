package com.xdl.xledger.ui.categories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分类管理页 UI 状态。 */
data class CategoriesUiState(
    /** 当前展示的收支类型：0 = 支出，1 = 收入。 */
    val selectedType: Int = CategoryEntity.TYPE_EXPENSE,
    /** 当前类型的全部分类（一级和二级混在一起，由界面组织成树）。 */
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadCategories()
    }

    fun switchType(transactionType: Int) {
        if (_uiState.value.selectedType == transactionType) return
        _uiState.update { it.copy(selectedType = transactionType) }
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            val type = _uiState.value.selectedType
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val categories = categoryRepository.getByType(type)
                _uiState.update { it.copy(categories = categories, isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载分类失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取分类")
                }
            }
        }
    }

    /**
     * 新增分类（类型跟随当前 Tab）。
     *
     * @param parentId 为 null 时新增一级分类；非 null 时新增该一级下的二级分类。
     * @return 是否创建成功。
     */
    suspend fun createCategory(name: String, parentId: Int? = null): Boolean {
        val type = _uiState.value.selectedType
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _message.value = "分类名称不能为空"
            return false
        }
        // 同级下不允许重名
        val duplicated = _uiState.value.categories.any { category ->
            category.parentId == parentId && category.categoryName == trimmed
        }
        if (duplicated) {
            _message.value = "同级下已存在同名分类"
            return false
        }

        _isSaving.value = true
        try {
            categoryRepository.create(trimmed, type, parentId)
            loadCategories()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "新增分类失败", error)
            _message.value = error.message ?: "无法新增分类"
            return false
        } finally {
            _isSaving.value = false
        }
    }

    /** 删除分类并自动解除相关交易关联（一级分类连同二级一起删除）。 */
    suspend fun deleteCategory(categoryId: Int) {
        try {
            categoryRepository.deleteCategoryAndUnassignTransactions(categoryId)
            loadCategories()
        } catch (error: Exception) {
            Log.w(TAG, "删除分类失败", error)
            _message.value = error.message ?: "无法删除分类"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        private const val TAG = "CategoriesViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                CategoriesViewModel(categoryRepository = application.categoryRepository)
            }
        }
    }
}
