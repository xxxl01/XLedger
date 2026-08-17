package com.xdl.xledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xdl.xledger.data.db.dao.AssetDao
import com.xdl.xledger.data.db.dao.CategoryDao
import com.xdl.xledger.data.db.dao.ConfigDao
import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.AssetEntity
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.ConfigEntity
import com.xdl.xledger.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AssetEntity::class,
        CategoryEntity::class,
        ConfigEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun assetDao(): AssetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun configDao(): ConfigDao

    companion object {
        private const val DATABASE_NAME = "x-ledger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
