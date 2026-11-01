package ir.metra.app.feature.shooting.data

import androidx.room.withTransaction
import ir.metra.app.domain.model.ShootingCableTypeBackup
import ir.metra.app.domain.model.ShootingDailyTotal
import ir.metra.app.domain.model.ShootingReportBackup
import ir.metra.app.domain.model.ShootingRouteBackup
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.ShootingBackupRepository
import ir.metra.app.domain.repository.ShootingSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ShootingOperationDraft(
    val startPointType: String,
    val startPointCode: String,
    val startMeter: Long,
    val endPointType: String,
    val endPointCode: String,
    val endMeter: Long,
    val cableTypeId: Long,
) {
    val meters: Long get() = abs(endMeter - startMeter)
}

data class ShootingReportDraft(
    val projectId: Long?,
    val projectName: String,
    val workDateEpochDay: Long,
    val workArea: String,
    val employer: String,
    val supervisor: String,
    val workerCount: Int = 0,
    val notes: String = "",
    val operations: List<ShootingOperationDraft>,
)

data class ShootingReportWithOperations(
    val report: ShootingReportEntity,
    val operations: List<ShootingOperationEntity>,
)

@Singleton
class ShootingRepository @Inject constructor(
    private val database: ShootingDatabase,
    private val projects: ProjectRepository,
) : ShootingSummaryRepository, ShootingBackupRepository {
    private val dao get() = database.shootingDao()

    fun observeReports(): Flow<List<ShootingReportEntity>> = dao.observeReports()
    fun observeCableTypes(): Flow<List<CableTypeEntity>> = dao.observeCableTypes()

    override fun observeDailyTotals(): Flow<List<ShootingDailyTotal>> =
        dao.observeReports().map { reports ->
            reports.map { ShootingDailyTotal(it.workDateEpochDay, it.projectName, it.totalMeters) }
        }

    override suspend fun totalMeters(
        startEpochDay: Long,
        endEpochDay: Long,
        projectId: Long?,
    ): Long = dao.reportsInRange(startEpochDay, endEpochDay)
        .asSequence()
        .filter { projectId == null || it.projectId == projectId }
        .sumOf { it.totalMeters }

    suspend fun allReports(): List<ShootingReportEntity> = dao.getAllReports()
    suspend fun allCableTypes(): List<CableTypeEntity> = dao.getAllCableTypes()
    suspend fun operations(reportId: Long): List<ShootingOperationEntity> = dao.getOperations(reportId)

    suspend fun seedCableTypes() {
        dao.insertCableTypes(
            listOf(6, 12, 24, 48, 96, 144).map {
                CableTypeEntity(coreCount = it, label = "$it کور")
            },
        )
    }

    suspend fun project(id: Long) = projects.getProject(id)

    suspend fun reportWithOperations(id: Long): ShootingReportWithOperations? {
        val report = dao.getReport(id) ?: return null
        return ShootingReportWithOperations(report, dao.getOperations(id))
    }

    suspend fun save(draft: ShootingReportDraft, reportId: Long? = null): Long {
        require(draft.projectName.isNotBlank())
        require(draft.projectId != null && projects.projectExists(draft.projectId))
        require(draft.workerCount >= 0)
        require(draft.operations.isNotEmpty())
        require(draft.operations.all {
            it.startMeter >= 0 && it.endMeter >= 0 &&
                it.startPointCode.isNotBlank() && it.endPointCode.isNotBlank() &&
                dao.cableTypeExists(it.cableTypeId)
        })

        val now = System.currentTimeMillis()
        return database.withTransaction {
            val existing = reportId?.let { dao.getReport(it) }
            if (reportId != null && existing == null) error("Shooting report no longer exists")
            val report = ShootingReportEntity(
                id = existing?.id ?: 0L,
                projectId = draft.projectId,
                projectName = draft.projectName,
                workDateEpochDay = draft.workDateEpochDay,
                workArea = draft.workArea,
                employer = draft.employer,
                supervisor = draft.supervisor,
                workerCount = draft.workerCount,
                notes = draft.notes,
                totalMeters = draft.operations.fold(0L) { total, operation -> Math.addExact(total, operation.meters) },
                createdAtEpochMilli = existing?.createdAtEpochMilli ?: now,
                updatedAtEpochMilli = now,
            )
            val id = if (existing == null) dao.insertReport(report) else {
                dao.updateReport(report)
                dao.deleteOperationsForReport(report.id)
                report.id
            }
            dao.insertOperations(
                draft.operations.map { op ->
                    ShootingOperationEntity(
                        reportId = id,
                        startPointType = op.startPointType,
                        startPointCode = op.startPointCode,
                        startMeter = op.startMeter,
                        endPointType = op.endPointType,
                        endPointCode = op.endPointCode,
                        endMeter = op.endMeter,
                        cableTypeId = op.cableTypeId,
                        operationMeters = op.meters,
                    )
                },
            )
            id
        }
    }

    suspend fun delete(id: Long) = dao.deleteReport(id)
    suspend fun reports(start: Long, end: Long) = dao.reportsInRange(start, end)
    suspend fun total(start: Long, end: Long) = dao.totalMeters(start, end)

    override suspend fun backupCableTypes(): List<ShootingCableTypeBackup> =
        dao.getAllCableTypes().map { ShootingCableTypeBackup(it.coreCount, it.label, it.isActive) }

    override suspend fun backupReports(): List<ShootingReportBackup> {
        val cableById = dao.getAllCableTypes().associateBy { it.id }
        return dao.getAllReports().map { report ->
            val operations = dao.getOperations(report.id).map { operation ->
                val cable = cableById[operation.cableTypeId]
                    ?: error("Shooting operation ${operation.id} references a missing cable type")
                ShootingRouteBackup(
                    startPointType = operation.startPointType,
                    startPointCode = operation.startPointCode,
                    startMeter = operation.startMeter,
                    endPointType = operation.endPointType,
                    endPointCode = operation.endPointCode,
                    endMeter = operation.endMeter,
                    cableCoreCount = cable.coreCount,
                    operationMeters = operation.operationMeters,
                )
            }
            ShootingReportBackup(
                localId = report.id,
                projectId = report.projectId,
                projectName = report.projectName,
                workDateEpochDay = report.workDateEpochDay,
                workArea = report.workArea,
                employer = report.employer,
                supervisor = report.supervisor,
                workerCount = report.workerCount,
                notes = report.notes,
                totalMeters = report.totalMeters,
                createdAtEpochMilli = report.createdAtEpochMilli,
                updatedAtEpochMilli = report.updatedAtEpochMilli,
                operations = operations,
            )
        }
    }

    override suspend fun clearShootingData() = database.withTransaction {
        dao.deleteAllReports()
        dao.deleteAllCableTypes()
    }

    override suspend fun seedDefaultCableTypes() = seedCableTypes()

    override suspend fun restoreCableTypes(types: List<ShootingCableTypeBackup>): Map<Int, Long> =
        database.withTransaction {
            types.forEach { type ->
                val existing = dao.getCableTypeByCoreCount(type.coreCount)
                val entity = CableTypeEntity(
                    id = existing?.id ?: 0L,
                    coreCount = type.coreCount,
                    label = type.label,
                    isActive = type.isActive,
                )
                if (existing == null) dao.insertCableTypes(listOf(entity)) else dao.updateCableType(entity)
            }
            dao.getAllCableTypes().associate { it.coreCount to it.id }
        }

    override suspend fun restoreReport(report: ShootingReportBackup, existingLocalId: Long?): Long {
        val cableTypeIds = dao.getAllCableTypes().associate { it.coreCount to it.id }
        val entity = ShootingReportEntity(
            id = existingLocalId ?: 0L,
            projectId = report.projectId,
            projectName = report.projectName,
            workDateEpochDay = report.workDateEpochDay,
            workArea = report.workArea,
            employer = report.employer,
            supervisor = report.supervisor,
            workerCount = report.workerCount,
            notes = report.notes,
            totalMeters = report.totalMeters,
            createdAtEpochMilli = report.createdAtEpochMilli,
            updatedAtEpochMilli = report.updatedAtEpochMilli,
        )
        val operations = report.operations.map { operation ->
            val cableTypeId = cableTypeIds[operation.cableCoreCount]
                ?: error("Missing cable type ${operation.cableCoreCount} core")
            ShootingOperationEntity(
                reportId = existingLocalId ?: 0L,
                startPointType = operation.startPointType,
                startPointCode = operation.startPointCode,
                startMeter = operation.startMeter,
                endPointType = operation.endPointType,
                endPointCode = operation.endPointCode,
                endMeter = operation.endMeter,
                cableTypeId = cableTypeId,
                operationMeters = operation.operationMeters,
            )
        }
        return database.withTransaction {
            val id = if (existingLocalId == null) {
                dao.insertReport(entity)
            } else {
                dao.updateReport(entity)
                dao.deleteOperationsForReport(existingLocalId)
                existingLocalId
            }
            dao.insertOperations(operations.map { it.copy(id = 0L, reportId = id) })
            id
        }
    }
}
