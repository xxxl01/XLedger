package com.xdl.xledger.ui.navigation

import com.composables.icons.lucide.R as LucideR

/**
 * 应用路由定义。
 *
 * 底部 Tab 由 [MainTab] 枚举承载（图标 + 标签 + 路由一一对应）；
 * 其余为覆盖 Tab 栏的全屏子页面。
 */
object Route {

    /** Tab 嵌套图容器。 */
    const val MAIN = "main"

    /** 账本主页（底部 Tab）。 */
    const val HOME = "home"

    /** 资产页（底部 Tab）。 */
    const val ASSETS = "assets"

    /** 省钱页（底部 Tab）。 */
    const val SAVINGS = "savings"

    /** 设置页（底部 Tab）。 */
    const val SETTINGS = "settings"

    /** 截图导入页（全屏子页）。 */
    const val UPLOAD = "upload"

    /** 分类管理页（全屏子页）。 */
    const val CATEGORIES = "categories"

    /** LLM 配置页（全屏子页）。 */
    const val LLM_SETTINGS = "llm_settings"

    /** 抵消记录页（全屏子页）。 */
    const val OFFSETS = "offsets"

    /** 交易编辑页（全屏子页，带交易 id 参数）。 */
    const val EDIT_TRANSACTION = "edit_transaction?transactionId={transactionId}"

    fun editTransaction(transactionId: Int): String =
        "edit_transaction?transactionId=$transactionId"

    /** 分类统计页（全屏子页，带年月参数）。 */
    const val MONTHLY_CATEGORY_STATS = "monthly_category_stats?year={year}&month={month}"

    fun monthlyCategoryStats(year: Int, month: Int): String =
        "monthly_category_stats?year=$year&month=$month"

    /** 大额支出页（全屏子页，带年月参数）：某月单笔金额最大的若干笔支出。 */
    const val TOP_EXPENSES = "top_expenses?year={year}&month={month}"

    fun topExpenses(year: Int, month: Int): String = "top_expenses?year=$year&month=$month"

    /**
     * 分类明细页（全屏子页）：某月某分类的每条记录。
     * categoryId = -1 表示未分类；includeChildren 为 true 时包含该一级分类下所有二级分类。
     */
    const val CATEGORY_TRANSACTIONS =
        "category_transactions?year={year}&month={month}&type={type}&categoryId={categoryId}&includeChildren={includeChildren}"

    fun categoryTransactions(
        year: Int,
        month: Int,
        transactionType: Int,
        categoryId: Int?,
        includeChildren: Boolean,
    ): String =
        "category_transactions?year=$year&month=$month&type=$transactionType" +
            "&categoryId=${categoryId ?: -1}&includeChildren=$includeChildren"
}

/** 底部 Tab 定义。 */
enum class MainTab(
    val route: String,
    val label: String,
    val iconRes: Int,
) {
    Home(route = Route.HOME, label = "账本", iconRes = LucideR.drawable.lucide_ic_house),
    Savings(route = Route.SAVINGS, label = "省钱", iconRes = LucideR.drawable.lucide_ic_piggy_bank),
    Assets(route = Route.ASSETS, label = "资产", iconRes = LucideR.drawable.lucide_ic_wallet),
    Settings(route = Route.SETTINGS, label = "设置", iconRes = LucideR.drawable.lucide_ic_settings),
}
