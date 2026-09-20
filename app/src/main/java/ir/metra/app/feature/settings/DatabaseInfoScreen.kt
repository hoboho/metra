package ir.metra.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow

/**
 * What the local database currently holds.
 *
 * Purely informational: the app is local-first, so this is the user's window
 * into where their data lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseInfoScreen(
    viewModel: DatabaseInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.db_info_title)) }) }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.db_info_title)) {
                    StatRow(stringResource(R.string.db_info_workdays), state.workRecords)
                    StatRow(stringResource(R.string.db_info_projects), state.projects)
                    StatRow(stringResource(R.string.db_info_expenses), state.expenses)
                    StatRow(stringResource(R.string.db_info_rules), state.rules)
                    StatRow(stringResource(R.string.db_info_backups), state.backups)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.settings_about_privacy)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}
