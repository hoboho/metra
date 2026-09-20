package ir.metra.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow
import ir.metra.app.ui.components.TagChip

/**
 * The home screen.
 *
 * Two things are optimised for: seeing today at a glance, and logging today's
 * work in as few taps as possible. Everything else on this screen is context.
 *
 * Company-reported amounts are labelled «ثبت‌شده» throughout so the app never
 * implies it calculated payroll.
 */
@Composable
fun DashboardScreen(
    onOpenWorkEditor: () -> Unit,
    onOpenWorkLog: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenStatistics: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshBestDay() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = if (state.userName.isBlank()) {
                        stringResource(R.string.dashboard_greeting_default)
                    } else {
                        stringResource(R.string.dashboard_greeting, state.userName)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = state.todayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenWorkEditor, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dashboard_quick_add))
                }
                FilledTonalButton(onClick = onOpenReports, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dashboard_quick_report))
                }
            }
        }

        item {
            ReceivablesHero(
                outstanding = state.monthTotals.outstandingText,
                collected = state.monthTotals.collectedText,
                totalReceivable = state.monthTotals.totalReceivableText,
            )
        }

        item { TodayCard(state.todayRecord, onOpenWorkEditor) }

        item {
            SectionCard(
                title = state.monthLabel,
                subtitle = stringResource(R.string.dashboard_this_month),
            ) {
                StatRow(
                    label = stringResource(R.string.dashboard_total_meters),
                    value = state.monthTotals.totalMetersText + " " + stringResource(R.string.meter),
                )
                StatRow(
                    label = stringResource(R.string.dashboard_average_meters),
                    value = state.monthTotals.averageMetersText + " " + stringResource(R.string.meter),
                )
                StatRow(
                    label = stringResource(R.string.dashboard_workdays),
                    value = state.monthTotals.workdaysText,
                )
                StatRow(
                    label = stringResource(R.string.dashboard_additional_meters),
                    value = state.monthTotals.additionalMetersText + " " + stringResource(R.string.meter),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                StatRow(
                    label = stringResource(R.string.dashboard_additional_payment),
                    value = state.monthTotals.additionalPaymentText,
                    emphasised = true,
                    hint = stringResource(R.string.dashboard_calculated_by_metra),
                )
            }
        }

        // One card, two lines: what the company owes for the month and how much
        // of it has actually been collected. Salary is gone entirely; expenses
        // are reimbursable so they belong on the receivable side.
        item {
            SectionCard(
                title = stringResource(R.string.dashboard_receivables),
                subtitle = stringResource(R.string.dashboard_receivables_hint),
            ) {
                StatRow(
                    label = stringResource(R.string.dashboard_total_expenses),
                    value = state.monthTotals.totalExpensesText,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                StatRow(
                    label = stringResource(R.string.dashboard_total_income),
                    value = state.monthTotals.totalReceivableText,
                    emphasised = true,
                )
                StatRow(
                    label = stringResource(R.string.dashboard_collected),
                    value = state.monthTotals.collectedText,
                )
                StatRow(
                    label = stringResource(R.string.dashboard_outstanding),
                    value = state.monthTotals.outstandingText,
                    emphasised = true,
                    hint = stringResource(R.string.dashboard_outstanding_hint),
                )
            }
        }

        state.bestDay?.let { best ->
            item {
                SectionCard(title = stringResource(R.string.dashboard_best_day)) {
                    Text(best.dateLabel, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    StatRow(label = stringResource(R.string.dashboard_total_meters), value = best.metersText)
                    StatRow(
                        label = stringResource(R.string.dashboard_additional_payment),
                        value = best.additionalPaymentText,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenWorkLog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_worklog))
                }
                FilledTonalButton(onClick = onOpenStatistics, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_statistics))
                }
            }
        }
    }
}

@Composable
private fun TodayCard(today: TodaySummary?, onOpenWorkEditor: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                today?.let { TagChip(it.projectName) }
            }
            Spacer(Modifier.height(8.dp))
            if (today == null) {
                Text(
                    text = stringResource(R.string.dashboard_today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenWorkEditor) {
                    Text(stringResource(R.string.dashboard_quick_add))
                }
            } else {
                StatRow(
                    label = stringResource(R.string.field_daily_meters),
                    value = today.metersText,
                )
                StatRow(
                    label = stringResource(R.string.field_additional_meters),
                    value = today.additionalMetersText,
                )
                StatRow(
                    label = stringResource(R.string.field_additional_payment),
                    value = today.additionalPaymentText,
                    emphasised = true,
                )
                StatRow(
                    label = stringResource(R.string.expense_title),
                    value = today.expenseText,
                )
            }
        }
    }
}

@Composable
private fun ReceivablesHero(
    outstanding: String,
    collected: String,
    totalReceivable: String,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.primary,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.dashboard_outstanding),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimary.copy(alpha = 0.82f),
            )
            Text(
                text = outstanding,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.onPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroCell(stringResource(R.string.dashboard_total_income), totalReceivable, Modifier.weight(1f))
                HeroCell(stringResource(R.string.dashboard_collected), collected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroCell(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onPrimary)
    }
}
