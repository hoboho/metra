package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An audit row written every time a backup is exported or a restore is applied.
 *
 * Restores are destructive to the current dataset, so the app needs a durable
 * record of what happened, when, and what the safety snapshot was called.
 */
@Entity(
    tableName = "backup_metadata",
    indices = [Index(value = ["created_at"])],
)
data class BackupMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "operation") val operation: String,
    /** One of: MERGE, REPLACE, ADD_ONLY. */
    @ColumnInfo(name = "restore_strategy") val restoreStrategy: String? = null,
    @ColumnInfo(name = "encrypted") val encrypted: Boolean = false,
    @ColumnInfo(name = "project_count") val projectCount: Int = 0,
    @ColumnInfo(name = "work_record_count") val workRecordCount: Int = 0,
    @ColumnInfo(name = "expense_count") val expenseCount: Int = 0,
    @ColumnInfo(name = "sha256") val sha256: String? = null,
    @ColumnInfo(name = "safety_snapshot_path") val safetySnapshotPath: String? = null,
) {
    companion object {
        const val OPERATION_EXPORT = "EXPORT"
        const val OPERATION_IMPORT = "IMPORT"
    }
}
