package ir.metra.app.domain.report

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.ReportType
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles [ReportData] from the repositories.
 *
 * Reports stream their rows in pages rather than loading a whole period at once,
 * so a multi-year report does not blow the heap on a low-end phone.
 */
@Singleton
class ReportBuilder @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val statisticsRepository: WorkStatisticsRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
    private val clock: Clock,
) {

    /** Rows pulled per page when assembling the detailed table. */
    private val pageSize = 500

    suspend fun build(request: ReportRequest): MetraResult<ReportData> = runCatching {
        val profile = userRepository.getProfile()
        val totals = statisticsRepository.getTotals(
            startEpochDay = request.startEpochDay,
            endEpochDay = request.endEpochDay,
            projectId = request.projectId,
        )
        val records = collectRecords(request)
        val projectBreakdown = if (request.includeProjectBreakdown) {
            statisticsRepository.getProjectBreakdown(request.startEpochDay, request.endEpochDay)
        } else {
            emptyList()
        }
        val expensesByCategory = if (request.includeExpenseBreakdown) {
            buildExpenseBreakdown(records)
        } else {
            emptyMap()
        }

        val projectName = request.projectId?.let { projectRepository.getProject(it)?.name }

        ReportData(
            type = request.type,
            title = titleFor(request.type, projectName),
            subtitle = subtitleFor(request),
            startEpochDay = request.startEpochDay,
            endEpochDay = request.endEpochDay,
            profile = profile,
            totals = totals,
            records = records,
            projectBreakdown = projectBreakdown,
            expensesByCategory = expensesByCategory,
        ).let(::success)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "report build")) })

    /**
     * Reads the whole period in bounded pages.
     *
     * Paging keeps peak memory flat regardless of how many years of records the
     * user has accumulated.
     */
    private suspend fun collectRecords(request: ReportRequest): List<ir.metra.app.domain.model.WorkRecord> {
        val all = ArrayList<ir.metra.app.domain.model.WorkRecord>()
        var offset = 0
        while (true) {
            val page = workRecordRepository.getPage(
                startEpochDay = request.startEpochDay,
                endEpochDay = request.endEpochDay,
                limit = pageSize,
                offset = offset,
            ).filter { request.projectId == null || it.projectId == request.projectId }
            if (page.isEmpty()) break
            all += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return all
    }

    private suspend fun buildExpenseBreakdown(
        records: List<ir.metra.app.domain.model.WorkRecord>,
    ): Map<ExpenseCategory, Long> {
        val totals = HashMap<ExpenseCategory, Long>()
        for (record in records) {
            val (_, expenses) = workRecordRepository.getRecordWithExpenses(record.id) ?: continue
            for (expense in expenses) {
                totals[expense.category] = (totals[expense.category] ?: 0L) + expense.amount
            }
        }
        return totals
    }

    private fun titleFor(type: ReportType, projectName: String?): String = when (type) {
        ReportType.DAILY -> "گزارش کارکرد روزانه"
        ReportType.MONTHLY -> "گزارش کارکرد ماهانه"
        ReportType.RANGE -> "گزارش کارکرد بازهٔ انتخابی"
        ReportType.PROJECT -> "گزارش کارکرد پروژه${projectName?.let { ": $it" } ?: ""}"
        ReportType.YEARLY -> "گزارش کارکرد سالانه"
    }

    private fun subtitleFor(request: ReportRequest): String =
        dateFormatter.formatRange(request.startEpochDay, request.endEpochDay)

    companion object {
        /** Persian labels for the expense breakdown section. */
        fun categoryLabel(category: ExpenseCategory): String = when (category) {
            ExpenseCategory.TRANSPORTATION -> "حمل و نقل"
            ExpenseCategory.FOOD -> "خورد و خوراک"
            ExpenseCategory.ACCOMMODATION -> "اسکان"
            ExpenseCategory.MATERIALS -> "مصالح"
            ExpenseCategory.TOOLS -> "ابزار"
            ExpenseCategory.OTHER -> "سایر"
        }

        /** Persian labels for report metadata rows. */
        const val LABEL_NAME = "نام"
        const val LABEL_COMPANY = "شرکت"
        const val LABEL_PERIOD = "بازهٔ گزارش"
        const val LABEL_GENERATED = "تاریخ صدور گزارش"
        const val LABEL_EMPLOYEE_CODE = "کد پرسنلی"
    }
}
