package com.xdl.xledger.ui.savings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.entity.SavingEntity
import com.xdl.xledger.data.repository.SavingRepository
import com.xdl.xledger.util.DateTimeFormat
import com.xdl.xledger.util.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 省钱记录编辑表单（本地时区展示字段）。
 *
 * [savingId] 为 null 表示新增。
 */
data class EditSavingForm(
    val savingId: Int?,
    val itemName: String,
    val originalAmount: String,
    val actualAmount: String,
    val date: LocalDate,
    val note: String,
)

/** 省钱页 UI 状态。 */
data class SavingsUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val savings: List<SavingEntity> = emptyList(),
    /** 累计省下金额（全部月份）。 */
    val totalSaved: Double = 0.0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editForm: EditSavingForm? = null,
    val isSavingEdit: Boolean = false,
    val editMessage: String? = null,
)

class SavingsViewModel(
    private val savingRepository: SavingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            try {
                val savings = savingRepository.getByMonth(state.year, state.month)
                val totalSaved = savingRepository.getTotalSaved()
                _uiState.update {
                    it.copy(savings = savings, totalSaved = totalSaved, isLoading = false)
                }
            } catch (error: Exception) {
                Log.w(TAG, "加载省钱记录失败", error)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "无法读取省钱记录")
                }
            }
        }
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value
        val target = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        _uiState.update { it.copy(year = target.year, month = target.monthValue) }
        refresh()
    }

    // ---- 新增 / 编辑 / 删除 ----

    fun openCreate() {
        _uiState.update {
            it.copy(
                editForm = EditSavingForm(
                    savingId = null,
                    itemName = "",
                    originalAmount = "",
                    actualAmount = "",
                    date = LocalDate.now(),
                    note = "",
                ),
                editMessage = null,
            )
        }
    }

    fun openEdit(saving: SavingEntity) {
        _uiState.update {
            it.copy(
                editForm = EditSavingForm(
                    savingId = saving.savingId,
                    itemName = saving.itemName,
                    originalAmount = saving.originalAmount,
                    actualAmount = saving.actualAmount,
                    date = DateTimeFormat.parseToLocal(saving.occurredAt)?.toLocalDate()
                        ?: LocalDate.now(),
                    note = saving.note.orEmpty(),
                ),
                editMessage = null,
            )
        }
    }

    fun closeEdit() {
        _uiState.update { it.copy(editForm = null, isSavingEdit = false, editMessage = null) }
    }

    fun onEditItemNameChange(value: String) {
        _uiState.update { state -> state.copy(editForm = state.editForm?.copy(itemName = value)) }
    }

    fun onEditOriginalAmountChange(value: String) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(originalAmount = value))
        }
    }

    fun onEditActualAmountChange(value: String) {
        _uiState.update { state ->
            state.copy(editForm = state.editForm?.copy(actualAmount = value))
        }
    }

    fun onEditDateChange(value: LocalDate) {
        _uiState.update { state -> state.copy(editForm = state.editForm?.copy(date = value)) }
    }

    fun onEditNoteChange(value: String) {
        _uiState.update { state -> state.copy(editForm = state.editForm?.copy(note = value)) }
    }

    /** 保存：校验项目名 / 金额后写库并重载。 */
    fun saveEdit() {
        val form = _uiState.value.editForm ?: return
        val itemName = form.itemName.trim()
        if (itemName.isEmpty()) {
            _uiState.update { it.copy(editMessage = "项目名称不能为空") }
            return
        }
        val original = form.originalAmount.trim().toDoubleOrNull()
        if (original == null || !original.isFinite() || original < 0) {
            _uiState.update { it.copy(editMessage = "原价无效，请输入不小于 0 的金额") }
            return
        }
        val actual = form.actualAmount.trim().toDoubleOrNull()
        if (actual == null || !actual.isFinite() || actual < 0) {
            _uiState.update { it.copy(editMessage = "实际花费无效，请输入不小于 0 的金额") }
            return
        }
        // 只记日期，时间固定为所选日期本地零点，存成 ISO 以复用原有月份范围查询
        val occurredAt = TimeFormat.toIsoString(
            form.date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        )

        _uiState.update { it.copy(isSavingEdit = true, editMessage = null) }
        viewModelScope.launch {
            try {
                val note = form.note.trim().ifEmpty { null }
                if (form.savingId == null) {
                    savingRepository.create(
                        itemName = itemName,
                        originalAmount = "%.2f".format(original),
                        actualAmount = "%.2f".format(actual),
                        occurredAt = occurredAt,
                        note = note,
                    )
                } else {
                    savingRepository.update(
                        savingId = form.savingId,
                        itemName = itemName,
                        originalAmount = "%.2f".format(original),
                        actualAmount = "%.2f".format(actual),
                        occurredAt = occurredAt,
                        note = note,
                    )
                }
                // 记录日期可能落在其他月份，切到该月再刷新，保证保存后能看到这条记录
                _uiState.update {
                    it.copy(
                        year = form.date.year,
                        month = form.date.monthValue,
                        editForm = null,
                        isSavingEdit = false,
                        editMessage = null,
                    )
                }
                refresh()
            } catch (error: Exception) {
                Log.w(TAG, "保存省钱记录失败", error)
                _uiState.update {
                    it.copy(isSavingEdit = false, editMessage = error.message ?: "无法保存记录")
                }
            }
        }
    }

    /** 删除当前编辑的记录并重载。 */
    fun deleteEditSaving() {
        val savingId = _uiState.value.editForm?.savingId ?: return
        viewModelScope.launch {
            try {
                savingRepository.delete(savingId)
                closeEdit()
                refresh()
            } catch (error: Exception) {
                Log.w(TAG, "删除省钱记录失败", error)
                _uiState.update {
                    it.copy(isSavingEdit = false, editMessage = error.message ?: "无法删除记录")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SavingsViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as XLedgerApplication
                SavingsViewModel(savingRepository = application.savingRepository)
            }
        }
    }
}
