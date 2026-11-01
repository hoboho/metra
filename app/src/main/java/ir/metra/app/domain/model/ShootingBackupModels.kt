package ir.metra.app.domain.model

/** Portable cable catalog row; the core count is the cross-device key. */
data class ShootingCableTypeBackup(
    val coreCount: Int,
    val label: String,
    val isActive: Boolean,
)

data class ShootingRouteBackup(
    val startPointType: String,
    val startPointCode: String,
    val startMeter: Long,
    val endPointType: String,
    val endPointCode: String,
    val endMeter: Long,
    val cableCoreCount: Int,
    val operationMeters: Long,
)

/**
 * Portable report data. [localId] is runtime-only and is never written to a backup;
 * project references are restored by the project-name snapshot.
 */
data class ShootingReportBackup(
    val localId: Long = 0L,
    val projectId: Long?,
    val projectName: String,
    val workDateEpochDay: Long,
    val workArea: String,
    val employer: String,
    val supervisor: String,
    val workerCount: Int,
    val notes: String,
    val totalMeters: Long,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
    val operations: List<ShootingRouteBackup>,
)
