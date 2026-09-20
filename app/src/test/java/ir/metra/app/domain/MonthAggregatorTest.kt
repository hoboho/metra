package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.domain.model.WorkRecord
import org.junit.Test

/**
 * Bucketing workdays into Jalali months, including the year-boundary case
 * (Esfand -> Farvardin) that naive Gregorian grouping would get wrong.
 */
class MonthAggregatorTest {

    private fun recordOn(jalaliYear: Int, month: Int, day: Int, meters: Int = 520): WorkRecord {
        val epochDay = JalaliCalendar.toEpochDay(JalaliDate(jalaliYear, month, day))
        val additional = PaymentCalculator.additionalMeters(meters, 400)
        return WorkRecord(
            projectName = "پروژه",
            workDateEpochDay = epochDay,
            dailyMeters = meters,
            additionalMeters = additional,
            thresholdMetersSnapshot = 400,
            ratePerMeterSnapshot = 15_000L,
            additionalMeterPayment = PaymentCalculator.additionalPayment(additional, 15_000L),
            createdAtEpochMilli = 0L,
            updatedAtEpochMilli = 0L,
        )
    }

    @Test
    fun `empty input yields no months`() {
        assertThat(MonthAggregator.aggregate(emptyList())).isEmpty()
    }

    @Test
    fun `records in the same jalali month share one bucket`() {
        val records = listOf(
            recordOn(1403, 5, 1),
            recordOn(1403, 5, 15),
            recordOn(1403, 5, 31),
        )
        val months = MonthAggregator.aggregate(records)
        assertThat(months).hasSize(1)
        assertThat(months.first().totals.workdays).isEqualTo(3)
        assertThat(months.first().totals.totalMeters).isEqualTo(1560)
    }

    @Test
    fun `records spanning esfand and farvardin land in two buckets`() {
        val records = listOf(
            recordOn(1403, 12, 29),
            recordOn(1403, 12, 30),
            recordOn(1404, 1, 1),
        )
        val months = MonthAggregator.aggregate(records)
        assertThat(months).hasSize(2)
        assertThat(months.first().key).isEqualTo(JalaliMonthKey(1403, 12))
        assertThat(months.last().key).isEqualTo(JalaliMonthKey(1404, 1))
        assertThat(months.first().totals.workdays).isEqualTo(2)
        assertThat(months.last().totals.workdays).isEqualTo(1)
    }

    @Test
    fun `months are returned in chronological order`() {
        val records = listOf(
            recordOn(1403, 7, 5),
            recordOn(1403, 2, 5),
            recordOn(1402, 11, 5),
            recordOn(1403, 12, 5),
        )
        val months = MonthAggregator.aggregate(records)
        assertThat(months.map { it.key }).isEqualTo(
            listOf(
                JalaliMonthKey(1402, 11),
                JalaliMonthKey(1403, 2),
                JalaliMonthKey(1403, 7),
                JalaliMonthKey(1403, 12),
            ),
        )
    }

    @Test
    fun `includeEmptyMonths fills every month across the covered years`() {
        val records = listOf(recordOn(1403, 1, 1), recordOn(1403, 12, 1))
        val months = MonthAggregator.aggregate(records, includeEmptyMonths = true)
        assertThat(months).hasSize(12)
        assertThat(months.filter { it.totals.workdays == 0 }).hasSize(10)
        assertThat(months.filter { it.totals.workdays == 1 }).hasSize(2)
    }

    @Test
    fun `labels use persian month names`() {
        val months = MonthAggregator.aggregate(listOf(recordOn(1403, 1, 1)))
        assertThat(months.first().label).isEqualTo("فروردین ۱۴۰۳")
    }
}
