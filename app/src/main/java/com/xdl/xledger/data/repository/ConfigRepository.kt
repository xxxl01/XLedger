package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.ConfigDao
import com.xdl.xledger.data.db.entity.ConfigEntity

/** 配置仓储，对应旧版 service/configs.ts。 */
class ConfigRepository(private val dao: ConfigDao) {

    suspend fun getValue(key: String): String? = dao.getByKey(key)?.configValue

    suspend fun getByKey(key: String): ConfigEntity? = dao.getByKey(key)

    suspend fun getAll(): List<ConfigEntity> = dao.getAll()

    /** 设置配置（key 已存在则覆盖）。 */
    suspend fun setConfig(key: String, value: String): ConfigEntity? {
        dao.upsert(ConfigEntity(configKey = key, configValue = value))
        return dao.getByKey(key)
    }

    suspend fun delete(key: String): Boolean = dao.deleteByKey(key) > 0
}
