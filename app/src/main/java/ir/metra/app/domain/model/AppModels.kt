package ir.metra.app.domain.model

/** Local, account-free profile used for report headers. */
data class UserProfile(
    val id: Long = UserProfileEntityId,
    val fullName: String = "",
    val companyName: String = "",
    val employeeCode: String = "",
    val reportFooterNote: String = "",
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val UserProfileEntityId = 1L
        val EMPTY = UserProfile()
    }
}

/** User-editable work and reminder defaults. */
data class AppSettings(
    val defaultProjectId: Long? = null,
    val defaultSupervisor: String = "",
    val defaultWorkerCount: Int = 0,
    val defaultWorkArea: String = "",
    val defaultThresholdMeters: Int = PaymentRule.DEFAULT_THRESHOLD_METERS,
    val defaultRatePerMeter: Long = PaymentRule.DEFAULT_RATE_PER_METER,
    val currencyCode: String = PaymentRule.CURRENCY_TOMAN,
    val usePreviousWorkdayInfo: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 20 * 60,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/** Kinds of report the app can produce. */
enum class ReportType {
    DAILY,
    MONTHLY,
    RANGE,
    PROJECT,
    YEARLY,
}

/** A saved report preset. */
data class ReportConfiguration(
    val id: Long = 0L,
    val name: String,
    val reportType: ReportType,
    val projectId: Long? = null,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val includeDailyTable: Boolean = true,
    val includeNotes: Boolean = true,
)

/** An audit entry describing a backup that was written or restored. */
data class BackupMetadata(
    val id: Long = 0L,
    val fileName: String,
    val createdAtEpochMilli: Long,
    val operation: String,
    val restoreStrategy: String? = null,
    val encrypted: Boolean = false,
    val projectCount: Int = 0,
    val workRecordCount: Int = 0,
    val expenseCount: Int = 0,
    val sha256: String? = null,
)
