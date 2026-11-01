package ir.metra.app.domain.repository

import ir.metra.app.domain.model.ShootingDailyTotal
import kotlinx.coroutines.flow.Flow

/** Read-only shooting aggregates consumed by statistics and report generation. */
interface ShootingSummaryRepository {
    fun observeDailyTotals(): Flow<List<ShootingDailyTotal>>

    suspend fun totalMeters(
        startEpochDay: Long,
        endEpochDay: Long,
        projectId: Long? = null,
    ): Long
}
