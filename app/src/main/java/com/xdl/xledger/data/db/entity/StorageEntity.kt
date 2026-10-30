package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 储藏记录：囤入一项物品后，按实际使用情况逐次扣除金额。
 */
@Entity(
    tableName = "storage_items",
    indices = [Index(value = ["created_at"])],
)
data class StorageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "storage_id")
    val storageId: Int = 0,

    @ColumnInfo(name = "item_name")
    val itemName: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: String,

    @ColumnInfo(name = "remaining_amount")
    val remainingAmount: String,

    @ColumnInfo(name = "deduction_amount")
    val deductionAmount: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    /** 最近一次扣除操作时间；从未扣除时为空。 */
    @ColumnInfo(name = "last_deducted_at")
    val lastDeductedAt: String?,

    val note: String?,
)
