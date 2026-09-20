package ir.metra.app.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.core.format.PersianDigits
import ir.metra.app.ui.components.BarChart
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow

/**
 * Annual overview: twelve Jalali months with totals and monthly charts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyOverviewScreen(
    viewModel: YearlyOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.statistics_yearly)) }) }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.onYearChange(state.year - 1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.calendar_month_navigation_previous),
                        )
                    }
                    Text(
                        text = PersianDigits.toPersian(state.year.toString()),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { viewModel.onYearChange(state.year + 1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.calendar_month_navigation_next),
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.statistics_yearly)) {
                    state.totalsText.forEach { (label, value) ->
                        StatRow(label = label, value = value)
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.statistics_chart_monthly_meters)) {
                    BarChart(points = state.monthlyMetersChart)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.statistics_chart_additional_payment)) {
                    BarChart(points = state.monthlyPaymentChart)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.statistics_chart_expenses)) {
                    BarChart(points = state.monthlyExpenseChart)
                }
            }

            items(state.rows) { row ->
                SectionCard(title = row.monthLabel) {
                    StatRow(stringResource(R.string.dashboard_workdays), row.workdays)
                    StatRow(
                        stringResource(R.string.dashboard_total_meters),
                        row.meters + " " + stringResource(R.string.meter),
                    )
                    StatRow(
                        stringResource(R.string.dashboard_additional_meters),
                        row.additionalMeters + " " + stringResource(R.string.meter),
                    )
                    StatRow(
                        stringResource(R.string.dashboard_additional_payment),
                        row.additionalPayment,
                        emphasised = true,
                    )
                    StatRow(stringResource(R.string.dashboard_total_expenses), row.expenses)
                    StatRow(stringResource(R.string.dashboard_total_income), row.recordedIncome)
                }
            }
        }
    }
}
