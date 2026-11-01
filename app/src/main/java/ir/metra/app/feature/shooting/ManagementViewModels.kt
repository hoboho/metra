package ir.metra.app.feature.shooting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.feature.shooting.data.CompanyExpenseEntity
import ir.metra.app.feature.shooting.data.ShootingRepository
import ir.metra.app.feature.shooting.data.ShootingReportEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CombinedReportState(
    val trench: List<ir.metra.app.domain.model.WorkRecord> = emptyList(),
    val shooting: List<ShootingReportEntity> = emptyList(),
    val trenchMeters: Long = 0,
    val shootingMeters: Long = 0,
    val loading: Boolean = false,
)
@HiltViewModel
class CombinedReportViewModel @Inject constructor(
    private val workRecords: WorkRecordRepository,
    private val shooting: ShootingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CombinedReportState())
    val state = _state.asStateFlow()
    fun load(start: Long, end: Long, projectId: Long? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val trench = workRecords.getRecordsInRangeAscending(start, end).filter { projectId == null || it.projectId == projectId }
            val shot = shooting.combinedReports(start, end, projectId)
            _state.update { it.copy(
                trench = trench, shooting = shot,
                trenchMeters = trench.sumOf { it.dailyMeters.toLong() },
                shootingMeters = shot.sumOf { it.totalMeters },
                loading = false,
            ) }
        }
    }
}

data class CompanyExpenseUiState(
    val projects: List<Project> = emptyList(),
    val expenses: List<Pair<CompanyExpenseEntity, Long>> = emptyList(),
    val projectId: Long? = null,
    val projectName: String = "",
    val date: Long = java.time.LocalDate.now().toEpochDay(),
    val activity: String = "GENERAL",
    val category: String = "سوخت خودرو",
    val amount: String = "",
    val description: String = "",
    val receiptReference: String = "",
    val settlementAmounts: Map<Long, String> = emptyMap(),
    val error: String? = null,
)
@HiltViewModel
class CompanyExpenseViewModel @Inject constructor(
    private val shooting: ShootingRepository,
    private val projects: ProjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CompanyExpenseUiState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch { projects.observeActiveProjects().collect { list -> _state.update { it.copy(projects=list) } } }
        refresh()
    }
    fun update(transform: (CompanyExpenseUiState) -> CompanyExpenseUiState) = _state.update(transform)
    fun selectProject(id: Long) { viewModelScope.launch {
        val p=projects.getProject(id) ?: return@launch
        _state.update { it.copy(projectId=id,projectName=p.name) }
    } }
    fun refresh() { viewModelScope.launch {
        val rows=shooting.companyExpenses(0L,Long.MAX_VALUE)
        _state.update { it.copy(expenses=rows) }
    } }
    fun save() {
        val s=state.value
        val amount=s.amount.toLongOrNull()
        if(amount==null || amount<=0 || s.projectName.isBlank()) {
            _state.update { it.copy(error="پروژه و مبلغ معتبر را وارد کنید.") }; return
        }
        viewModelScope.launch {
            runCatching { shooting.addCompanyExpense(CompanyExpenseEntity(
                projectId=s.projectId,projectName=s.projectName,expenseDateEpochDay=s.date,
                activity=s.activity,category=s.category,amount=amount,description=s.description,
                receiptReference=s.receiptReference,createdAtEpochMilli=System.currentTimeMillis(),
            )) }.onSuccess {
                _state.update { it.copy(amount="",description="",receiptReference="",error=null) }; refresh()
            }.onFailure { e -> _state.update { it.copy(error=e.message ?: "خطا در ثبت هزینه") } }
        }
    }
    fun settle(expenseId: Long, date: Long, notes: String = "") {
        val amount=state.value.settlementAmounts[expenseId]?.toLongOrNull()
        if(amount==null || amount<=0) { _state.update { it.copy(error="مبلغ تسویه معتبر نیست.") }; return }
        viewModelScope.launch {
            runCatching { shooting.addSettlement(expenseId,date,amount,notes) }
                .onSuccess { _state.update { it.copy(settlementAmounts=it.settlementAmounts - expenseId,error=null) }; refresh() }
                .onFailure { e -> _state.update { it.copy(error=e.message ?: "خطا در ثبت تسویه") } }
        }
    }
    fun delete(id: Long) { viewModelScope.launch { shooting.deleteCompanyExpense(id); refresh() } }
    fun consumeError() = _state.update { it.copy(error=null) }
}
