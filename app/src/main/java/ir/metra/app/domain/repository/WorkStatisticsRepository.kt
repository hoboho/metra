package ir.metra.app.domain.repository

import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.date.DateRange
import ir.metra.app.domain.ProjectSummary
import ir.metra.app.domain.WorkTotals
import kotlinx.coroutines.flow.Flow

/**
 * Read-only aggregation over work records.
 *
 * Split out from [WorkRecordRepository] so dashboards and reports depend on an
 * interface that cannot write.
 */
interface WorkStatisticsRepository {

    /** Live month summary; emits immediately and on every relevant change. */
    fun observeTotals(startEpochDay: Long, endEpochDay: Long): Flow<WorkTotals>

    suspend fun getTotals(startEpochDay: Long, endEpochDay: Long, projectId: Long? = null): WorkTotals

    suspend fun getProjectBreakdown(startEpochDay: Long, endEpochDay: Long): List<ProjectSummary>

    /**
     * Monthly buckets across everything ever recorded, oldest first.
     * Computed in SQL so the yearly overview scales to years of data.
     */
    suspend fun getMonthlySeries(): List<ir.metra.app.domain.MonthBucket>
}
