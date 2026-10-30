package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.TransactionEntity

@Dao
interface TransactionDao {

    /** 插入交易。dedupe_key 不再有唯一约束，重复记录可以并存。 */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /** 查询已存在的 dedupe_key（导入重复检测提示）。 */
    @Query("SELECT dedupe_key FROM transactions WHERE dedupe_key IN (:keys)")
    suspend fun getDedupeKeys(keys: List<String>): List<String>

    /**
     * 导入按名称带出分类：同名且已有分类的历史交易，最近的排在前面。
     * 调用方需分批传入 [descriptions]（SQLite 绑定参数有上限）。
     */
    @Query(
        "SELECT description, transaction_type AS transactionType, category_id AS categoryId " +
            "FROM transactions WHERE category_id IS NOT NULL AND description IN (:descriptions) " +
            "ORDER BY occurred_at DESC, transaction_id DESC",
    )
    suspend fun getCategorizedByDescriptions(descriptions: List<String>): List<DescriptionCategory>

    @Query("SELECT * FROM transactions WHERE transaction_id = :id LIMIT 1")
    suspend fun getById(id: Int): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, transaction_id DESC")
    suspend fun getAll(): List<TransactionEntity>

    /**
     * 抵消候选：指定时间范围内仍有剩余金额的支出，按时间倒序。半开区间 [startAt, endAt)。
     * 已被完全抵消（金额 0.00）的支出没有再抵消的意义，直接排除。
     */
    @Query(
        "SELECT * FROM transactions WHERE transaction_type = 0 AND CAST(amount AS REAL) > 0 " +
            "AND occurred_at >= :startAt AND occurred_at < :endAt " +
            "ORDER BY occurred_at DESC, transaction_id DESC",
    )
    suspend fun getOffsettableExpenses(startAt: String, endAt: String): List<TransactionEntity>

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

    /**
     * 抵消专用更新：只改金额、描述和随金额变化的 dedupe_key，不动分类 / 时间 / 收支类型。
     * 不复用 [update]，因为那个方法会把 category_id 一并覆盖（其语义是“传 null 即清空分类”）。
     */
    @Query(
        "UPDATE transactions SET amount = :amount, description = :description, " +
            "dedupe_key = :dedupeKey WHERE transaction_id = :id",
    )
    suspend fun updateAmountAndDescription(
        id: Int,
        amount: String,
        description: String?,
        dedupeKey: String,
    )

    /** 删除分类时解除关联：将对应交易的 category_id 置空。返回影响行数。 */
    @Query("UPDATE transactions SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategory(categoryId: Int): Int

    /** 删除一级分类时解除关联：该一级分类及其二级分类下的交易全部置空。 */
    @Query(
        "UPDATE transactions SET category_id = NULL WHERE category_id IN " +
            "(SELECT category_id FROM categories WHERE category_id = :categoryId OR parent_id = :categoryId)",
    )
    suspend fun clearCategoryWithChildren(categoryId: Int): Int

    @Query("DELETE FROM transactions WHERE transaction_id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

/** 同名历史交易的分类，见 [TransactionDao.getCategorizedByDescriptions]。 */
data class DescriptionCategory(
    val description: String,
    val transactionType: Int,
    val categoryId: Int,
)
