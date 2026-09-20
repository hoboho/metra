package ir.metra.app.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.core.date.StatisticsPeriod
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.StatisticsCalculator
import ir.metra.app.domain.WorkStatistics
import ir.metra.app.domain.usecase.CompareMonths
import ir.metra.app.domain.usecase.GetPeriodStatistics
import ir.metra.app.domain.usecase.GetYearlyOverview
import ir.metra.app.domain.usecase.MonthComparison
import ir.metra.app.domain.usecase.YearlyOverview
import ir.metra.app.ui.components.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val period: StatisticsPeriod = StatisticsPeriod.CURRENT_MONTH,
    val customRange: DateRange? = null,
    val rangeLabel: String = "",
    val statistics: WorkStatistics? = null,
    val dailyMetersChart: List<ChartPoint> = emptyList(),
    val additionalMetersChart: List<ChartPoint> = emptyList(),
    val expensesChart: List<ChartPoint> = emptyList(),
    val incomeChart: List<ChartPoint> = emptyList(),
    val kpis: KpiUi = KpiUi.EMPTY,
    val comparison: ComparisonUi? = null,
    val loading: Boolean = true,
) {
    data class KpiUi(
        val averageMeters: String = "",
        val maxMeters: String = "",
        val minMeters: String = "",
        val totalMeters: String = "",
        val totalAdditionalMeters: String = "",
        val averageAdditionalMeters: String = "",
        val totalExpenses: String = "",
        val averageExpense: String = "",
    ) {
        companion object {
            val EMPTY = KpiUi()
        }
    }

    data class ComparisonUi(
        val firstLabel: String,
        val secondLabel: String,
        val metersFirst: Double,
        val metersSecond: Double,
        val workdaysFirst: Double,
        val workdaysSecond: Double,
        val additionalFirst: Double,
        val additionalSecond: Double,
        val paymentFirst: Double,
        val paymentSecond: Double,
        val expensesFirst: Double,
        val expensesSecond: Double,
        val incomeFirst: Double,
        val incomeSecond: Double,
        val metersChange: String,
        val paymentChange: String,
        val expensesChange: String,
    )
}

/**
 * Statistics for the selected period, plus the monthly comparison.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getPeriodStatistics: GetPeriodStatistics,
    private val compareMonths: CompareMonths,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        refresh()
        loadComparison()
    }

    fun onPeriodChange(period: StatisticsPeriod) {
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun onCustomRangeChange(range: DateRange) {
        _state.update { it.copy(period = StatisticsPeriod.CUSTOM, customRange = range) }
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            val (range, statistics) = getPeriodStatistics.invoke(current.period, current.customRange)
            _state.update {
                it.copy(
                    statistics = statistics,
                    rangeLabel = dateFormatter.formatRange(range.start, range.end),
                    kpis = buildKpis(statistics),
                    loading = false,
                )
            }
            loadCharts(range)
        }
    }

    /** Daily series for the selected range, one point per recorded workday. */
    private suspend fun loadCharts(range: DateRange) {
        val daily = ArrayList<ChartPoint>()
        val additional = ArrayList<ChartPoint>()
        val expenses = ArrayList<ChartPoint>()
        val income = ArrayList<ChartPoint>()

        var offset = 0
        while (true) {
            val page = getPeriodStatistics.readPage(range, offset, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                val label = dateFormatter.formatDayMonth(record.workDateEpochDay)
                daily += ChartPoint(label, record.dailyMeters.toDouble())
                additional += ChartPoint(label, record.additionalMeters.toDouble())
                expenses += ChartPoint(label, record.expenseTotal.toDouble())
                income += ChartPoint(label, record.receivableFromCompany.toDouble())
            }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        _state.update {
            it.copy(
                dailyMetersChart = daily,
                additionalMetersChart = additional,
                expensesChart = expenses,
                incomeChart = income,
            )
        }
    }

    private fun buildKpis(statistics: WorkStatistics): StatisticsUiState.KpiUi = StatisticsUiState.KpiUi(
        averageMeters = numberFormatter.formatAverage(statistics.averageMetersPerDay),
        maxMeters = numberFormatter.formatMetersValue(statistics.maxMetersInDay),
        minMeters = numberFormatter.formatMetersValue(statistics.minMetersInDay),
        totalMeters = numberFormatter.formatMetersValue(statistics.totals.totalMeters),
        totalAdditionalMeters = numberFormatter.formatMetersValue(statistics.totals.totalAdditionalMeters),
        averageAdditionalMeters = numberFormatter.formatAverage(statistics.averageAdditionalMetersPerDay),
        totalExpenses = numberFormatter.formatToman(statistics.totals.totalExpenses),
        averageExpense = numberFormatter.formatAverage(statistics.averageExpensePerDay),
    )

    private fun loadComparison() {
        viewModelScope.launch {
            val (first, second) = compareMonths.invokeDefault()
            _state.update { it.copy(comparison = buildComparison(first, second)) }
        }
    }

    private fun buildComparison(first: MonthComparison, second: MonthComparison): StatisticsUiState.ComparisonUi =
        StatisticsUiState.ComparisonUi(
            firstLabel = first.label,
            secondLabel = second.label,
            metersFirst = first.totals.totalMeters.toDouble(),
            metersSecond = second.totals.totalMeters.toDouble(),
            workdaysFirst = first.totals.workdays.toDouble(),
            workdaysSecond = second.totals.workdays.toDouble(),
            additionalFirst = first.totals.totalAdditionalMeters.toDouble(),
            additionalSecond = second.totals.totalAdditionalMeters.toDouble(),
            paymentFirst = first.totals.totalAdditionalPayment.toDouble(),
            paymentSecond = second.totals.totalAdditionalPayment.toDouble(),
            expensesFirst = first.totals.totalExpenses.toDouble(),
            expensesSecond = second.totals.totalExpenses.toDouble(),
            incomeFirst = first.totals.totalReceivable.toDouble(),
            incomeSecond = second.totals.totalReceivable.toDouble(),
            metersChange = numberFormatter.formatSignedPercent(
                StatisticsCalculator.percentageChange(
                    first.totals.totalMeters.toDouble(),
                    second.totals.totalMeters.toDouble(),
                ),
            ),
            paymentChange = numberFormatter.formatSignedPercent(
                StatisticsCalculator.percentageChange(
                    first.totals.totalAdditionalPayment.toDouble(),
                    second.totals.totalAdditionalPayment.toDouble(),
                ),
            ),
            expensesChange = numberFormatter.formatSignedPercent(
                StatisticsCalculator.percentageChange(
                    first.totals.totalExpenses.toDouble(),
                    second.totals.totalExpenses.toDouble(),
                ),
            ),
        )

    private companion object {
        const val PAGE_SIZE = 500
    }
}

data class YearlyOverviewUiState(
    val year: Int = JalaliCalendar.todayJalali().year,
    val overview: YearlyOverview? = null,
    val monthlyMetersChart: List<ChartPoint> = emptyList(),
    val monthlyPaymentChart: List<ChartPoint> = emptyList(),
    val monthlyExpenseChart: List<ChartPoint> = emptyList(),
    val rows: List<YearRow> = emptyList(),
    val totalsText: List<Pair<String, String>> = emptyList(),
    val loading: Boolean = true,
)

data class YearRow(
    val monthLabel: String,
    val workdays: String,
    val meters: String,
    val additionalMeters: String,
    val additionalPayment: String,
    val expenses: String,
    val recordedIncome: String,
)

/**
 * Yearly overview: twelve Jalali months, totals, and monthly charts.
 */
@HiltViewModel
class YearlyOverviewViewModel @Inject constructor(
    private val getYearlyOverview: GetYearlyOverview,
    private val numberFormatter: NumberFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(YearlyOverviewUiState())
    val state: StateFlow<YearlyOverviewUiState> = _state.asStateFlow()

    init {
        load(_state.value.year)
    }

    fun onYearChange(year: Int) {
        _state.update { it.copy(year = year) }
        load(year)
    }

    private fun load(year: Int) {
        viewModelScope.launch {
            val overview = getYearlyOverview.invoke(year)
            val meters = overview.months.map { bucket ->
                ChartPoint(shortMonthLabel(bucket.monthStartEpochDay), bucket.totals.totalMeters.toDouble())
            }
            val payments = overview.months.map { bucket ->
                ChartPoint(shortMonthLabel(bucket.monthStartEpochDay), bucket.totals.totalAdditionalPayment.toDouble())
            }
            val expenses = overview.months.map { bucket ->
                ChartPoint(shortMonthLabel(bucket.monthStartEpochDay), bucket.totals.totalExpenses.toDouble())
            }
            val rows = overview.months.map { bucket ->
                YearRow(
                    monthLabel = JalaliDate.monthName(JalaliCalendar.toJalali(bucket.monthStartEpochDay).month),
                    workdays = numberFormatter.formatPersian(bucket.totals.workdays.toLong()),
                    meters = numberFormatter.formatMetersValue(bucket.totals.totalMeters),
                    additionalMeters = numberFormatter.formatMetersValue(bucket.totals.totalAdditionalMeters),
                    additionalPayment = numberFormatter.formatToman(bucket.totals.totalAdditionalPayment),
                    expenses = numberFormatter.formatToman(bucket.totals.totalExpenses),
                    recordedIncome = numberFormatter.formatToman(bucket.totals.totalReceivable),
                )
            }
            _state.update {
                it.copy(
                    overview = overview,
                    monthlyMetersChart = meters,
                    monthlyPaymentChart = payments,
                    monthlyExpenseChart = expenses,
                    rows = rows,
                    totalsText = listOf(
                        strings.string(R.string.label_workdays) to
                            numberFormatter.formatPersian(overview.totals.workdays.toLong()),
                        strings.string(R.string.label_total_meters) to
                            strings.string(
                                R.string.unit_meters_suffix,
                                numberFormatter.formatMetersValue(overview.totals.totalMeters),
                            ),
                        strings.string(R.string.label_total_additional) to
                            strings.string(
                                R.string.unit_meters_suffix,
                                numberFormatter.formatMetersValue(overview.totals.totalAdditionalMeters),
                            ),
                        strings.string(R.string.label_total_additional_payment) to
                            numberFormatter.formatToman(overview.totals.totalAdditionalPayment),
                        strings.string(R.string.label_total_expenses) to
                            numberFormatter.formatToman(overview.totals.totalExpenses),
                        strings.string(R.string.label_total_recorded_income) to
                            numberFormatter.formatToman(overview.totals.totalReceivable),
                    ),
                    loading = false,
                )
            }
        }
    }

    private fun shortMonthLabel(epochDay: Long): String {
        val jalali = JalaliCalendar.toJalali(epochDay)
        return JalaliDate.monthName(jalali.month).take(4)
    }
}
