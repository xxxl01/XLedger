package com.xdl.xledger.ui.savings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.StorageEntity
import com.xdl.xledger.data.repository.StorageRepository
import com.xdl.xledger.util.MoneyFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditStorageForm(
    val storageId: Int?,
    val itemName: String,
    val totalAmount: String,
    val remainingAmount: String,
    val deductionAmount: String,
    val note: String,
)

data class StorageUiState(
    val items: List<StorageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editForm: EditStorageForm? = null,
    val isSavingEdit: Boolean = false,
    val editMessage: String? = null,
    val deductTarget: StorageEntity? = null,
    val deductAmount: String = "",
    val isDeducting: Boolean = false,
    val deductMessage: String? = null,
)

class StorageViewModel(
    private val repository: StorageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                _uiState.update { it.copy(items = repository.getAll(), isLoading = false) }
            } catch (error: Exception) {
                Log.w(TAG, "加载储藏记录失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取储藏记录")
                }
            }
        }
    }

    fun openCreate() {
        _uiState.update {
            it.copy(
                editForm = EditStorageForm(null, "", "", "", "", ""),
                editMessage = null,
            )
        }
    }

    fun openEdit(item: StorageEntity) {
        _uiState.update {
            it.copy(
                editForm = EditStorageForm(
                    item.storageId,
                    item.itemName,
                    item.totalAmount,
                    item.remainingAmount,
                    item.deductionAmount,
                    item.note.orEmpty(),
                ),
                editMessage = null,
            )
        }
    }

    fun closeEdit() {
        _uiState.update { it.copy(editForm = null, isSavingEdit = false, editMessage = null) }
    }

    fun onItemNameChange(value: String) = updateForm { copy(itemName = value) }
    fun onTotalAmountChange(value: String) = updateForm { copy(totalAmount = value) }
    fun onRemainingAmountChange(value: String) = updateForm { copy(remainingAmount = value) }
    fun onDeductionAmountChange(value: String) = updateForm { copy(deductionAmount = value) }
    fun onNoteChange(value: String) = updateForm { copy(note = value) }

    private fun updateForm(update: EditStorageForm.() -> EditStorageForm) {
        _uiState.update { state -> state.copy(editForm = state.editForm?.update()) }
    }

    fun saveEdit() {
        val form = _uiState.value.editForm ?: return
        val itemName = form.itemName.trim()
        val total = parseCents(form.totalAmount)
        // 新建时剩余金额留空表示整笔尚未使用，自动取囤入总金额。
        val remaining = parseCents(form.remainingAmount) ?: if (form.storageId == null) total else null
        val deduction = parseCents(form.deductionAmount)
        when {
            itemName.isEmpty() -> setEditMessage("物品名称不能为空")
            total == null || total <= 0 -> setEditMessage("囤入总金额必须大于 0")
            remaining == null || remaining < 0 || remaining > total ->
                setEditMessage("剩余金额必须在 0 与总金额之间")
            deduction == null || deduction <= 0 -> setEditMessage("每次扣除金额必须大于 0")
            else -> {
                _uiState.update { it.copy(isSavingEdit = true, editMessage = null) }
                viewModelScope.launch {
                    try {
                        val totalText = MoneyFormat.fromCents(total)
                        val remainingText = MoneyFormat.fromCents(remaining)
                        val deductionText = MoneyFormat.fromCents(deduction)
                        val note = form.note.trim().ifEmpty { null }
                        if (form.storageId == null) {
                            repository.create(itemName, totalText, deductionText, note)
                        } else {
                            repository.update(
                                form.storageId,
                                itemName,
                                totalText,
                                remainingText,
                                deductionText,
                                note,
                            )
                        }
                        closeEdit()
                        refresh()
                    } catch (error: Exception) {
                        Log.w(TAG, "保存储藏记录失败", error)
                        _uiState.update {
                            it.copy(isSavingEdit = false, editMessage = error.message ?: "无法保存记录")
                        }
                    }
                }
            }
        }
    }

    fun deleteEdit() {
        val id = _uiState.value.editForm?.storageId ?: return
        viewModelScope.launch {
            try {
                repository.delete(id)
                closeEdit()
                refresh()
            } catch (error: Exception) {
                _uiState.update { it.copy(editMessage = error.message ?: "无法删除记录") }
            }
        }
    }

    fun openDeduct(item: StorageEntity) {
        _uiState.update {
            it.copy(deductTarget = item, deductAmount = item.deductionAmount, deductMessage = null)
        }
    }

    fun closeDeduct() {
        _uiState.update {
            it.copy(deductTarget = null, deductAmount = "", isDeducting = false, deductMessage = null)
        }
    }

    fun onDeductAmountChange(value: String) {
        _uiState.update { it.copy(deductAmount = value, deductMessage = null) }
    }

    fun deduct() {
        val target = _uiState.value.deductTarget ?: return
        val amount = parseCents(_uiState.value.deductAmount)
        when {
            amount == null || amount <= 0 -> _uiState.update { it.copy(deductMessage = "请输入大于 0 的扣除金额") }
            amount > MoneyFormat.toCents(target.remainingAmount) ->
                _uiState.update { it.copy(deductMessage = "扣除金额不能超过剩余金额") }
            else -> {
                _uiState.update { it.copy(isDeducting = true, deductMessage = null) }
                viewModelScope.launch {
                    try {
                        repository.deduct(target.storageId, MoneyFormat.fromCents(amount))
                        closeDeduct()
                        refresh()
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(isDeducting = false, deductMessage = error.message ?: "扣除失败")
                        }
                    }
                }
            }
        }
    }

    private fun setEditMessage(message: String) = _uiState.update { it.copy(editMessage = message) }

    private fun parseCents(value: String): Long? =
        value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
            MoneyFormat.toCents(it.toString())
        }

    companion object {
        private const val TAG = "StorageViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                StorageViewModel(application.storageRepository)
            }
        }
    }
}
