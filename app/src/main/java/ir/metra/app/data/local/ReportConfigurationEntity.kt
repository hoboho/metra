package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Saved report presets: a name plus the range/shape used the last time.
 *
 * Optional by design — reports work without any saved configuration.
 */
@Entity(tableName = "report_configurations")
data class ReportConfigurationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    /** One of: DAILY, MONTHLY, RANGE, PROJECT, YEARLY. */
    @ColumnInfo(name = "report_type") val reportType: String,
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long? = null,
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long? = null,
    @ColumnInfo(name = "include_daily_table") val includeDailyTable: Boolean = true,
    @ColumnInfo(name = "include_notes") val includeNotes: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,
)
