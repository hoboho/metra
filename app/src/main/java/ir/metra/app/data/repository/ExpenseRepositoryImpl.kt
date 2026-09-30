package ir.metra.app.data.repository

import androidx.room.withTransaction
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.data.local.ExpenseDao
import ir.metra.app.data.local.ExpenseEntity
import ir.metra.app.data.local.MetraDatabase
import ir.metra.app.data.local.WorkRecordDao
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.ExpenseCalculator
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Expense persistence.
 *
 * Every mutation runs inside a transaction that also refreshes the parent work
 * record's `expense_total`, so the denormalised aggregate used by reports can
 * never drift from the expense rows it summarises.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val database: MetraDatabase,
    private val expenseDao: ExpenseDao,
    private val workRecordDao: WorkRecordDao,
    private val clock: Clock,
    private val strings: StringProvider,
) : ExpenseRepository {

    override fun observeExpensesFor(workRecordId: Long): Flow<List<Expense>> =
        expenseDao.observeByWorkRecord(workRecordId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getExpensesFor(workRecordId: Long): List<Expense> =
        expenseDao.getByWorkRecord(workRecordId).map { it.toDomain() }

    override suspend fun add(expense: Expense): MetraResult<Long> = runCatching {
        if (expense.amount < 0) {
            return failure(MetraError.Validation(MetraError.Validation.Field.EXPENSE, strings.string(R.string.msg_expense_not_negative)))
        }
        val now = clock.nowEpochMilli()
        var newId = 0L
        database.withTransaction {
            val entity = expense.toEntity().copy(createdAtEpochMilli = now)
            newId = expenseDao.upsert(entity)
            if (newId == -1L) newId = expense.id
            refreshTotal(expense.workRecordId)
        }
        success(newId)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "add expense")) })

    override suspend fun update(expense: Expense): MetraResult<Unit> = runCatching {
        database.withTransaction {
            expenseDao.upsert(expense.toEntity())
            refreshTotal(expense.workRecordId)
        }
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "update expense")) })

    override suspend fun delete(expenseId: Long): MetraResult<Unit> = runCatching {
        database.withTransaction {
            val existing = expenseDao.getAll().firstOrNull { it.id == expenseId }
            if (existing != null) {
                expenseDao.delete(existing)
                refreshTotal(existing.workRecordId)
            }
        }
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "delete expense")) })

    /** Recomputes and stores the parent record's cached expense total. */
    private suspend fun refreshTotal(workRecordId: Long) {
        val rows = expenseDao.getByWorkRecord(workRecordId)
        val total = ExpenseCalculator.dailyExpenseTotal(rows.map { it.toDomain() })
        workRecordDao.updateExpenseTotal(workRecordId, total, clock.nowEpochMilli())
    }
}
