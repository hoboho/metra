package ir.metra.app.feature.reports

import android.app.Application
import android.net.Uri
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.StatisticsPeriod
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.share.FileSharer
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.ReportType
import ir.metra.app.domain.report.ReportFileWriter
import ir.metra.app.domain.report.ReportRequest
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReportsUiState(
    val reportType: ReportType = ReportType.MONTHLY,
    val startEpochDay: Long = 0L,
    val endEpochDay: Long = 0L,
    val startLabel: String = "",
    val endLabel: String = "",
    val projects: List<Project> = emptyList(),
    val selectedProjectId: Long? = null,
    val includeDailyTable: Boolean = true,
    val includeNotes: Boolean = true,
    val includeProjectBreakdown: Boolean = true,
    val includeExpenseBreakdown: Boolean = true,
    val totals: WorkTotals? = null,
    val totalsUi: TotalsUi = TotalsUi.EMPTY,
    val working: Boolean = false,
    val generatedFileName: String? = null,
    val message: String? = null,
) {
    data class TotalsUi(
        val workdays: String = "",
        val meters: String = "",
        val average: String = "",
        val additionalMeters: String = "",
        val additionalPayment: String = "",
        val recordedIncome: String = "",
        val expenses: String = "",

    ) {
        companion object {
            val EMPTY = TotalsUi()
        }
    }
}

/**
 * Report generation and sharing.
 *
 * PDF/CSV writing happens on the IO dispatcher, then the produced file is handed
 * to [FileSharer], which uses the platform share sheet — WhatsApp, Telegram,
 * Gmail and Drive appear there without any per-app integration.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val reportFileWriter: ReportFileWriter,
    private val fileSharer: FileSharer,
    private val statisticsRepository: WorkStatisticsRepository,
    private val projectRepository: ProjectRepository,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val today = JalaliCalendar.today().toEpochDay()

    private val _state = MutableStateFlow(
        ReportsUiState(
            startEpochDay = JalaliCalendar.firstDayOfJalaliMonth(today),
            endEpochDay = JalaliCalendar.lastDayOfJalaliMonth(today),
            startLabel = dateFormatter.format(JalaliCalendar.firstDayOfJalaliMonth(today)),
            endLabel = dateFormatter.format(JalaliCalendar.lastDayOfJalaliMonth(today)),
        ),
    )
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeActiveProjects().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        refreshTotals()
    }

    fun onReportTypeChange(type: ReportType) {
        viewModelScope.launch {
            val (start, end) = defaultRangeFor(type)
            _state.update {
                it.copy(
                    reportType = type,
                    startEpochDay = start,
                    endEpochDay = end,
                    startLabel = dateFormatter.format(start),
                    endLabel = dateFormatter.format(end),
                    selectedProjectId = if (type == ReportType.PROJECT) it.selectedProjectId else null,
                )
            }
            refreshTotals()
        }
    }

    fun onDateRangeChange(startEpochDay: Long, endEpochDay: Long) {
        val normalisedStart = minOf(startEpochDay, endEpochDay)
        val normalisedEnd = maxOf(startEpochDay, endEpochDay)
        _state.update {
            it.copy(
                startEpochDay = normalisedStart,
                endEpochDay = normalisedEnd,
                startLabel = dateFormatter.format(normalisedStart),
                endLabel = dateFormatter.format(normalisedEnd),
            )
        }
        refreshTotals()
    }

    fun onProjectSelected(projectId: Long?) {
        _state.update { it.copy(selectedProjectId = projectId) }
        refreshTotals()
    }

    fun onOptionChange(transform: (ReportsUiState) -> ReportsUiState) {
        _state.update(transform)
    }

    /** Suggested file name for the save-to-file picker. */
    fun suggestedPdfName(): String =
        "metra-${JalaliCalendar.today().toEpochDay()}.pdf"

    /**
     * Builds the PDF and copies it into [target], a content Uri returned by the
     * system file picker.
     *
     * Going through `CreateDocument` rather than writing to Downloads directly
     * means no storage permission is needed and the user chooses the folder, so
     * the file lands wherever they can actually find it again.
     */
    fun savePdfTo(target: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val request = currentRequest()
            val outcome = withContext(Dispatchers.IO) {
                val generated = reportFileWriter.writePdf(
                    request = request,
                    includeDailyTable = _state.value.includeDailyTable,
                    includeNotes = _state.value.includeNotes,
                )
                generated.fold(
                    onSuccess = { file ->
                        runCatching {
                            appContext.contentResolver.openOutputStream(target)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            } ?: error("no output stream")
                        }.map { file.name }
                    },
                    onFailure = { error -> Result.failure<String>(error) },
                )
            }
            _state.update {
                it.copy(
                    working = false,
                    message = outcome.fold(
                        onSuccess = { strings.string(R.string.msg_report_saved) },
                        onFailure = { error -> error.metraError.userMessage },
                    ),
                    generatedFileName = outcome.getOrNull() ?: it.generatedFileName,
                )
            }
        }
    }

    /** Builds the PDF and opens the system share sheet. */
    fun generateAndSharePdf() {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val request = currentRequest()
            val result = withContext(Dispatchers.IO) {
                reportFileWriter.writePdf(
                    request = request,
                    includeDailyTable = _state.value.includeDailyTable,
                    includeNotes = _state.value.includeNotes,
                )
            }
            result.fold(
                onSuccess = { file ->
                    val shareResult = fileSharer.share(
                        file = file,
                        subject = strings.string(R.string.share_subject_report),
                        mimeType = FileSharer.MIME_PDF,
                    )
                    _state.update {
                        it.copy(
                            working = false,
                            generatedFileName = file.name,
                            message = shareResult.fold(
                                onSuccess = { strings.string(R.string.msg_report_ready) },
                                onFailure = { error -> error.metraError.userMessage },
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(working = false, message = error.metraError.userMessage) }
                },
            )
        }
    }

    /** CSV export with Persian headers and a UTF-8 BOM for Excel. */
    fun generateAndShareCsv() {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val result = withContext(Dispatchers.IO) { reportFileWriter.writeCsv(currentRequest()) }
            result.fold(
                onSuccess = { file ->
                    fileSharer.share(file, strings.string(R.string.share_subject_csv), FileSharer.MIME_CSV)
                    _state.update {
                        it.copy(working = false, generatedFileName = file.name, message = strings.string(R.string.msg_csv_ready))
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(working = false, message = error.metraError.userMessage) }
                },
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun currentRequest(): ReportRequest {
        val current = _state.value
        return ReportRequest(
            type = current.reportType,
            startEpochDay = current.startEpochDay,
            endEpochDay = current.endEpochDay,
            projectId = if (current.reportType == ReportType.PROJECT) current.selectedProjectId else null,
            includeDailyTable = current.includeDailyTable,
            includeNotes = current.includeNotes,
            includeProjectBreakdown = current.includeProjectBreakdown,
            includeExpenseBreakdown = current.includeExpenseBreakdown,
        )
    }

    private suspend fun defaultRangeFor(type: ReportType): Pair<Long, Long> = when (type) {
        ReportType.DAILY -> today to today
        ReportType.MONTHLY ->
            JalaliCalendar.firstDayOfJalaliMonth(today) to JalaliCalendar.lastDayOfJalaliMonth(today)

        ReportType.RANGE ->
            JalaliCalendar.firstDayOfJalaliMonth(today) to JalaliCalendar.lastDayOfJalaliMonth(today)

        ReportType.PROJECT ->
            JalaliCalendar.firstDayOfJalaliMonth(today) to JalaliCalendar.lastDayOfJalaliMonth(today)

        ReportType.YEARLY ->
            JalaliCalendar.firstDayOfJalaliYear(today) to JalaliCalendar.lastDayOfJalaliYear(today)
    }

    private fun refreshTotals() {
        viewModelScope.launch {
            val current = _state.value
            val totals = statisticsRepository.getTotals(
                startEpochDay = current.startEpochDay,
                endEpochDay = current.endEpochDay,
                projectId = if (current.reportType == ReportType.PROJECT) current.selectedProjectId else null,
            )
            _state.update { it.copy(totals = totals, totalsUi = totals.toUi()) }
        }
    }

    private fun WorkTotals.toUi(): ReportsUiState.TotalsUi = ReportsUiState.TotalsUi(
        workdays = numberFormatter.formatPersian(workdays.toLong()),
        meters = numberFormatter.formatMetersValue(totalMeters),
        average = numberFormatter.formatAverage(averageMetersPerDay),
        additionalMeters = numberFormatter.formatMetersValue(totalAdditionalMeters),
        additionalPayment = numberFormatter.formatToman(totalAdditionalPayment),
        recordedIncome = numberFormatter.formatToman(totalReceivable),
        expenses = numberFormatter.formatToman(totalExpenses),

    )
}
