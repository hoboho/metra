package ir.metra.app.domain

import ir.metra.app.domain.model.WorkRecord
import kotlin.math.roundToInt

/**
 * Statistical summaries over a set of workdays.
 *
 * Pure functions only — no Android, no I/O — so every KPI on the statistics
 * screen is covered by JVM unit tests.
 */
data class WorkStatistics(
    val totals: WorkTotals,
    val maxMetersInDay: Int = 0,
    val minMetersInDay: Int = 0,
    val maxAdditionalMetersInDay: Int = 0,
    val busiestDayEpochDay: Long? = null,
    val bestPaymentDayEpochDay: Long? = null,
    val daysWithAdditionalMeters: Int = 0,
    val daysWithExpenses: Int = 0,
    val maxExpenseInDay: Long = 0L,
) {
    val averageMetersPerDay: Double get() = totals.averageMetersPerDay
    val averageAdditionalMetersPerDay: Double get() = totals.averageAdditionalMetersPerDay
    val averageExpensePerDay: Double get() = totals.averageExpensePerDay

    companion object {
        val EMPTY = WorkStatistics(WorkTotals.EMPTY)
    }
}

object StatisticsCalculator {

    fun calculate(records: List<WorkRecord>): WorkStatistics {
        if (records.isEmpty()) return WorkStatistics.EMPTY

        var maxMeters = Int.MIN_VALUE
        var minMeters = Int.MAX_VALUE
        var maxAdditional = 0
        var maxExpense = 0L
        var busiestDay: Long? = null
        var bestPaymentDay: Long? = null
        var busiestMeters = -1
        var bestPayment = -1L

        for (record in records) {
            if (record.dailyMeters > maxMeters) maxMeters = record.dailyMeters
            if (record.dailyMeters < minMeters) minMeters = record.dailyMeters
            if (record.additionalMeters > maxAdditional) maxAdditional = record.additionalMeters
            if (record.expenseTotal > maxExpense) maxExpense = record.expenseTotal
            if (record.dailyMeters > busiestMeters) {
                busiestMeters = record.dailyMeters
                busiestDay = record.workDateEpochDay
            }
            if (record.additionalMeterPayment > bestPayment) {
                bestPayment = record.additionalMeterPayment
                bestPaymentDay = record.workDateEpochDay
            }
        }

        return WorkStatistics(
            totals = WorkTotals.of(records),
            maxMetersInDay = maxMeters,
            minMetersInDay = minMeters,
            maxAdditionalMetersInDay = maxAdditional,
            busiestDayEpochDay = busiestDay,
            bestPaymentDayEpochDay = bestPaymentDay,
            daysWithAdditionalMeters = records.count { it.additionalMeters > 0 },
            daysWithExpenses = records.count { it.expenseTotal > 0L },
            maxExpenseInDay = maxExpense,
        )
    }

    /**
     * Percentage change from [previous] to [current].
     *
     * Returns null when [previous] is zero, because a percentage change from
     * zero is undefined and showing "∞%" would be meaningless.
     */
    fun percentageChange(previous: Double, current: Double): Double? {
        if (previous == 0.0) return null
        return ((current - previous) / kotlin.math.abs(previous)) * 100.0
    }

    /** Rounds a KPI to the nearest whole meter for display. */
    fun roundMeters(value: Double): Int = value.roundToInt()
}
