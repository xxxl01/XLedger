package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.OffsetEntity

/**
 * 抵消记录 + 关联收支记录的展示明细。
 *
 * 用 LEFT JOIN 关联，收支记录被用户手动删除时对应字段为 null，展示为「记录已删除」。
 * 注意 [incomeAmount] / [expenseAmount] 是两边**当前剩余**金额，不是抵消当时的金额。
 */
data class OffsetDetail(
    val offsetId: Int,
    val amount: String,
    val createdAt: String,
    val incomeDescription: String?,
    val incomeAmount: String?,
    val incomeOccurredAt: String?,
    val expenseDescription: String?,
    val expenseAmount: String?,
    val expenseOccurredAt: String?,
)

@Dao
interface OffsetDao {

    @Insert
    suspend fun insert(offset: OffsetEntity): Long

    @Insert
    suspend fun insertAll(offsets: List<OffsetEntity>)

    /**
     * 这条收支记录历史上被抵消过的次数。
     * 用于判断是否首次抵消：只有首次才在描述里追加「原支出 ¥60.00」，避免重复追加。
     */
    @Query("SELECT COUNT(*) FROM offsets WHERE income_id = :transactionId OR expense_id = :transactionId")
    suspend fun countByTransactionId(transactionId: Int): Int

    /** 抵消记录页明细，按抵消时间倒序。 */
    @Query(
        "SELECT o.offset_id AS offsetId, o.amount AS amount, o.created_at AS createdAt, " +
            "i.description AS incomeDescription, i.amount AS incomeAmount, " +
            "i.occurred_at AS incomeOccurredAt, " +
            "e.description AS expenseDescription, e.amount AS expenseAmount, " +
            "e.occurred_at AS expenseOccurredAt " +
            "FROM offsets o " +
            "LEFT JOIN transactions i ON i.transaction_id = o.income_id " +
            "LEFT JOIN transactions e ON e.transaction_id = o.expense_id " +
            "ORDER BY o.created_at DESC, o.offset_id DESC",
    )
    suspend fun getAllDetails(): List<OffsetDetail>

    /** 备份导出用。 */
    @Query("SELECT * FROM offsets ORDER BY offset_id")
    suspend fun getAll(): List<OffsetEntity>

    @Query("DELETE FROM offsets")
    suspend fun deleteAll()
}
