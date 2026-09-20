package ir.metra.app.domain.usecase

import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.DateRangeResolver
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.StatisticsPeriod
import ir.metra.app.domain.MonthAggregator
import ir.metra.app.domain.MonthBucket
import ir.metra.app.domain.StatisticsCalculator
import ir.metra.app.domain.WorkStatistics
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics for a period.
 *
 * Records are read in bounded pages so a multi-year period does not materialise
 * the whole table; only the aggregated result is retained.
 */
@Singleton
class GetPeriodStatistics @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val statisticsRepository: WorkStatisticsRepository,
    private val dateRangeResolver: DateRangeResolver,
) {

    private val pageSize = 1_000

    suspend fun invoke(
        period: StatisticsPeriod,
        custom: DateRange? = null,
        todayEpochDay: Long = JalaliCalendar.today().toEpochDay(),
    ): Pair<DateRange, WorkStatistics> {
        val range = dateRangeResolver.resolve(period, todayEpochDay, custom)
        val records = collect(range)
        return range to StatisticsCalculator.calculate(records)
    }

    suspend fun totalsFor(range: DateRange): WorkTotals =
        statisticsRepository.getTotals(range.start, range.end)

    /**
     * Bounded page read used by chart builders.
     *
     * Callers loop until a short page comes back, which keeps peak memory flat
     * no matter how long the period is.
     */
    suspend fun readPage(range: DateRange, offset: Int, limit: Int = pageSize): List<WorkRecord> =
        workRecordRepository.getPage(range.start, range.end, limit, offset)

    private suspend fun collect(range: DateRange): List<WorkRecord> {
        val all = ArrayList<WorkRecord>()
        var offset = 0
        while (true) {
            val page = workRecordRepository.getPage(range.start, range.end, pageSize, offset)
            if (page.isEmpty()) break
            all += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return all
    }
}

/** One row of the monthly comparison view. */
data class MonthComparison(
    val range: DateRange,
    val label: String,
    val totals: WorkTotals,
)

/**
 * Side-by-side comparison of two Jalali months with percentage deltas.
 */
@Singleton
class CompareMonths @Inject constructor(
    private val statisticsRepository: WorkStatisticsRepository,
    private val dateRangeResolver: DateRangeResolver,
) {

    suspend fun invoke(
        firstRange: DateRange,
        secondRange: DateRange,
        firstLabel: String,
        secondLabel: String,
    ): Pair<MonthComparison, MonthComparison> {
        val first = statisticsRepository.getTotals(firstRange.start, firstRange.end)
        val second = statisticsRepository.getTotals(secondRange.start, secondRange.end)
        return MonthComparison(firstRange, firstLabel, first) to
            MonthComparison(secondRange, secondLabel, second)
    }

    /** The default comparison: previous month versus current month. */
    suspend fun invokeDefault(todayEpochDay: Long = JalaliCalendar.today().toEpochDay()):
        Pair<MonthComparison, MonthComparison> {
        val (previous, current) = dateRangeResolver.monthComparisonPair(todayEpochDay)
        val previousJalali = JalaliCalendar.toJalali(previous.start)
        val currentJalali = JalaliCalendar.toJalali(current.start)
        return invoke(
            previous,
            current,
            ir.metra.app.core.date.JalaliDate.monthLabel(previousJalali.year, previousJalali.month),
            ir.metra.app.core.date.JalaliDate.monthLabel(currentJalali.year, currentJalali.month),
        )
    }
}

/** Everything the yearly overview screen needs. */
data class YearlyOverview(
    val year: Int,
    val months: List<MonthBucket>,
    val totals: WorkTotals,
)

/**
 * Annual overview: twelve Jalali month buckets plus the year total.
 *
 * Empty months are included so the chart keeps a stable twelve-slot axis.
 */
@Singleton
class GetYearlyOverview @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
) {

    suspend fun invoke(year: Int): YearlyOverview {
        val start = JalaliCalendar.toEpochDay(ir.metra.app.core.date.JalaliDate(year, 1, 1))
        val end = JalaliCalendar.toEpochDay(
            ir.metra.app.core.date.JalaliDate(year, 12, ir.metra.app.core.date.JalaliDate.daysInMonth(year, 12)),
        )
        val records = ArrayList<WorkRecord>()
        var offset = 0
        while (true) {
            val page = workRecordRepository.getPage(start, end, 1_000, offset)
            if (page.isEmpty()) break
            records += page
            if (page.size < 1_000) break
            offset += 1_000
        }
        val aggregations = MonthAggregator.aggregate(records, includeEmptyMonths = true)
            .filter { it.key.year == year }
        val buckets = aggregations.map { aggregation ->
            MonthBucket(
                monthStartEpochDay = aggregation.range.start,
                totals = aggregation.totals,
                range = aggregation.range,
            )
        }
        return YearlyOverview(year = year, months = buckets, totals = WorkTotals.of(records))
    }
}
