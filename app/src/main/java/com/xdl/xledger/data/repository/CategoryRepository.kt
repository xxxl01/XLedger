package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.CategoryDao
import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.CategoryEntity

/** 分类仓储，对应旧版 service/categories.ts。 */
class CategoryRepository(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao,
) {

    suspend fun create(categoryName: String, categoryIcon: String = "•"): CategoryEntity {
        val id = dao.insert(CategoryEntity(categoryName = categoryName, categoryIcon = categoryIcon))
        return dao.getById(id.toInt())!!
    }

    suspend fun getById(categoryId: Int): CategoryEntity? = dao.getById(categoryId)

    suspend fun getAll(): List<CategoryEntity> = dao.getAll()

    suspend fun update(
        categoryId: Int,
        categoryName: String,
        categoryIcon: String,
    ): CategoryEntity? {
        dao.update(categoryId, categoryName, categoryIcon)
        return dao.getById(categoryId)
    }

    suspend fun delete(categoryId: Int): Boolean = dao.deleteById(categoryId) > 0

    /** 删除分类并解除相关交易的关联（对应旧版 deleteCategoryAndUnassignTransactions）。 */
    suspend fun deleteCategoryAndUnassignTransactions(categoryId: Int): Boolean {
        transactionDao.clearCategory(categoryId)
        return dao.deleteById(categoryId) > 0
    }
}
