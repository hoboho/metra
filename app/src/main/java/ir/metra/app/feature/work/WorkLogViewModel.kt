package ir.metra.app.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.metraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.DateRangeResolver
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.StatisticsPeriod
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkRecordFilter
import ir.metra.app.domain.repository.WorkRecordSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One rendered row in the work log. */
data class WorkLogRow(
    val id: Long,
    val dateLabel: String,
    val weekdayLabel: String,
    val projectName: String,
    val workArea: String,
    val employer: String,
    val metersText: String,
    val additionalMetersText: String,
    val additionalPaymentText: String,
    val expenseText: String,
    val hasAdditional: Boolean,
    val hasExpenses: Boolean,
    val notesPreview: String,
)

/** A day cell for the calendar view. */
data class CalendarDay(
    val epochDay: Long,
    val dayOfMonth: Int,
    val recordId: Long?,
    val meters: Int,
    val isToday: Boolean,
)

data class WorkLogUiState(
    val rows: List<WorkLogRow> = emptyList(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val calendarMonthLabel: String = "",
    val calendarLeadingBlanks: Int = 0,
    val filter: WorkRecordFilter? = null,
    val sort: WorkRecordSort = WorkRecordSort.DATE_DESC,
    val listMode: Boolean = true,
    val searching: Boolean = false,
    val query: String = "",
    val activeFilterCount: Int = 0,
    val projectNames: Map<Long, String> = emptyMap(),
    val employers: List<String> = emptyList(),
    val supervisors: List<String> = emptyList(),
    val workAreas: List<String> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

/**
 * The work log: search, filter, sort, and two presentations of the same data.
 *
 * The filter sheet and the list share one [WorkRecordFilter] that is turned into
 * a single SQL query, so filtering never loads the whole table into memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkLogViewModel @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val projectRepository: ProjectRepository,
    private val dateRangeResolver: DateRangeResolver,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val todayEpochDay = JalaliCalendar.today().toEpochDay()
    private var calendarAnchorEpochDay = todayEpochDay

    private val filterFlow = MutableStateFlow(defaultFilter())
    private val sortFlow = MutableStateFlow(WorkRecordSort.DATE_DESC)
    private val queryFlow = MutableStateFlow("")
    private val listModeFlow = MutableStateFlow(true)
    private val calendarFlow = MutableStateFlow(todayEpochDay)

    private val _state = MutableStateFlow(WorkLogUiState())
    val state: StateFlow<WorkLogUiState> = _state.asStateFlow()

    init {
        observeLookups()
        observeList()
        observeCalendar()
    }

    private fun defaultFilter(): WorkRecordFilter {
        val range = dateRangeResolver.resolve(StatisticsPeriod.CURRENT_MONTH, todayEpochDay)
        // A wide default window: the log shows everything until filtered down.
        return WorkRecordFilter(fromEpochDay = range.start - 3650, toEpochDay = range.end + 365)
    }

    private fun observeLookups() {
        viewModelScope.launch {
            combine(
                projectRepository.observeProjects(),
                workRecordRepository.observeDistinctEmployers(),
                workRecordRepository.observeDistinctSupervisors(),
                workRecordRepository.observeDistinctWorkAreas(),
            ) { projects, employers, supervisors, areas ->
                LookupData(
                    projectNames = projects.associate { it.id to it.name },
                    employers = employers,
                    supervisors = supervisors,
                    workAreas = areas,
                )
            }.collect { lookups ->
                _state.update {
                    it.copy(
                        projectNames = lookups.projectNames,
                        employers = lookups.employers,
                        supervisors = lookups.supervisors,
                        workAreas = lookups.workAreas,
                    )
                }
            }
        }
    }

    private fun observeList() {
        viewModelScope.launch {
            combine(filterFlow, sortFlow, queryFlow, listModeFlow) { filter, sort, query, listMode ->
                ListInputs(filter, sort, query, listMode)
            }.flatMapLatest { inputs ->
                if (inputs.query.isNotBlank()) {
                    workRecordRepository.observeSearch(inputs.query)
                } else {
                    workRecordRepository.observeFiltered(inputs.filter, inputs.sort)
                }
            }.collect { records ->
                _state.update { current ->
                    current.copy(rows = records.map { it.toRow() }, loading = false)
                }
            }
        }
    }

    private fun observeCalendar() {
        viewModelScope.launch {
            calendarFlow.collect { anchor ->
                val start = JalaliCalendar.firstDayOfJalaliMonth(anchor)
                val end = JalaliCalendar.lastDayOfJalaliMonth(anchor)
                workRecordRepository.observeRecordsInRange(start, end).collect { records ->
                    buildCalendar(anchor, start, end, records)
                }
            }
        }
    }

    private fun buildCalendar(anchor: Long, start: Long, end: Long, records: List<WorkRecord>) {
        val byDay = records.associateBy { it.workDateEpochDay }
        val jalali = JalaliCalendar.toJalali(start)
        val daysInMonth = JalaliCalendar.daysInCurrentJalaliMonth(anchor)
        val leading = JalaliCalendar.dayOfWeek(start)
        val days = (1..daysInMonth).map { day ->
            val epochDay = start + (day - 1)
            val record = byDay[epochDay]
            CalendarDay(
                epochDay = epochDay,
                dayOfMonth = day,
                recordId = record?.id,
                meters = record?.dailyMeters ?: 0,
                isToday = epochDay == todayEpochDay,
            )
        }
        _state.update {
            it.copy(
                calendarDays = days,
                calendarLeadingBlanks = leading,
                calendarMonthLabel = dateFormatter.formatMonth(anchor),
            )
        }
    }

    // ------------------------------------------------------------ user input

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, searching = query.isNotBlank()) }
        queryFlow.value = query
    }

    fun onSortChange(sort: WorkRecordSort) {
        sortFlow.value = sort
        _state.update { it.copy(sort = sort) }
    }

    fun onToggleListMode() {
        listModeFlow.value = !listModeFlow.value
        _state.update { it.copy(listMode = listModeFlow.value) }
    }

    fun onApplyFilter(filter: WorkRecordFilter) {
        filterFlow.value = filter
        _state.update {
            it.copy(
                filter = filter,
                activeFilterCount = countActiveConstraints(filter),
            )
        }
    }

    fun onClearFilters() {
        filterFlow.value = defaultFilter()
        queryFlow.value = ""
        _state.update {
            it.copy(filter = null, activeFilterCount = 0, query = "", searching = false)
        }
    }

    fun onCalendarMonthShift(months: Int) {
        calendarAnchorEpochDay = JalaliCalendar.plusJalaliMonths(calendarAnchorEpochDay, months)
        calendarFlow.value = calendarAnchorEpochDay
    }

    fun deleteRecord(workRecordId: Long) {
        viewModelScope.launch {
            val result = workRecordRepository.softDelete(workRecordId)
            result.fold(
                onSuccess = { _state.update { it.copy(message = strings.string(R.string.msg_record_deleted)) } },
                onFailure = { error -> _state.update { it.copy(message = error.metraError.userMessage) } },
            )
        }
    }

    fun duplicateRecord(workRecordId: Long) {
        viewModelScope.launch {
            val result = workRecordRepository.duplicateAsNewDay(workRecordId, todayEpochDay)
            result.fold(
                onSuccess = { _state.update { it.copy(message = strings.string(R.string.msg_record_duplicated)) } },
                onFailure = { error -> _state.update { it.copy(message = error.metraError.userMessage) } },
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun countActiveConstraints(filter: WorkRecordFilter): Int {
        var count = 0
        if (filter.projectId != null) count++
        if (!filter.employer.isNullOrBlank()) count++
        if (!filter.supervisor.isNullOrBlank()) count++
        if (!filter.workArea.isNullOrBlank()) count++
        if (filter.minMeters != null) count++
        if (filter.maxMeters != null) count++
        if (filter.onlyWithAdditionalMeters) count++
        if (filter.onlyWithExpenses) count++
        return count
    }

    private fun WorkRecord.toRow(): WorkLogRow = WorkLogRow(
        id = id,
        dateLabel = dateFormatter.formatLong(workDateEpochDay),
        weekdayLabel = JalaliCalendar.weekdayName(workDateEpochDay),
        projectName = projectName,
        workArea = workArea,
        employer = employer,
        metersText = numberFormatter.formatMetersValue(dailyMeters),
        additionalMetersText = numberFormatter.formatMetersValue(additionalMeters),
        additionalPaymentText = numberFormatter.formatToman(additionalMeterPayment),
        expenseText = numberFormatter.formatToman(expenseTotal),
        hasAdditional = additionalMeters > 0,
        hasExpenses = expenseTotal > 0,
        notesPreview = notes.take(60),
    )

    private data class LookupData(
        val projectNames: Map<Long, String>,
        val employers: List<String>,
        val supervisors: List<String>,
        val workAreas: List<String>,
    )

    private data class ListInputs(
        val filter: WorkRecordFilter,
        val sort: WorkRecordSort,
        val query: String,
        val listMode: Boolean,
    )
}
