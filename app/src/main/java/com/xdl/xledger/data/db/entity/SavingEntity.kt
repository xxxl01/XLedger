package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 省钱记录。
 *
 * - [originalAmount]: 本来应该花的钱（原价）
 * - [actualAmount]: 实际花的钱
 * - 省下的金额 = originalAmount - actualAmount，不落库，读取时计算
 * - [occurredAt]: ISO-8601 UTC 字符串，与 transactions 保持一致
 */
@Entity(
    tableName = "savings",
    indices = [
        Index(value = ["occurred_at"]),
    ],
)
data class SavingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "saving_id")
    val savingId: Int = 0,

    @ColumnInfo(name = "item_name")
    val itemName: String,

    @ColumnInfo(name = "original_amount")
    val originalAmount: String,

    @ColumnInfo(name = "actual_amount")
    val actualAmount: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: String,

    val note: String?,
) {
    /** 本条省下的金额。 */
    val savedAmount: Double
        get() = (originalAmount.toDoubleOrNull() ?: 0.0) - (actualAmount.toDoubleOrNull() ?: 0.0)
}
