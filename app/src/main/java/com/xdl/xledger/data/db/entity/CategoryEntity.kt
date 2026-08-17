package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 交易分类。
 *
 * [categoryIcon] 旧版默认为 "•"，原生版暂不渲染图标，仅占位保留。
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    val categoryId: Int = 0,

    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "category_icon")
    val categoryIcon: String = "•",
)
