package com.xdl.xledger.ui.offsets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.dao.OffsetDao
import com.xdl.xledger.data.db.dao.OffsetDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 抵消记录页 UI 状态。 */
data class OffsetsUiState(
    val offsets: List<OffsetDetail> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class OffsetsViewModel(private val offsetDao: OffsetDao) : ViewModel() {

    private val _uiState = MutableStateFlow(OffsetsUiState())
    val uiState: StateFlow<OffsetsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val offsets = offsetDao.getAllDetails()
                _uiState.update { it.copy(offsets = offsets, isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载抵消记录失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取抵消记录")
                }
            }
        }
    }

    companion object {
        private const val TAG = "OffsetsViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                OffsetsViewModel(application.database.offsetDao())
            }
        }
    }
}
