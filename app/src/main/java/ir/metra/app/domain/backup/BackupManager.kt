package ir.metra.app.domain.backup

import ir.metra.app.data.preferences.ThemeMode
import ir.metra.app.data.preferences.UserPreferencesRepository
import ir.metra.app.core.notification.ReminderScheduler
import kotlinx.coroutines.flow.first
import ir.metra.app.core.backup.PreferencesDto
import ir.metra.app.core.backup.toDomain
import ir.metra.app.core.backup.AppSettingsDto
import ir.metra.app.core.backup.BackupCipher
import ir.metra.app.core.backup.BackupCounts
import ir.metra.app.core.backup.AppInfoProvider
import ir.metra.app.core.backup.BackupPayload
import ir.metra.app.core.backup.ExpenseDto
import ir.metra.app.core.backup.PaymentRuleDto
import ir.metra.app.core.backup.ProjectDto
import ir.metra.app.core.backup.ReportConfigurationDto
import ir.metra.app.core.backup.toDto
import ir.metra.app.core.backup.UserProfileDto
import ir.metra.app.core.backup.WorkRecordDto
import ir.metra.app.core.backup.toDto
import ir.metra.app.core.backup.BackupDirectoryProvider
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.data.local.BackupMetadataDao
import ir.metra.app.data.local.BackupMetadataEntity
import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.ReportConfiguration
import ir.metra.app.domain.model.ReportType
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.LedgerRepository
import ir.metra.app.domain.repository.ExpenseRepository
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export / import of the whole local dataset.
 *
 * Design rules enforced here:
 *  - A restore always writes a **safety snapshot** of the current database first.
 *  - The UI must obtain a [RestorePreview] and an explicit [RestoreStrategy]
 *    before [restore] will touch anything.
 *  - Projects are matched by name so their work records keep pointing at the
 *    right project after an import.
 *  - Work records are imported with their original rule snapshots intact, so
 *    restoring never re-prices history.
 */
@Singleton
class BackupManager @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val projectRepository: ProjectRepository,
    private val expenseRepository: ExpenseRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backupMetadataDao: BackupMetadataDao,
    private val backupCipher: BackupCipher,
    private val dateFormatter: DateFormatter,
    private val clock: Clock,
    private val appInfo: AppInfoProvider,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ------------------------------------------------------------------ export

    /**
     * Writes a backup file.
     *
     * @param passphrase when non-null the file is sealed with AES-256-GCM.
     */
    suspend fun export(targetFile: File, passphrase: String? = null): MetraResult<File> = runCatching {
        val now = clock.nowEpochMilli()
        val projects = projectRepository.getProjects()

        val records = ArrayList<WorkRecord>()
        var offset = 0
        while (true) {
            val page = workRecordRepository.getPage(
                startEpochDay = Long.MIN_VALUE / 4,
                endEpochDay = Long.MAX_VALUE / 4,
                limit = PAGE_SIZE,
                offset = offset,
            )
            if (page.isEmpty()) break
            records += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        // Expenses are keyed by the *index* of their work record in `records`,
        // which is stable inside the file and independent of database ids.
        val expenses = ArrayList<ExpenseDto>()
        records.forEachIndexed { index, record ->
            val (_, recordExpenses) = workRecordRepository.getRecordWithExpenses(record.id) ?: return@forEachIndexed
            for (expense in recordExpenses) {
                expenses += expense.toDto(workRecordIndex = index)
            }
        }

        val payload = BackupPayload(
            formatVersion = BackupPayload.CURRENT_FORMAT_VERSION,
            appVersionName = appInfo.info().versionName,
            appVersionCode = appInfo.info().versionCode,
            exportedAtEpochMilli = now,
            profile = userRepository.getProfile().toDto(),
            settings = settingsRepository.getSettings().toDto(),
            paymentRules = paymentRuleRepository.getRules().map { it.toDto() },
            projects = projects.map { it.toDto() },
            workRecords = records.map { it.toDto() },
            expenses = expenses,
            reportConfigurations = emptyList(),
            ledgerEntries = ledgerRepository.getEntries().map { it.toDto() },
            preferences = preferencesRepository.preferences.first().let { prefs ->
                PreferencesDto(
                    themeMode = prefs.themeMode.name,
                    reminderEnabled = prefs.reminderEnabled,
                    reminderMinuteOfDay = prefs.reminderMinuteOfDay,
                    usePreviousWorkdayInfo = prefs.usePreviousWorkdayInfo,
                    onboardingCompleted = prefs.onboardingCompleted,
                )
            },
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val bytes = if (passphrase.isNullOrBlank()) {
            encoded
        } else {
            backupCipher.encrypt(encoded, passphrase).getOrThrow()
        }
        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(bytes)

        backupMetadataDao.upsert(
            BackupMetadataEntity(
                fileName = targetFile.name,
                createdAtEpochMilli = now,
                operation = BackupMetadataEntity.OPERATION_EXPORT,
                encrypted = !passphrase.isNullOrBlank(),
                projectCount = payload.projects.size,
                workRecordCount = payload.workRecords.size,
                expenseCount = payload.expenses.size,
                sha256 = sha256(bytes),
            ),
        )
        success(targetFile)
    }.fold({ it }, { failure(MetraError.Backup(it.message ?: "unknown")) })

    // ------------------------------------------------------------------ import

    /**
     * Reads a backup file without modifying anything, so the UI can show the
     * user exactly what is about to happen.
     */
    suspend fun preview(sourceFile: File, passphrase: String? = null): MetraResult<RestorePreview> = runCatching {
        val payload = readPayload(sourceFile, passphrase).getOrThrow()
        val existingProjects = projectRepository.getProjects().size
        val existingRecords = workRecordRepository.countAll()
        val existingExpenses = payload.expenses.size // placeholder refined below
        success(
            RestorePreview(
                formatVersion = payload.formatVersion,
                exportedAtEpochMilli = payload.exportedAtEpochMilli,
                appVersionName = payload.appVersionName,
                incomingProjects = payload.projects.size,
                incomingWorkRecords = payload.workRecords.size,
                incomingExpenses = payload.expenses.size,
                incomingPaymentRules = payload.paymentRules.size,
                existingProjects = existingProjects,
                existingWorkRecords = existingRecords,
                existingExpenses = existingExpenses,
                encrypted = backupCipher.isEncrypted(sourceFile.readBytes()),
                formatSupported = payload.formatVersion <= BackupPayload.CURRENT_FORMAT_VERSION,
            ),
        )
    }.fold({ it }, { failure(MetraError.Restore(it.message ?: "preview failed")) })

    /**
     * Applies [strategy] to the current database.
     *
     * Always writes a safety snapshot first and records the outcome in
     * `backup_metadata`.
     */
    suspend fun restore(
        sourceFile: File,
        strategy: RestoreStrategy,
        passphrase: String? = null,
        safetySnapshotDirectory: File,
    ): MetraResult<RestoreOutcome> = runCatching {
        val payload = readPayload(sourceFile, passphrase).getOrThrow()
        if (payload.formatVersion > BackupPayload.CURRENT_FORMAT_VERSION) {
            return failure(
                MetraError.InvalidBackup(
                    "این فایل پشتیبان با نسخهٔ ${payload.appVersionName} ساخته شده و با نسخهٔ فعلی سازگار نیست",
                ),
            )
        }

        // 1. Safety snapshot of whatever is there now.
        val snapshot = File(safetySnapshotDirectory, "metra-safety-${System.currentTimeMillis()}.metra")
        val snapshotResult = export(snapshot)
        val snapshotPath = snapshotResult.getOrNull()?.absolutePath

        val now = clock.nowEpochMilli()

        // 2. Optionally clear current data.
        if (strategy == RestoreStrategy.REPLACE) {
            for (project in projectRepository.getProjects()) {
                projectRepository.delete(project.id).getOrThrow()
            }
        }

        // 3. Projects, keyed by name so work records can be relinked.
        val existingProjectsByName = projectRepository.getProjects().associateBy { it.name }
        val projectNameToId = HashMap<String, Long>()
        var projectsImported = 0
        for (dto in payload.projects) {
            val existing = existingProjectsByName[dto.name]
            if (existing != null) {
                projectNameToId[dto.name] = existing.id
                if (strategy == RestoreStrategy.MERGE) {
                    projectRepository.upsert(
                        existing.copy(
                            employer = dto.employer,
                            workArea = dto.workArea,
                            defaultSupervisor = dto.defaultSupervisor,
                            defaultWorkerCount = dto.defaultWorkerCount,
                            notes = dto.notes,
                            isActive = dto.isActive,
                            updatedAtEpochMilli = now,
                        ),
                    ).getOrThrow()
                }
                continue
            }
            val id = projectRepository.upsert(
                Project(
                    name = dto.name,
                    employer = dto.employer,
                    workArea = dto.workArea,
                    defaultSupervisor = dto.defaultSupervisor,
                    defaultWorkerCount = dto.defaultWorkerCount,
                    notes = dto.notes,
                    isActive = dto.isActive,
                    createdAtEpochMilli = dto.createdAtEpochMilli,
                    updatedAtEpochMilli = dto.updatedAtEpochMilli,
                ),
            ).getOrThrow()
            projectNameToId[dto.name] = id
            projectsImported += 1
        }

        // 4. Payment rules: append versions that are not already present.
        val existingRuleKeys = paymentRuleRepository.getRules()
            .map { it.effectiveFromEpochDay to it.ratePerMeter }
            .toSet()
        var rulesImported = 0
        for (dto in payload.paymentRules) {
            val key = dto.effectiveFromEpochDay to dto.ratePerMeter
            if (key in existingRuleKeys) continue
            paymentRuleRepository.addRule(
                PaymentRule(
                    thresholdMeters = dto.thresholdMeters,
                    ratePerMeter = dto.ratePerMeter,
                    effectiveFromEpochDay = dto.effectiveFromEpochDay,
                    currencyCode = dto.currencyCode,
                    label = dto.label,
                    createdAtEpochMilli = dto.createdAtEpochMilli,
                ),
            ).getOrThrow()
            rulesImported += 1
        }

        // 5. Work records. Snapshots are copied verbatim so history is never
        //    re-priced by the rules that happen to be current today.
        var recordsImported = 0
        val newRecordIds = LongArray(payload.workRecords.size)
        for ((index, dto) in payload.workRecords.withIndex()) {
            val record = WorkRecord(
                projectId = projectNameToId[dto.projectName],
                projectName = dto.projectName,
                workArea = dto.workArea,
                employer = dto.employer,
                supervisor = dto.supervisor,
                workerCount = dto.workerCount,
                workDateEpochDay = dto.workDateEpochDay,
                dailyMeters = dto.dailyMeters,
                additionalMeters = dto.additionalMeters,
                thresholdMetersSnapshot = dto.thresholdMetersSnapshot,
                ratePerMeterSnapshot = dto.ratePerMeterSnapshot,
                additionalMeterPayment = dto.additionalMeterPayment,
                expenseTotal = dto.expenseTotal,
                notes = dto.notes,
                workStartMinuteOfDay = dto.workStartMinuteOfDay,
                workEndMinuteOfDay = dto.workEndMinuteOfDay,
                createdAtEpochMilli = dto.createdAtEpochMilli,
                updatedAtEpochMilli = dto.updatedAtEpochMilli,
            )
            newRecordIds[index] = workRecordRepository.upsert(record).getOrThrow()
            recordsImported += 1
        }

        // 8. DataStore preferences. Restored last so a failure in the row
        // imports above cannot leave the UI configured for data that is absent.
        payload.preferences?.let { prefs ->
            val theme = runCatching { ThemeMode.valueOf(prefs.themeMode) }
                .getOrDefault(ThemeMode.SYSTEM)
            preferencesRepository.setThemeMode(theme)
            preferencesRepository.setReminder(prefs.reminderEnabled, prefs.reminderMinuteOfDay)
            preferencesRepository.setUsePreviousWorkdayInfo(prefs.usePreviousWorkdayInfo)
            preferencesRepository.setOnboardingCompleted(prefs.onboardingCompleted)
            // The reminder schedule has to be re-armed by hand: the alarm lives
            // in the OS, not in the database, so it does not come back on its own.
            if (prefs.reminderEnabled) {
                reminderScheduler.schedule(prefs.reminderMinuteOfDay)
            }
        }

        // 7. Ledger movements. Independent of work records, so they restore
        // even when a receipt's project no longer exists.
        for (dto in payload.ledgerEntries) {
            ledgerRepository.upsert(dto.toDomain(now)).getOrThrow()
        }

        // 6. Expenses, reattached through the index mapping.
        var expensesImported = 0
        for (dto in payload.expenses) {
            val workRecordId = newRecordIds.getOrElse(dto.workRecordIndex) { 0L }
            if (workRecordId == 0L) continue
            expenseRepository.add(
                Expense(
                    workRecordId = workRecordId,
                    amount = dto.amount,
                    category = runCatching { ExpenseCategory.valueOf(dto.category) }
                        .getOrDefault(ExpenseCategory.OTHER),
                    description = dto.description,
                    createdAtEpochMilli = dto.createdAtEpochMilli,
                ),
            ).getOrThrow()
            expensesImported += 1
        }

        // 7. Profile / settings: only fill in what the user has not set.
        payload.profile?.let { dto ->
            val current = userRepository.getProfile()
            if (current.fullName.isBlank() || strategy == RestoreStrategy.REPLACE) {
                userRepository.saveProfile(
                    UserProfile(
                        fullName = dto.fullName,
                        companyName = dto.companyName,
                        employeeCode = dto.employeeCode,
                        reportFooterNote = dto.reportFooterNote,
                        onboardingCompleted = dto.onboardingCompleted || current.onboardingCompleted,
                    ),
                ).getOrThrow()
            }
        }
        payload.settings?.let { dto ->
            val current = settingsRepository.getSettings()
            if (current.defaultSupervisor.isBlank() || strategy == RestoreStrategy.REPLACE) {
                settingsRepository.saveSettings(
                    AppSettings(
                        defaultProjectId = projectNameToId[current.defaultWorkArea] ?: current.defaultProjectId,
                        defaultSupervisor = dto.defaultSupervisor,
                        defaultWorkerCount = dto.defaultWorkerCount,
                        defaultWorkArea = dto.defaultWorkArea,
                        defaultThresholdMeters = dto.defaultThresholdMeters,
                        defaultRatePerMeter = dto.defaultRatePerMeter,
                        currencyCode = dto.currencyCode,
                        usePreviousWorkdayInfo = dto.usePreviousWorkdayInfo,
                        reminderEnabled = current.reminderEnabled,
                        reminderMinuteOfDay = current.reminderMinuteOfDay,
                    ),
                ).getOrThrow()
            }
        }

        backupMetadataDao.upsert(
            BackupMetadataEntity(
                fileName = sourceFile.name,
                createdAtEpochMilli = clock.nowEpochMilli(),
                operation = BackupMetadataEntity.OPERATION_IMPORT,
                restoreStrategy = strategy.name,
                encrypted = !passphrase.isNullOrBlank(),
                projectCount = payload.projects.size,
                workRecordCount = payload.workRecords.size,
                expenseCount = payload.expenses.size,
                sha256 = sha256(sourceFile.readBytes()),
                safetySnapshotPath = snapshotPath,
            ),
        )

        success(
            RestoreOutcome(
                strategy = strategy,
                projectsImported = projectsImported,
                workRecordsImported = recordsImported,
                expensesImported = expensesImported,
                paymentRulesImported = rulesImported,
                safetySnapshotFile = snapshotPath,
            ),
        )
    }.fold({ it }, { failure(MetraError.Restore(it.message ?: "unknown")) })

    /** Full JSON dump of every work record — the portable export format. */
    suspend fun exportJson(targetFile: File): MetraResult<File> = runCatching {
        val payload = collectPayload().getOrThrow()
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(json.encodeToString(BackupPayload.serializer(), payload), Charsets.UTF_8)
        success(targetFile)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "json export")) })

    // ----------------------------------------------------------------- helpers

    private suspend fun readPayload(sourceFile: File, passphrase: String?): MetraResult<BackupPayload> {
        if (!sourceFile.exists()) {
            return failure(MetraError.InvalidBackup("فایل یافت نشد"))
        }
        val bytes = sourceFile.readBytes()
        if (backupCipher.isEncrypted(bytes)) {
            if (passphrase.isNullOrBlank()) {
                return failure(MetraError.WrongPassphrase)
            }
            val plain = backupCipher.decrypt(bytes, passphrase).getOrNull()
                ?: return failure(MetraError.WrongPassphrase)
            return decode(plain)
        }
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): MetraResult<BackupPayload> = runCatching {
        success(json.decodeFromString(BackupPayload.serializer(), String(bytes, Charsets.UTF_8)))
    }.fold({ it }, { failure(MetraError.InvalidBackup(it.message ?: "unparseable")) })

    private suspend fun collectPayload(): MetraResult<BackupPayload> = runCatching {
        val now = clock.nowEpochMilli()
        val projects = projectRepository.getProjects()
        val records = ArrayList<WorkRecord>()
        var offset = 0
        while (true) {
            val page = workRecordRepository.getPage(
                startEpochDay = Long.MIN_VALUE / 4,
                endEpochDay = Long.MAX_VALUE / 4,
                limit = PAGE_SIZE,
                offset = offset,
            )
            if (page.isEmpty()) break
            records += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        val expenses = ArrayList<ExpenseDto>()
        records.forEachIndexed { index, record ->
            val (_, recordExpenses) = workRecordRepository.getRecordWithExpenses(record.id) ?: return@forEachIndexed
            for (expense in recordExpenses) {
                expenses += expense.toDto(workRecordIndex = index)
            }
        }
        success(
            BackupPayload(
                formatVersion = BackupPayload.CURRENT_FORMAT_VERSION,
                appVersionName = appInfo.info().versionName,
                appVersionCode = appInfo.info().versionCode,
                exportedAtEpochMilli = now,
                profile = userRepository.getProfile().toDto(),
                settings = settingsRepository.getSettings().toDto(),
                paymentRules = paymentRuleRepository.getRules().map { it.toDto() },
                projects = projects.map { it.toDto() },
                workRecords = records.map { it.toDto() },
                expenses = expenses,
            ),
        )
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "collect")) })

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val PAGE_SIZE = 500
    }
}
