package ir.metra.app.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.StatisticsPeriod
import ir.metra.app.ui.components.BarChart
import ir.metra.app.ui.components.ComparisonBar
import ir.metra.app.ui.components.LineChart
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow
import ir.metra.app.ui.components.JalaliDatePickerDialog

/**
 * Statistics: period selector, KPIs, charts and the monthly comparison.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onOpenYearlyOverview: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val moneyFormatter = remember {
        { value: Double -> androidx.compose.ui.text.intl.Locale.current.let { _ -> value.toLong().toString() } }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.statistics_title)) }) }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PeriodChips(current = state.period, onSelected = { viewModel.onPeriodChange(it) })
            }

            if (state.period == StatisticsPeriod.CUSTOM) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(state.rangeLabel.substringBefore(" - "))
                        }
                        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(state.rangeLabel.substringAfter(" - "))
                        }
                    }
                }
            }

            item {
                Text(
                    text = state.rangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionCard(title = stringResource(R.string.statistics_title)) {
                    StatRow(
                        stringResource(R.string.statistics_kpi_average),
                        stringResource(R.string.unit_meters_suffix, state.kpis.averageMeters),
                    )
                    StatRow(
                        stringResource(R.string.statistics_kpi_max),
                        stringResource(R.string.unit_meters_suffix, state.kpis.maxMeters),
                    )
                    StatRow(
                        stringResource(R.string.statistics_kpi_min),
                        stringResource(R.string.unit_meters_suffix, state.kpis.minMeters),
                    )
                    StatRow(
                        stringResource(R.string.statistics_kpi_total_meters),
                        stringResource(R.string.unit_meters_suffix, state.kpis.totalMeters),
                    )
                    StatRow(
                        stringResource(R.string.statistics_kpi_total_additional),
                        stringResource(R.string.unit_meters_suffix, state.kpis.totalAdditionalMeters),
                    )
                    StatRow(
                        stringResource(R.string.statistics_kpi_average_additional),
                        stringResource(R.string.unit_meters_suffix, state.kpis.averageAdditionalMeters),
                    )
                    StatRow(stringResource(R.string.statistics_kpi_total_expenses), state.kpis.totalExpenses)
                    StatRow(stringResource(R.string.statistics_kpi_average_expense), state.kpis.averageExpense)
                }
            }

            item {
                SectionCard(title = stringResource(R.string.statistics_chart_daily_meters)) {
                    BarChart(points = state.dailyMetersChart)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.statistics_chart_additional_meters)) {
                    BarChart(points = state.additionalMetersChart)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.statistics_chart_expenses)) {
                    LineChart(points = state.expensesChart)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.statistics_chart_recorded_income)) {
                    LineChart(points = state.incomeChart)
                }
            }

            state.comparison?.let { comparison ->
                item {
                    SectionCard(title = stringResource(R.string.statistics_comparison)) {
                        ComparisonBar(
                            label = stringResource(R.string.statistics_kpi_total_meters),
                            firstValue = comparison.metersFirst,
                            secondValue = comparison.metersSecond,
                            firstLabel = comparison.firstLabel,
                            secondLabel = comparison.secondLabel,
                            valueFormatter = { it.toLong().toString() },
                        )
                        Text(
                            text = "${stringResource(R.string.statistics_change)}: ${comparison.metersChange}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ComparisonBar(
                            label = stringResource(R.string.dashboard_additional_payment),
                            firstValue = comparison.paymentFirst,
                            secondValue = comparison.paymentSecond,
                            firstLabel = comparison.firstLabel,
                            secondLabel = comparison.secondLabel,
                            valueFormatter = { it.toLong().toString() },
                        )
                        Text(
                            text = "${stringResource(R.string.statistics_change)}: ${comparison.paymentChange}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ComparisonBar(
                            label = stringResource(R.string.statistics_kpi_total_expenses),
                            firstValue = comparison.expensesFirst,
                            secondValue = comparison.expensesSecond,
                            firstLabel = comparison.firstLabel,
                            secondLabel = comparison.secondLabel,
                            valueFormatter = { it.toLong().toString() },
                        )
                        Text(
                            text = "${stringResource(R.string.statistics_change)}: ${comparison.expensesChange}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = onOpenYearlyOverview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.statistics_yearly))
                }
            }
        }
    }

    val customStart = state.customRange?.start ?: JalaliCalendar.firstDayOfJalaliMonth(
        JalaliCalendar.today().toEpochDay(),
    )
    val customEnd = state.customRange?.end ?: JalaliCalendar.today().toEpochDay()

    if (showStartPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = customStart,
            onDismiss = { showStartPicker = false },
            onDateSelected = { viewModel.onCustomRangeChange(DateRange(it, customEnd)) },
        )
    }
    if (showEndPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = customEnd,
            onDismiss = { showEndPicker = false },
            onDateSelected = { viewModel.onCustomRangeChange(DateRange(customStart, it)) },
        )
    }
}

@Composable
private fun PeriodChips(current: StatisticsPeriod, onSelected: (StatisticsPeriod) -> Unit) {
    val options = listOf(
        StatisticsPeriod.CURRENT_WEEK to stringResource(R.string.statistics_period_week),
        StatisticsPeriod.CURRENT_MONTH to stringResource(R.string.statistics_period_month),
        StatisticsPeriod.PREVIOUS_MONTH to stringResource(R.string.statistics_period_previous_month),
        StatisticsPeriod.CURRENT_YEAR to stringResource(R.string.statistics_period_year),
        StatisticsPeriod.PREVIOUS_YEAR to stringResource(R.string.statistics_period_previous_year),
        StatisticsPeriod.CUSTOM to stringResource(R.string.statistics_period_custom),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (period, label) ->
            FilterChip(
                selected = period == current,
                onClick = { onSelected(period) },
                label = { Text(label) },
            )
        }
    }
}
