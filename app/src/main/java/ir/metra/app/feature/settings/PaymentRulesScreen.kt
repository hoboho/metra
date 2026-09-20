package ir.metra.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow
import ir.metra.app.ui.components.TagChip

/**
 * Payment rule versions.
 *
 * New rates are always *appended* with an effective date. That is what keeps a
 * record from last year priced at last year's rate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentRulesScreen(
    viewModel: PaymentRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewRule by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_rate_history)) }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_rate_history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.rules, key = { it.id }) { rule ->
                SectionCard(
                    title = rule.label,
                    trailing = { if (rule.isCurrent) TagChip(stringResource(R.string.label_rule_current)) },
                ) {
                    StatRow(stringResource(R.string.settings_threshold), rule.thresholdText)
                    StatRow(stringResource(R.string.settings_rate), rule.rateText)
                    StatRow(stringResource(R.string.settings_rate_effective_date), rule.effectiveFromLabel)
                }
            }
            item {
                Button(onClick = { showNewRule = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_rate_new))
                }
            }
        }
    }

    if (showNewRule) {
        NewRuleDialog(
            initialThreshold = state.defaultThresholdMeters,
            initialRate = state.defaultRatePerMeter,
            onDismiss = { showNewRule = false },
            onConfirm = { threshold, rate, epochDay ->
                viewModel.addRule(threshold, rate, epochDay)
                showNewRule = false
            },
        )
    }
}

@Composable
private fun NewRuleDialog(
    initialThreshold: Int,
    initialRate: Long,
    onDismiss: () -> Unit,
    onConfirm: (threshold: Int, rate: Long, effectiveFromEpochDay: Long) -> Unit,
) {
    var threshold by remember { mutableStateOf(initialThreshold.toString()) }
    var rate by remember { mutableStateOf(initialRate.toString()) }
    var epochDay by remember { mutableStateOf(JalaliCalendar.today().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateFormatter() }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onConfirm(
                        threshold.toIntOrNull() ?: 0,
                        rate.toLongOrNull() ?: 0L,
                        epochDay,
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = { Text(stringResource(R.string.settings_rate_new)) },
        text = {
            Column {
                MetraNumberField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = stringResource(R.string.settings_threshold),
                    suffix = stringResource(R.string.meter),
                )
                Spacer(Modifier.height(8.dp))
                MetraNumberField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = stringResource(R.string.settings_rate),
                    suffix = stringResource(R.string.toman),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.settings_rate_effective_date)}: ${dateFormatter.formatLong(epochDay)}")
                }
            }
        },
    )

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialEpochDay = epochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { epochDay = it },
        )
    }
}
