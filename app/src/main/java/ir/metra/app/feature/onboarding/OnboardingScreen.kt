package ir.metra.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import ir.metra.app.core.format.DateFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraButtonLevel
import ir.metra.app.ui.components.MetraTextField
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.MinuteOfDayPickerDialog

/**
 * First-run setup flow.
 *
 * Deliberately short and skippable: the app is fully usable with defaults, so
 * nothing here blocks the user from reaching the work log.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateFormatter() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name_latin),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (state.step) {
                    0 -> WelcomeStep()
                    1 -> ProfileStep(
                        fullName = state.fullName,
                        companyName = state.companyName,
                        onFullNameChange = { value -> viewModel.onFieldChange { it.copy(fullName = value) } },
                        onCompanyNameChange = { value -> viewModel.onFieldChange { it.copy(companyName = value) } },
                    )

                    2 -> RulesStep(
                        threshold = state.thresholdMeters,
                        rate = state.ratePerMeter,
                        onThresholdChange = { value -> viewModel.onFieldChange { it.copy(thresholdMeters = value) } },
                        onRateChange = { value -> viewModel.onFieldChange { it.copy(ratePerMeter = value) } },
                    )

                    else -> ReminderStep(
                        enabled = state.reminderEnabled,
                        minuteOfDay = state.reminderMinuteOfDay,
                        formattedTime = dateFormatter.formatTime(state.reminderMinuteOfDay),
                        onEnabledChange = { value -> viewModel.onFieldChange { it.copy(reminderEnabled = value) } },
                        onPickTime = { showTimePicker = true },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step > 0) {
                MetraButton(
                    text = stringResource(R.string.action_back),
                    onClick = { viewModel.back() },
                    level = MetraButtonLevel.Outline,
                    fullWidth = false,
                    modifier = Modifier.weight(1f),
                )
            }
            MetraButton(
                text = if (state.isLastStep) {
                    stringResource(R.string.onboarding_finish)
                } else {
                    stringResource(R.string.onboarding_next)
                },
                onClick = { if (state.isLastStep) viewModel.finish() else viewModel.next() },
                loading = state.saving,
                enabled = state.canContinue && !state.saving,
                fullWidth = false,
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.isLastStep) {
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MetraButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = { viewModel.finish() },
                    level = MetraButtonLevel.Text,
                    fullWidth = false,
                )
            }
        }
    }

    if (showTimePicker) {
        MinuteOfDayPickerDialog(
            initialMinuteOfDay = state.reminderMinuteOfDay,
            title = stringResource(R.string.settings_reminder_time),
            onDismiss = { showTimePicker = false },
            onTimeSelected = { minute -> viewModel.onFieldChange { it.copy(reminderMinuteOfDay = minute) } },
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column {
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProfileStep(
    fullName: String,
    companyName: String,
    onFullNameChange: (String) -> Unit,
    onCompanyNameChange: (String) -> Unit,
) {
    Column {
        Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_profile_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        MetraTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            label = stringResource(R.string.settings_full_name),
            singleLine = true,
            leadingIcon = Icons.Filled.Person,
        )
        Spacer(Modifier.height(12.dp))
        MetraTextField(
            value = companyName,
            onValueChange = onCompanyNameChange,
            label = stringResource(R.string.settings_company_name),
            singleLine = true,
            leadingIcon = Icons.Filled.Business,
        )
    }
}

@Composable
private fun RulesStep(
    threshold: String,
    rate: String,
    onThresholdChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
) {
    Column {
        Text(stringResource(R.string.onboarding_rules_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_rules_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        MetraNumberField(
            value = threshold,
            onValueChange = onThresholdChange,
            label = stringResource(R.string.settings_threshold),
            suffix = stringResource(R.string.meter),
        )
        Spacer(Modifier.height(12.dp))
        MetraNumberField(
            value = rate,
            onValueChange = onRateChange,
            label = stringResource(R.string.settings_rate),
            suffix = stringResource(R.string.toman),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_rule_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderStep(
    enabled: Boolean,
    minuteOfDay: Int,
    formattedTime: String,
    onEnabledChange: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.onboarding_reminder_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_reminder_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_reminder_enabled), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            MetraButton(
                text = "${stringResource(R.string.settings_reminder_time)}: $formattedTime",
                onClick = onPickTime,
                level = MetraButtonLevel.Outline,
                icon = Icons.Filled.Schedule,
            )
        }
    }
}
