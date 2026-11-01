package ir.metra.app.feature.shooting.data

import androidx.room.withTransaction
import ir.metra.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ShootingOperationDraft(
    val startPointType: String, val startPointCode: String, val startMeter: Long,
    val endPointType: String, val endPointCode: String, val endMeter: Long,
    val cableTypeId: Long,
) {
    val meters: Long get() = abs(endMeter - startMeter)
}
data class ShootingReportDraft(
    val projectId: Long?, val projectName: String, val workDateEpochDay: Long,
    val workArea: String, val employer: String, val supervisor: String,
    val workerCount: Int = 0, val notes: String = "",
    val operations: List<ShootingOperationDraft>,
)

@Singleton
class ShootingRepository @Inject constructor(
    private val database: ShootingDatabase,
    private val projects: ProjectRepository,
) {
    private val dao get() = database.shootingDao()
    fun observeReports(): Flow<List<ShootingReportEntity>> = dao.observeReports()
    fun observeCableTypes(): Flow<List<CableTypeEntity>> = dao.observeCableTypes()

    suspend fun seedCableTypes() {
        dao.insertCableTypes(listOf(6, 12, 24, 48, 96, 144).map { CableTypeEntity(coreCount = it, label = it.toString() + " کور") })
    }
    suspend fun project(id: Long) = projects.getProject(id)
    suspend fun save(draft: ShootingReportDraft): Long {
        require(draft.projectName.isNotBlank())
        require(draft.workerCount >= 0)
        require(draft.operations.isNotEmpty())
        require(draft.operations.all { it.startMeter >= 0 && it.endMeter >= 0 && it.startPointCode.isNotBlank() && it.endPointCode.isNotBlank() })
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val total = draft.operations.sumOf { it.meters }
            val id = dao.insertReport(ShootingReportEntity(
                projectId = draft.projectId, projectName = draft.projectName,
                workDateEpochDay = draft.workDateEpochDay, workArea = draft.workArea,
                employer = draft.employer, supervisor = draft.supervisor,
                workerCount = draft.workerCount, notes = draft.notes, totalMeters = total,
                createdAtEpochMilli = now, updatedAtEpochMilli = now,
            ))
            dao.insertOperations(draft.operations.map { op ->
                ShootingOperationEntity(
                    reportId = id, startPointType = op.startPointType, startPointCode = op.startPointCode,
                    startMeter = op.startMeter, endPointType = op.endPointType, endPointCode = op.endPointCode,
                    endMeter = op.endMeter, cableTypeId = op.cableTypeId, operationMeters = op.meters,
                )
            })
            id
        }
    }
    suspend fun delete(id: Long) = dao.deleteReport(id)
    suspend fun operations(id: Long) = dao.getOperations(id)
    suspend fun reports(start: Long, end: Long) = dao.reportsInRange(start, end)
    suspend fun total(start: Long, end: Long) = dao.totalMeters(start, end)
}
