package ir.metra.app.feature.shooting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.feature.shooting.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShootingOperationForm(
    val startType: String = "ODC", val startCode: String = "", val startMeter: String = "",
    val endType: String = "FAT", val endCode: String = "", val endMeter: String = "",
    val cableTypeId: Long = 0,
)
data class ShootingUiState(
    val projects: List<Project> = emptyList(), val projectId: Long? = null, val projectName: String = "",
    val workArea: String = "", val employer: String = "", val supervisor: String = "",
    val workerCount: String = "0", val notes: String = "",
    val date: Long = JalaliCalendar.today().toEpochDay(),
    val cables: List<CableTypeEntity> = emptyList(), val operations: List<ShootingOperationForm> = listOf(ShootingOperationForm()),
    val reports: List<ShootingReportEntity> = emptyList(), val saving: Boolean = false,
    val saved: Boolean = false, val error: String? = null,
)
@HiltViewModel
class ShootingViewModel @Inject constructor(
    private val repository: ShootingRepository, private val projectsRepository: ProjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShootingUiState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            repository.seedCableTypes()
            repository.observeCableTypes().collect { types ->
                _state.update { s -> s.copy(cables = types, operations = s.operations.map { if (it.cableTypeId == 0L) it.copy(cableTypeId = types.firstOrNull()?.id ?: 0L) else it }) }
            }
        }
        viewModelScope.launch { projectsRepository.observeActiveProjects().collect { p -> _state.update { it.copy(projects = p) } } }
        viewModelScope.launch { repository.observeReports().collect { r -> _state.update { it.copy(reports = r) } } }
    }
    fun selectProject(id: Long) { viewModelScope.launch {
        val p = projectsRepository.getProject(id) ?: return@launch
        _state.update { it.copy(projectId = id, projectName = p.name, workArea = p.workArea, employer = p.employer, supervisor = p.defaultSupervisor) }
    } }
    fun header(key: String, value: String) = _state.update {
        when(key) {
            "area" -> it.copy(workArea=value); "employer" -> it.copy(employer=value)
            "supervisor" -> it.copy(supervisor=value); "workers" -> it.copy(workerCount=value.filter(Char::isDigit))
            "notes" -> it.copy(notes=value); else -> it
        }
    }
    fun operation(index: Int, value: ShootingOperationForm) = _state.update {
        it.copy(operations = it.operations.toMutableList().also { list -> list[index] = value })
    }
    fun addOperation() = _state.update { it.copy(operations = it.operations + ShootingOperationForm(cableTypeId = it.cables.firstOrNull()?.id ?: 0L)) }
    fun removeOperation(index: Int) = _state.update { if(it.operations.size == 1) it else it.copy(operations = it.operations.filterIndexed { i, _ -> i != index }) }
    fun save() {
        val s = state.value
        val ops = s.operations.mapNotNull { o ->
            val a=o.startMeter.toLongOrNull(); val b=o.endMeter.toLongOrNull()
            if(a==null || b==null || o.startCode.isBlank() || o.endCode.isBlank() || o.cableTypeId==0L) null
            else ShootingOperationDraft(o.startType,o.startCode.trim(),a,o.endType,o.endCode.trim(),b,o.cableTypeId)
        }
        if(s.projectName.isBlank() || ops.size != s.operations.size) {
            _state.update { it.copy(error="پروژه و تمام مشخصات عملیات را کامل کنید.") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving=true,error=null) }
            runCatching { repository.save(ShootingReportDraft(s.projectId,s.projectName,s.date,s.workArea,s.employer,s.supervisor,s.workerCount.toIntOrNull() ?: 0,s.notes,ops)) }
                .onSuccess { _state.update { it.copy(saving=false,saved=true,operations=listOf(ShootingOperationForm(cableTypeId=it.cables.firstOrNull()?.id ?: 0L))) } }
                .onFailure { e -> _state.update { it.copy(saving=false,error=e.message ?: "ذخیره گزارش ناموفق بود") } }
        }
    }
    fun delete(id: Long) { viewModelScope.launch { repository.delete(id) } }
    fun consumeSaved() = _state.update { it.copy(saved=false) }
    fun consumeError() = _state.update { it.copy(error=null) }
}
