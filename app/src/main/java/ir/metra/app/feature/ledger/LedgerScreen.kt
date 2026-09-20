package ir.metra.app.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.domain.model.LedgerKind
import ir.metra.app.domain.model.LedgerReason
import androidx.compose.material3.TopAppBar

/**
 * The receivables ledger.
 *
 * One hero figure — what the company still owes — supported by two smaller ones
 * and the list of receipts behind them. Nothing here is editable in place:
 * tapping a row opens the editor, which keeps this screen to a single purpose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.ledger_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ledger_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutstandingHero(
                    outstanding = state.outstandingText,
                    totalReceivable = state.totalReceivableText,
                    collected = state.collectedText,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.ledger_entries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )
            }

            if (state.entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                    )
                }
            } else {
                items(state.entries, key = { it.id }) { row ->
                    LedgerRowCard(row = row, onClick = { onEditEntry(row.id) })
                }
            }
        }
    }
}

/**
 * The three figures that answer "where do I stand?".
 *
 * The outstanding amount is the only one given visual weight; the other two
 * exist so the user can see how it was reached without opening another screen.
 */
@Composable
private fun OutstandingHero(
    outstanding: String,
    totalReceivable: String,
    collected: String,
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
                text = stringResource(R.string.ledger_outstanding),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimary.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = outstanding,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = scheme.onPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroCell(
                    label = stringResource(R.string.ledger_total_receivable),
                    value = totalReceivable,
                    modifier = Modifier.weight(1f),
                )
                HeroCell(
                    label = stringResource(R.string.ledger_collected),
                    value = collected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroCell(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onPrimary.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = scheme.onPrimary,
        )
    }
}

@Composable
private fun LedgerRowCard(row: LedgerRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isReceipt = row.kind == LedgerKind.RECEIPT
    val accent = if (isReceipt) scheme.primary else scheme.error
    val accentSoft = if (isReceipt) {
        scheme.primary.copy(alpha = 0.12f)
    } else {
        scheme.error.copy(alpha = 0.12f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isReceipt) "↓" else "↑",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reasonLabel(row.reason),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                val detail = listOf(row.dateText, row.subtitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isReceipt) row.amountText else "−${row.amountText}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
    }
}

@Composable
private fun reasonLabel(reason: LedgerReason): String = when (reason) {
    LedgerReason.METRAJE -> stringResource(R.string.ledger_reason_metraje)
    LedgerReason.EXPENSE -> stringResource(R.string.ledger_reason_expense)
    LedgerReason.SALARY -> stringResource(R.string.ledger_reason_salary)
    LedgerReason.OTHER -> stringResource(R.string.ledger_reason_other)
}
