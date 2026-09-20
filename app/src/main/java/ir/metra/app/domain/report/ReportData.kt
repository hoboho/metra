package ir.metra.app.domain.report

import ir.metra.app.domain.ProjectSummary
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.ReportType
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.model.WorkRecord

/**
 * The report's data payload, assembled by [ReportBuilder] from repositories.
 *
 * Pure Kotlin and free of Android types so report content is unit-testable.
 */
data class ReportData(
    val type: ReportType,
    val title: String,
    val subtitle: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val profile: UserProfile,
    val totals: WorkTotals,
    val records: List<WorkRecord>,
    val projectBreakdown: List<ProjectSummary>,
    val expensesByCategory: Map<ExpenseCategory, Long>,
    val additionalNotes: List<String> = emptyList(),
)

/** Options that shape a generated report. */
data class ReportRequest(
    val type: ReportType,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val projectId: Long? = null,
    val includeDailyTable: Boolean = true,
    val includeNotes: Boolean = true,
    val includeProjectBreakdown: Boolean = true,
    val includeExpenseBreakdown: Boolean = true,
) {
    init {
        require(startEpochDay <= endEpochDay) { "start must be <= end" }
    }
}
