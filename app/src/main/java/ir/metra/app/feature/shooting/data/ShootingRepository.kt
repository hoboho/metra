package ir.metra.app.feature.shooting.data

import androidx.room.withTransaction
import ir.metra.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ShootingOperationDraft(
    val startPointType: String, val startPointCode: String, val startMeter: Long,
    val endPointType: String, val endPointCode: String, val endMeter: Long,
    val cableTypeId: Long,
) {
    val meters: Long get() = abs(endMeter - startMeter)
}
data class ShootingReportDraft(
    val projectId: Long?, val projectName: String, val workDateEpochDay: Long,
    val workArea: String, val employer: String, val supervisor: String,
    val workerCount: Int = 0, val notes: String = "",
    val operations: List<ShootingOperationDraft>,
)

@Singleton
class ShootingRepository @Inject constructor(
    private val database: ShootingDatabase,
    private val projects: ProjectRepository,
) {
    private val dao get() = database.shootingDao()
    fun observeReports(): Flow<List<ShootingReportEntity>> = dao.observeReports()
    fun observeCableTypes(): Flow<List<CableTypeEntity>> = dao.observeCableTypes()

    suspend fun seedCableTypes() {
        dao.insertCableTypes(listOf(6, 12, 24, 48, 96, 144).map { CableTypeEntity(coreCount = it, label = it.toString() + " کور") })
    }
    suspend fun project(id: Long) = projects.getProject(id)
    suspend fun save(draft: ShootingReportDraft): Long {
        require(draft.projectName.isNotBlank())
        require(draft.workerCount >= 0)
        require(draft.operations.isNotEmpty())
        require(draft.operations.all { it.startMeter >= 0 && it.endMeter >= 0 && it.startPointCode.isNotBlank() && it.endPointCode.isNotBlank() })
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val total = draft.operations.sumOf { it.meters }
            val id = dao.insertReport(ShootingReportEntity(
                projectId = draft.projectId, projectName = draft.projectName,
                workDateEpochDay = draft.workDateEpochDay, workArea = draft.workArea,
                employer = draft.employer, supervisor = draft.supervisor,
                workerCount = draft.workerCount, notes = draft.notes, totalMeters = total,
                createdAtEpochMilli = now, updatedAtEpochMilli = now,
            ))
            dao.insertOperations(draft.operations.map { op ->
                ShootingOperationEntity(
                    reportId = id, startPointType = op.startPointType, startPointCode = op.startPointCode,
                    startMeter = op.startMeter, endPointType = op.endPointType, endPointCode = op.endPointCode,
                    endMeter = op.endMeter, cableTypeId = op.cableTypeId, operationMeters = op.meters,
                )
            })
            id
        }
    }
    suspend fun delete(id: Long) = dao.deleteReport(id)
    suspend fun operations(id: Long) = dao.getOperations(id)
    suspend fun reports(start: Long, end: Long) = dao.reportsInRange(start, end)
    suspend fun total(start: Long, end: Long) = dao.totalMeters(start, end)


    suspend fun clearShootingReports() = dao.deleteAllShootingReports()
    suspend fun allShootingReports() = dao.reportsInRange(0L, Long.MAX_VALUE)
    suspend fun allShootingOperations(reportId: Long) = dao.getOperations(reportId)
    suspend fun allCableTypes() = dao.getCableTypes()
    suspend fun importShootingReport(report: ShootingReportEntity, operations: List<ShootingOperationEntity>): Long =
        database.withTransaction {
            val id = dao.insertReport(report.copy(id = 0L))
            dao.insertOperations(operations.map { it.copy(id = 0L, reportId = id) })
            id
        }
\n    suspend fun addCompanyExpense(expense: CompanyExpenseEntity): Long {
        require(expense.amount > 0L)
        return dao.insertCompanyExpense(expense)
    }
    suspend fun addSettlement(expenseId: Long, date: Long, amount: Long, notes: String = ""): Long {
        require(amount > 0L)
        return database.withTransaction {
            val expense = dao.allExpenses().firstOrNull { it.id == expenseId } ?: error("Expense not found")
            val settled = dao.settledAmount(expenseId)
            require(settled + amount <= expense.amount) { "Settlement exceeds outstanding expense" }
            dao.insertSettlement(ExpenseSettlementEntity(expenseId = expenseId, settlementDateEpochDay = date, amount = amount, notes = notes, createdAtEpochMilli = System.currentTimeMillis()))
        }
    }
    suspend fun companyExpenses(start: Long, end: Long) = dao.expensesInRange(start, end).map { expense ->
        expense to dao.settledAmount(expense.id)
    }
    suspend fun allCompanyExpenses() = dao.allExpenses()
    suspend fun allExpenseSettlements() = dao.allSettlements()
    suspend fun deleteCompanyExpense(id: Long) = dao.deleteCompanyExpense(id)
    suspend fun clearCompanyExpenses() = database.withTransaction {
        dao.deleteAllSettlements()
        dao.deleteAllCompanyExpenses()
    }
    suspend fun importCompanyExpense(expense: CompanyExpenseEntity) = dao.insertCompanyExpense(expense)
    suspend fun importSettlement(settlement: ExpenseSettlementEntity) = dao.insertSettlement(settlement)
    suspend fun combinedReports(start: Long, end: Long, projectId: Long?) = dao.reportsInRangeForProject(start, end, projectId)

}
