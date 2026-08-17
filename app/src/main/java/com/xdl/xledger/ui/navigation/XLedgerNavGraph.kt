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
import com.xdl.xledger.ui.home.HomeScreen
import com.xdl.xledger.ui.import.UploadScreen
import com.xdl.xledger.ui.settings.LlmSettingsScreen
import com.xdl.xledger.ui.settings.SettingsScreen
import com.xdl.xledger.ui.stats.MonthlyCategoryStatsScreen
import com.xdl.xledger.ui.theme.xledgerColors

/**
 * 根导航图：
 *
 * - `main` 嵌套图承载底部 Tab（账本 / 资产 / 设置），Tab 之间独立保存状态；
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
                onOpenMonthlyCategoryStats = { year, month ->
                    navController.navigate(Route.monthlyCategoryStats(year, month))
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
    onOpenMonthlyCategoryStats: (year: Int, month: Int) -> Unit,
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
                    onOpenMonthlyCategoryStats = onOpenMonthlyCategoryStats,
                )
            }
            composable(Route.ASSETS) {
                AssetsScreen()
            }
            composable(Route.SETTINGS) {
                SettingsScreen(onOpenLlmSettings = onOpenLlmSettings)
            }
        }
    }
}
