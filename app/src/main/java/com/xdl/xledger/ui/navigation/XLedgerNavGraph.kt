package com.xdl.xledger.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xdl.xledger.ui.assets.AssetsScreen
import com.xdl.xledger.ui.categories.CategoriesScreen
import com.xdl.xledger.ui.edit.TransactionEditScreen
import com.xdl.xledger.ui.home.HomeScreen
import com.xdl.xledger.ui.import.UploadScreen
import com.xdl.xledger.ui.offsets.OffsetsScreen
import com.xdl.xledger.ui.savings.SavingsScreen
import com.xdl.xledger.ui.settings.LlmSettingsScreen
import com.xdl.xledger.ui.settings.SettingsScreen
import com.xdl.xledger.ui.stats.CategoryTransactionsScreen
import com.xdl.xledger.ui.stats.MonthlyCategoryStatsScreen
import com.xdl.xledger.ui.stats.TopExpensesScreen
import com.xdl.xledger.ui.theme.xledgerColors

/**
 * 根导航图：
 *
 * - `main` 嵌套图承载底部 Tab（账本 / 省钱 / 资产 / 设置），Tab 之间独立保存状态；
 * - 上传、分类、LLM 配置、分类统计为全屏子页，覆盖 Tab 栏。
 */
@Composable
fun XLedgerNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Route.MAIN) {
            MainTabScaffold(
                onOpenUpload = { navController.navigate(Route.UPLOAD) },
                onOpenCategories = { navController.navigate(Route.CATEGORIES) },
                onOpenLlmSettings = { navController.navigate(Route.LLM_SETTINGS) },
                onOpenOffsets = { navController.navigate(Route.OFFSETS) },
                onOpenTransactionEdit = { transactionId ->
                    navController.navigate(Route.editTransaction(transactionId))
                },
                onOpenMonthlyCategoryStats = { year, month ->
                    navController.navigate(Route.monthlyCategoryStats(year, month))
                },
                onOpenTopExpenses = { year, month ->
                    navController.navigate(Route.topExpenses(year, month))
                },
            )
        }

        composable(Route.UPLOAD) {
            UploadScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.CATEGORIES) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.LLM_SETTINGS) {
            LlmSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.OFFSETS) {
            OffsetsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Route.EDIT_TRANSACTION,
            arguments = listOf(
                navArgument("transactionId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getInt("transactionId") ?: 0
            TransactionEditScreen(
                transactionId = transactionId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Route.MONTHLY_CATEGORY_STATS,
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val year = backStackEntry.arguments?.getInt("year") ?: 0
            val month = backStackEntry.arguments?.getInt("month") ?: 0
            MonthlyCategoryStatsScreen(
                year = year,
                month = month,
                onBack = { navController.popBackStack() },
                onOpenCategoryTransactions = { transactionType, categoryId, includeChildren ->
                    navController.navigate(
                        Route.categoryTransactions(year, month, transactionType, categoryId, includeChildren),
                    )
                },
            )
        }

        composable(
            route = Route.TOP_EXPENSES,
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            TopExpensesScreen(
                year = backStackEntry.arguments?.getInt("year") ?: 0,
                month = backStackEntry.arguments?.getInt("month") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenEdit = { transactionId ->
                    navController.navigate(Route.editTransaction(transactionId))
                },
            )
        }

        composable(
            route = Route.CATEGORY_TRANSACTIONS,
            arguments = listOf(
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
                navArgument("type") { type = NavType.IntType },
                navArgument("categoryId") { type = NavType.IntType },
                navArgument("includeChildren") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            CategoryTransactionsScreen(
                year = args?.getInt("year") ?: 0,
                month = args?.getInt("month") ?: 0,
                transactionType = args?.getInt("type") ?: 0,
                categoryId = args?.getInt("categoryId")?.takeIf { it >= 0 },
                includeChildren = args?.getBoolean("includeChildren") ?: false,
                onBack = { navController.popBackStack() },
                onOpenEdit = { transactionId ->
                    navController.navigate(Route.editTransaction(transactionId))
                },
            )
        }
    }
}

/**
 * Tab 容器：底部导航栏 + 内部 NavHost。
 * Tab 间通过 popUpTo(findStartDestination) + saveState/restoreState 保留各自的页面栈。
 */
@Composable
private fun MainTabScaffold(
    onOpenUpload: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenOffsets: () -> Unit,
    onOpenTransactionEdit: (Int) -> Unit,
    onOpenMonthlyCategoryStats: (year: Int, month: Int) -> Unit,
    onOpenTopExpenses: (year: Int, month: Int) -> Unit,
) {
    val tabNavController = rememberNavController()
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.xledgerColors.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.xledgerColors.card) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = Route.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    onOpenUpload = onOpenUpload,
                    onOpenCategories = onOpenCategories,
                    onOpenTransactionEdit = onOpenTransactionEdit,
                    onOpenMonthlyCategoryStats = onOpenMonthlyCategoryStats,
                    onOpenTopExpenses = onOpenTopExpenses,
                )
            }
            composable(Route.SAVINGS) {
                SavingsScreen()
            }
            composable(Route.ASSETS) {
                AssetsScreen()
            }
            composable(Route.SETTINGS) {
                SettingsScreen(
                    onOpenLlmSettings = onOpenLlmSettings,
                    onOpenOffsets = onOpenOffsets,
                )
            }
        }
    }
}
