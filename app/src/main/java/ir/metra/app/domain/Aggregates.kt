package ir.metra.app.domain

import ir.metra.app.core.date.DateRange

/** A project's contribution to a reporting period. */
data class ProjectSummary(
    val projectName: String,
    val workdays: Int,
    val totalMeters: Int,
    val totalAdditionalMeters: Int,
    val totalAdditionalPayment: Long,
    val totalExpenses: Long,
) {
    val totalReceivable: Long get() = totalAdditionalPayment - totalExpenses
}

/** One Jalali month's totals, for the yearly overview and monthly charts. */
data class MonthBucket(
    val monthStartEpochDay: Long,
    val totals: WorkTotals,
    val range: DateRange,
)
