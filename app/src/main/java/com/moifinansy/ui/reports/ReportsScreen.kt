package com.moifinansy.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BarGroup
import com.moifinansy.core.ui.BarsChart
import com.moifinansy.core.ui.DonutChart
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyText
import com.moifinansy.core.ui.SectionCard
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.PeriodKind
import com.moifinansy.domain.model.ReportPeriod
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenCategory: (categoryId: Long, kind: CategoryKind, period: ReportPeriod) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickRange by remember { mutableStateOf(false) }
    val colors = LocalFinanceColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.grid, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid),
    ) {
        item(key = "kinds") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                listOf(
                    PeriodKind.DAY to R.string.period_day,
                    PeriodKind.WEEK to R.string.period_week,
                    PeriodKind.MONTH to R.string.period_month,
                    PeriodKind.YEAR to R.string.period_year,
                    PeriodKind.CUSTOM to R.string.period_custom,
                ).forEach { (kind, label) ->
                    FilterChip(
                        selected = state.period.kind == kind,
                        onClick = {
                            viewModel.setKind(kind)
                            if (kind == PeriodKind.CUSTOM) pickRange = true
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        item(key = "period") {
            // Перелистывание периода — стрелками или свайпом по подписи.
            var drag by remember { mutableStateOf(0f) }
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 120) viewModel.shift(-1) else if (drag < -120) viewModel.shift(1)
                            drag = 0f
                        },
                    ) { _, dx -> drag += dx }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.shift(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.ops_prev))
                }
                Text(
                    DateFormat.period(state.period, state.firstDay),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable(enabled = state.period.kind == PeriodKind.CUSTOM) { pickRange = true },
                )
                IconButton(onClick = { viewModel.shift(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.ops_next))
                }
            }
        }
        item(key = "tabs") {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.reports_tab_expense, R.string.reports_tab_income, R.string.reports_tab_dynamics).forEachIndexed { i, label ->
                    Tab(selected = state.tab.ordinal == i, onClick = { viewModel.setTab(ReportTab.entries[i]) }, text = { Text(stringResource(label)) })
                }
            }
        }

        when (state.tab) {
            ReportTab.EXPENSE, ReportTab.INCOME -> {
                val expense = state.tab == ReportTab.EXPENSE
                val slices = if (expense) state.expenseSlices else state.incomeSlices
                val total = if (expense) state.expense else state.income
                item(key = "donut") {
                    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.grid), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = slices.map { Color(it.category.color) to it.total },
                            centerTitle = stringResource(R.string.reports_total),
                            centerValue = MoneyFormat.format(total, state.baseCurrency),
                        )
                    }
                }
                if (!state.loading && slices.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.reports_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                items(slices, key = { "s" + it.category.id }) { slice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategory(slice.category.id, slice.category.kind, state.period) }
                            .padding(vertical = Spacing.grid),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
                    ) {
                        IconBadge(slice.category.icon, slice.category.color)
                        Column(Modifier.weight(1f)) {
                            Text(slice.category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${slice.percent} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(MoneyFormat.format(slice.total, state.baseCurrency), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            ReportTab.DYNAMICS -> {
                item(key = "bars") {
                    SectionCard(title = stringResource(R.string.reports_months)) {
                        BarsChart(
                            groups = state.months.map { BarGroup(DateFormat.monthShort(it.month), listOf(it.income, it.expense), it.balanceEnd) },
                            colors = listOf(colors.income, colors.expense),
                            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
                        )
                        Text(
                            stringResource(R.string.reports_legend),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                        )
                    }
                }
            }
        }
        item(key = "summary") { SummaryCard(state) }
    }

    if (pickRange) {
        val (from, to) = state.period.days(state.firstDay)
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = rangeState.selectedStartDateMillis
                    val end = rangeState.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        viewModel.setCustom(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    pickRange = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DateRangePicker(state = rangeState, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun SummaryCard(state: ReportsState) {
    SectionCard(title = stringResource(R.string.reports_summary)) {
        Column(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            SummaryLine(stringResource(R.string.home_income), state.income, state.baseCurrency, signed = true, change = state.incomeChange())
            SummaryLine(stringResource(R.string.home_expense), -state.expense, state.baseCurrency, signed = true, change = state.expenseChange())
            SummaryLine(stringResource(R.string.reports_difference), state.difference, state.baseCurrency, signed = true)
            SummaryLine(stringResource(R.string.reports_avg_daily), state.avgDailyExpense, state.baseCurrency)
            if (state.expenseSlices.isNotEmpty()) {
                Text(stringResource(R.string.reports_top5), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.expenseSlices.take(5).forEach { s ->
                    Row {
                        Text(s.category.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(MoneyFormat.format(s.total, state.baseCurrency))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(title: String, amount: Long, currency: String, signed: Boolean = false, change: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (change != null) {
                Text(
                    stringResource(R.string.reports_change, (if (change > 0) "+" else "") + change + " %"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(amount, currency, signed = signed)
    }
}
