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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import ir.metra.app.BuildConfig
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
import ir.metra.app.ui.components.ChoiceChips
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.text.input.KeyboardType
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraButtonLevel
import ir.metra.app.ui.components.MetraTextField
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
                    MetraTextField(
                        value = state.profile.fullName,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(fullName = value) } },
                        label = stringResource(R.string.settings_full_name),
                        singleLine = true,
                        leadingIcon = Icons.Filled.Person,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraTextField(
                        value = state.profile.companyName,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(companyName = value) } },
                        label = stringResource(R.string.settings_company_name),
                        singleLine = true,
                        leadingIcon = Icons.Filled.Business,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraTextField(
                        value = state.profile.employeeCode,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(employeeCode = value) } },
                        label = stringResource(R.string.settings_employee_code),
                        singleLine = true,
                        leadingIcon = Icons.Filled.Badge,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraTextField(
                        value = state.profile.reportFooterNote,
                        onValueChange = { value -> viewModel.onProfileChange { it.copy(reportFooterNote = value) } },
                        label = stringResource(R.string.settings_report_footer),
                        leadingIcon = Icons.Filled.Notes,
                        minLines = 2,
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

            // ------------------------------------------------- update
            item {
                SectionCard(title = stringResource(R.string.settings_section_update)) {
                    val context = LocalContext.current
                    MetraButton(
                        text = stringResource(R.string.settings_check_update),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/hoboho/metra/releases/latest"),
                                ),
                            )
                        },
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Filled.Refresh,
                    )
                    Text(
                        text = stringResource(R.string.settings_update_note, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ------------------------------------------------- work defaults
            item {
                SectionCard(title = stringResource(R.string.settings_section_defaults)) {
                    MetraTextField(
                        value = state.settings.defaultWorkArea,
                        onValueChange = { value ->
                            viewModel.onSettingsChange { it.copy(defaultWorkArea = value) }
                        },
                        label = stringResource(R.string.settings_default_work_area),
                        singleLine = true,
                        leadingIcon = Icons.Filled.LocationOn,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraTextField(
                        value = state.settings.defaultSupervisor,
                        onValueChange = { value ->
                            viewModel.onSettingsChange { it.copy(defaultSupervisor = value) }
                        },
                        label = stringResource(R.string.settings_default_supervisor),
                        singleLine = true,
                        leadingIcon = Icons.Filled.Person,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraTextField(
                        value = state.settings.defaultWorkerCount.toString(),
                        onValueChange = { value ->
                            viewModel.onSettingsChange {
                                it.copy(defaultWorkerCount = value.toIntOrNull() ?: 0)
                            }
                        },
                        label = stringResource(R.string.settings_default_worker_count),
                        singleLine = true,
                        leadingIcon = Icons.Filled.Groups,
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraButton(
                        text = stringResource(R.string.settings_projects),
                        onClick = onOpenProjects,
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Filled.Folder,
                    )
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
                    MetraButton(
                        text = "${stringResource(R.string.settings_reminder_time)}: ${state.reminderTimeLabel}",
                        onClick = { showReminderPicker = true },
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Filled.Schedule,
                        enabled = state.settings.reminderEnabled,
                    )
                }
            }

            // ---------------------------------------------------- appearance
            item {
                SectionCard(title = stringResource(R.string.settings_section_appearance)) {
                    ChoiceChips(
                        options = ThemeMode.entries.toList(),
                        selected = state.preferences.themeMode,
                        onSelected = { viewModel.onThemeModeChange(it) },
                        labelOf = { mode ->
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                            }
                        },
                    )
                }
            }

            // ---------------------------------------------------------- data
            item {
                SectionCard(title = stringResource(R.string.settings_section_data)) {
                    MetraButton(
                        text = stringResource(R.string.settings_backup),
                        onClick = onOpenBackup,
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Filled.Backup,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetraButton(
                        text = stringResource(R.string.settings_database_info),
                        onClick = onOpenDatabaseInfo,
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Filled.Storage,
                    )
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


