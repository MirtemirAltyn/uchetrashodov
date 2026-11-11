package com.moifinansy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moifinansy.R
import com.moifinansy.ui.debts.DebtsScreen
import com.moifinansy.ui.home.HomeScreen
import com.moifinansy.ui.more.MoreScreen
import com.moifinansy.ui.operations.OperationsScreen
import com.moifinansy.ui.reports.ReportsScreen

@Composable
fun MoiFinansyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        bottomBar = {
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
            composable(TopLevelDestination.MORE.route) { MoreScreen() }
        }
    }
}
