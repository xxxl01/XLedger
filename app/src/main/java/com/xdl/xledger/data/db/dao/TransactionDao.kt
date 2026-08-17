package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xdl.xledger.data.db.entity.TransactionEntity

@Dao
interface TransactionDao {

    /** 插入并忽略 dedupe_key 冲突；返回 -1 表示冲突未插入。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    /** 强制插入（调用方保证不会与现有行冲突）。 */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** 查询已存在的 dedupe_key（导入重复检测）。 */
    @Query("SELECT dedupe_key FROM transactions WHERE dedupe_key IN (:keys)")
    suspend fun getDedupeKeys(keys: List<String>): List<String>

    @Query("SELECT * FROM transactions WHERE transaction_id = :id LIMIT 1")
    suspend fun getById(id: Int): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, transaction_id DESC")
    suspend fun getAll(): List<TransactionEntity>

    /** ISO-8601 字符串字典序即时间序，使用半开区间 [startAt, endAt)。 */
    @Query(
        "SELECT * FROM transactions WHERE occurred_at >= :startAt AND occurred_at < :endAt " +
            "ORDER BY occurred_at DESC, transaction_id DESC",
    )
    suspend fun getByDateRange(startAt: String, endAt: String): List<TransactionEntity>

    @Query(
        "UPDATE transactions SET transaction_type = :transactionType, amount = :amount, " +
            "occurred_at = :occurredAt, description = :description, category_id = :categoryId, " +
            "dedupe_key = :dedupeKey WHERE transaction_id = :id",
    )
    suspend fun update(
        id: Int,
        transactionType: Int,
        amount: String,
        occurredAt: String,
        description: String?,
        categoryId: Int?,
        dedupeKey: String,
    )

    /** 删除分类时解除关联：将对应交易的 category_id 置空。返回影响行数。 */
    @Query("UPDATE transactions SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategory(categoryId: Int): Int

    @Query("DELETE FROM transactions WHERE transaction_id = :id")
    suspend fun deleteById(id: Int): Int
}
