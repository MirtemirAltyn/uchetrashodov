package com.moifinansy.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moifinansy.R
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.ui.accounts.AccountDetailScreen
import com.moifinansy.ui.accounts.AccountEditScreen
import com.moifinansy.ui.accounts.AccountsScreen
import com.moifinansy.ui.categories.CategoriesScreen
import com.moifinansy.ui.categories.CategoryEditScreen
import com.moifinansy.ui.debts.DebtsScreen
import com.moifinansy.ui.home.HomeScreen
import com.moifinansy.ui.more.MoreScreen
import com.moifinansy.ui.operations.OperationsScreen
import com.moifinansy.ui.reports.ReportsScreen
import com.moifinansy.ui.settings.SettingsScreen

@Composable
fun MoiFinansyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)
    val idArg = navArgument("id") {
        type = NavType.LongType
        defaultValue = Routes.NONE
    }

    Scaffold(
        // Отступы системных панелей обрабатывают сами экраны; здесь — только высота нижней панели.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = destination == current,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (current?.hasFab == true) {
                FloatingActionButton(onClick = { /* Экран добавления — этап 4 */ }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fab_add))
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.start.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.OPERATIONS.route) { OperationsScreen() }
            composable(TopLevelDestination.DEBTS.route) { DebtsScreen() }
            composable(TopLevelDestination.REPORTS.route) { ReportsScreen() }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = navController::popBackStack) }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onBack = navController::popBackStack,
                    onOpen = { navController.navigate(Routes.account(it)) },
                    onAdd = { navController.navigate(Routes.accountEdit()) },
                )
            }
            composable(Routes.ACCOUNT, arguments = listOf(idArg)) {
                AccountDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(Routes.accountEdit(it)) },
                )
            }
            composable(Routes.ACCOUNT_EDIT, arguments = listOf(idArg)) {
                AccountEditScreen(onDone = navController::popBackStack)
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    onBack = navController::popBackStack,
                    onOpen = { navController.navigate(Routes.categoryEdit(it)) },
                    onAdd = { navController.navigate(Routes.categoryEdit(kind = it)) },
                )
            }
            composable(
                Routes.CATEGORY_EDIT,
                arguments = listOf(
                    idArg,
                    navArgument("kind") {
                        type = NavType.StringType
                        defaultValue = CategoryKind.EXPENSE.name
                    },
                    navArgument("parentId") {
                        type = NavType.LongType
                        defaultValue = Routes.NONE
                    },
                ),
            ) {
                CategoryEditScreen(
                    onDone = navController::popBackStack,
                    onOpen = { navController.navigate(Routes.categoryEdit(it)) },
                    onAddSub = { parentId -> navController.navigate(Routes.categoryEdit(parentId = parentId)) },
                )
            }
        }
    }
}
