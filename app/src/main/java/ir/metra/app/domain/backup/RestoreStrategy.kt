package ir.metra.app.domain.backup

/**
 * How a restore merges a backup into the current database.
 *
 * The UI must show this choice explicitly — a restore is never silent, and the
 * app always writes a safety snapshot first.
 */
enum class RestoreStrategy {
    /**
     * Insert rows that are not already present, leave existing rows untouched.
     * The safe default.
     */
    ADD_ONLY,

    /**
     * Insert missing rows and overwrite rows whose natural key matches
     * (project name, or workday + project + meters).
     */
    MERGE,

    /** Wipe the current data, then import. Destructive; requires confirmation. */
    REPLACE,
}

/** What a restore will do, computed *before* anything is written. */
data class RestorePreview(
    val formatVersion: Int,
    val exportedAtEpochMilli: Long,
    val appVersionName: String,
    val incomingProjects: Int,
    val incomingWorkRecords: Int,
    val incomingExpenses: Int,
    val incomingPaymentRules: Int,
    val existingProjects: Int,
    val existingWorkRecords: Int,
    val existingExpenses: Int,
    val encrypted: Boolean,
    val formatSupported: Boolean,
) {
    val willOverwriteExistingData: Boolean get() = existingWorkRecords > 0 || existingProjects > 0
}

/** Outcome of a restore. */
data class RestoreOutcome(
    val strategy: RestoreStrategy,
    val projectsImported: Int,
    val workRecordsImported: Int,
    val expensesImported: Int,
    val paymentRulesImported: Int,
    val safetySnapshotFile: String?,
)
