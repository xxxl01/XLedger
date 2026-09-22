package com.xdl.xledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xdl.xledger.data.db.dao.AssetDao
import com.xdl.xledger.data.db.dao.CategoryDao
import com.xdl.xledger.data.db.dao.ConfigDao
import com.xdl.xledger.data.db.dao.OffsetDao
import com.xdl.xledger.data.db.dao.SavingDao
import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.AssetEntity
import com.xdl.xledger.data.db.entity.CategoryEntity
import com.xdl.xledger.data.db.entity.ConfigEntity
import com.xdl.xledger.data.db.entity.OffsetEntity
import com.xdl.xledger.data.db.entity.SavingEntity
import com.xdl.xledger.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AssetEntity::class,
        CategoryEntity::class,
        ConfigEntity::class,
        SavingEntity::class,
        OffsetEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun assetDao(): AssetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun configDao(): ConfigDao
    abstract fun savingDao(): SavingDao
    abstract fun offsetDao(): OffsetDao

    companion object {
        private const val DATABASE_NAME = "x-ledger.db"

        /**
         * v1 → v2：分类支持两级 + 收支分离。
         * 原 categories 表（category_id / category_name / category_icon）重建为：
         * category_id / category_name / transaction_type / parent_id / sort_order。
         * 旧分类数据（如有）统一归入支出、作为一级分类、按原 id 排序。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories_new (" +
                        "category_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "category_name TEXT NOT NULL, " +
                        "transaction_type INTEGER NOT NULL, " +
                        "parent_id INTEGER, " +
                        "sort_order INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO categories_new " +
                        "(category_id, category_name, transaction_type, parent_id, sort_order) " +
                        "SELECT category_id, category_name, 0, NULL, category_id FROM categories",
                )
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                // 与 CategoryEntity 上的 parent_id 索引保持一致，否则 Room schema 校验失败
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_categories_parent_id ON categories(parent_id)",
                )
            }
        }

        /**
         * v2 → v3：去掉 dedupe_key 唯一索引。
         * 重复不再作为数据库硬规则，只保留导入时的“可能重复”提示，是否保留由用户决定。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_transactions_dedupe_key")
            }
        }

        /**
         * v3 → v4：新增 savings 表（省钱记录）。
         * 省下的金额由 original_amount - actual_amount 计算，不落库。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings (" +
                        "saving_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "item_name TEXT NOT NULL, " +
                        "original_amount TEXT NOT NULL, " +
                        "actual_amount TEXT NOT NULL, " +
                        "occurred_at TEXT NOT NULL, " +
                        "note TEXT)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_savings_occurred_at ON savings(occurred_at)",
                )
            }
        }

        /**
         * v4 → v5：新增 offsets 表（抵消记录）。
         * 抵消只扣减 transactions.amount，不删除记录，transactions 表结构保持不变。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS offsets (" +
                        "offset_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "income_id INTEGER NOT NULL, " +
                        "expense_id INTEGER NOT NULL, " +
                        "amount TEXT NOT NULL, " +
                        "created_at TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offsets_income_id ON offsets(income_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offsets_expense_id ON offsets(expense_id)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null


        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
