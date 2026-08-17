package com.xdl.xledger.ui.assets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.AssetEntity
import com.xdl.xledger.data.repository.AssetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 资产页 UI 状态。 */
data class AssetsUiState(
    val assets: List<AssetEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AssetsViewModel(
    private val assetRepository: AssetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState: StateFlow<AssetsUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadAssets()
    }

    fun loadAssets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val assets = assetRepository.getAll()
                _uiState.update { it.copy(assets = assets, isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载资产失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取资产")
                }
            }
        }
    }

    /** 新增账户；名称非空且金额有效时成功。 */
    suspend fun createAsset(accountName: String, amount: String): Boolean {
        val name = accountName.trim()
        val parsed = amount.trim().toDoubleOrNull()
        if (name.isEmpty()) {
            _message.value = "账户名称不能为空"
            return false
        }
        if (parsed == null || !parsed.isFinite()) {
            _message.value = "金额无效"
            return false
        }

        _isSaving.value = true
        try {
            assetRepository.create(name, "%.2f".format(parsed))
            loadAssets()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "新增资产失败", error)
            _message.value = error.message ?: "无法新增账户"
            return false
        } finally {
            _isSaving.value = false
        }
    }

    /** 更新账户。 */
    suspend fun updateAsset(assetId: Int, accountName: String, amount: String): Boolean {
        val name = accountName.trim()
        val parsed = amount.trim().toDoubleOrNull()
        if (name.isEmpty()) {
            _message.value = "账户名称不能为空"
            return false
        }
        if (parsed == null || !parsed.isFinite()) {
            _message.value = "金额无效"
            return false
        }

        _isSaving.value = true
        try {
            assetRepository.update(assetId, name, "%.2f".format(parsed))
            loadAssets()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "更新资产失败", error)
            _message.value = error.message ?: "无法保存账户"
            return false
        } finally {
            _isSaving.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        private const val TAG = "AssetsViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                AssetsViewModel(assetRepository = application.assetRepository)
            }
        }
    }
}
