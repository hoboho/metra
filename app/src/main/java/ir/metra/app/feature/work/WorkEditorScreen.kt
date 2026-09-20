package ir.metra.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.format.PersianDigits
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.ui.components.expenseCategoryLabel
import ir.metra.app.ui.components.JalaliDatePickerDialog
import ir.metra.app.ui.components.MetraNumberField
import ir.metra.app.ui.components.MinuteOfDayPickerDialog
import ir.metra.app.ui.components.SectionCard
import ir.metra.app.ui.components.TagChip

/**
 * The daily work record form.
 *
 * Sections mirror the product spec: day information, measured work (with the
 * live calculation), company-reported amounts, expenses, notes and work times.
 * Prefilled values are flagged so the user always knows what was copied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkEditorScreen(
    workRecordId: Long,
    epochDay: Long?,
    todayShortcut: Boolean,
    onDone: () -> Unit,
    onOpenExpenseEditor: (Long, Long) -> Unit,
    onOpenProjects: () -> Unit,
    viewModel: WorkEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolved through stringResource so the text is configuration-aware, then
    // remembered: a coroutine cannot call stringResource directly.
    val recordSavedMessage = stringResource(ir.metra.app.R.string.msg_record_saved)
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var expenseDialogIndex by remember { mutableStateOf<Int?>(null) }
    val dateFormatter = remember { DateFormatter() }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(recordSavedMessage)
            viewModel.consumeSaved()
            onDone()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) {
                            stringResource(R.string.editor_new_title)
                        } else {
                            stringResource(R.string.editor_edit_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            state.prefillBanner?.let { banner ->
                SectionCard(title = stringResource(R.string.prefill_toggle)) {
                    Text(banner, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ------------------------------------------------ SECTION 1: day
            SectionCard(title = stringResource(R.string.editor_section_day)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${stringResource(R.string.field_date)}: ${state.dateLabel}")
                }
                Spacer(Modifier.height(8.dp))

                ProjectDropdown(
                    projects = state.projects,
                    selectedId = state.projectId,
                    onSelected = { viewModel.onProjectSelected(it) },
                    onManageProjects = onOpenProjects,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.workArea,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_WORK_AREA, it) },
                    label = { Text(stringResource(R.string.field_work_area)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.employer,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_EMPLOYER, it) },
                    label = { Text(stringResource(R.string.field_employer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.supervisor,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_SUPERVISOR, it) },
                    label = { Text(stringResource(R.string.field_supervisor)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                MetraNumberField(
                    value = state.workerCount,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_WORKERS, it) },
                    label = stringResource(R.string.field_worker_count),
                )
            }

            // ---------------------------------------------- SECTION 2: work
            SectionCard(
                title = stringResource(R.string.editor_section_work),
                subtitle = stringResource(R.string.dashboard_calculated_by_metra),
            ) {
                MetraNumberField(
                    value = state.dailyMeters,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_METERS, it) },
                    label = stringResource(R.string.field_daily_meters),
                    suffix = stringResource(R.string.meter),
                    isError = state.fieldErrors.containsKey(WorkEditorViewModel.KEY_METERS),
                    errorMessage = state.fieldErrors[WorkEditorViewModel.KEY_METERS],
                )
                Spacer(Modifier.height(12.dp))
                state.calculationUi?.let { calculation ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(calculation.workedText, style = MaterialTheme.typography.bodyMedium)
                        Text(calculation.thresholdText, style = MaterialTheme.typography.bodyMedium)
                        Text(calculation.additionalText, style = MaterialTheme.typography.bodyMedium)
                        Text(calculation.rateText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = calculation.paymentText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        calculation.explainsNoPayment?.let { reason ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ----------------------------------------- SECTION 4: expenses
            SectionCard(
                title = stringResource(R.string.editor_section_expenses),
                trailing = { TagChip(state.expenseTotalText) },
            ) {
                if (state.expenses.isEmpty()) {
                    Text(
                        text = stringResource(R.string.expense_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.expenses.forEachIndexed { index, expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expenseCategoryLabel(expense.category),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = NumberFormatter().formatToman(expense.amount),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (expense.description.isNotBlank()) {
                                    Text(
                                        text = expense.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { expenseDialogIndex = index }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { viewModel.removeExpense(index) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { expenseDialogIndex = -1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.editor_add_expense))
                }
            }

            // -------------------------------------------- SECTION 5: notes
            SectionCard(title = stringResource(R.string.editor_section_notes)) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { viewModel.onFieldChange(WorkEditorViewModel.KEY_NOTES, it) },
                    label = { Text(stringResource(R.string.field_notes)) },
                    placeholder = { Text(stringResource(R.string.field_notes_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // -------------------------------------------- SECTION 6: times
            SectionCard(title = stringResource(R.string.editor_section_time)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "${stringResource(R.string.field_start_time)}: " +
                                dateFormatter.formatTime(state.workStartMinuteOfDay),
                        )
                    }
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "${stringResource(R.string.field_end_time)}: " +
                                dateFormatter.formatTime(state.workEndMinuteOfDay),
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialEpochDay = state.dateEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { viewModel.onDateSelected(it) },
        )
    }
    if (showStartPicker) {
        MinuteOfDayPickerDialog(
            initialMinuteOfDay = state.workStartMinuteOfDay,
            title = stringResource(R.string.field_start_time),
            onDismiss = { showStartPicker = false },
            onTimeSelected = { viewModel.onWorkStartSelected(it) },
        )
    }
    if (showEndPicker) {
        MinuteOfDayPickerDialog(
            initialMinuteOfDay = state.workEndMinuteOfDay,
            title = stringResource(R.string.field_end_time),
            onDismiss = { showEndPicker = false },
            onTimeSelected = { viewModel.onWorkEndSelected(it) },
        )
    }
    expenseDialogIndex?.let { index ->
        val existing = if (index >= 0) state.expenses.getOrNull(index) else null
        ExpenseDialog(
            initialAmount = existing?.amount?.toString() ?: "",
            initialCategory = existing?.category ?: ExpenseCategory.TRANSPORTATION,
            initialDescription = existing?.description ?: "",
            onDismiss = { expenseDialogIndex = null },
            onConfirm = { amount, category, description ->
                if (index >= 0) {
                    viewModel.updateExpense(index, amount, category, description)
                } else {
                    viewModel.addExpense(amount, category, description)
                }
                expenseDialogIndex = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(
    projects: List<ir.metra.app.domain.model.Project>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    onManageProjects: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = projects.firstOrNull { it.id == selectedId }?.name

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_project)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = {
                        onSelected(project.id)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_projects)) },
                onClick = {
                    expanded = false
                    onManageProjects()
                },
            )
        }
    }
}

