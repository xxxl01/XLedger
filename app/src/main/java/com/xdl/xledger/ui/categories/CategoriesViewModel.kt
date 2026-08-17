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

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val categories = categoryRepository.getAll()
                _uiState.update { it.copy(categories = categories, isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载分类失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取分类")
                }
            }
        }
    }

    /** 新增分类；名称为空或已存在时返回 false。 */
    suspend fun createCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _message.value = "分类名称不能为空"
            return false
        }
        if (_uiState.value.categories.any { it.categoryName == trimmed }) {
            _message.value = "分类已存在"
            return false
        }

        _isSaving.value = true
        try {
            categoryRepository.create(trimmed)
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

    /** 删除分类并自动解除相关交易关联。 */
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
