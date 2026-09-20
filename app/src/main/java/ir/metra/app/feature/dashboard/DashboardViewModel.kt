package ir.metra.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.LedgerRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable dashboard state.
 *
 * Figures are pre-formatted strings rather than raw numbers: the screen only
 * renders, which keeps recomposition cheap and guarantees the same Persian
 * formatting everywhere.
 */
data class DashboardUiState(
    val userName: String = "",
    val todayLabel: String = "",
    val monthLabel: String = "",
    val todayRecord: TodaySummary? = null,
    val monthTotals: MonthSummary = MonthSummary.EMPTY,
    val bestDay: BestDay? = null,
    val loading: Boolean = true,
)

data class TodaySummary(
    val metersText: String,
    val additionalMetersText: String,
    val additionalPaymentText: String,
    val expenseText: String,
    val projectName: String,
    val workRecordId: Long,
)

data class MonthSummary(
    val workdaysText: String = "",
    val totalMetersText: String = "",
    val averageMetersText: String = "",
    val additionalMetersText: String = "",
    val additionalPaymentText: String = "",

    val totalExpensesText: String = "",
    val totalReceivableText: String = "",
    val collectedText: String = "",
    val outstandingText: String = "",
) {
    companion object {
        val EMPTY = MonthSummary()
    }
}

data class BestDay(
    val dateLabel: String,
    val metersText: String,
    val additionalPaymentText: String,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val statisticsRepository: WorkStatisticsRepository,
    private val ledgerRepository: LedgerRepository,
    private val userRepository: UserRepository,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
) : ViewModel() {

    private val todayEpochDay = JalaliCalendar.today().toEpochDay()
    private val monthStart = JalaliCalendar.firstDayOfJalaliMonth(todayEpochDay)
    private val monthEnd = JalaliCalendar.lastDayOfJalaliMonth(todayEpochDay)

    private val _state = MutableStateFlow(
        DashboardUiState(
            todayLabel = dateFormatter.formatLongWithWeekday(todayEpochDay),
            monthLabel = dateFormatter.formatMonth(todayEpochDay),
        ),
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        observeProfile()
        observeMonth()
        observeToday()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            userRepository.observeProfile().collect { profile ->
                _state.update { it.copy(userName = profile.fullName) }
            }
        }
    }

    /**
     * Month totals come straight from the SQL aggregate, not from a full scan.
     *
     * Combined with the ledger so the dashboard can show what has actually been
     * collected this month and what the company still holds. Both streams are
     * derived, so neither can drift from the other.
     */
    private fun observeMonth() {
        viewModelScope.launch {
            combine(
                statisticsRepository.observeTotals(monthStart, monthEnd),
                ledgerRepository.observeSummaryBetween(monthStart, monthEnd),
            ) { totals, ledger ->
                totals to ledger
            }.collect { (totals, ledger) ->
                _state.update {
                    it.copy(
                        monthTotals = totals.toUi().copy(
                            collectedText = numberFormatter.formatToman(ledger.netCollected),
                            outstandingText = numberFormatter.formatToman(ledger.outstanding),
                        ),
                        loading = false,
                    )
                }
            }
        }
    }

    private fun observeToday() {
        viewModelScope.launch {
            workRecordRepository.observeRecordsInRange(todayEpochDay, todayEpochDay).collect { records ->
                val today = records.firstOrNull()
                _state.update { it.copy(todayRecord = today?.toTodaySummary()) }
            }
        }
    }

    /** Best workday of the current month, by meters. */
    fun refreshBestDay() {
        viewModelScope.launch {
            val records = workRecordRepository.getRecordsInRangeAscending(monthStart, monthEnd)
            val best = records.maxByOrNull { it.dailyMeters }
            _state.update {
                it.copy(
                    bestDay = best?.let { record ->
                        BestDay(
                            dateLabel = dateFormatter.formatLong(record.workDateEpochDay),
                            metersText = numberFormatter.formatMeters(record.dailyMeters),
                            additionalPaymentText = numberFormatter.formatToman(record.additionalMeterPayment),
                        )
                    },
                )
            }
        }
    }

    private fun WorkTotals.toUi(): MonthSummary = MonthSummary(
        workdaysText = numberFormatter.formatPersian(workdays.toLong()),
        totalMetersText = numberFormatter.formatMetersValue(totalMeters),
        averageMetersText = numberFormatter.formatAverage(averageMetersPerDay),
        additionalMetersText = numberFormatter.formatMetersValue(totalAdditionalMeters),
        additionalPaymentText = numberFormatter.formatToman(totalAdditionalPayment),
        totalExpensesText = numberFormatter.formatToman(totalExpenses),
        totalReceivableText = numberFormatter.formatToman(totalReceivable),
    )

    private fun WorkRecord.toTodaySummary(): TodaySummary = TodaySummary(
        metersText = numberFormatter.formatMeters(dailyMeters),
        additionalMetersText = numberFormatter.formatMeters(additionalMeters),
        additionalPaymentText = numberFormatter.formatToman(additionalMeterPayment),
        expenseText = numberFormatter.formatToman(expenseTotal),
        projectName = projectName,
        workRecordId = id,
    )
}
