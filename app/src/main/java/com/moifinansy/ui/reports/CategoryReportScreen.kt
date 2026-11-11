package com.moifinansy.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.BarGroup
import com.moifinansy.core.ui.BarsChart
import com.moifinansy.core.ui.GradientHeader
import com.moifinansy.core.ui.SectionCard
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.ui.operations.OperationItem
import com.moifinansy.ui.operations.OperationRow
import java.time.DayOfWeek

@Composable
fun CategoryReportScreen(onBack: () -> Unit, onOpenOperation: (OperationItem) -> Unit, viewModel: CategoryReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val category = state.category
    Scaffold(
        topBar = { BackTopBar(category?.name.orEmpty(), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item(key = "head") {
                val change = state.change
                GradientHeader(
                    title = DateFormat.period(state.period, DayOfWeek.MONDAY),
                    amount = MoneyFormat.format(state.total, state.baseCurrency),
                    subtitle = stringResource(R.string.reports_share, state.sharePercent) + " · " +
                        if (change == null) stringResource(R.string.reports_change_none) else stringResource(R.string.reports_change, (if (change > 0) "+" else "") + change + " %"),
                )
            }
            item(key = "months") {
                SectionCard(title = stringResource(R.string.reports_category_months)) {
                    BarsChart(
                        groups = state.months.map { (m, v) -> BarGroup(DateFormat.monthShort(m), listOf(v)) },
                        colors = listOf(Color(category?.color ?: 0xFF5B5BD6)),
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
                        height = androidx.compose.ui.unit.Dp(140f),
                    )
                }
            }
            if (state.subcategories.isNotEmpty()) {
                item(key = "subTitle") { SectionTitle(stringResource(R.string.reports_subcategories)) }
                items(state.subcategories, key = { "sub" + it.first.id }) { (c, total) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.grid / 2)) {
                        Text(c.name, modifier = Modifier.weight(1f))
                        Text(MoneyFormat.format(total, state.baseCurrency), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item(key = "opsTitle") { SectionTitle(stringResource(R.string.reports_operations)) }
            items(state.operations, key = { it.id }) { item -> OperationRow(item, onClick = { onOpenOperation(item) }, showDate = true) }
        }
    }
}
