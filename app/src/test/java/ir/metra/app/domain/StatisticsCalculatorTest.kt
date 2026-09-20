package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.domain.model.WorkRecord
import org.junit.Test

/**
 * Statistics derivation: extremes, busiest day and month-over-month change.
 *
 * `percentageChange` returns null (not Infinity or 0) when the previous period
 * was empty — a growth percentage from nothing is meaningless, and the UI shows
 * a dash rather than a misleading number.
 */
class StatisticsCalculatorTest {

    private fun record(epochDay: Long, meters: Int, additional: Int = 0, expenses: Long = 0L) = WorkRecord(
        projectName = "پروژه",
        workDateEpochDay = epochDay,
        dailyMeters = meters,
        additionalMeters = additional,
        thresholdMetersSnapshot = 400,
        ratePerMeterSnapshot = 15_000L,
        additionalMeterPayment = additional * 15_000L,
        expenseTotal = expenses,
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )

    @Test
    fun `empty record set produces neutral statistics`() {
        val stats = StatisticsCalculator.calculate(emptyList())
        assertThat(stats.totals.workdays).isEqualTo(0)
        assertThat(stats.maxMetersInDay).isEqualTo(0)
        assertThat(stats.minMetersInDay).isEqualTo(0)
        assertThat(stats.busiestDayEpochDay).isNull()
        assertThat(stats.bestPaymentDayEpochDay).isNull()
        assertThat(stats.averageMetersPerDay).isEqualTo(0.0)
    }

    @Test
    fun `extremes and busiest day are identified`() {
        val first = JalaliCalendar.toEpochDay(JalaliDate(1403, 5, 1))
        val stats = StatisticsCalculator.calculate(
            listOf(
                record(first, 520, 120),
                record(first + 1, 300),
                record(first + 2, 800, 400),
            ),
        )
        assertThat(stats.maxMetersInDay).isEqualTo(800)
        assertThat(stats.minMetersInDay).isEqualTo(300)
        assertThat(stats.maxAdditionalMetersInDay).isEqualTo(400)
        assertThat(stats.busiestDayEpochDay).isEqualTo(first + 2)
        assertThat(stats.bestPaymentDayEpochDay).isEqualTo(first + 2)
        assertThat(stats.daysWithAdditionalMeters).isEqualTo(2)
    }

    @Test
    fun `days with expenses are counted`() {
        val first = JalaliCalendar.toEpochDay(JalaliDate(1403, 5, 1))
        val stats = StatisticsCalculator.calculate(
            listOf(
                record(first, 520, expenses = 530_000L),
                record(first + 1, 400),
                record(first + 2, 410, 10, expenses = 95_000L),
            ),
        )
        assertThat(stats.daysWithExpenses).isEqualTo(2)
        assertThat(stats.maxExpenseInDay).isEqualTo(530_000L)
    }

    // ------------------------------------------------------ percent change

    @Test
    fun `percentage change between two non-zero periods`() {
        assertThat(StatisticsCalculator.percentageChange(previous = 100.0, current = 150.0))
            .isWithin(0.0001).of(50.0)
        assertThat(StatisticsCalculator.percentageChange(previous = 200.0, current = 100.0))
            .isWithin(0.0001).of(-50.0)
    }

    @Test
    fun `percentage change from zero is undefined and returns null`() {
        assertThat(StatisticsCalculator.percentageChange(previous = 0.0, current = 500.0)).isNull()
        assertThat(StatisticsCalculator.percentageChange(previous = 0.0, current = 0.0)).isNull()
    }

    @Test
    fun `percentage change of equal periods is zero`() {
        assertThat(StatisticsCalculator.percentageChange(previous = 430.0, current = 430.0))
            .isWithin(0.0001).of(0.0)
    }
}
