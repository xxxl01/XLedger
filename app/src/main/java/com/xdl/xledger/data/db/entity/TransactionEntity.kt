package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收支记录。
 *
 * - [transactionType]: 0 = 支出, 1 = 收入
 * - [amount]: 两位小数字符串（如 "28.50"），与 dedupe_key 拼接逻辑保持一致
 * - [occurredAt]: ISO-8601 UTC 字符串（如 "2026-05-04T04:30:00.000Z"）
 * - [dedupeKey]: `occurred_at_amount_transaction_type`，仅用于导入时“可能重复”提示，
 *   不设唯一约束：是否保留重复记录由用户自行决定。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurred_at"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    val transactionId: Int = 0,

    @ColumnInfo(name = "transaction_type")
    val transactionType: Int,

    val amount: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: String,

    val description: String?,

    @ColumnInfo(name = "category_id")
    val categoryId: Int?,

    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String,
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}
