package ir.metra.app.domain

import ir.metra.app.domain.model.WorkRecord

/**
 * Aggregated totals for any collection of workdays (a day, a month, a project,
 * a year, an arbitrary range).
 *
 * The grouping of "calculated by Metra" versus "recorded by the user" is
 * preserved here so reports and dashboards can label each figure honestly.
 */
data class WorkTotals(
    val workdays: Int = 0,
    val totalMeters: Int = 0,
    val totalAdditionalMeters: Int = 0,
    val totalAdditionalPayment: Long = 0L,
    val totalExpenses: Long = 0L,
) {
    /**
     * What the company owes across the period.
     *
     * Metra's calculated meter payment plus the reimbursable expenses. Company
     * Company salary is not part of this: Metra does not track payroll at
     * so folding it into a per-day aggregate would double-count or mis-spread it.
     */
    val totalReceivable: Long
        get() = totalAdditionalPayment + totalExpenses

    /** Average meters per workday, or 0 when there are no workdays. */
    val averageMetersPerDay: Double
        get() = if (workdays == 0) 0.0 else totalMeters.toDouble() / workdays

    /** Average additional meters per workday, or 0 when there are no workdays. */
    val averageAdditionalMetersPerDay: Double
        get() = if (workdays == 0) 0.0 else totalAdditionalMeters.toDouble() / workdays

    /** Average expense per workday, or 0 when there are no workdays. */
    val averageExpensePerDay: Double
        get() = if (workdays == 0) 0.0 else totalExpenses.toDouble() / workdays

    val hasAdditionalMeters: Boolean get() = totalAdditionalMeters > 0

    companion object {
        val EMPTY = WorkTotals()

        /** Folds [records] into a single [WorkTotals]. */
        fun of(records: List<WorkRecord>): WorkTotals {
            var workdays = 0
            var meters = 0L
            var additional = 0L
            var additionalPayment = 0L
                            var expenses = 0L
            for (record in records) {
                workdays += 1
                meters += record.dailyMeters
                additional += record.additionalMeters
                additionalPayment += record.additionalMeterPayment
                                expenses += record.expenseTotal
            }
            return WorkTotals(
                workdays = workdays,
                totalMeters = meters.toInt(),
                totalAdditionalMeters = additional.toInt(),
                totalAdditionalPayment = additionalPayment,
                totalExpenses = expenses,
            )
        }
    }
}
