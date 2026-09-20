package ir.metra.app.data.repository

import ir.metra.app.core.date.DateRange
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.data.local.MonthSummaryRow
import ir.metra.app.data.local.RangeSummaryRow
import ir.metra.app.data.local.TotalsColumns
import ir.metra.app.data.local.WorkRecordDao
import ir.metra.app.domain.MonthBucket
import ir.metra.app.domain.ProjectSummary
import ir.metra.app.domain.WorkTotals
import ir.metra.app.domain.repository.WorkStatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkStatisticsRepositoryImpl @Inject constructor(
    private val workRecordDao: WorkRecordDao,
) : WorkStatisticsRepository {

    override fun observeTotals(startEpochDay: Long, endEpochDay: Long): Flow<WorkTotals> =
        workRecordDao.observeMonthSummary(startEpochDay, endEpochDay).map { it.totals.toTotals() }

    override suspend fun getTotals(startEpochDay: Long, endEpochDay: Long, projectId: Long?): WorkTotals =
        workRecordDao.getRangeSummary(startEpochDay, endEpochDay, projectId).totals.toTotals()

    override suspend fun getProjectBreakdown(startEpochDay: Long, endEpochDay: Long): List<ProjectSummary> =
        workRecordDao.getProjectBreakdown(startEpochDay, endEpochDay).map { row ->
            ProjectSummary(
                projectName = row.projectName,
                workdays = row.workdays,
                totalMeters = row.totalMeters.toInt(),
                totalAdditionalMeters = row.totalAdditionalMeters.toInt(),
                totalAdditionalPayment = row.totalAdditionalPayment,
                totalExpenses = row.totalExpenses,
            )
        }

    override suspend fun getMonthlySeries(): List<MonthBucket> =
        workRecordDao.getMonthlyBreakdown().mapNotNull { row ->
            // `monthStartEpochDay` is MIN(work_date) of the Gregorian month
            // bucket; it is only used to locate the bucket on the Jalali axis.
            if (row.monthStartEpochDay == 0L && row.totals.workdays == 0) return@mapNotNull null
            MonthBucket(
                monthStartEpochDay = row.monthStartEpochDay,
                totals = row.totals.toTotals(),
                range = DateRange(row.monthStartEpochDay, row.monthStartEpochDay),
            )
        }

    private fun TotalsColumns.toTotals(): WorkTotals = WorkTotals(
        workdays = workdays,
        totalMeters = totalMeters.toInt(),
        totalAdditionalMeters = totalAdditionalMeters.toInt(),
        totalAdditionalPayment = totalAdditionalPayment,
        totalExpenses = totalExpenses,
    )
}
