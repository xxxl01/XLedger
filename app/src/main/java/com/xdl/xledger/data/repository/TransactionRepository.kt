package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.util.TimeFormat

/**
 * 交易仓储，对应旧版 service/transactions.ts。
 *
 * 去重规则与旧版一致：dedupe_key = `occurred_at_amount_transaction_type`。
 */
class TransactionRepository(private val dao: TransactionDao) {

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
        val isDuplicate: Boolean = false,
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
     * - [allowDuplicate] = false 时按 dedupe_key 去重（冲突则返回 null）
     * - [allowDuplicate] = true 时在 key 后追加 `_manual_<后缀>` 强制写入
     */
    suspend fun createTransaction(
        input: CreateTransactionInput,
        allowDuplicate: Boolean = false,
        duplicateKeySuffix: String? = null,
    ): TransactionEntity? {
        val baseKey = createDedupeKey(input)
        val dedupeKey = if (allowDuplicate) {
            "${baseKey}_manual_${duplicateKeySuffix ?: System.currentTimeMillis()}"
        } else {
            baseKey
        }
        val entity = TransactionEntity(
            transactionType = input.transactionType,
            amount = input.amount,
            occurredAt = input.occurredAt,
            description = input.description,
            categoryId = input.categoryId,
            dedupeKey = dedupeKey,
        )
        val id = if (allowDuplicate) dao.insert(entity) else dao.insertIgnore(entity)
        return if (id != -1L) dao.getById(id.toInt()) else null
    }

    /** 批量写入导入候选：优先按 isDuplicate 标记处理，冲突时强制允许重复再试一次。 */
    suspend fun createSelectedTransactionsAllowingDuplicates(
        items: List<ImportCandidate>,
    ): List<TransactionEntity> {
        val created = mutableListOf<TransactionEntity>()
        for ((index, item) in items.withIndex()) {
            val suffix = "${System.currentTimeMillis()}_$index"
            val input = CreateTransactionInput(
                transactionType = item.transactionType,
                amount = item.amount,
                occurredAt = item.occurredAt,
                description = item.description,
                categoryId = item.categoryId,
            )
            var transaction = createTransaction(
                input,
                allowDuplicate = item.isDuplicate,
                duplicateKeySuffix = suffix,
            )
            if (transaction == null) {
                transaction = createTransaction(input, allowDuplicate = true, duplicateKeySuffix = suffix)
            }
            if (transaction != null) {
                created += transaction
            }
        }
        return created
    }

    /** 查询输入列表中已存在于库中的 dedupe_key（导入重复检测）。 */
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

    /** 编辑交易：合并字段后按新值重新生成 dedupe_key。 */
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
}
