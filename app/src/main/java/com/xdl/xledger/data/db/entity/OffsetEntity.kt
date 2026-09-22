package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 抵消记录：一条收入用于抵消一条支出。
 *
 * 抵消不删除原记录，而是把两边的 [TransactionEntity.amount] 各扣减掉本次抵消金额，
 * 完全抵消的记录金额留为 "0.00"。因此 transactions 表里的金额始终等于「当前剩余金额」，
 * 所有统计口径无需改动。
 *
 * - [amount]: 本次抵消金额，两位小数字符串，与 transactions 保持一致
 * - [createdAt]: 抵消操作时间，ISO-8601 UTC 字符串
 *
 * 同一笔记录可以被多次抵消（如一笔 100 元支出先被 30 元收入抵掉、再被 70 元收入抵掉），
 * 每次抵消写一行，历史抵消明细由本表还原。
 */
@Entity(
    tableName = "offsets",
    indices = [
        Index(value = ["income_id"]),
        Index(value = ["expense_id"]),
    ],
)
data class OffsetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "offset_id")
    val offsetId: Int = 0,

    @ColumnInfo(name = "income_id")
    val incomeId: Int,

    @ColumnInfo(name = "expense_id")
    val expenseId: Int,

    val amount: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,
)
