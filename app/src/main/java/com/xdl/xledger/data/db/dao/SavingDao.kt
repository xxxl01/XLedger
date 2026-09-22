package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.SavingEntity

@Dao
interface SavingDao {

    @Insert
    suspend fun insert(saving: SavingEntity): Long

    @Query("SELECT * FROM savings WHERE saving_id = :id LIMIT 1")
    suspend fun getById(id: Int): SavingEntity?

    /** 半开区间 [startAt, endAt)，按时间倒序。 */
    @Query(
        "SELECT * FROM savings WHERE occurred_at >= :startAt AND occurred_at < :endAt " +
            "ORDER BY occurred_at DESC, saving_id DESC",
    )
    suspend fun getByDateRange(startAt: String, endAt: String): List<SavingEntity>

    @Query("SELECT * FROM savings ORDER BY occurred_at DESC, saving_id DESC")
    suspend fun getAll(): List<SavingEntity>

    /** 累计省下金额（全部记录，原价 - 实付）。 */
    @Query(
        "SELECT COALESCE(SUM(CAST(original_amount AS REAL) - CAST(actual_amount AS REAL)), 0) " +
            "FROM savings",
    )
    suspend fun sumSaved(): Double

    @Query(
        "UPDATE savings SET item_name = :itemName, original_amount = :originalAmount, " +
            "actual_amount = :actualAmount, occurred_at = :occurredAt, note = :note " +
            "WHERE saving_id = :id",
    )
    suspend fun update(
        id: Int,
        itemName: String,
        originalAmount: String,
        actualAmount: String,
        occurredAt: String,
        note: String?,
    )

    @Query("DELETE FROM savings WHERE saving_id = :id")
    suspend fun deleteById(id: Int): Int
}
