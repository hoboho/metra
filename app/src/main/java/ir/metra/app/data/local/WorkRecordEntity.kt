package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One workday.
 *
 * Column semantics are described in detail on the domain `WorkRecord`; the short
 * version: `additional_*` columns are calculated by Metra and `expense_total`
 * is reimbursable by the company. Company salary is monthly, not per-day, and
 * lives in `monthly_salaries`
 * recorded verbatim by the user, and `expense_total` is a maintained aggregate
 * of the rows in `expenses`.
 *
 * `project_id` is nullable with `ON DELETE SET NULL` so historical work survives
 * project deletion; the `*_snapshot` columns below carry the values that were in
 * effect when the row was written.
 */
@Entity(
    tableName = "work_records",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["work_date_epoch_day"]),
        Index(value = ["project_id"]),
        Index(value = ["is_deleted"]),
        Index(value = ["is_deleted", "work_date_epoch_day"]),
    ],
)
data class WorkRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    @ColumnInfo(name = "project_name_snapshot") val projectNameSnapshot: String,
    @ColumnInfo(name = "work_area_snapshot") val workAreaSnapshot: String = "",
    @ColumnInfo(name = "employer_snapshot") val employerSnapshot: String = "",
    @ColumnInfo(name = "supervisor_snapshot") val supervisorSnapshot: String = "",
    @ColumnInfo(name = "worker_count") val workerCount: Int = 0,

    @ColumnInfo(name = "work_date_epoch_day") val workDateEpochDay: Long,

    @ColumnInfo(name = "daily_meters") val dailyMeters: Int,
    @ColumnInfo(name = "additional_meters") val additionalMeters: Int,
    @ColumnInfo(name = "threshold_meters_snapshot") val thresholdMetersSnapshot: Int,
    @ColumnInfo(name = "rate_per_meter_snapshot") val ratePerMeterSnapshot: Long,
    @ColumnInfo(name = "additional_meter_payment") val additionalMeterPayment: Long,

    @ColumnInfo(name = "expense_total") val expenseTotal: Long = 0L,
    @ColumnInfo(name = "notes") val notes: String = "",

    @ColumnInfo(name = "work_start_minute_of_day") val workStartMinuteOfDay: Int? = null,
    @ColumnInfo(name = "work_end_minute_of_day") val workEndMinuteOfDay: Int? = null,

    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,

    /**
     * Soft delete. Retained because a deleted workday must not silently take its
     * expenses and report history with it, and because an accidental tap should
     * be recoverable. Rows are purged only by an explicit user action.
     */
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
)
