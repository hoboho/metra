package ir.metra.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ir.metra.app.domain.backup.RestoreStrategy

/**
 * Backup, restore and export.
 *
 * A restore is never silent: the user picks a strategy, sees the incoming and
 * existing counts, and the app writes a safety snapshot before touching
 * anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var showRestorePreview by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { viewModel.exportToUri(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.previewRestore(it) } }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.preview) {
        if (state.preview != null) showRestorePreview = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_backup)) }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.settings_backup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row2(
                        label = stringResource(R.string.settings_backup_encrypted),
                        checked = state.encryptBackup,
                        onCheckedChange = { viewModel.setEncryptBackup(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (state.encryptBackup) {
                                showPassphraseDialog = true
                            } else {
                                exportLauncher.launch(viewModel.suggestedFileName())
                            }
                        },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.working) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.settings_backup))
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = stringResource(R.string.settings_restore),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.restore_safety_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_restore)) }
                }
            }

            item {
                Column {
                    OutlinedButton(
                        onClick = { viewModel.exportCsv() },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_export_csv)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.exportJson() },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_export_json)) }
                }
            }
        }
    }

    if (showPassphraseDialog) {
        PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_title),
            hint = stringResource(R.string.backup_passphrase_hint),
            onDismiss = { showPassphraseDialog = false },
            onConfirm = { passphrase ->
                showPassphraseDialog = false
                exportLauncher.launch(viewModel.suggestedFileName())
                viewModel.setPendingPassphrase(passphrase)
            },
        )
    }

    if (showRestorePreview && state.preview != null) {
        RestorePreviewDialog(
            preview = state.preview!!,
            strategy = state.strategy,
            needsPassphrase = state.preview!!.encrypted,
            onStrategyChange = { viewModel.setStrategy(it) },
            onDismiss = {
                showRestorePreview = false
                viewModel.clearPreview()
            },
            onConfirm = { passphrase ->
                showRestorePreview = false
                viewModel.confirmRestore(passphrase)
            },
        )
    }
}

@Composable
private fun Row2(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = passphrase.length >= 4) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun RestorePreviewDialog(
    preview: ir.metra.app.domain.backup.RestorePreview,
    strategy: RestoreStrategy,
    needsPassphrase: Boolean,
    onStrategyChange: (RestoreStrategy) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(needsPassphrase) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (needsPassphrase && !showPassphrase) {
                        showPassphrase = true
                    } else {
                        onConfirm(passphrase.takeIf { it.isNotBlank() })
                    }
                },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.restore_preview_title)) },
        text = {
            Column {
                if (!preview.formatSupported) {
                    Text(
                        text = stringResource(R.string.restore_unsupported),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    return@Column
                }
                Text(
                    text = stringResource(
                        R.string.restore_preview_message,
                        ir.metra.app.core.format.PersianDigits.toPersian(preview.incomingProjects.toString()),
                        ir.metra.app.core.format.PersianDigits.toPersian(preview.incomingWorkRecords.toString()),
                        ir.metra.app.core.format.PersianDigits.toPersian(preview.incomingExpenses.toString()),
                        ir.metra.app.core.format.PersianDigits.toPersian(preview.existingProjects.toString()),
                        ir.metra.app.core.format.PersianDigits.toPersian(preview.existingWorkRecords.toString()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.restore_strategy_title), style = MaterialTheme.typography.titleSmall)
                RestoreStrategy.entries.forEach { option ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == strategy,
                            onClick = { onStrategyChange(option) },
                        )
                        Text(
                            text = when (option) {
                                RestoreStrategy.ADD_ONLY -> stringResource(R.string.restore_strategy_add_only)
                                RestoreStrategy.MERGE -> stringResource(R.string.restore_strategy_merge)
                                RestoreStrategy.REPLACE -> stringResource(R.string.restore_strategy_replace)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (showPassphrase) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase_restore_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.restore_safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
