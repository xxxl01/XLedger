package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.StorageDao
import com.xdl.xledger.data.db.entity.StorageEntity
import com.xdl.xledger.util.MoneyFormat
import com.xdl.xledger.util.TimeFormat
import java.time.Instant

/** 储藏记录仓储。 */
class StorageRepository(private val dao: StorageDao) {

    suspend fun getAll(): List<StorageEntity> = dao.getAll()

    suspend fun create(
        itemName: String,
        totalAmount: String,
        deductionAmount: String,
        note: String?,
    ): StorageEntity {
        val entity = StorageEntity(
            itemName = itemName,
            totalAmount = totalAmount,
            remainingAmount = totalAmount,
            deductionAmount = deductionAmount,
            createdAt = TimeFormat.toIsoString(Instant.now()),
            lastDeductedAt = null,
            note = note,
        )
        val id = dao.insert(entity).toInt()
        return dao.getById(id) ?: error("储藏记录写入后未找到")
    }

    suspend fun update(
        storageId: Int,
        itemName: String,
        totalAmount: String,
        remainingAmount: String,
        deductionAmount: String,
        note: String?,
    ): StorageEntity? {
        dao.update(storageId, itemName, totalAmount, remainingAmount, deductionAmount, note)
        return dao.getById(storageId)
    }

    /** 按分做整数运算，避免扣减后出现浮点尾数。 */
    suspend fun deduct(storageId: Int, amount: String): StorageEntity? {
        val current = dao.getById(storageId) ?: return null
        val currentCents = MoneyFormat.toCents(current.remainingAmount)
        val deductionCents = MoneyFormat.toCents(amount)
        require(deductionCents > 0) { "扣除金额必须大于 0" }
        require(deductionCents <= currentCents) { "扣除金额不能超过剩余金额" }
        val updated = dao.deduct(
            id = storageId,
            deductionAmount = MoneyFormat.fromCents(deductionCents),
            remainingAmount = MoneyFormat.fromCents(currentCents - deductionCents),
            lastDeductedAt = TimeFormat.toIsoString(Instant.now()),
        )
        if (updated == 0) error("储藏余额已发生变化，请刷新后重试")
        return dao.getById(storageId)
    }

    suspend fun delete(storageId: Int): Boolean = dao.deleteById(storageId) > 0
}
