package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 交易分类。
 *
 * - [transactionType]: 0 = 支出，1 = 收入，收支分类各自独立
 * - [parentId]: null = 一级分类；非 null = 该一级分类下的二级分类
 * - [sortOrder]: 同级展示顺序（按创建先后）
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["parent_id"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    val categoryId: Int = 0,

    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "transaction_type")
    val transactionType: Int,

    @ColumnInfo(name = "parent_id")
    val parentId: Int?,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}
