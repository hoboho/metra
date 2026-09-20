package ir.metra.app.core.date

/**
 * An inclusive day range expressed in epoch days.
 *
 * Inclusive on both ends because workdays are whole days: a record dated on
 * [end] belongs to the range.
 */
data class DateRange(val start: Long, val end: Long) {
    init {
        require(start <= end) { "start ($start) must be <= end ($end)" }
    }

    val lengthInDays: Long get() = end - start + 1

    fun contains(epochDay: Long): Boolean = epochDay in start..end

    operator fun contains(range: DateRange): Boolean = range.start >= start && range.end <= end
}

/** Statistics/report periods offered in the UI. */
enum class StatisticsPeriod {
    CURRENT_WEEK,
    CURRENT_MONTH,
    PREVIOUS_MONTH,
    CURRENT_YEAR,
    PREVIOUS_YEAR,
    CUSTOM,
}

/**
 * Resolves a [StatisticsPeriod] into a concrete [DateRange].
 *
 * Pure and injectable so period boundaries — especially the month and year
 * boundaries that differ between the Gregorian and Jalali calendars — are
 * covered by unit tests.
 */
class DateRangeResolver {

    fun resolve(period: StatisticsPeriod, todayEpochDay: Long, custom: DateRange? = null): DateRange =
        when (period) {
            StatisticsPeriod.CURRENT_WEEK ->
                DateRange(JalaliCalendar.startOfWeek(todayEpochDay), JalaliCalendar.endOfWeek(todayEpochDay))

            StatisticsPeriod.CURRENT_MONTH ->
                DateRange(
                    JalaliCalendar.firstDayOfJalaliMonth(todayEpochDay),
                    JalaliCalendar.lastDayOfJalaliMonth(todayEpochDay),
                )

            StatisticsPeriod.PREVIOUS_MONTH -> {
                val shifted = JalaliCalendar.plusJalaliMonths(todayEpochDay, -1)
                DateRange(
                    JalaliCalendar.firstDayOfJalaliMonth(shifted),
                    JalaliCalendar.lastDayOfJalaliMonth(shifted),
                )
            }

            StatisticsPeriod.CURRENT_YEAR ->
                DateRange(
                    JalaliCalendar.firstDayOfJalaliYear(todayEpochDay),
                    JalaliCalendar.lastDayOfJalaliYear(todayEpochDay),
                )

            StatisticsPeriod.PREVIOUS_YEAR -> {
                val jalali = JalaliCalendar.toJalali(todayEpochDay)
                val previousYear = jalali.year - 1
                DateRange(
                    JalaliCalendar.toEpochDay(JalaliDate(previousYear, 1, 1)),
                    JalaliCalendar.toEpochDay(
                        JalaliDate(previousYear, 12, JalaliDate.daysInMonth(previousYear, 12)),
                    ),
                )
            }

            StatisticsPeriod.CUSTOM ->
                requireNotNull(custom) { "CUSTOM period requires an explicit range" }
        }

    /** Jalali months of [year], as epoch-day ranges, index 0 = Farvardin. */
    fun jalaliMonthsOf(year: Int): List<DateRange> = (1..12).map { month ->
        val start = JalaliCalendar.toEpochDay(JalaliDate(year, month, 1))
        val end = JalaliCalendar.toEpochDay(JalaliDate(year, month, JalaliDate.daysInMonth(year, month)))
        DateRange(start, end)
    }

    /** The two Jalali months to compare for "last month vs this month". */
    fun monthComparisonPair(todayEpochDay: Long): Pair<DateRange, DateRange> {
        val previous = resolve(StatisticsPeriod.PREVIOUS_MONTH, todayEpochDay)
        val current = resolve(StatisticsPeriod.CURRENT_MONTH, todayEpochDay)
        return previous to current
    }
}
