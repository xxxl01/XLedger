package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.AssetEntity

@Dao
interface AssetDao {

    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Query("SELECT * FROM assets WHERE asset_id = :id LIMIT 1")
    suspend fun getById(id: Int): AssetEntity?

    @Query("SELECT * FROM assets ORDER BY asset_id DESC")
    suspend fun getAll(): List<AssetEntity>

    @Query(
        "UPDATE assets SET account_name = :accountName, amount = :amount WHERE asset_id = :id",
    )
    suspend fun update(id: Int, accountName: String, amount: String)

    @Query("DELETE FROM assets WHERE asset_id = :id")
    suspend fun deleteById(id: Int): Int
}
