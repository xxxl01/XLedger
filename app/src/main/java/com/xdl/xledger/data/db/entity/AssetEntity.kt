package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 资产账户及余额。
 *
 * [amount] 沿用字符串存储（两位小数），与旧版 drizzle numeric 列行为一致。
 */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "asset_id")
    val assetId: Int = 0,

    @ColumnInfo(name = "account_name")
    val accountName: String,

    val amount: String,
)
