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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.domain.model.LedgerKind
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

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.entryId == 0L) R.string.ledger_add else R.string.ledger_edit,
                        ),
                    )
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
            // ------------------------------------------------------- kind
            item {
                FieldLabel(stringResource(R.string.ledger_kind))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.kind == LedgerKind.RECEIPT,
                        onClick = { viewModel.onKindChange(LedgerKind.RECEIPT) },
                        label = { Text(stringResource(R.string.ledger_receipt)) },
                    )
                    FilterChip(
                        selected = state.kind == LedgerKind.PAYMENT,
                        onClick = { viewModel.onKindChange(LedgerKind.PAYMENT) },
                        label = { Text(stringResource(R.string.ledger_payment)) },
                    )
                }
            }

            // ----------------------------------------------------- amount
            item {
                FieldLabel(stringResource(R.string.ledger_amount))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text(stringResource(R.string.toman)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } },
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
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LedgerReason.entries.forEach { reason ->
                        FilterChip(
                            selected = state.reason == reason,
                            onClick = { viewModel.onReasonChange(reason) },
                            label = { Text(reasonLabel(reason)) },
                        )
                    }
                }
            }

            // ------------------------------------------------------- date
            item {
                FieldLabel(stringResource(R.string.ledger_date))
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                FieldLabel(stringResource(R.string.ledger_notes))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }

            item {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ledger_save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
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
}
