package ir.metra.app.domain.repository

import ir.metra.app.domain.model.ShootingCableTypeBackup
import ir.metra.app.domain.model.ShootingReportBackup

/** Persistence boundary for the shooting data included in portable backups. */
interface ShootingBackupRepository {
    suspend fun backupCableTypes(): List<ShootingCableTypeBackup>
    suspend fun backupReports(): List<ShootingReportBackup>
    suspend fun clearShootingData()
    suspend fun seedDefaultCableTypes()
    suspend fun restoreCableTypes(types: List<ShootingCableTypeBackup>): Map<Int, Long>
    suspend fun restoreReport(report: ShootingReportBackup, existingLocalId: Long? = null): Long
}
