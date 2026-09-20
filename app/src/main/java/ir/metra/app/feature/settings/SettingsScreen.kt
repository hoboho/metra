package ir.metra.app.feature.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.data.preferences.ThemeMode
import ir.metra.app.ui.components.MinuteOfDayPickerDialog
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow
import ir.metra.app.ui.components.TagChip

/**
 * All settings, grouped exactly as specified: profile, payment rules, work
 * defaults, notifications, appearance, security, data and about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenProjects: () -> Unit,
    onOpenPaymentRules: () -> Unit,
    onOpenDatabaseInfo: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReminderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------- profile
            item {
                SectionCard(title = stringResource(R.string.settings_section_profile)) {
                    OutlinedTextField(
                        value = state.profile.fullName,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(fullName = value) } },
                        label = { Text(stringResource(R.string.settings_full_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.profile.companyName,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(companyName = value) } },
                        label = { Text(stringResource(R.string.settings_company_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.profile.employeeCode,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(employeeCode = value) } },
                        label = { Text(stringResource(R.string.settings_employee_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.profile.reportFooterNote,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(reportFooterNote = value) } },
                        label = { Text(stringResource(R.string.settings_report_footer)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ------------------------------------------------- payment rules
            item {
                SectionCard(
                    title = stringResource(R.string.settings_section_payment),
                    trailing = {
                        TextButton(onClick = onOpenPaymentRules) {
                            Text(stringResource(R.string.settings_rate_history))
                        }
                    },
                ) {
                    state.rules.firstOrNull()?.let { rule ->
                        StatRow(stringResource(R.string.settings_threshold), rule.thresholdText)
                        StatRow(stringResource(R.string.settings_rate), rule.rateText)
                        StatRow(stringResource(R.string.settings_rate_effective_date), rule.effectiveFromLabel)
                    }
                    Text(
                        text = stringResource(R.string.settings_rate_change_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ------------------------------------------------- work defaults
            item {
                SectionCard(title = stringResource(R.string.settings_section_defaults)) {
                    OutlinedTextField(
                        value = state.settings.defaultWorkArea,
                        onValueChange = { value ->
                            viewModel.onSettingsChange { it.copy(defaultWorkArea = value) }
                        },
                        label = { Text(stringResource(R.string.settings_default_work_area)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.settings.defaultSupervisor,
                        onValueChange = { value ->
                            viewModel.onSettingsChange { it.copy(defaultSupervisor = value) }
                        },
                        label = { Text(stringResource(R.string.settings_default_supervisor)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.settings.defaultWorkerCount.toString(),
                        onValueChange = { value ->
                            viewModel.onSettingsChange {
                                it.copy(defaultWorkerCount = value.toIntOrNull() ?: 0)
                            }
                        },
                        label = { Text(stringResource(R.string.settings_default_worker_count)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_projects))
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        label = stringResource(R.string.settings_use_previous_workday),
                        checked = state.settings.usePreviousWorkdayInfo,
                        onCheckedChange = { viewModel.onUsePreviousWorkdayChange(it) },
                    )
                }
            }

            // ------------------------------------------------- notifications
            item {
                SectionCard(title = stringResource(R.string.settings_section_notifications)) {
                    SwitchRow(
                        label = stringResource(R.string.settings_reminder_enabled),
                        checked = state.settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.onReminderChange(enabled, state.settings.reminderMinuteOfDay)
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_reminder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showReminderPicker = true },
                        enabled = state.settings.reminderEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${stringResource(R.string.settings_reminder_time)}: ${state.reminderTimeLabel}")
                    }
                }
            }

            // ---------------------------------------------------- appearance
            item {
                SectionCard(title = stringResource(R.string.settings_section_appearance)) {
                    ThemeRow(
                        label = stringResource(R.string.settings_theme_system),
                        selected = state.preferences.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.onThemeModeChange(ThemeMode.SYSTEM) },
                    )
                    ThemeRow(
                        label = stringResource(R.string.settings_theme_light),
                        selected = state.preferences.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.onThemeModeChange(ThemeMode.LIGHT) },
                    )
                    ThemeRow(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = state.preferences.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.onThemeModeChange(ThemeMode.DARK) },
                    )
                }
            }

            // ---------------------------------------------------------- data
            item {
                SectionCard(title = stringResource(R.string.settings_section_data)) {
                    OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_backup))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenDatabaseInfo, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_database_info))
                    }
                }
            }

            // --------------------------------------------------------- about
            item {
                SectionCard(title = stringResource(R.string.settings_section_about)) {
                    StatRow(
                        label = stringResource(R.string.settings_about_version),
                        value = ir.metra.app.BuildConfig.VERSION_NAME,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showReminderPicker) {
        MinuteOfDayPickerDialog(
            initialMinuteOfDay = state.settings.reminderMinuteOfDay,
            title = stringResource(R.string.settings_reminder_time),
            onDismiss = { showReminderPicker = false },
            onTimeSelected = { minute ->
                viewModel.onReminderChange(state.settings.reminderEnabled, minute)
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selected) TagChip(stringResource(R.string.action_confirm))
    }
}

