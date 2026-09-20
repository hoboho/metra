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
import ir.metra.app.data.local.MetraDatabase
import ir.metra.app.data.local.PaymentRuleDao
import ir.metra.app.data.local.WorkRecordDao
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.ExpenseCalculator
import ir.metra.app.domain.PaymentCalculator
import ir.metra.app.domain.PaymentRuleSelector
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.WorkRecordFilter
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkRecordSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkRecordRepositoryImpl @Inject constructor(
    private val database: MetraDatabase,
    private val workRecordDao: WorkRecordDao,
    private val expenseDao: ExpenseDao,
    private val paymentRuleDao: PaymentRuleDao,
    private val clock: Clock,
    private val strings: StringProvider,
) : WorkRecordRepository {

    override fun observeRecordWithExpenses(id: Long): Flow<Pair<WorkRecord, List<Expense>>?> =
        workRecordDao.observeWithExpenses(id).map { row ->
            row?.let { it.record.toDomain() to it.expenses.map { expense -> expense.toDomain() } }
        }

    override suspend fun getRecordWithExpenses(id: Long): Pair<WorkRecord, List<Expense>>? =
        workRecordDao.getWithExpenses(id)?.let { row ->
            row.record.toDomain() to row.expenses.map { it.toDomain() }
        }

    override fun observeRecordsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<WorkRecord>> =
        workRecordDao.observeInRange(startEpochDay, endEpochDay).map { rows -> rows.map { it.toDomain() } }

    override fun observeFiltered(filter: WorkRecordFilter, sort: WorkRecordSort): Flow<List<WorkRecord>> {
        val normalized = filter.withBlankStringsNormalised()
        return workRecordDao.filtered(
            fromEpochDay = normalized.fromEpochDay,
            toEpochDay = normalized.toEpochDay,
            projectId = normalized.projectId,
            employer = normalized.employer,
            supervisor = normalized.supervisor,
            workArea = normalized.workArea,
            minMeters = normalized.minMeters,
            maxMeters = normalized.maxMeters,
            onlyWithAdditionalMeters = normalized.onlyWithAdditionalMeters,
            onlyWithExpenses = normalized.onlyWithExpenses,
            textQuery = normalized.textQuery,
        ).map { rows -> rows.map { it.toDomain() }.sortedWith(sort.comparator()) }
    }

    override fun observeSearch(query: String): Flow<List<WorkRecord>> =
        workRecordDao.search(query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRecordsInRangeAscending(startEpochDay: Long, endEpochDay: Long): List<WorkRecord> =
        workRecordDao.getInRangeAscending(startEpochDay, endEpochDay).map { it.toDomain() }

    override suspend fun getPage(
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int,
        offset: Int,
    ): List<WorkRecord> = workRecordDao.getPage(startEpochDay, endEpochDay, limit, offset).map { it.toDomain() }

    override suspend fun countInRange(startEpochDay: Long, endEpochDay: Long): Int =
        workRecordDao.countInRange(startEpochDay, endEpochDay)

    override suspend fun countAll(): Int = workRecordDao.countAll()

    override suspend fun hasRecordOn(epochDay: Long): Boolean = workRecordDao.countOnDate(epochDay) > 0

    override suspend fun getRecordOn(epochDay: Long): WorkRecord? =
        workRecordDao.getByExactDate(epochDay)?.toDomain()

    override suspend fun getLatestBefore(epochDay: Long): WorkRecord? =
        workRecordDao.getLatestBefore(epochDay)?.toDomain()

    override suspend fun getLatest(): WorkRecord? = workRecordDao.getLatest()?.toDomain()

    override fun observeDistinctEmployers(): Flow<List<String>> = workRecordDao.observeDistinctEmployers()
    override fun observeDistinctSupervisors(): Flow<List<String>> = workRecordDao.observeDistinctSupervisors()
    override fun observeDistinctWorkAreas(): Flow<List<String>> = workRecordDao.observeDistinctWorkAreas()

    override suspend fun upsert(record: WorkRecord): MetraResult<Long> = runCatching {
        val now = clock.nowEpochMilli()
        val rule = resolveRule(record.workDateEpochDay)

        // Metra-derived fields are always recomputed here. A caller cannot
        // persist an additional-meter figure that disagrees with the rule.
        val additional = PaymentCalculator.additionalMeters(record.dailyMeters, rule.thresholdMeters)
        val payment = PaymentCalculator.additionalPayment(additional, rule.ratePerMeter)

        var resultId = record.id
        database.withTransaction {
            val existing = if (record.id != 0L) workRecordDao.getById(record.id) else null
            val entity = record
                .copy(
                    additionalMeters = additional,
                    thresholdMetersSnapshot = rule.thresholdMeters,
                    ratePerMeterSnapshot = rule.ratePerMeter,
                    additionalMeterPayment = payment,
                    expenseTotal = existing?.expenseTotal ?: record.expenseTotal,
                    createdAtEpochMilli = existing?.createdAtEpochMilli ?: now,
                    updatedAtEpochMilli = now,
                )
                .toEntity()

            val id = workRecordDao.upsert(entity)
            resultId = when {
                id != -1L -> id
                record.id != 0L -> record.id
                else -> 0L
            }
        }
        success(resultId)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "upsert work record")) })

    override suspend fun replaceExpenses(workRecordId: Long, expenses: List<Expense>): MetraResult<Unit> =
        runCatching {
            if (expenses.any { it.amount < 0 }) {
                return failure(
                    MetraError.Validation(MetraError.Validation.Field.EXPENSE, strings.string(R.string.msg_expense_not_negative)),
                )
            }
            database.withTransaction {
                expenseDao.deleteByWorkRecord(workRecordId)
                val now = clock.nowEpochMilli()
                if (expenses.isNotEmpty()) {
                    expenseDao.insertAll(
                        expenses.map { it.toEntity().copy(id = 0L, createdAtEpochMilli = now) },
                    )
                }
                val total = ExpenseCalculator.dailyExpenseTotal(expenses)
                workRecordDao.updateExpenseTotal(workRecordId, total, clock.nowEpochMilli())
            }
            success(Unit)
        }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "replace expenses")) })

    override suspend fun addExpense(expense: Expense): MetraResult<Long> = runCatching {
        var newId = 0L
        database.withTransaction {
            val id = expenseDao.upsert(expense.toEntity())
            newId = if (id == -1L) expense.id else id
            val total = expenseDao.totalForWorkRecord(expense.workRecordId)
            workRecordDao.updateExpenseTotal(expense.workRecordId, total, clock.nowEpochMilli())
        }
        success(newId)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "add expense")) })

    override suspend fun deleteExpense(expenseId: Long): MetraResult<Unit> = runCatching {
        database.withTransaction {
            val rows = expenseDao.getAll()
            val target = rows.firstOrNull { it.id == expenseId }
            if (target != null) {
                expenseDao.delete(target)
                val total = expenseDao.totalForWorkRecord(target.workRecordId)
                workRecordDao.updateExpenseTotal(target.workRecordId, total, clock.nowEpochMilli())
            }
        }
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "delete expense")) })

    override suspend fun softDelete(workRecordId: Long): MetraResult<Unit> = runCatching {
        workRecordDao.softDelete(workRecordId, clock.nowEpochMilli())
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "softDelete")) })

    override suspend fun restore(workRecordId: Long): MetraResult<Unit> = runCatching {
        workRecordDao.restore(workRecordId, clock.nowEpochMilli())
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "restore")) })

    override suspend fun hardDelete(workRecordId: Long): MetraResult<Unit> = runCatching {
        workRecordDao.hardDelete(workRecordId)
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "hardDelete")) })

    override suspend fun purgeDeleted(): MetraResult<Int> = runCatching {
        success(workRecordDao.purgeDeleted())
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "purgeDeleted")) })

    override suspend fun duplicateAsNewDay(workRecordId: Long, targetEpochDay: Long): MetraResult<Long> =
        runCatching {
            val source = workRecordDao.getWithExpenses(workRecordId)
                ?: return failure(MetraError.NotFound(strings.string(R.string.entity_work_record)))
            val record = source.record.toDomain()
            val now = clock.nowEpochMilli()
            val rule = resolveRule(targetEpochDay)

            // "Duplicate" copies the contextual shape of the day but resets
            // every money and quantity value — the user must enter real figures.
            val copy = record.copy(
                id = 0L,
                workDateEpochDay = targetEpochDay,
                dailyMeters = 0,
                additionalMeters = 0,
                additionalMeterPayment = 0L,
                thresholdMetersSnapshot = rule.thresholdMeters,
                ratePerMeterSnapshot = rule.ratePerMeter,
                expenseTotal = 0L,
                notes = "",
                createdAtEpochMilli = now,
                updatedAtEpochMilli = now,
            )
            val newId = upsert(copy).getOrThrow()
            success(newId)
        }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "duplicate")) })

    /** The rule in force on [epochDay], seeding the default when unset. */
    private suspend fun resolveRule(epochDay: Long): PaymentRule {
        val rules = paymentRuleDao.getAll().map { it.toDomain() }
        PaymentRuleSelector.applicableRule(rules, epochDay)?.let { return it }
        val fallback = PaymentRule(
            thresholdMeters = PaymentRule.DEFAULT_THRESHOLD_METERS,
            ratePerMeter = PaymentRule.DEFAULT_RATE_PER_METER,
            effectiveFromEpochDay = epochDay,
            createdAtEpochMilli = clock.nowEpochMilli(),
        )
        paymentRuleDao.upsert(fallback.toEntity())
        return fallback
    }

    private fun WorkRecordSort.comparator(): Comparator<WorkRecord> = when (this) {
        WorkRecordSort.DATE_DESC -> compareByDescending<WorkRecord> { it.workDateEpochDay }
            .thenByDescending { it.id }

        WorkRecordSort.DATE_ASC -> compareBy<WorkRecord> { it.workDateEpochDay }.thenBy { it.id }
        WorkRecordSort.METERS_DESC -> compareByDescending<WorkRecord> { it.dailyMeters }
            .thenByDescending { it.workDateEpochDay }

        WorkRecordSort.METERS_ASC -> compareBy<WorkRecord> { it.dailyMeters }
            .thenBy { it.workDateEpochDay }

        WorkRecordSort.ADDITIONAL_PAYMENT_DESC ->
            compareByDescending<WorkRecord> { it.additionalMeterPayment }
                .thenByDescending { it.workDateEpochDay }

        WorkRecordSort.EXPENSES_DESC -> compareByDescending<WorkRecord> { it.expenseTotal }
            .thenByDescending { it.workDateEpochDay }
    }
}
