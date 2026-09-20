package ir.metra.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.metra.app.R
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.domain.repository.WorkRecordFilter
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.ui.components.MetraNumberField

/**
 * The full filter sheet for the work log.
 *
 * Every dimension in the product spec is exposed here and maps to one nullable
 * parameter in the DAO query, so applying filters never re-reads the database in
 * memory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogFilterSheet(
    state: WorkLogUiState,
    onDismiss: () -> Unit,
    onApply: (WorkRecordFilter) -> Unit,
    onClear: () -> Unit,
    onOpenProjects: () -> Unit,
) {
    val current = state.filter
    var fromEpochDay by remember { mutableStateOf(current?.fromEpochDay ?: JalaliCalendar.today().toEpochDay() - 30) }
    var toEpochDay by remember { mutableStateOf(current?.toEpochDay ?: JalaliCalendar.today().toEpochDay()) }
    var projectId by remember { mutableStateOf(current?.projectId) }
    var employer by remember { mutableStateOf(current?.employer) }
    var supervisor by remember { mutableStateOf(current?.supervisor) }
    var workArea by remember { mutableStateOf(current?.workArea) }
    var minMeters by remember { mutableStateOf(current?.minMeters?.toString() ?: "") }
    var maxMeters by remember { mutableStateOf(current?.maxMeters?.toString() ?: "") }
    var onlyAdditional by remember { mutableStateOf(current?.onlyWithAdditionalMeters ?: false) }
    var onlyExpenses by remember { mutableStateOf(current?.onlyWithExpenses ?: false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateFormatter() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        WorkRecordFilter(
                            fromEpochDay = fromEpochDay,
                            toEpochDay = toEpochDay,
                            projectId = projectId,
                            employer = employer,
                            supervisor = supervisor,
                            workArea = workArea,
                            minMeters = minMeters.toIntOrNull(),
                            maxMeters = maxMeters.toIntOrNull(),
                            onlyWithAdditionalMeters = onlyAdditional,
                            onlyWithExpenses = onlyExpenses,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text(stringResource(R.string.action_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        title = { Text(stringResource(R.string.worklog_filters)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.worklog_filter_date_range), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFromPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(dateFormatter.format(fromEpochDay))
                    }
                    OutlinedButton(onClick = { showToPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(dateFormatter.format(toEpochDay))
                    }
                }

                DropdownField(
                    label = stringResource(R.string.worklog_filter_project),
                    options = state.projectNames.values.toList(),
                    selected = state.projectNames[projectId],
                    onSelected = { name ->
                        projectId = state.projectNames.entries.firstOrNull { it.value == name }?.key
                    },
                    onClearValue = { projectId = null },
                )
                DropdownField(
                    label = stringResource(R.string.worklog_filter_employer),
                    options = state.employers,
                    selected = employer,
                    onSelected = { employer = it },
                    onClearValue = { employer = null },
                )
                DropdownField(
                    label = stringResource(R.string.worklog_filter_supervisor),
                    options = state.supervisors,
                    selected = supervisor,
                    onSelected = { supervisor = it },
                    onClearValue = { supervisor = null },
                )
                DropdownField(
                    label = stringResource(R.string.worklog_filter_work_area),
                    options = state.workAreas,
                    selected = workArea,
                    onSelected = { workArea = it },
                    onClearValue = { workArea = null },
                )

                MetraNumberField(
                    value = minMeters,
                    onValueChange = { minMeters = it },
                    label = stringResource(R.string.worklog_filter_min_meters),
                    suffix = stringResource(R.string.meter),
                )
                MetraNumberField(
                    value = maxMeters,
                    onValueChange = { maxMeters = it },
                    label = stringResource(R.string.worklog_filter_max_meters),
                    suffix = stringResource(R.string.meter),
                )

                CheckRow(
                    label = stringResource(R.string.worklog_filter_only_additional),
                    checked = onlyAdditional,
                    onCheckedChange = { onlyAdditional = it },
                )
                CheckRow(
                    label = stringResource(R.string.worklog_filter_only_expenses),
                    checked = onlyExpenses,
                    onCheckedChange = { onlyExpenses = it },
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    )

    if (showFromPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = fromEpochDay,
            onDismiss = { showFromPicker = false },
            onDateSelected = { fromEpochDay = it },
        )
    }
    if (showToPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = toEpochDay,
            onDismiss = { showToPicker = false },
            onDateSelected = { toEpochDay = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    onClearValue: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_clear)) },
                onClick = {
                    onClearValue()
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
