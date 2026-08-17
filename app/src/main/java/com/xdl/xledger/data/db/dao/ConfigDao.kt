package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xdl.xledger.data.db.entity.ConfigEntity

@Dao
interface ConfigDao {

    /** 按 config_key upsert（REPLACE 保持 key 唯一）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ConfigEntity): Long

    @Query("SELECT * FROM configs WHERE config_key = :key LIMIT 1")
    suspend fun getByKey(key: String): ConfigEntity?

    @Query("SELECT * FROM configs ORDER BY config_key ASC")
    suspend fun getAll(): List<ConfigEntity>

    @Query("DELETE FROM configs WHERE config_key = :key")
    suspend fun deleteByKey(key: String): Int
}
