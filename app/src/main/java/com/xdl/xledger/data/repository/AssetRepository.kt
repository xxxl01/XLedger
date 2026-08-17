package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.AssetDao
import com.xdl.xledger.data.db.entity.AssetEntity

/** 资产仓储，对应旧版 service/assets.ts。 */
class AssetRepository(private val dao: AssetDao) {

    suspend fun create(accountName: String, amount: String): AssetEntity {
        val id = dao.insert(AssetEntity(accountName = accountName, amount = amount))
        return dao.getById(id.toInt())!!
    }

    suspend fun getById(assetId: Int): AssetEntity? = dao.getById(assetId)

    suspend fun getAll(): List<AssetEntity> = dao.getAll()

    suspend fun update(assetId: Int, accountName: String, amount: String): AssetEntity? {
        dao.update(assetId, accountName, amount)
        return dao.getById(assetId)
    }

    suspend fun updateAmount(assetId: Int, amount: String): AssetEntity? {
        val accountName = dao.getById(assetId)?.accountName ?: return null
        return update(assetId, accountName, amount)
    }

    suspend fun delete(assetId: Int): Boolean = dao.deleteById(assetId) > 0
}
