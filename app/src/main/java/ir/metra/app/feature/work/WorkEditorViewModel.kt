package ir.metra.app.feature.work

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.R
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.usecase.BuildPrefill
import ir.metra.app.domain.usecase.CalculateMeterPayment
import ir.metra.app.domain.usecase.MeterCalculation
import ir.metra.app.domain.usecase.PrefillSource
import ir.metra.app.domain.usecase.SaveWorkRecord
import ir.metra.app.domain.usecase.WorkDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live preview of the Metra calculation, ready to render. */
data class CalculationUi(
    val workedText: String,
    val thresholdText: String,
    val additionalText: String,
    val rateText: String,
    val paymentText: String,
    val explainsNoPayment: String?,
)

data class WorkEditorUiState(
    val workRecordId: Long = 0L,
    val isNew: Boolean = true,
    val dateEpochDay: Long = JalaliCalendar.today().toEpochDay(),
    val dateLabel: String = "",
    val projectId: Long? = null,
    val projectName: String = "",
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: String = "",
    val dailyMeters: String = "",
    val overtime: String = "",
    val notes: String = "",
    val workStartMinuteOfDay: Int? = null,
    val workEndMinuteOfDay: Int? = null,
    val expenses: List<Expense> = emptyList(),
    val projects: List<Project> = emptyList(),
    val prefillSource: PrefillSource = PrefillSource.None,
    val prefillBanner: String? = null,
    val calculation: MeterCalculation? = null,
    val calculationUi: CalculationUi? = null,
    val expenseTotalText: String = "",
    val thresholdMeters: Int = PaymentRule.DEFAULT_THRESHOLD_METERS,
    val ratePerMeter: Long = PaymentRule.DEFAULT_RATE_PER_METER,
    val fieldErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val loading: Boolean = true,
)

/**
 * The daily work form.
 *
 * Responsibilities are deliberately narrow: hold the draft, recompute the meter
 * calculation, and delegate persistence to [SaveWorkRecord]. The additional-meter
 * rule is never re-implemented here — the form displays whatever the domain
 * calculator returns.
 */
@HiltViewModel
class WorkEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workRecordRepository: WorkRecordRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val buildPrefill: BuildPrefill,
    private val saveWorkRecord: SaveWorkRecord,
    private val calculateMeterPayment: CalculateMeterPayment,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val requestedRecordId: Long =
        savedStateHandle.get<Long>("workRecordId") ?: 0L
    private val requestedEpochDay: Long? = savedStateHandle.get<Long>("epochDay")
    private val todayShortcut: Boolean = savedStateHandle.get<Boolean>("todayShortcut") ?: false

    private val _state = MutableStateFlow(WorkEditorUiState())
    val state: StateFlow<WorkEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeActiveProjects().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        viewModelScope.launch { initialise() }
    }

    private suspend fun initialise() {
        if (requestedRecordId != 0L) {
            loadExisting(requestedRecordId)
        } else {
            val targetDay = requestedEpochDay
                ?: if (todayShortcut) JalaliCalendar.today().toEpochDay() else JalaliCalendar.today().toEpochDay()
            startNew(targetDay)
        }
    }

    private suspend fun loadExisting(workRecordId: Long) {
        val (record, expenses) = workRecordRepository.getRecordWithExpenses(workRecordId)
            ?: run {
                _state.update { it.copy(loading = false, errorMessage = strings.string(R.string.msg_record_not_found)) }
                return
            }
        val rule = paymentRuleRepository.applicableRuleOn(record.workDateEpochDay)
        _state.update {
            it.copy(
                workRecordId = record.id,
                isNew = false,
                dateEpochDay = record.workDateEpochDay,
                dateLabel = dateFormatter.formatLong(record.workDateEpochDay),
                projectId = record.projectId,
                projectName = record.projectName,
                workArea = record.workArea,
                employer = record.employer,
                supervisor = record.supervisor,
                workerCount = record.workerCount.takeIf { count -> count > 0 }?.toString() ?: "",
                dailyMeters = record.dailyMeters.toString(),
                notes = record.notes,
                workStartMinuteOfDay = record.workStartMinuteOfDay,
                workEndMinuteOfDay = record.workEndMinuteOfDay,
                expenses = expenses,
                thresholdMeters = record.thresholdMetersSnapshot,
                ratePerMeter = record.ratePerMeterSnapshot,
                loading = false,
            )
        }
        recompute()
    }

    private suspend fun startNew(targetDay: Long) {
        val settings = settingsRepository.getSettings()
        val prefill = buildPrefill.invoke(
            targetEpochDay = targetDay,
            selectedProjectId = settings.defaultProjectId,
            usePreviousWorkday = settings.usePreviousWorkdayInfo,
        )
        _state.update {
            it.copy(
                isNew = true,
                dateEpochDay = targetDay,
                dateLabel = dateFormatter.formatLongWithWeekday(targetDay),
                projectId = prefill.projectId,
                projectName = prefill.projectName,
                workArea = prefill.workArea,
                employer = prefill.employer,
                supervisor = prefill.supervisor,
                workerCount = prefill.workerCount?.toString() ?: "",
                workStartMinuteOfDay = prefill.workStartMinuteOfDay,
                workEndMinuteOfDay = prefill.workEndMinuteOfDay,
                prefillSource = prefill.source,
                prefillBanner = describePrefill(prefill.source, prefill.projectName),
                thresholdMeters = prefill.thresholdMeters,
                ratePerMeter = prefill.ratePerMeter,
                loading = false,
            )
        }
        recompute()
    }

    // ------------------------------------------------------------ user input

    fun onDateSelected(epochDay: Long) {
        _state.update {
            it.copy(dateEpochDay = epochDay, dateLabel = dateFormatter.formatLongWithWeekday(epochDay))
        }
        // The applicable rate can differ by date, so the preview must follow.
        viewModelScope.launch { refreshRuleForCurrentDate() }
    }

    fun onProjectSelected(projectId: Long?) {
        viewModelScope.launch {
            val project = projectId?.let { projectRepository.getProject(it) }
            _state.update { current ->
                current.copy(
                    projectId = project?.id,
                    // Selecting a project applies its defaults; the user can still
                    // edit every one of them before saving.
                    projectName = project?.name ?: current.projectName,
                    workArea = project?.workArea?.takeIf { it.isNotBlank() } ?: current.workArea,
                    employer = project?.employer?.takeIf { it.isNotBlank() } ?: current.employer,
                    supervisor = project?.defaultSupervisor?.takeIf { it.isNotBlank() } ?: current.supervisor,
                    workerCount = project?.defaultWorkerCount?.takeIf { count -> count > 0 }?.toString()
                        ?: current.workerCount,
                    fieldErrors = current.fieldErrors - KEY_PROJECT,
                )
            }
        }
    }

    fun onFieldChange(field: String, value: String) {
        _state.update { current ->
            val next = when (field) {
                KEY_PROJECT -> current.copy(projectName = value)
                KEY_WORK_AREA -> current.copy(workArea = value)
                KEY_EMPLOYER -> current.copy(employer = value)
                KEY_SUPERVISOR -> current.copy(supervisor = value)
                KEY_WORKERS -> current.copy(workerCount = value)
                KEY_METERS -> current.copy(dailyMeters = value)
                // Company salary is monthly, not per-day: those inputs moved to
                // the monthly salary screen.
                KEY_NOTES -> current.copy(notes = value)
                else -> current
            }
            next.copy(fieldErrors = next.fieldErrors - field)
        }
        if (field == KEY_METERS) recompute()
    }

    fun onWorkStartSelected(minuteOfDay: Int?) {
        _state.update { it.copy(workStartMinuteOfDay = minuteOfDay) }
    }

    fun onWorkEndSelected(minuteOfDay: Int?) {
        _state.update { it.copy(workEndMinuteOfDay = minuteOfDay) }
    }

    /** Adds an empty expense row the user then fills in through the editor. */
    fun addExpense(amount: Long, category: ExpenseCategory, description: String) {
        _state.update { current ->
            val expense = Expense(
                workRecordId = current.workRecordId,
                amount = amount,
                category = category,
                description = description,
                createdAtEpochMilli = System.currentTimeMillis(),
            )
            val expenses = current.expenses + expense
            current.copy(
                expenses = expenses,
                expenseTotalText = numberFormatter.formatToman(expenses.sumOf { it.amount }),
            )
        }
    }

    fun updateExpense(index: Int, amount: Long, category: ExpenseCategory, description: String) {
        _state.update { current ->
            if (index !in current.expenses.indices) return@update current
            val updated = current.expenses.toMutableList()
            updated[index] = updated[index].copy(amount = amount, category = category, description = description)
            current.copy(
                expenses = updated,
                expenseTotalText = numberFormatter.formatToman(updated.sumOf { it.amount }),
            )
        }
    }

    fun removeExpense(index: Int) {
        _state.update { current ->
            if (index !in current.expenses.indices) return@update current
            val updated = current.expenses.toMutableList().also { it.removeAt(index) }
            current.copy(
                expenses = updated,
                expenseTotalText = numberFormatter.formatToman(updated.sumOf { it.amount }),
            )
        }
    }

    // -------------------------------------------------------------- save flow

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, errorMessage = null) }
            val current = _state.value
            val draft = current.toDraft()
            val result = saveWorkRecord.invoke(draft)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, saved = true) }
                },
                onFailure = { error ->
                    val metraError = error.metraError
                    // Route the message to the project field by its typed field
                    // marker. Matching on the message text would break as soon
                    // as the string is reworded or translated.
                    val isProjectError = metraError is MetraError.Validation &&
                        metraError.field == MetraError.Validation.Field.PROJECT_NAME
                    _state.update {
                        it.copy(
                            saving = false,
                            errorMessage = metraError.userMessage,
                            fieldErrors = if (isProjectError) {
                                mapOf(KEY_PROJECT to metraError.userMessage)
                            } else {
                                emptyMap()
                            },
                        )
                    }
                },
            )
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }

    // --------------------------------------------------------------- internal

    /** Re-reads the applicable rule when the date changes. */
    private suspend fun refreshRuleForCurrentDate() {
        val rule = paymentRuleRepository.applicableRuleOn(_state.value.dateEpochDay)
        _state.update {
            it.copy(
                thresholdMeters = rule.thresholdMeters,
                ratePerMeter = rule.ratePerMeter,
            )
        }
        recompute()
    }

    /** Recomputes the Metra calculation and its formatted presentation. */
    private fun recompute() {
        _state.update { current ->
            val rule = PaymentRule(
                thresholdMeters = current.thresholdMeters,
                ratePerMeter = current.ratePerMeter,
                effectiveFromEpochDay = current.dateEpochDay,
                createdAtEpochMilli = 0L,
            )
            val calculation = calculateMeterPayment.invoke(current.dailyMeters.toIntOrNull(), rule)
            current.copy(
                calculation = calculation,
                calculationUi = describe(calculation),
                expenseTotalText = numberFormatter.formatToman(current.expenses.sumOf { it.amount }),
            )
        }
    }

    private fun describe(calculation: MeterCalculation): CalculationUi {
        val explainsNoPayment = when {
            calculation.dailyMeters < calculation.thresholdMeters ->
                strings.string(R.string.calc_below_threshold)

            calculation.dailyMeters == calculation.thresholdMeters ->
                strings.string(R.string.calc_at_threshold)

            else -> null
        }
        return CalculationUi(
            workedText = strings.string(
                R.string.calc_meters,
                numberFormatter.formatPersian(calculation.dailyMeters.toLong()),
            ),
            thresholdText = strings.string(
                R.string.calc_threshold,
                numberFormatter.formatPersian(calculation.thresholdMeters.toLong()),
            ),
            additionalText = strings.string(
                R.string.calc_additional,
                numberFormatter.formatPersian(calculation.additionalMeters.toLong()),
            ),
            rateText = strings.string(
                R.string.calc_rate,
                numberFormatter.formatPersian(calculation.ratePerMeter),
            ),
            paymentText = strings.string(
                R.string.calc_additional_payment,
                numberFormatter.formatPersian(calculation.additionalPayment),
            ),
            explainsNoPayment = explainsNoPayment,
        )
    }

    private fun describePrefill(source: PrefillSource, projectName: String): String? = when (source) {
        is PrefillSource.None -> null
        is PrefillSource.PreviousWorkday ->
            strings.string(R.string.prefill_from_previous_day, dateFormatter.formatLong(source.epochDay))

        is PrefillSource.ProjectDefaults -> strings.string(R.string.prefill_from_project, source.projectName)
        is PrefillSource.Both ->
            strings.string(
                R.string.prefill_from_project_and_day,
                source.projectName,
                dateFormatter.formatLong(source.epochDay),
            )
    }

    private fun WorkEditorUiState.toDraft(): WorkDraft = WorkDraft(
        id = workRecordId.takeIf { it != 0L },
        projectId = projectId,
        workDateEpochDay = dateEpochDay,
        projectName = projectName,
        workArea = workArea,
        employer = employer,
        supervisor = supervisor,
        workerCount = workerCount.toIntOrNull(),
        dailyMeters = dailyMeters.toIntOrNull(),
        expenses = expenses,
        notes = notes,
        workStartMinuteOfDay = workStartMinuteOfDay,
        workEndMinuteOfDay = workEndMinuteOfDay,
    )

    companion object {
        const val KEY_PROJECT = "projectName"
        const val KEY_WORK_AREA = "workArea"
        const val KEY_EMPLOYER = "employer"
        const val KEY_SUPERVISOR = "supervisor"
        const val KEY_WORKERS = "workerCount"
        const val KEY_METERS = "dailyMeters"

        const val KEY_NOTES = "notes"
    }
}
