package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE category_id = :id LIMIT 1")
    suspend fun getById(id: Int): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY category_id DESC")
    suspend fun getAll(): List<CategoryEntity>

    @Query(
        "UPDATE categories SET category_name = :categoryName, category_icon = :categoryIcon " +
            "WHERE category_id = :id",
    )
    suspend fun update(id: Int, categoryName: String, categoryIcon: String)

    @Query("DELETE FROM categories WHERE category_id = :id")
    suspend fun deleteById(id: Int): Int
}
