package ir.metra.app.feature.shooting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.R
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.PersianDigits
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.feature.shooting.data.CableTypeEntity
import ir.metra.app.feature.shooting.data.ShootingOperationDraft
import ir.metra.app.feature.shooting.data.ShootingRepository
import ir.metra.app.feature.shooting.data.ShootingReportEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShootingOperationForm(
    val startType: String = "ODC",
    val startCode: String = "",
    val startMeter: String = "",
    val endType: String = "FAT",
    val endCode: String = "",
    val endMeter: String = "",
    val cableTypeId: Long = 0,
)

data class ShootingUiState(
    val projects: List<Project> = emptyList(),
    val projectId: Long? = null,
    val projectName: String = "",
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: String = "0",
    val notes: String = "",
    val date: Long = JalaliCalendar.today().toEpochDay(),
    val cables: List<CableTypeEntity> = emptyList(),
    val operations: List<ShootingOperationForm> = listOf(ShootingOperationForm()),
    val reports: List<ShootingReportEntity> = emptyList(),
    val editingReportId: Long? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val lastSaveWasUpdate: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ShootingViewModel @Inject constructor(
    private val repository: ShootingRepository,
    private val projectsRepository: ProjectRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(ShootingUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedCableTypes()
            repository.observeCableTypes().collect { types ->
                _state.update { state ->
                    state.copy(
                        cables = types,
                        operations = state.operations.map { operation ->
                            if (operation.cableTypeId == 0L) {
                                operation.copy(cableTypeId = types.firstOrNull()?.id ?: 0L)
                            } else operation
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            projectsRepository.observeActiveProjects().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        viewModelScope.launch {
            repository.observeReports().collect { reports ->
                _state.update { it.copy(reports = reports) }
            }
        }
    }

    fun selectProject(id: Long) {
        viewModelScope.launch {
            val project = projectsRepository.getProject(id) ?: return@launch
            _state.update {
                it.copy(
                    projectId = id,
                    projectName = project.name,
                    workArea = project.workArea,
                    employer = project.employer,
                    supervisor = project.defaultSupervisor,
                )
            }
        }
    }

    fun updateDate(epochDay: Long) = _state.update { it.copy(date = epochDay) }

    fun header(key: String, value: String) = _state.update {
        when (key) {
            "area" -> it.copy(workArea = value)
            "employer" -> it.copy(employer = value)
            "supervisor" -> it.copy(supervisor = value)
            "workers" -> it.copy(workerCount = value.filter(Char::isDigit))
            "notes" -> it.copy(notes = value)
            else -> it
        }
    }

    fun operation(index: Int, value: ShootingOperationForm) = _state.update { state ->
        if (index !in state.operations.indices) state
        else state.copy(operations = state.operations.toMutableList().also { it[index] = value })
    }

    fun addOperation() = _state.update {
        it.copy(operations = it.operations + ShootingOperationForm(cableTypeId = it.cables.firstOrNull()?.id ?: 0L))
    }

    fun removeOperation(index: Int) = _state.update {
        if (it.operations.size == 1 || index !in it.operations.indices) it
        else it.copy(operations = it.operations.filterIndexed { i, _ -> i != index })
    }

    fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val reportWithOperations = repository.reportWithOperations(id)
            if (reportWithOperations == null) {
                _state.update { it.copy(error = strings.string(R.string.shooting_report_missing)) }
                return@launch
            }
            val (report, operations) = reportWithOperations
            _state.update {
                it.copy(
                    projectId = report.projectId,
                    projectName = report.projectName,
                    workArea = report.workArea,
                    employer = report.employer,
                    supervisor = report.supervisor,
                    workerCount = report.workerCount.toString(),
                    notes = report.notes,
                    date = report.workDateEpochDay,
                    operations = operations.map { operation ->
                        ShootingOperationForm(
                            startType = operation.startPointType,
                            startCode = operation.startPointCode,
                            startMeter = operation.startMeter.toString(),
                            endType = operation.endPointType,
                            endCode = operation.endPointCode,
                            endMeter = operation.endMeter.toString(),
                            cableTypeId = operation.cableTypeId,
                        )
                    }.ifEmpty { listOf(ShootingOperationForm(cableTypeId = it.cables.firstOrNull()?.id ?: 0L)) },
                    editingReportId = report.id,
                    saved = false,
                    error = null,
                )
            }
        }
    }

    fun cancelEdit() = _state.update {
        it.copy(
            editingReportId = null,
            operations = listOf(ShootingOperationForm(cableTypeId = it.cables.firstOrNull()?.id ?: 0L)),
            saved = false,
            error = null,
        )
    }

    fun save() {
        val current = state.value
        if (current.saving) return
        val workerCount = PersianDigits.normalizeNumberInput(current.workerCount).toIntOrNull()
        val operations = current.operations.mapNotNull { operation ->
            val startMeter = PersianDigits.normalizeNumberInput(operation.startMeter).toLongOrNull()?.takeIf { it >= 0L }
            val endMeter = PersianDigits.normalizeNumberInput(operation.endMeter).toLongOrNull()?.takeIf { it >= 0L }
            if (
                startMeter == null || endMeter == null ||
                operation.startCode.isBlank() || operation.endCode.isBlank() ||
                operation.cableTypeId == 0L
            ) null else ShootingOperationDraft(
                startPointType = operation.startType,
                startPointCode = operation.startCode.trim(),
                startMeter = startMeter,
                endPointType = operation.endType,
                endPointCode = operation.endCode.trim(),
                endMeter = endMeter,
                cableTypeId = operation.cableTypeId,
            )
        }

        val validationError = when {
            current.projectId == null || current.projectName.isBlank() -> R.string.shooting_validation_project
            workerCount == null -> R.string.shooting_validation_workers
            operations.size != current.operations.size -> R.string.shooting_validation_operations
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = strings.string(validationError)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                repository.save(
                    draft = ir.metra.app.feature.shooting.data.ShootingReportDraft(
                        projectId = current.projectId,
                        projectName = current.projectName,
                        workDateEpochDay = current.date,
                        workArea = current.workArea,
                        employer = current.employer,
                        supervisor = current.supervisor,
                        workerCount = workerCount!!,
                        notes = current.notes,
                        operations = operations,
                    ),
                    reportId = current.editingReportId,
                )
            }.onSuccess {
                _state.update { state ->
                    state.copy(
                        saving = false,
                        saved = true,
                        lastSaveWasUpdate = current.editingReportId != null,
                        editingReportId = null,
                        operations = listOf(ShootingOperationForm(cableTypeId = state.cables.firstOrNull()?.id ?: 0L)),
                    )
                }
            }.onFailure {
                _state.update { it.copy(saving = false, error = strings.string(R.string.shooting_save_failed)) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure {
                    _state.update { it.copy(error = strings.string(R.string.shooting_delete_failed)) }
                }
            if (state.value.editingReportId == id) cancelEdit()
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun consumeDeleted() = _state.update { it.copy(deleted = false) }
    fun consumeError() = _state.update { it.copy(error = null) }
}
