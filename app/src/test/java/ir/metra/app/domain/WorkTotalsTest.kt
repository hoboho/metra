package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.WorkRecord
import org.junit.Test

/**
 * Monthly and range aggregation, and the derived figures shown on dashboards
 * and reports.
 */
class WorkTotalsTest {

    private fun record(
        meters: Int,
        additional: Int = 0,
        payment: Long = 0L,
        salary: Long = 0L,
        mission: Long = 0L,
        overtime: Long = 0L,
        other: Long = 0L,
        expenses: Long = 0L,
    ) = WorkRecord(
        projectName = "پروژه",
        workDateEpochDay = 0L,
        dailyMeters = meters,
        additionalMeters = additional,
        thresholdMetersSnapshot = 400,
        ratePerMeterSnapshot = 15_000L,
        additionalMeterPayment = payment,
        expenseTotal = expenses,
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )

    @Test
    fun `empty record set yields empty totals`() {
        val totals = WorkTotals.of(emptyList())
        assertThat(totals.workdays).isEqualTo(0)
        assertThat(totals.totalMeters).isEqualTo(0)
        assertThat(totals.totalReceivable).isEqualTo(0L)
        assertThat(totals.totalReceivable).isEqualTo(0L)
    }

    @Test
    fun `averages over zero workdays are zero, not NaN`() {
        val totals = WorkTotals.EMPTY
        assertThat(totals.averageMetersPerDay).isEqualTo(0.0)
        assertThat(totals.averageAdditionalMetersPerDay).isEqualTo(0.0)
        assertThat(totals.averageExpensePerDay).isEqualTo(0.0)
        assertThat(totals.averageMetersPerDay.isNaN()).isFalse()
    }

    @Test
    fun `aggregates a month of mixed days`() {
        val records = listOf(
            record(meters = 520, additional = 120, payment = 1_800_000L, expenses = 530_000L),
            record(meters = 400, additional = 0, payment = 0L),
            record(meters = 399, additional = 0, payment = 0L, expenses = 120_000L),
            record(meters = 401, additional = 1, payment = 15_000L),
        )
        val totals = WorkTotals.of(records)
        assertThat(totals.workdays).isEqualTo(4)
        assertThat(totals.totalMeters).isEqualTo(1720)
        assertThat(totals.totalAdditionalMeters).isEqualTo(121)
        assertThat(totals.totalAdditionalPayment).isEqualTo(1_815_000L)
        assertThat(totals.totalExpenses).isEqualTo(650_000L)
        assertThat(totals.averageMetersPerDay).isEqualTo(430.0)
    }

    @Test
    fun `company salary is not part of the per-day receivable`() {
        // Salary is monthly now, so a workday's receivable only ever contains
        // Metra's own calculation. This guards against salary creeping back in.
        val records = listOf(record(meters = 520, additional = 120, payment = 1_800_000L))
        val totals = WorkTotals.of(records)
        assertThat(totals.totalAdditionalPayment).isEqualTo(1_800_000L)
        assertThat(totals.totalReceivable).isEqualTo(1_800_000L)
    }

    @Test
    fun `expenses are reimbursable so they ADD to the receivable`() {
        // The company pays expenses back, so they are owed *to* the worker.
        // They must never be subtracted.
        val records = listOf(
            record(meters = 520, additional = 120, payment = 1_800_000L, expenses = 530_000L),
        )
        val totals = WorkTotals.of(records)
        assertThat(totals.totalExpenses).isEqualTo(530_000L)
        assertThat(totals.totalReceivable).isEqualTo(1_800_000L + 530_000L)
    }

    @Test
    fun `a day with only expenses is still a receivable, never negative`() {
        // An expense-heavy day with no additional meters still means the company
        // owes that money back.
        val records = listOf(record(meters = 300, expenses = 900_000L))
        val totals = WorkTotals.of(records)
        assertThat(totals.totalAdditionalPayment).isEqualTo(0L)
        assertThat(totals.totalReceivable).isEqualTo(900_000L)
    }

    @Test
    fun `hasAdditionalMeters reflects whether any surplus was earned`() {
        assertThat(WorkTotals.of(listOf(record(meters = 400))).hasAdditionalMeters).isFalse()
        assertThat(WorkTotals.of(listOf(record(meters = 401, additional = 1))).hasAdditionalMeters).isTrue()
    }
}
