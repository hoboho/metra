package ir.metra.app.feature.shooting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.format.PersianDigits
import ir.metra.app.feature.shooting.data.ShootingReportEntity
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.ui.components.MetraButton
import ir.metra.app.ui.components.MetraButtonLevel
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.MetraTextField
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootingScreen(viewModel: ShootingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dateFormatter = remember { DateFormatter() }
    val numberFormatter = remember { NumberFormatter() }
    var showDatePicker by remember { mutableStateOf(false) }
    var reportToDelete by remember { mutableStateOf<ShootingReportEntity?>(null) }

    val savedMessage = stringResource(
        if (state.lastSaveWasUpdate) R.string.shooting_report_updated else R.string.shooting_report_saved,
    )
    val deletedMessage = stringResource(R.string.shooting_report_deleted)
    LaunchedEffect(state.saved, savedMessage) {
        if (state.saved) {
            snackbar.showSnackbar(savedMessage)
            viewModel.consumeSaved()
        }
    }
    LaunchedEffect(state.deleted, deletedMessage) {
        if (state.deleted) {
            snackbar.showSnackbar(deletedMessage)
            viewModel.consumeDeleted()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.shooting_title)) }) },
    ) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = stringResource(if (state.editingReportId == null) R.string.shooting_new_report else R.string.shooting_edit_report),
                    subtitle = stringResource(R.string.shooting_form_hint),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectPicker(
                        projects = state.projects,
                        selectedName = state.projectName,
                        onSelect = viewModel::selectProject,
                    )
                    MetraButton(
                        text = stringResource(R.string.shooting_date, dateFormatter.formatLong(state.date)),
                        onClick = { showDatePicker = true },
                        level = MetraButtonLevel.Outline,
                        icon = Icons.Default.CalendarToday,
                    )
                    MetraTextField(
                        value = state.workArea,
                        onValueChange = { viewModel.header("area", it) },
                        label = stringResource(R.string.shooting_work_area),
                    )
                    MetraTextField(
                        value = state.employer,
                        onValueChange = { viewModel.header("employer", it) },
                        label = stringResource(R.string.shooting_employer),
                    )
                    MetraTextField(
                        value = state.supervisor,
                        onValueChange = { viewModel.header("supervisor", it) },
                        label = stringResource(R.string.shooting_supervisor),
                    )
                    MetraNumberField(
                        value = state.workerCount,
                        onValueChange = { viewModel.header("workers", it) },
                        label = stringResource(R.string.shooting_worker_count),
                        groupThousands = true,
                    )
                    MetraTextField(
                        value = state.notes,
                        onValueChange = { viewModel.header("notes", it) },
                        label = stringResource(R.string.shooting_notes),
                        singleLine = false,
                        minLines = 2,
                    )
                    }
                }
            }

            itemsIndexed(state.operations, key = { index, _ -> "operation-$index" }) { index, operation ->
                SectionCard(
                    title = stringResource(R.string.shooting_operation_number, PersianDigits.toPersian((index + 1).toString())),
                    trailing = {
                        IconButton(
                            onClick = { viewModel.removeOperation(index) },
                            enabled = state.operations.size > 1,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shooting_remove_operation))
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PointTypePicker(
                            label = stringResource(R.string.shooting_start_point),
                        value = operation.startType,
                        onSelect = { viewModel.operation(index, operation.copy(startType = it)) },
                    )
                    MetraTextField(
                        value = operation.startCode,
                        onValueChange = { viewModel.operation(index, operation.copy(startCode = it)) },
                        label = stringResource(R.string.shooting_start_code),
                    )
                    MetraNumberField(
                        value = operation.startMeter,
                        onValueChange = { viewModel.operation(index, operation.copy(startMeter = it)) },
                        label = stringResource(R.string.shooting_start_meter),
                        suffix = stringResource(R.string.meter),
                        groupThousands = true,
                    )
                    PointTypePicker(
                        label = stringResource(R.string.shooting_end_point),
                        value = operation.endType,
                        onSelect = { viewModel.operation(index, operation.copy(endType = it)) },
                    )
                    MetraTextField(
                        value = operation.endCode,
                        onValueChange = { viewModel.operation(index, operation.copy(endCode = it)) },
                        label = stringResource(R.string.shooting_end_code),
                    )
                    MetraNumberField(
                        value = operation.endMeter,
                        onValueChange = { viewModel.operation(index, operation.copy(endMeter = it)) },
                        label = stringResource(R.string.shooting_end_meter),
                        suffix = stringResource(R.string.meter),
                        groupThousands = true,
                    )
                    CableTypePicker(
                        cableTypes = state.cables,
                        selectedId = operation.cableTypeId,
                        onSelect = { viewModel.operation(index, operation.copy(cableTypeId = it)) },
                    )
                    val meters = operationMeters(operation)
                    StatRow(
                        label = stringResource(R.string.shooting_route_length),
                        value = stringResource(R.string.unit_meters_suffix, numberFormatter.formatPersian(meters)),
                        emphasised = true,
                    )
                    }
                }
            }

            item {
                MetraButton(
                    text = stringResource(R.string.shooting_add_route),
                    onClick = viewModel::addOperation,
                    level = MetraButtonLevel.Outline,
                    icon = Icons.Default.Add,
                )
            }

            item {
                val total = state.operations.sumOf(::operationMeters)
                SectionCard(title = stringResource(R.string.shooting_daily_summary)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow(
                        label = stringResource(R.string.shooting_total_meters),
                        value = stringResource(R.string.unit_meters_suffix, numberFormatter.formatPersian(total)),
                        emphasised = true,
                    )
                    MetraButton(
                        text = stringResource(if (state.saving) R.string.shooting_saving else if (state.editingReportId == null) R.string.shooting_save else R.string.shooting_update),
                        onClick = viewModel::save,
                        loading = state.saving,
                        enabled = state.projects.isNotEmpty() && state.cables.isNotEmpty(),
                    )
                    if (state.editingReportId != null) {
                        MetraButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = viewModel::cancelEdit,
                            level = MetraButtonLevel.Text,
                        )
                    }
                    if (state.projects.isEmpty()) {
                        Text(
                            text = stringResource(R.string.shooting_no_projects),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.shooting_saved_reports),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.reports.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.shooting_empty_reports),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.reports, key = { it.id }) { report ->
                ShootingReportCard(
                    report = report,
                    selected = report.id == state.editingReportId,
                    onEdit = {
                        viewModel.loadForEdit(report.id)
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onDelete = { reportToDelete = report },
                )
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialEpochDay = state.date,
            onDismiss = { showDatePicker = false },
            onDateSelected = viewModel::updateDate,
        )
    }
    reportToDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text(stringResource(R.string.shooting_delete_title)) },
            text = { Text(stringResource(R.string.shooting_delete_message, report.projectName)) },
            confirmButton = {
                TextButton(onClick = {
                    reportToDelete = null
                    viewModel.delete(report.id)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPicker(
    projects: List<ir.metra.app.domain.model.Project>,
    selectedName: String,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.shooting_project)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = { onSelect(project.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointTypePicker(label: String, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val pointTypes = listOf(
        "ODC" to R.string.shooting_point_odc,
        "FAT" to R.string.shooting_point_fat,
        "HANDHOLE" to R.string.shooting_point_handhole,
        "MANHOLE" to R.string.shooting_point_manhole,
    )
    val stableValue = when (value) {
        "حوضچه" -> "HANDHOLE"
        "منهول" -> "MANHOLE"
        else -> value
    }
    val displayValue = pointTypes.firstOrNull { it.first == stableValue }?.second
        ?.let { stringResource(it) } ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pointTypes.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { onSelect(code); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CableTypePicker(
    cableTypes: List<ir.metra.app.feature.shooting.data.CableTypeEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cableTypes.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (selected == null) "" else if (selected.label == "${selected.coreCount} کور") {
                stringResource(R.string.shooting_cable_cores, PersianDigits.toPersian(selected.coreCount.toString()))
            } else selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.shooting_cable_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cableTypes.forEach { cable ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (cable.label == "${cable.coreCount} کور") {
                                stringResource(R.string.shooting_cable_cores, PersianDigits.toPersian(cable.coreCount.toString()))
                            } else cable.label,
                        )
                    },
                    onClick = { onSelect(cable.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ShootingReportCard(
    report: ShootingReportEntity,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormatter = remember { DateFormatter() }
    val numberFormatter = remember { NumberFormatter() }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(report.projectName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        dateFormatter.formatLong(report.workDateEpochDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (report.workArea.isNotBlank()) {
                Text(report.workArea, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatRow(
                stringResource(R.string.shooting_total_meters),
                stringResource(R.string.unit_meters_suffix, numberFormatter.formatPersian(report.totalMeters)),
                emphasised = true,
            )
        }
    }
}

private fun operationMeters(operation: ShootingOperationForm): Long {
    val start = PersianDigits.normalizeNumberInput(operation.startMeter).toLongOrNull() ?: 0L
    val end = PersianDigits.normalizeNumberInput(operation.endMeter).toLongOrNull() ?: 0L
    return if (end >= start) end - start else start - end
}
