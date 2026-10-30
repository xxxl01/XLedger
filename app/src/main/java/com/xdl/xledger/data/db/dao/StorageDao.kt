package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.StorageEntity

@Dao
interface StorageDao {

    @Insert
    suspend fun insert(storage: StorageEntity): Long

    @Query("SELECT * FROM storage_items WHERE storage_id = :id LIMIT 1")
    suspend fun getById(id: Int): StorageEntity?

    @Query("SELECT * FROM storage_items ORDER BY remaining_amount > 0 DESC, created_at DESC, storage_id DESC")
    suspend fun getAll(): List<StorageEntity>

    @Query(
        "UPDATE storage_items SET item_name = :itemName, total_amount = :totalAmount, " +
            "remaining_amount = :remainingAmount, deduction_amount = :deductionAmount, note = :note " +
            "WHERE storage_id = :id",
    )
    suspend fun update(
        id: Int,
        itemName: String,
        totalAmount: String,
        remainingAmount: String,
        deductionAmount: String,
        note: String?,
    )

    @Query(
        "UPDATE storage_items SET remaining_amount = :remainingAmount, last_deducted_at = :lastDeductedAt " +
            "WHERE storage_id = :id AND CAST(remaining_amount AS REAL) >= CAST(:deductionAmount AS REAL)",
    )
    suspend fun deduct(
        id: Int,
        deductionAmount: String,
        remainingAmount: String,
        lastDeductedAt: String,
    ): Int

    @Query("DELETE FROM storage_items WHERE storage_id = :id")
    suspend fun deleteById(id: Int): Int
}
