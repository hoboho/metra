package ir.metra.app.domain

import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate

/**
 * Grouping of work records by Jalali month.
 *
 * Reports and the yearly overview need month buckets that follow the Persian
 * calendar, so the bucket key is a Jalali (year, month) pair rather than a
 * Gregorian one.
 */
data class JalaliMonthKey(val year: Int, val month: Int) : Comparable<JalaliMonthKey> {
    override fun compareTo(other: JalaliMonthKey): Int = when {
        year != other.year -> year.compareTo(other.year)
        else -> month.compareTo(other.month)
    }

    fun label(): String = JalaliDate.monthLabel(year, month)
}

data class MonthAggregation(
    val key: JalaliMonthKey,
    val range: DateRange,
    val totals: WorkTotals,
) {
    val label: String get() = key.label()
}

object MonthAggregator {

    /**
     * Buckets [records] by Jalali month.
     *
     * @param includeEmptyMonths when true, every month of the covered years is
     *   returned (with zero totals where nothing was recorded) so charts keep a
     *   stable, continuous axis.
     */
    fun aggregate(
        records: List<ir.metra.app.domain.model.WorkRecord>,
        includeEmptyMonths: Boolean = false,
    ): List<MonthAggregation> {
        val grouped = records
            .groupBy { record ->
                val jalali = JalaliCalendar.toJalali(record.workDateEpochDay)
                JalaliMonthKey(jalali.year, jalali.month)
            }

        val keys: List<JalaliMonthKey> = if (includeEmptyMonths) {
            val years = grouped.keys.map { it.year }
            if (years.isEmpty()) {
                emptyList()
            } else {
                (years.min()..years.max()).flatMap { year ->
                    (1..12).map { month -> JalaliMonthKey(year, month) }
                }
            }
        } else {
            grouped.keys.sorted()
        }

        return keys.map { key ->
            val monthRecords = grouped[key].orEmpty()
            val range = if (monthRecords.isNotEmpty()) {
                DateRange(
                    monthRecords.minOf { it.workDateEpochDay },
                    monthRecords.maxOf { it.workDateEpochDay },
                )
            } else {
                val start = JalaliCalendar.toEpochDay(JalaliDate(key.year, key.month, 1))
                val end = JalaliCalendar.toEpochDay(
                    JalaliDate(key.year, key.month, JalaliDate.daysInMonth(key.year, key.month)),
                )
                DateRange(start, end)
            }
            MonthAggregation(key, range, WorkTotals.of(monthRecords))
        }
    }
}
