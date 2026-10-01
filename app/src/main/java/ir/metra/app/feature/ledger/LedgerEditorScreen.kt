package ir.metra.app.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.domain.model.LedgerKind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.CalendarToday
import ir.metra.app.domain.model.reasonsFor
import ir.metra.app.ui.components.ChoiceChips
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.MetraTextField
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.domain.model.LedgerReason

/** Toman amounts offered as one-tap chips so a receipt takes two taps, not ten. */
private val QUICK_AMOUNTS = listOf(1_000_000L, 5_000_000L, 10_000_000L, 20_000_000L)

/**
 * Add or edit one ledger movement.
 *
 * The kind selector comes first because it changes what the rest of the form
 * means: a receipt settles what the company owes, a personal payment increases
 * it. Everything else is optional so the fastest path stays short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEditorScreen(
    onDone: () -> Unit,
    viewModel: LedgerEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projectNames.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(
                                when {
                                    state.entryId != 0L -> R.string.ledger_edit
                                    state.kind == LedgerKind.CLAIM -> R.string.ledger_add_claim
                                    else -> R.string.ledger_add
                                },
                            ),
                        )
                        Text(
                            text = stringResource(
                                if (state.kind == LedgerKind.CLAIM) R.string.ledger_claim_subtitle
                                else R.string.ledger_subtitle,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------- operation
            item {
                FieldLabel(stringResource(R.string.ledger_kind))
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = listOf(LedgerKind.RECEIPT, LedgerKind.CLAIM),
                    selected = state.kind,
                    onSelected = { viewModel.onKindChange(it) },
                    labelOf = { kind ->
                        stringResource(if (kind == LedgerKind.RECEIPT) R.string.ledger_receipt else R.string.ledger_claim)
                    },
                )
            }

            // ----------------------------------------------------- amount
            item {
                MetraNumberField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = stringResource(R.string.ledger_amount),
                    suffix = stringResource(R.string.toman),
                    groupThousands = true,
                    isError = state.error != null,
                    errorMessage = state.error,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QUICK_AMOUNTS.forEach { amount ->
                        SuggestionChip(
                            onClick = { viewModel.addQuickAmount(amount) },
                            label = {
                                Text(
                                    text = (amount / 1_000_000L).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }

            // ----------------------------------------------------- reason
            item {
                FieldLabel(stringResource(R.string.ledger_reason))
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = reasonsFor(state.kind),
                    selected = state.reason,
                    onSelected = { viewModel.onReasonChange(it) },
                    labelOf = { reasonLabel(it) },
                )
            }

            // ------------------------------------------------------- date
            item {
                FieldLabel(stringResource(R.string.ledger_date))
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = state.dateText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                }
            }

            // ---------------------------------------------------- project
            if (projects.isNotEmpty()) {
                item {
                    FieldLabel(stringResource(R.string.ledger_project))
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilterChip(
                            selected = state.projectName.isEmpty(),
                            onClick = { viewModel.onProjectChange("") },
                            label = { Text(stringResource(R.string.ledger_project_all)) },
                        )
                        projects.take(3).forEach { name ->
                            FilterChip(
                                selected = state.projectName == name,
                                onClick = { viewModel.onProjectChange(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------- method
            item {
                FieldLabel(stringResource(R.string.ledger_method))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        R.string.ledger_method_card,
                        R.string.ledger_method_cash,
                        R.string.ledger_method_cheque,
                    ).forEach { resId ->
                        val label = stringResource(resId)
                        FilterChip(
                            selected = state.method == label,
                            onClick = { viewModel.onMethodChange(label) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ------------------------------------------------------ notes
            item {
                MetraTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = stringResource(
                        if (state.reason == LedgerReason.OTHER) R.string.ledger_other_details
                        else R.string.ledger_notes,
                    ),
                    minLines = 2,
                    leadingIcon = Icons.Filled.Notes,
                )
            }

            item {
                MetraButton(
                    text = stringResource(R.string.ledger_save),
                    onClick = viewModel::save,
                )
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialEpochDay = state.dateEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { viewModel.onDateChange(it) },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun reasonLabel(reason: LedgerReason): String = when (reason) {
    LedgerReason.METRAJE -> stringResource(R.string.ledger_reason_metraje)
    LedgerReason.EXPENSE -> stringResource(R.string.ledger_reason_expense)
    LedgerReason.SALARY -> stringResource(R.string.ledger_reason_salary)
    LedgerReason.OTHER -> stringResource(R.string.ledger_reason_other)
    LedgerReason.FUEL -> stringResource(R.string.ledger_reason_fuel)
    LedgerReason.TRANSPORT -> stringResource(R.string.ledger_reason_transport)
    LedgerReason.WORKER -> stringResource(R.string.ledger_reason_worker)
    LedgerReason.OUTSIDE_PROJECT -> stringResource(R.string.ledger_reason_outside_project)
}
