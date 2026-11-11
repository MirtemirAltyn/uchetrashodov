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
import com.moifinansy.ui.debts.ContactScreen
import com.moifinansy.ui.debts.DebtDetailScreen
import com.moifinansy.ui.debts.DebtEditScreen
import com.moifinansy.ui.debts.DebtsScreen
import com.moifinansy.ui.home.HomeScreen
import com.moifinansy.ui.more.MoreScreen
import com.moifinansy.ui.operations.OperationEditScreen
import com.moifinansy.ui.operations.OperationItem
import com.moifinansy.ui.operations.OperationsScreen
import com.moifinansy.ui.operations.UndoViewModel
import com.moifinansy.domain.model.TransactionType
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.ui.reports.CategoryReportScreen
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
    fun navigateTab(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openDebt(id: Long) = navController.navigate(Routes.debt(id))
    fun openOperation(item: OperationItem) {
        if (item.type == TransactionType.DEBT) {
            item.transaction.debtId?.let(::openDebt)
        } else {
            navController.navigate(Routes.operationEdit(item.id))
        }
    }

    val undoViewModel: UndoViewModel = hiltViewModel()
    val deleted by undoViewModel.deleted.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.op_deleted)
    val undoText = stringResource(R.string.op_undo)
    LaunchedEffect(deleted) {
        if (deleted != null) {
            val result = snackbarHostState.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undoViewModel.undo() else undoViewModel.dismiss()
        }
    }

    Scaffold(
        // Отступы системных панелей обрабатывают сами экраны; здесь — только высота нижней панели.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (current != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = destination == current,
                            onClick = { navigateTab(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (current?.hasFab == true) {
                FloatingActionButton(onClick = {
                    when (current) {
                        TopLevelDestination.DEBTS -> navController.navigate(Routes.debtEdit())
                        else -> navController.navigate(Routes.operationEdit())
                    }
                }) {
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
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenAccount = { navController.navigate(Routes.account(it)) },
                    onOpenOperation = ::openOperation,
                    onAllOperations = { navigateTab(TopLevelDestination.OPERATIONS) },
                    onDebts = { navigateTab(TopLevelDestination.DEBTS) },
                    onOpenDebt = ::openDebt,
                )
            }
            composable(TopLevelDestination.OPERATIONS.route) { OperationsScreen(onOpen = ::openOperation) }
            composable(
                Routes.OPERATION_EDIT,
                arguments = listOf(
                    idArg,
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = TransactionType.EXPENSE.name
                    },
                    navArgument("copyFrom") {
                        type = NavType.LongType
                        defaultValue = Routes.NONE
                    },
                ),
            ) {
                OperationEditScreen(
                    onDone = navController::popBackStack,
                    onCopy = { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.operationEdit(copyFrom = id))
                    },
                )
            }
            composable(TopLevelDestination.DEBTS.route) {
                DebtsScreen(
                    onOpenDebt = ::openDebt,
                    onOpenContact = { navController.navigate(Routes.contact(it)) },
                    onAdd = { navController.navigate(Routes.debtEdit()) },
                )
            }
            composable(Routes.DEBT, arguments = listOf(idArg)) {
                DebtDetailScreen(onBack = navController::popBackStack, onOpenContact = { navController.navigate(Routes.contact(it)) })
            }
            composable(
                Routes.DEBT_EDIT,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType; defaultValue = Routes.NONE }),
            ) {
                DebtEditScreen(
                    onBack = navController::popBackStack,
                    onSaved = { id ->
                        navController.popBackStack()
                        openDebt(id)
                    },
                )
            }
            composable(Routes.CONTACT, arguments = listOf(idArg)) {
                ContactScreen(
                    onBack = navController::popBackStack,
                    onOpenDebt = ::openDebt,
                    onAddDebt = { navController.navigate(Routes.debtEdit(it)) },
                )
            }
            composable(TopLevelDestination.REPORTS.route) {
                ReportsScreen(onOpenCategory = { id, kind, period -> navController.navigate(Routes.categoryReport(id, kind, period)) })
            }
            composable(
                Routes.CATEGORY_REPORT,
                arguments = listOf(
                    idArg,
                    navArgument("kind") { type = NavType.StringType; defaultValue = CategoryKind.EXPENSE.name },
                    navArgument("period") { type = NavType.StringType; defaultValue = "MONTH" },
                    navArgument("anchor") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("from") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("to") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                ),
            ) {
                CategoryReportScreen(onBack = navController::popBackStack, onOpenOperation = ::openOperation)
            }
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
                    onOpenOperation = { id, debtId ->
                        if (debtId != null) openDebt(debtId) else navController.navigate(Routes.operationEdit(id))
                    },
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
