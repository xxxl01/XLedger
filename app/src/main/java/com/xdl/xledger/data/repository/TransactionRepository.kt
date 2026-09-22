package com.xdl.xledger.data.repository

import androidx.room.withTransaction
import com.xdl.xledger.data.db.AppDatabase
import com.xdl.xledger.data.db.dao.OffsetDao
import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.OffsetEntity
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.util.MoneyFormat
import com.xdl.xledger.util.TimeFormat
import java.time.Instant

/**
 * 交易仓储，对应旧版 service/transactions.ts。
 *
 * dedupe_key = `occurred_at_amount_transaction_type`，仅用于导入时的“可能重复”提示，
 * 不作为数据库唯一约束：是否保留重复记录由用户自行决定。
 */
class TransactionRepository(
    private val dao: TransactionDao,
    private val offsetDao: OffsetDao,
    private val database: AppDatabase,
) {

    /** 与旧版 CreateTransactionInput 对应的新建输入。 */
    data class CreateTransactionInput(
        val transactionType: Int,
        val amount: String,
        val occurredAt: String,
        val description: String?,
        val categoryId: Int?,
    )

    /** 带重复标记的导入候选。 */
    data class ImportCandidate(
        val transactionType: Int,
        val amount: String,
        val occurredAt: String,
        val description: String?,
        val categoryId: Int? = null,
    )

    /** 编辑输入：字段为 null 表示不修改；[categoryId] 传 null 表示显式清空分类。 */
    data class TransactionPatch(
        val transactionType: Int? = null,
        val amount: String? = null,
        val occurredAt: String? = null,
        val description: String? = null,
        val categoryId: Int? = null,
    )

    fun createDedupeKey(occurredAt: String, amount: String, transactionType: Int): String =
        "${occurredAt}_${amount}_${transactionType}"

    private fun createDedupeKey(input: CreateTransactionInput): String =
        createDedupeKey(input.occurredAt, input.amount, input.transactionType)

    /**
     * 创建交易。
     *
     * dedupe_key 无唯一约束，重复记录直接写入，是否保留由用户决定。
     */
    suspend fun createTransaction(input: CreateTransactionInput): TransactionEntity {
        val entity = TransactionEntity(
            transactionType = input.transactionType,
            amount = input.amount,
            occurredAt = input.occurredAt,
            description = input.description,
            categoryId = input.categoryId,
            dedupeKey = createDedupeKey(input),
        )
        return dao.getById(dao.insert(entity).toInt())
            ?: error("交易写入后未找到，transactionId 未知")
    }

    /** 批量写入导入候选，逐条直接入库（重复与否由用户勾选时自行决定）。 */
    suspend fun createSelectedTransactions(
        items: List<ImportCandidate>,
    ): List<TransactionEntity> {
        val created = mutableListOf<TransactionEntity>()
        for (item in items) {
            created += createTransaction(
                CreateTransactionInput(
                    transactionType = item.transactionType,
                    amount = item.amount,
                    occurredAt = item.occurredAt,
                    description = item.description,
                    categoryId = item.categoryId,
                ),
            )
        }
        return created
    }

    /** 查询输入列表中已存在于库中的 dedupe_key（导入“可能重复”提示）。 */
    suspend fun getExistingDedupeKeys(inputs: List<CreateTransactionInput>): Set<String> {
        val keys = inputs.map(::createDedupeKey).distinct()
        if (keys.isEmpty()) return emptySet()
        return dao.getDedupeKeys(keys).toSet()
    }

    suspend fun getById(transactionId: Int): TransactionEntity? = dao.getById(transactionId)

    suspend fun getAll(): List<TransactionEntity> = dao.getAll()

    suspend fun getByDateRange(startAt: String, endAt: String): List<TransactionEntity> =
        dao.getByDateRange(startAt, endAt)

    /** 本地时区月份范围查询。 */
    suspend fun getByMonth(year: Int, month: Int): List<TransactionEntity> =
        dao.getByDateRange(
            TimeFormat.monthStartIsoString(year, month),
            TimeFormat.monthEndIsoString(year, month),
        )

    /** 编辑交易：合并字段后按新值重新生成 dedupe_key（无唯一约束，不会因重复冲突）。 */
    suspend fun updateTransaction(
        transactionId: Int,
        patch: TransactionPatch,
    ): TransactionEntity? {
        val existing = dao.getById(transactionId) ?: return null
        val nextType = patch.transactionType ?: existing.transactionType
        val nextAmount = patch.amount ?: existing.amount
        val nextOccurredAt = patch.occurredAt ?: existing.occurredAt
        val nextDescription = patch.description ?: existing.description
        val nextCategoryId = patch.categoryId
        val nextDedupeKey = createDedupeKey(nextOccurredAt, nextAmount, nextType)

        dao.update(
            id = transactionId,
            transactionType = nextType,
            amount = nextAmount,
            occurredAt = nextOccurredAt,
            description = nextDescription,
            categoryId = nextCategoryId,
            dedupeKey = nextDedupeKey,
        )
        return dao.getById(transactionId)
    }

    /** 删除分类时解除关联。 */
    suspend fun clearCategory(categoryId: Int): Int = dao.clearCategory(categoryId)

    suspend fun deleteTransaction(transactionId: Int): Boolean =
        dao.deleteById(transactionId) > 0

    // ---- 抵消 ----

    /** 一次抵消的结果，用于给用户回显提示。 */
    data class OffsetResult(
        val offsetAmount: String,
        val incomeRemaining: String,
        val expenseRemaining: String,
    )

    /** 抵消候选：指定月份内仍有剩余金额的支出。抵消弹窗按月翻页，不一次性拉全表。 */
    suspend fun getOffsettableExpensesByMonth(year: Int, month: Int): List<TransactionEntity> =
        dao.getOffsettableExpenses(
            TimeFormat.monthStartIsoString(year, month),
            TimeFormat.monthEndIsoString(year, month),
        )

    /**
     * 用一笔收入抵消一笔支出。
     *
     * 抵消金额 = min(收入剩余, 支出剩余)，两边各扣减这个金额；完全抵消的记录金额留为 "0.00"，
     * 记录本身不删除，所以 amount 始终等于「当前剩余金额」，各处统计口径无需改动。
     * 同时往 offsets 表写一行台账，历史抵消明细由该表还原。
     */
    suspend fun offsetIncomeWithExpense(incomeId: Int, expenseId: Int): OffsetResult =
        database.withTransaction {
            val income = dao.getById(incomeId) ?: error("收入记录不存在")
            val expense = dao.getById(expenseId) ?: error("支出记录不存在")
            require(income.transactionType == TransactionEntity.TYPE_INCOME) {
                "只能用收入记录去抵消支出"
            }
            require(expense.transactionType == TransactionEntity.TYPE_EXPENSE) {
                "只能抵消支出记录"
            }

            val incomeCents = MoneyFormat.toCents(income.amount)
            val expenseCents = MoneyFormat.toCents(expense.amount)
            require(incomeCents > 0) { "这笔收入已经没有可用金额了" }
            require(expenseCents > 0) { "这笔支出已经被抵消完了" }

            val offsetCents = minOf(incomeCents, expenseCents)
            val incomeRemaining = MoneyFormat.fromCents(incomeCents - offsetCents)
            val expenseRemaining = MoneyFormat.fromCents(expenseCents - offsetCents)

            applyOffset(income, incomeRemaining, "原收入")
            applyOffset(expense, expenseRemaining, "原支出")
            offsetDao.insert(
                OffsetEntity(
                    incomeId = incomeId,
                    expenseId = expenseId,
                    amount = MoneyFormat.fromCents(offsetCents),
                    createdAt = TimeFormat.toIsoString(Instant.now()),
                ),
            )

            OffsetResult(
                offsetAmount = MoneyFormat.fromCents(offsetCents),
                incomeRemaining = incomeRemaining,
                expenseRemaining = expenseRemaining,
            )
        }

    /**
     * 把一侧记录的金额改为抵消后的剩余金额。
     *
     * 首次被抵消时在描述后追加「原支出 ¥60.00」留痕；后续再被抵消不重复追加，
     * 否则会出现「（原支出 ¥60.00）（原支出 ¥30.00）」这种自相矛盾的描述。
     */
    private suspend fun applyOffset(
        transaction: TransactionEntity,
        remaining: String,
        markLabel: String,
    ) {
        val description = if (offsetDao.countByTransactionId(transaction.transactionId) == 0) {
            val mark = "$markLabel ${MoneyFormat.formatPlain(transaction.amount)}"
            if (transaction.description.isNullOrBlank()) mark else "${transaction.description}（$mark）"
        } else {
            transaction.description
        }
        dao.updateAmountAndDescription(
            id = transaction.transactionId,
            amount = remaining,
            description = description,
            dedupeKey = createDedupeKey(
                transaction.occurredAt,
                remaining,
                transaction.transactionType,
            ),
        )
    }
}
