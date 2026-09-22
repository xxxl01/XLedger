package com.xdl.xledger.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.xdl.xledger.XLedgerApplication
import com.xdl.xledger.data.db.AppDatabase
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.OffsetEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DataTransferUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
)

@Serializable
private data class BackupData(
    val version: Int = 1,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    /** 抵消记录，v1 旧备份没有该字段，缺省为空列表。 */
    val offsets: List<BackupOffset> = emptyList(),
)

@Serializable
private data class BackupCategory(
    val categoryId: Int,
    val categoryName: String,
    val transactionType: Int,
    val parentId: Int?,
    val sortOrder: Int,
)

@Serializable
private data class BackupTransaction(
    val transactionId: Int,
    val transactionType: Int,
    val amount: String,
    val occurredAt: String,
    val description: String?,
    val categoryId: Int?,
    val dedupeKey: String,
)

@Serializable
private data class BackupOffset(
    val offsetId: Int,
    val incomeId: Int,
    val expenseId: Int,
    val amount: String,
    val createdAt: String,
)

class DataTransferViewModel(
    private val database: AppDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataTransferUiState())
    val uiState: StateFlow<DataTransferUiState> = _uiState.asStateFlow()

    fun exportData(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = DataTransferUiState(isWorking = true)
        viewModelScope.launch {
            try {
                val backup = BackupData(
                    version = BACKUP_VERSION,
                    categories = database.categoryDao().getAll().map {
                        BackupCategory(it.categoryId, it.categoryName, it.transactionType, it.parentId, it.sortOrder)
                    },
                    transactions = database.transactionDao().getAll().map {
                        BackupTransaction(
                            it.transactionId, it.transactionType, it.amount, it.occurredAt,
                            it.description, it.categoryId, it.dedupeKey,
                        )
                    },
                    offsets = database.offsetDao().getAll().map {
                        BackupOffset(it.offsetId, it.incomeId, it.expenseId, it.amount, it.createdAt)
                    },
                )
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(Json.encodeToString(backup))
                } ?: error("无法创建备份文件")
                _uiState.value = DataTransferUiState(message = "已导出 ${backup.categories.size} 个分类和 ${backup.transactions.size} 条账单")
            } catch (error: Exception) {
                _uiState.value = DataTransferUiState(message = error.message ?: "导出失败")
            }
        }
    }

    fun importData(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = DataTransferUiState(isWorking = true)
        viewModelScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取备份文件")
                val backup = Json.decodeFromString<BackupData>(text)
                require(backup.version == 1 || backup.version == BACKUP_VERSION) { "不支持的备份文件版本" }
                val categoryIds = backup.categories.map { it.categoryId }.toSet()
                require(categoryIds.size == backup.categories.size) { "备份中存在重复分类 ID" }
                require(backup.categories.all { it.parentId == null || it.parentId in categoryIds }) {
                    "备份中的分类层级不完整"
                }
                require(backup.transactions.all { it.categoryId == null || it.categoryId in categoryIds }) {
                    "备份中的账单引用了不存在的分类"
                }
                val transactionIds = backup.transactions.map { it.transactionId }.toSet()
                require(backup.offsets.all { it.incomeId in transactionIds && it.expenseId in transactionIds }) {
                    "备份中的抵消记录引用了不存在的账单"
                }
                database.withTransaction {
                    database.offsetDao().deleteAll()
                    database.transactionDao().deleteAll()
                    database.categoryDao().deleteAll()
                    database.categoryDao().insertAll(backup.categories.map {
                        CategoryEntity(it.categoryId, it.categoryName, it.transactionType, it.parentId, it.sortOrder)
                    })
                    database.transactionDao().insertAll(backup.transactions.map {
                        TransactionEntity(
                            it.transactionId, it.transactionType, it.amount, it.occurredAt,
                            it.description, it.categoryId, it.dedupeKey,
                        )
                    })
                    database.offsetDao().insertAll(backup.offsets.map {
                        OffsetEntity(it.offsetId, it.incomeId, it.expenseId, it.amount, it.createdAt)
                    })
                }
                _uiState.value = DataTransferUiState(
                    message = "已导入 ${backup.categories.size} 个分类和 ${backup.transactions.size} 条账单",
                )
            } catch (error: Exception) {
                _uiState.value = DataTransferUiState(message = error.message ?: "导入失败")
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        /** 备份格式版本：v2 起包含 offsets（抵消记录），导入时仍兼容 v1 旧备份。 */
        private const val BACKUP_VERSION = 2

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as XLedgerApplication
                DataTransferViewModel(application.database)
            }
        }
    }
}
