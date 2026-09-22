package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.SavingDao
import com.xdl.xledger.data.db.entity.SavingEntity
import com.xdl.xledger.util.TimeFormat

/** 省钱记录仓储。 */
class SavingRepository(private val dao: SavingDao) {

    suspend fun create(
        itemName: String,
        originalAmount: String,
        actualAmount: String,
        occurredAt: String,
        note: String?,
    ): SavingEntity {
        val id = dao.insert(
            SavingEntity(
                itemName = itemName,
                originalAmount = originalAmount,
                actualAmount = actualAmount,
                occurredAt = occurredAt,
                note = note,
            ),
        )
        return dao.getById(id.toInt()) ?: error("省钱记录写入后未找到，savingId 未知")
    }

    /** 本地时区月份范围查询。 */
    suspend fun getByMonth(year: Int, month: Int): List<SavingEntity> =
        dao.getByDateRange(
            TimeFormat.monthStartIsoString(year, month),
            TimeFormat.monthEndIsoString(year, month),
        )

    suspend fun getAll(): List<SavingEntity> = dao.getAll()

    /** 累计省下金额（全部记录）。 */
    suspend fun getTotalSaved(): Double = dao.sumSaved()

    suspend fun update(
        savingId: Int,
        itemName: String,
        originalAmount: String,
        actualAmount: String,
        occurredAt: String,
        note: String?,
    ): SavingEntity? {
        dao.update(savingId, itemName, originalAmount, actualAmount, occurredAt, note)
        return dao.getById(savingId)
    }

    suspend fun delete(savingId: Int): Boolean = dao.deleteById(savingId) > 0
}
