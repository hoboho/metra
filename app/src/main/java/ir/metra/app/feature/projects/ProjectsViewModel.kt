package ir.metra.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.metraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val message: String? = null,
)

data class ProjectEditorUiState(
    val id: Long = 0L,
    val name: String = "",
    val employer: String = "",
    val workArea: String = "",
    val defaultSupervisor: String = "",
    val defaultWorkerCount: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val strings: StringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
    }

    fun setActive(projectId: Long, active: Boolean) {
        viewModelScope.launch {
            projectRepository.setActive(projectId, active).onFailure { error ->
                _state.update { it.copy(message = error.metraError.userMessage) }
            }
        }
    }

    fun delete(projectId: Long) {
        viewModelScope.launch {
            projectRepository.delete(projectId).fold(
                onSuccess = { _state.update { it.copy(message = strings.string(R.string.msg_project_deleted)) } },
                onFailure = { error -> _state.update { it.copy(message = error.metraError.userMessage) } },
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
    private val strings: StringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectEditorUiState())
    val state: StateFlow<ProjectEditorUiState> = _state.asStateFlow()

    /** Loads an existing project, or resets to a blank form for a new one. */
    fun load(projectId: Long) {
        viewModelScope.launch {
            if (projectId == 0L) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val project = projectRepository.getProject(projectId)
            if (project == null) {
                _state.update { it.copy(loading = false, errorMessage = strings.string(R.string.msg_project_not_found)) }
                return@launch
            }
            _state.update {
                it.copy(
                    id = project.id,
                    name = project.name,
                    employer = project.employer,
                    workArea = project.workArea,
                    defaultSupervisor = project.defaultSupervisor,
                    defaultWorkerCount = project.defaultWorkerCount.takeIf { count -> count > 0 }?.toString() ?: "",
                    notes = project.notes,
                    isActive = project.isActive,
                    loading = false,
                )
            }
        }
    }

    fun onFieldChange(transform: (ProjectEditorUiState) -> ProjectEditorUiState) {
        _state.update { transform(it).copy(errorMessage = null) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            if (current.name.isBlank()) {
                _state.update { it.copy(errorMessage = strings.string(R.string.msg_project_name_required)) }
                return@launch
            }
            _state.update { it.copy(saving = true) }
            val now = clock.nowEpochMilli()
            val project = Project(
                id = current.id,
                name = current.name,
                employer = current.employer,
                workArea = current.workArea,
                defaultSupervisor = current.defaultSupervisor,
                defaultWorkerCount = current.defaultWorkerCount.toIntOrNull() ?: 0,
                notes = current.notes,
                isActive = current.isActive,
                createdAtEpochMilli = now,
                updatedAtEpochMilli = now,
            )
            projectRepository.upsert(project).fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { error ->
                    _state.update { it.copy(saving = false, errorMessage = error.metraError.userMessage) }
                },
            )
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }
}
