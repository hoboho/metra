package ir.metra.app.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableChart
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.domain.model.ReportType
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.StatRow

/**
 * Reports: pick a shape and a period, review the summary, then produce a PDF or
 * CSV and share it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenProjects: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        // A null Uri means the user backed out of the picker: nothing to do.
        if (uri != null) viewModel.savePdfTo(uri)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var projectMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.reports_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.reports_title)) {
                ReportTypeChips(current = state.reportType, onSelected = { viewModel.onReportTypeChange(it) })
            }

            SectionCard(title = stringResource(R.string.report_period)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(state.startLabel)
                    }
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(state.endLabel)
                    }
                }
                if (state.reportType == ReportType.PROJECT) {
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = projectMenuExpanded,
                        onExpandedChange = { projectMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = state.projects.firstOrNull { it.id == state.selectedProjectId }?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.worklog_filter_project)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false },
                        ) {
                            state.projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        viewModel.onProjectSelected(project.id)
                                        projectMenuExpanded = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_projects)) },
                                onClick = {
                                    projectMenuExpanded = false
                                    onOpenProjects()
                                },
                            )
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.report_summary)) {
                StatRow(stringResource(R.string.dashboard_workdays), state.totalsUi.workdays)
                StatRow(
                    stringResource(R.string.dashboard_total_meters),
                    state.totalsUi.meters + " " + stringResource(R.string.meter),
                )
                StatRow(
                    stringResource(R.string.dashboard_average_meters),
                    state.totalsUi.average + " " + stringResource(R.string.meter),
                )
                StatRow(
                    stringResource(R.string.dashboard_additional_meters),
                    state.totalsUi.additionalMeters + " " + stringResource(R.string.meter),
                )
                StatRow(
                    stringResource(R.string.dashboard_additional_payment),
                    state.totalsUi.additionalPayment,
                    emphasised = true,
                    hint = stringResource(R.string.dashboard_calculated_by_metra),
                )
                StatRow(stringResource(R.string.dashboard_total_expenses), state.totalsUi.expenses)
                StatRow(
                    stringResource(R.string.dashboard_total_income),
                    state.totalsUi.recordedIncome,
                    emphasised = true,
                )
            }

            SectionCard(title = stringResource(R.string.report_generate_pdf)) {
                OptionRow(
                    label = stringResource(R.string.report_include_daily_table),
                    checked = state.includeDailyTable,
                    onCheckedChange = { viewModel.onOptionChange { current -> current.copy(includeDailyTable = it) } },
                )
                OptionRow(
                    label = stringResource(R.string.report_include_notes),
                    checked = state.includeNotes,
                    onCheckedChange = { viewModel.onOptionChange { current -> current.copy(includeNotes = it) } },
                )
                OptionRow(
                    label = stringResource(R.string.report_include_project_breakdown),
                    checked = state.includeProjectBreakdown,
                    onCheckedChange = {
                        viewModel.onOptionChange { current -> current.copy(includeProjectBreakdown = it) }
                    },
                )
                OptionRow(
                    label = stringResource(R.string.report_include_expense_breakdown),
                    checked = state.includeExpenseBreakdown,
                    onCheckedChange = {
                        viewModel.onOptionChange { current -> current.copy(includeExpenseBreakdown = it) }
                    },
                )
            }

            Button(
                onClick = { viewModel.generateAndSharePdf() },
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
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.report_generate_pdf))
                }
            }
            // Save-to-file goes through the system document picker: no storage
            // permission, and the user picks a folder they can find again.
            OutlinedButton(
                onClick = { savePdfLauncher.launch(viewModel.suggestedPdfName()) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.report_save_pdf))
            }
            OutlinedButton(
                onClick = { viewModel.generateAndShareCsv() },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.TableChart, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.report_generate_csv))
            }
        }
    }

    if (showStartPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = state.startEpochDay,
            onDismiss = { showStartPicker = false },
            onDateSelected = { viewModel.onDateRangeChange(it, state.endEpochDay) },
        )
    }
    if (showEndPicker) {
        JalaliDatePickerDialog(
            initialEpochDay = state.endEpochDay,
            onDismiss = { showEndPicker = false },
            onDateSelected = { viewModel.onDateRangeChange(state.startEpochDay, it) },
        )
    }
}

@Composable
private fun ReportTypeChips(current: ReportType, onSelected: (ReportType) -> Unit) {
    val options = listOf(
        ReportType.DAILY to stringResource(R.string.report_type_daily),
        ReportType.MONTHLY to stringResource(R.string.report_type_monthly),
        ReportType.RANGE to stringResource(R.string.report_type_range),
        ReportType.PROJECT to stringResource(R.string.report_type_project),
        ReportType.YEARLY to stringResource(R.string.report_type_yearly),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = type == current,
                        onClick = { onSelected(type) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
