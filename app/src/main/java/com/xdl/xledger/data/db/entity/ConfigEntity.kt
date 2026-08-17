package com.xdl.xledger.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地键值配置（含 LLM 设置）。
 */
@Entity(
    tableName = "configs",
    indices = [Index(value = ["config_key"], unique = true)],
)
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "config_id")
    val configId: Int = 0,

    @ColumnInfo(name = "config_key")
    val configKey: String,

    @ColumnInfo(name = "config_value")
    val configValue: String,
)
