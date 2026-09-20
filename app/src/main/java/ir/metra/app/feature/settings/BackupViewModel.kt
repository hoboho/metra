package ir.metra.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.metra.app.core.backup.BackupDirectoryProvider
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.share.FileSharer
import ir.metra.app.data.local.BackupMetadataDao
import ir.metra.app.data.local.ExpenseDao
import ir.metra.app.domain.backup.BackupManager
import ir.metra.app.domain.backup.RestorePreview
import ir.metra.app.domain.backup.RestoreStrategy
import ir.metra.app.domain.model.ReportType
import ir.metra.app.domain.report.ReportFileWriter
import ir.metra.app.domain.report.ReportRequest
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val encryptBackup: Boolean = false,
    val working: Boolean = false,
    val preview: RestorePreview? = null,
    val strategy: RestoreStrategy = RestoreStrategy.ADD_ONLY,
    val message: String? = null,
)

/**
 * Backup / restore / export orchestration.
 *
 * All file work happens on the IO dispatcher. The preview-then-confirm sequence
 * is mandatory: nothing is written until the user has seen what the file
 * contains and chosen a strategy.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val backupDirectoryProvider: BackupDirectoryProvider,
    private val fileSharer: FileSharer,
    private val reportFileWriter: ReportFileWriter,
    private val dateFormatter: DateFormatter,
    private val strings: StringProvider,
    private val numberFormatter: NumberFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Passphrase captured just before an encrypted export is launched. */
    private var pendingExportPassphrase: String? = null

    /** Uri chosen for the pending restore. */
    private var pendingRestoreUri: Uri? = null

    fun suggestedFileName(): String =
        "metra-backup-${dateFormatter.format(JalaliCalendar.today().toEpochDay(), persianDigits = false)
            .replace('/', '-')}.metra"

    fun setEncryptBackup(enabled: Boolean) {
        _state.update { it.copy(encryptBackup = enabled) }
    }

    fun setPendingPassphrase(passphrase: String?) {
        pendingExportPassphrase = passphrase
    }

    fun setStrategy(strategy: RestoreStrategy) {
        _state.update { it.copy(strategy = strategy) }
    }

    fun clearPreview() {
        _state.update { it.copy(preview = null) }
        pendingRestoreUri = null
    }

    /** Writes the backup to the user-chosen document Uri. */
    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                val temp = File(context.cacheDir, "backups").let { dir ->
                    dir.mkdirs()
                    File(dir, suggestedFileName())
                }
                val exported = backupManager.export(temp, pendingExportPassphrase)
                exported.fold(
                    onSuccess = { file ->
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                file.inputStream().use { input -> input.copyTo(output) }
                            }
                            file.delete()
                            true
                        }.getOrElse { false }
                    },
                    onFailure = { false },
                )
            }
            pendingExportPassphrase = null
            _state.update {
                it.copy(
                    working = false,
                    message = strings.string(
                        if (result) R.string.msg_backup_ready else R.string.msg_backup_failed,
                    ),
                )
            }
        }
    }

    /** Reads the chosen file and shows what a restore would do. */
    fun previewRestore(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            pendingRestoreUri = uri
            val result = withContext(Dispatchers.IO) {
                val temp = File(context.cacheDir, "restore-input.metra")
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                }.getOrElse { false }
                if (!copied) {
                    null
                } else {
                    backupManager.preview(temp).getOrNull()
                }
            }
            _state.update {
                it.copy(
                    working = false,
                    preview = result,
                    message = if (result == null) strings.string(R.string.msg_read_file_failed) else null,
                )
            }
        }
    }

    /** Applies the chosen strategy to the current database. */
    fun confirmRestore(passphrase: String?) {
        val uri = pendingRestoreUri
        val preview = _state.value.preview
        if (uri == null || preview == null) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null, preview = null) }
            val result = withContext(Dispatchers.IO) {
                val temp = File(context.cacheDir, "restore-input.metra")
                backupManager.restore(
                    sourceFile = temp,
                    strategy = _state.value.strategy,
                    passphrase = passphrase,
                    safetySnapshotDirectory = backupDirectoryProvider.backupDirectory(),
                )
            }
            result.fold(
                onSuccess = { outcome ->
                    _state.update {
                        it.copy(
                            working = false,
                            message = strings.string(
                            R.string.msg_backup_restored,
                            outcome.projectsImported.toString(),
                            outcome.workRecordsImported.toString(),
                            outcome.expensesImported.toString(),
                        ),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(working = false, message = error.metraError.userMessage) }
                },
            )
        }
    }

    /** CSV of everything recorded so far. */
    fun exportCsv() {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val request = ReportRequest(
                type = ReportType.RANGE,
                startEpochDay = Long.MIN_VALUE / 4,
                endEpochDay = Long.MAX_VALUE / 4,
            )
            val result = withContext(Dispatchers.IO) { reportFileWriter.writeCsv(request) }
            result.fold(
                onSuccess = { file ->
                    fileSharer.share(file, strings.string(R.string.share_subject_csv), FileSharer.MIME_CSV)
                    _state.update { it.copy(working = false, message = strings.string(R.string.msg_csv_ready)) }
                },
                onFailure = { error ->
                    _state.update { it.copy(working = false, message = error.metraError.userMessage) }
                },
            )
        }
    }

    /** Full JSON dump of the dataset. */
    fun exportJson() {
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                val target = File(
                    File(context.filesDir, "reports").apply { mkdirs() },
                    "metra-export-${System.currentTimeMillis()}.json",
                )
                backupManager.exportJson(target)
            }
            result.fold(
                onSuccess = { file ->
                    fileSharer.share(file, strings.string(R.string.share_subject_json), FileSharer.MIME_JSON)
                    _state.update { it.copy(working = false, message = strings.string(R.string.msg_json_ready)) }
                },
                onFailure = { error ->
                    _state.update { it.copy(working = false, message = error.metraError.userMessage) }
                },
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

data class DatabaseInfoUiState(
    val workRecords: String = "",
    val projects: String = "",
    val expenses: String = "",
    val rules: String = "",
    val backups: String = "",
    val loading: Boolean = true,
)

/**
 * Read-only view of what the local database actually holds.
 */
@HiltViewModel
class DatabaseInfoViewModel @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val projectRepository: ProjectRepository,
    private val expenseDao: ExpenseDao,
    private val backupMetadataDao: BackupMetadataDao,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val numberFormatter: NumberFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(DatabaseInfoUiState())
    val state: StateFlow<DatabaseInfoUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val workRecords = workRecordRepository.countAll()
            val projects = projectRepository.getProjects().size
            val expenses = expenseDao.getAll().size
            val rules = paymentRuleRepository.getRules().size
            val backups = backupMetadataDao.getAll().size
            _state.update {
                it.copy(
                    workRecords = numberFormatter.formatPersian(workRecords.toLong()),
                    projects = numberFormatter.formatPersian(projects.toLong()),
                    expenses = numberFormatter.formatPersian(expenses.toLong()),
                    rules = numberFormatter.formatPersian(rules.toLong()),
                    backups = numberFormatter.formatPersian(backups.toLong()),
                    loading = false,
                )
            }
        }
    }
}
