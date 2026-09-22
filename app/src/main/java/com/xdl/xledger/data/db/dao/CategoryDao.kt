package com.xdl.xledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xdl.xledger.data.db.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE category_id = :id LIMIT 1")
    suspend fun getById(id: Int): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY transaction_type ASC, sort_order ASC, category_id ASC")
    suspend fun getAll(): List<CategoryEntity>

    /** 指定收支类型的全部分类（一级和二级混在一起，由上层组织成树）。 */
    @Query(
        "SELECT * FROM categories WHERE transaction_type = :type " +
            "ORDER BY parent_id ASC, sort_order ASC, category_id ASC",
    )
    suspend fun getByType(type: Int): List<CategoryEntity>

    /** 同级最大 sort_order，用于新增分类时排序。 */
    @Query(
        "SELECT COALESCE(MAX(sort_order), 0) FROM categories WHERE transaction_type = :type " +
            "AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)",
    )
    suspend fun maxSortOrder(type: Int, parentId: Int?): Int

    @Query("UPDATE categories SET category_name = :categoryName WHERE category_id = :id")
    suspend fun updateName(id: Int, categoryName: String)

    @Query("DELETE FROM categories WHERE category_id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /** 删除一级分类时连同其下二级分类一起删除。 */
    @Query("DELETE FROM categories WHERE category_id = :id OR parent_id = :id")
    suspend fun deleteWithChildren(id: Int): Int
}
