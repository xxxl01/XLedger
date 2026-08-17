package com.xdl.xledger

import android.app.Application
import com.xdl.xledger.data.db.AppDatabase
import com.xdl.xledger.data.remote.LlmApiService
import com.xdl.xledger.data.repository.AssetRepository
import com.xdl.xledger.data.repository.CategoryRepository
import com.xdl.xledger.data.repository.ConfigRepository
import com.xdl.xledger.data.repository.TransactionRepository

/**
 * 应用入口，持有全局依赖（轻量手动 DI，项目规模暂不需要 Hilt）。
 */
class XLedgerApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    val assetRepository: AssetRepository by lazy {
        AssetRepository(database.assetDao())
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao(), database.transactionDao())
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(database.configDao())
    }

    val llmApiService: LlmApiService by lazy {
        LlmApiService(configRepository)
    }
}
