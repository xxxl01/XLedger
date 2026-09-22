package com.xdl.xledger.data.repository

import com.xdl.xledger.data.db.dao.CategoryDao
import com.xdl.xledger.data.db.dao.TransactionDao
import com.xdl.xledger.data.db.entity.CategoryEntity

/** 分类仓储，对应旧版 service/categories.ts。 */
class CategoryRepository(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao,
) {

    /**
     * 新增分类。
     *
     * @param parentId 为 null 时创建一级分类；非 null 时创建该一级下的二级分类。
     */
    suspend fun create(
        categoryName: String,
        transactionType: Int,
        parentId: Int? = null,
    ): CategoryEntity {
        val sortOrder = dao.maxSortOrder(transactionType, parentId) + 1
        val id = dao.insert(
            CategoryEntity(
                categoryName = categoryName,
                transactionType = transactionType,
                parentId = parentId,
                sortOrder = sortOrder,
            ),
        )
        return dao.getById(id.toInt())!!
    }

    suspend fun getById(categoryId: Int): CategoryEntity? = dao.getById(categoryId)

    suspend fun getAll(): List<CategoryEntity> = dao.getAll()

    suspend fun getByType(transactionType: Int): List<CategoryEntity> = dao.getByType(transactionType)

    suspend fun rename(categoryId: Int, categoryName: String): Boolean {
        dao.updateName(categoryId, categoryName)
        return dao.getById(categoryId) != null
    }

    /**
     * 删除分类并解除相关交易关联：
     * - 一级分类：连同其下全部二级分类一起删除，相关交易全部置为未分类
     * - 二级分类：仅删除自身，相关交易置为未分类
     */
    suspend fun deleteCategoryAndUnassignTransactions(categoryId: Int): Boolean {
        val category = dao.getById(categoryId) ?: return false
        return if (category.parentId == null) {
            transactionDao.clearCategoryWithChildren(categoryId)
            dao.deleteWithChildren(categoryId) > 0
        } else {
            transactionDao.clearCategory(categoryId)
            dao.deleteById(categoryId) > 0
        }
    }
}
