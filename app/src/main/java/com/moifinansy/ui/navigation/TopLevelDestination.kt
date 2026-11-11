package com.moifinansy.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.moifinansy.R

/** Пять вкладок нижней панели (SPEC.md, раздел 5). */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    /** На каких вкладках есть кнопка «+». */
    val hasFab: Boolean,
) {
    HOME("home", R.string.tab_home, Icons.Rounded.Home, hasFab = true),
    OPERATIONS("operations", R.string.tab_operations, Icons.Rounded.AccountBalanceWallet, hasFab = true),
    DEBTS("debts", R.string.tab_debts, Icons.Rounded.Handshake, hasFab = true),
    REPORTS("reports", R.string.tab_reports, Icons.Rounded.PieChart, hasFab = false),
    MORE("more", R.string.tab_more, Icons.Rounded.MoreHoriz, hasFab = false),
    ;

    companion object {
        val start = HOME

        fun fromRoute(route: String?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}
