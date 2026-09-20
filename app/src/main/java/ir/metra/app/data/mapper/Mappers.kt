package ir.metra.app.data.mapper

import ir.metra.app.domain.model.LedgerEntry
import ir.metra.app.domain.model.LedgerKind
import ir.metra.app.domain.model.LedgerReason
import ir.metra.app.data.local.LedgerEntryEntity
import ir.metra.app.data.local.AppSettingsEntity
import ir.metra.app.data.local.BackupMetadataEntity
import ir.metra.app.data.local.ExpenseEntity
import ir.metra.app.data.local.PaymentRuleEntity
import ir.metra.app.data.local.ProjectEntity
import ir.metra.app.data.local.ReportConfigurationEntity
import ir.metra.app.data.local.UserProfileEntity
import ir.metra.app.data.local.WorkRecordEntity
import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.BackupMetadata
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.ReportConfiguration
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.model.WorkRecord

/**
 * Entity <-> domain mapping.
 *
 * Entities are Room-shaped and mutable-by-copy at the persistence boundary;
 * domain models are what the rest of the app sees. Keeping the translation in
 * one file makes the boundary auditable — nothing above the data layer ever
 * touches an `*Entity`.
 */

// ------------------------------------------------------------------- profile

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    fullName = fullName,
    companyName = companyName,
    employeeCode = employeeCode,
    reportFooterNote = reportFooterNote,
    onboardingCompleted = onboardingCompleted,
)

fun UserProfile.toEntity(nowEpochMilli: Long, createdAtEpochMilli: Long = nowEpochMilli): UserProfileEntity =
    UserProfileEntity(
        id = UserProfileEntity.SINGLETON_ID,
        fullName = fullName.trim(),
        companyName = companyName.trim(),
        employeeCode = employeeCode.trim(),
        reportFooterNote = reportFooterNote.trim(),
        onboardingCompleted = onboardingCompleted,
        createdAtEpochMilli = createdAtEpochMilli,
        updatedAtEpochMilli = nowEpochMilli,
    )

// ------------------------------------------------------------------- project

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    employer = employer,
    workArea = workArea,
    defaultSupervisor = defaultSupervisor,
    defaultWorkerCount = defaultWorkerCount,
    notes = notes,
    isActive = isActive,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name.trim(),
    employer = employer.trim(),
    workArea = workArea.trim(),
    defaultSupervisor = defaultSupervisor.trim(),
    defaultWorkerCount = defaultWorkerCount,
    notes = notes.trim(),
    isActive = isActive,
    createdAtEpochMilli = if (createdAtEpochMilli == 0L) updatedAtEpochMilli else createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
)

// --------------------------------------------------------------- work record

fun WorkRecordEntity.toDomain(): WorkRecord = WorkRecord(
    id = id,
    projectId = projectId,
    projectName = projectNameSnapshot,
    workArea = workAreaSnapshot,
    employer = employerSnapshot,
    supervisor = supervisorSnapshot,
    workerCount = workerCount,
    workDateEpochDay = workDateEpochDay,
    dailyMeters = dailyMeters,
    additionalMeters = additionalMeters,
    thresholdMetersSnapshot = thresholdMetersSnapshot,
    ratePerMeterSnapshot = ratePerMeterSnapshot,
    additionalMeterPayment = additionalMeterPayment,
    expenseTotal = expenseTotal,
    notes = notes,
    workStartMinuteOfDay = workStartMinuteOfDay,
    workEndMinuteOfDay = workEndMinuteOfDay,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
    isDeleted = isDeleted,
)

fun WorkRecord.toEntity(): WorkRecordEntity = WorkRecordEntity(
    id = id,
    projectId = projectId,
    projectNameSnapshot = projectName.trim(),
    workAreaSnapshot = workArea.trim(),
    employerSnapshot = employer.trim(),
    supervisorSnapshot = supervisor.trim(),
    workerCount = workerCount,
    workDateEpochDay = workDateEpochDay,
    dailyMeters = dailyMeters,
    additionalMeters = additionalMeters,
    thresholdMetersSnapshot = thresholdMetersSnapshot,
    ratePerMeterSnapshot = ratePerMeterSnapshot,
    additionalMeterPayment = additionalMeterPayment,
    expenseTotal = expenseTotal,
    notes = notes.trim(),
    workStartMinuteOfDay = workStartMinuteOfDay,
    workEndMinuteOfDay = workEndMinuteOfDay,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
    isDeleted = isDeleted,
)

// ------------------------------------------------------------------- expense

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    workRecordId = workRecordId,
    amount = amount,
    category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    workRecordId = workRecordId,
    amount = amount,
    category = category.name,
    description = description.trim(),
    receiptPhotoUri = receiptPhotoUri,
    createdAtEpochMilli = createdAtEpochMilli,
)

// -------------------------------------------------------------- payment rule

fun PaymentRuleEntity.toDomain(): PaymentRule = PaymentRule(
    id = id,
    thresholdMeters = thresholdMeters,
    ratePerMeter = ratePerMeter,
    effectiveFromEpochDay = effectiveFromEpochDay,
    currencyCode = currencyCode,
    label = label,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun PaymentRule.toEntity(): PaymentRuleEntity = PaymentRuleEntity(
    id = id,
    thresholdMeters = thresholdMeters,
    ratePerMeter = ratePerMeter,
    effectiveFromEpochDay = effectiveFromEpochDay,
    currencyCode = currencyCode,
    label = label?.trim()?.takeIf { it.isNotEmpty() },
    createdAtEpochMilli = createdAtEpochMilli,
)

// ------------------------------------------------------------------ settings

fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
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

fun AppSettings.toEntity(nowEpochMilli: Long): AppSettingsEntity = AppSettingsEntity(
    id = AppSettingsEntity.SINGLETON_ID,
    defaultProjectId = defaultProjectId,
    defaultSupervisor = defaultSupervisor.trim(),
    defaultWorkerCount = defaultWorkerCount,
    defaultWorkArea = defaultWorkArea.trim(),
    defaultThresholdMeters = defaultThresholdMeters,
    defaultRatePerMeter = defaultRatePerMeter,
    currencyCode = currencyCode,
    usePreviousWorkdayInfo = usePreviousWorkdayInfo,
    reminderEnabled = reminderEnabled,
    reminderMinuteOfDay = reminderMinuteOfDay,
    updatedAtEpochMilli = nowEpochMilli,
)

// ------------------------------------------------------------ report config

fun ReportConfigurationEntity.toDomain(): ReportConfiguration = ReportConfiguration(
    id = id,
    name = name,
    reportType = runCatching { ir.metra.app.domain.model.ReportType.valueOf(reportType) }
        .getOrDefault(ir.metra.app.domain.model.ReportType.MONTHLY),
    projectId = projectId,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    includeDailyTable = includeDailyTable,
    includeNotes = includeNotes,
)

fun ReportConfiguration.toEntity(nowEpochMilli: Long): ReportConfigurationEntity =
    ReportConfigurationEntity(
        id = id,
        name = name.trim(),
        reportType = reportType.name,
        projectId = projectId,
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        includeDailyTable = includeDailyTable,
        includeNotes = includeNotes,
        createdAtEpochMilli = nowEpochMilli,
        updatedAtEpochMilli = nowEpochMilli,
    )

// ------------------------------------------------------------ backup history

fun BackupMetadataEntity.toDomain(): BackupMetadata = BackupMetadata(
    id = id,
    fileName = fileName,
    createdAtEpochMilli = createdAtEpochMilli,
    operation = operation,
    restoreStrategy = restoreStrategy,
    encrypted = encrypted,
    projectCount = projectCount,
    workRecordCount = workRecordCount,
    expenseCount = expenseCount,
    sha256 = sha256,
)

fun LedgerEntryEntity.toDomain() = LedgerEntry(
    id = id,
    kind = LedgerKind.valueOf(kind),
    amount = amount,
    reason = LedgerReason.valueOf(reason),
    entryDateEpochDay = entryDateEpochDay,
    method = method,
    projectName = projectName,
    notes = notes,
    createdAtEpochMilli = createdAtEpochMilli,
    updatedAtEpochMilli = updatedAtEpochMilli,
)

fun LedgerEntry.toEntity() = LedgerEntryEntity(
    id = id,
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
