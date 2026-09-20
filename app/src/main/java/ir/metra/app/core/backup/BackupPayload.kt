package ir.metra.app.core.backup

import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.ReportConfiguration
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.model.LedgerEntry
import ir.metra.app.domain.model.LedgerKind
import ir.metra.app.domain.model.LedgerReason
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a Metra backup.
 *
 * Versioned explicitly so a future build can read older backups and so an
 * incompatible file is rejected with a clear Persian message instead of being
 * half-imported.
 */
@Serializable
data class BackupPayload(
    val formatVersion: Int = BackupPayload.CURRENT_FORMAT_VERSION,
    val appVersionName: String,
    val appVersionCode: Int,
    val exportedAtEpochMilli: Long,
    val profile: UserProfileDto? = null,
    val settings: AppSettingsDto? = null,
    val paymentRules: List<PaymentRuleDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    val workRecords: List<WorkRecordDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val reportConfigurations: List<ReportConfigurationDto> = emptyList(),
    // Defaults to empty so a v1 backup still deserialises: the ledger did not
    // exist then, and "no receipts" is the correct reading of its absence.
    val ledgerEntries: List<LedgerEntryDto> = emptyList(),
    // DataStore preferences live outside Room, so without this block a restore
    // silently resets the theme, the reminder and the prefill toggle.
    val preferences: PreferencesDto? = null,
) {
    fun counts(): BackupCounts = BackupCounts(
        projects = projects.size,
        workRecords = workRecords.size,
        expenses = expenses.size,
        paymentRules = paymentRules.size,
        ledgerEntries = ledgerEntries.size,
        preferences = if (preferences != null) 1 else 0,
    )

    companion object {
        const val CURRENT_FORMAT_VERSION = 3
        const val MAGIC = "METRA-BACKUP"
    }
}

@Serializable
data class BackupCounts(
    val projects: Int,
    val workRecords: Int,
    val expenses: Int,
    val paymentRules: Int,
    val ledgerEntries: Int = 0,
    val preferences: Int = 0,
)

@Serializable
data class UserProfileDto(
    val fullName: String = "",
    val companyName: String = "",
    val employeeCode: String = "",
    val reportFooterNote: String = "",
    val onboardingCompleted: Boolean = false,
)

@Serializable
data class AppSettingsDto(
    val defaultProjectId: Long? = null,
    val defaultSupervisor: String = "",
    val defaultWorkerCount: Int = 0,
    val defaultWorkArea: String = "",
    val defaultThresholdMeters: Int,
    val defaultRatePerMeter: Long,
    val currencyCode: String,
    val usePreviousWorkdayInfo: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 20 * 60,
)

@Serializable
data class PaymentRuleDto(
    val thresholdMeters: Int,
    val ratePerMeter: Long,
    val effectiveFromEpochDay: Long,
    val currencyCode: String,
    val label: String? = null,
    val createdAtEpochMilli: Long,
)

@Serializable
data class ProjectDto(
    val name: String,
    val employer: String = "",
    val workArea: String = "",
    val defaultSupervisor: String = "",
    val defaultWorkerCount: Int = 0,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)

@Serializable
data class WorkRecordDto(
    val projectName: String,
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: Int = 0,
    val workDateEpochDay: Long,
    val dailyMeters: Int,
    val additionalMeters: Int,
    val thresholdMetersSnapshot: Int,
    val ratePerMeterSnapshot: Long,
    val additionalMeterPayment: Long,
    val expenseTotal: Long = 0,
    val notes: String = "",
    val workStartMinuteOfDay: Int? = null,
    val workEndMinuteOfDay: Int? = null,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)

@Serializable
data class ExpenseDto(
    val workRecordIndex: Int,
    val amount: Long,
    val category: String,
    val description: String = "",
    val createdAtEpochMilli: Long,
)

@Serializable
data class ReportConfigurationDto(
    val name: String,
    val reportType: String,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val includeDailyTable: Boolean = true,
    val includeNotes: Boolean = true,
)

// ---------------------------------------------------------------- converters

fun UserProfile.toDto() = UserProfileDto(fullName, companyName, employeeCode, reportFooterNote, onboardingCompleted)

fun AppSettings.toDto() = AppSettingsDto(
    defaultProjectId = defaultProjectId,
    defaultSupervisor = defaultSupervisor,
    defaultWorkerCount = defaultWorkerCount,
    defaultWorkArea = defaultWorkArea,
    defaultThresholdMeters = defaultThresholdMeters,
    defaultRatePerMeter = defaultRatePerMeter,
    currencyCode = currencyCode,
    usePreviousWorkdayInfo = usePreviousWorkdayInfo,
    reminderEnabled = reminderEnabled,
    reminderMinuteOfDay = reminderMinuteOfDay,
)

fun PaymentRule.toDto() = PaymentRuleDto(
    thresholdMeters, ratePerMeter, effectiveFromEpochDay, currencyCode, label, createdAtEpochMilli,
)

fun Project.toDto() = ProjectDto(
    name, employer, workArea, defaultSupervisor, defaultWorkerCount, notes, isActive,
    createdAtEpochMilli, updatedAtEpochMilli,
)

fun WorkRecord.toDto() = WorkRecordDto(
    projectName, workArea, employer, supervisor, workerCount, workDateEpochDay,
    dailyMeters, additionalMeters, thresholdMetersSnapshot, ratePerMeterSnapshot, additionalMeterPayment,
    expenseTotal, notes,
    workStartMinuteOfDay, workEndMinuteOfDay, createdAtEpochMilli, updatedAtEpochMilli,
)

/**
 * [workRecordIndex] is the record's position in the payload's `workRecords`
 * list. Local database ids are meaningless in another installation, so the link
 * between an expense and its workday is positional.
 */
fun Expense.toDto(workRecordIndex: Int) = ExpenseDto(
    workRecordIndex = workRecordIndex,
    amount = amount,
    category = category.name,
    description = description,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun ReportConfiguration.toDto() = ReportConfigurationDto(
    name, reportType.name, startEpochDay, endEpochDay, includeDailyTable, includeNotes,
)

@Serializable
data class LedgerEntryDto(
    val kind: String,
    val amount: Long,
    val reason: String,
    val entryDateEpochDay: Long,
    val method: String = "",
    val projectName: String = "",
    val notes: String = "",
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)

fun LedgerEntry.toDto() = LedgerEntryDto(
    kind = kind.name,
    amount = amount,
    reason = reason.name,
    entryDateEpochDay = entryDateEpochDay,
    method = method,
    projectName = projectName,
    notes = notes,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
)

/**
 * Restores one ledger movement.
 *
 * An unrecognised `kind` or `reason` falls back rather than throwing: a backup
 * written by a newer build must still import, and guessing the safer value
 * (a receipt, `OTHER`) beats losing the row entirely.
 */
fun LedgerEntryDto.toDomain(nowEpochMilli: Long) = LedgerEntry(
    kind = runCatching { LedgerKind.valueOf(kind) }.getOrDefault(LedgerKind.RECEIPT),
    amount = amount,
    reason = runCatching { LedgerReason.valueOf(reason) }.getOrDefault(LedgerReason.OTHER),
    entryDateEpochDay = entryDateEpochDay,
    method = method,
    projectName = projectName,
    notes = notes,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
)

/**
 * The DataStore preferences that are genuinely user configuration.
 *
 * `lastRemindedEpochDay` is deliberately excluded: it is runtime state, and
 * restoring a stale value would suppress tomorrow's reminder.
 */
@Serializable
data class PreferencesDto(
    val themeMode: String = "SYSTEM",
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 20 * 60,
    val usePreviousWorkdayInfo: Boolean = true,
    val onboardingCompleted: Boolean = false,
)
