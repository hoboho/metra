package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-editable defaults that belong to the *data*, not to the device.
 *
 * Kept in Room (rather than only in DataStore) so that a backup file carries the
 * work defaults with it and a restore reproduces the same behaviour on another
 * device. Exactly one row (id = 1).
 *
 * Purely device-local preferences — theme, reminder enablement, app lock — live
 * in DataStore instead.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,

    // Work defaults
    @ColumnInfo(name = "default_project_id") val defaultProjectId: Long? = null,
    @ColumnInfo(name = "default_supervisor") val defaultSupervisor: String = "",
    @ColumnInfo(name = "default_worker_count") val defaultWorkerCount: Int = 0,
    @ColumnInfo(name = "default_work_area") val defaultWorkArea: String = "",

    // Payment defaults for *newly created* rules.
    @ColumnInfo(name = "default_threshold_meters") val defaultThresholdMeters: Int,
    @ColumnInfo(name = "default_rate_per_meter") val defaultRatePerMeter: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,

    // Behaviour
    @ColumnInfo(name = "use_previous_workday_info") val usePreviousWorkdayInfo: Boolean = true,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean = false,
    @ColumnInfo(name = "reminder_minute_of_day") val reminderMinuteOfDay: Int = DEFAULT_REMINDER_MINUTE,

    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,
) {
    companion object {
        const val SINGLETON_ID = 1L

        /** 20:00 local time. */
        const val DEFAULT_REMINDER_MINUTE = 20 * 60
    }
}
