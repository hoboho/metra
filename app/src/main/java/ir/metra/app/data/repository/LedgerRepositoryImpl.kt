package ir.metra.app.data.repository

import androidx.room.withTransaction
import ir.metra.app.R
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.data.local.LedgerEntryDao
import ir.metra.app.data.local.MetraDatabase
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.model.LedgerEntry
import ir.metra.app.domain.model.LedgerSummary
import ir.metra.app.domain.repository.LedgerRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ledger persistence.
 *
 * The outstanding balance is derived, never stored: it is the receivable built
 * from work records minus the net of the ledger. Storing it would create a
 * second copy that could drift the moment a workday was edited.
 */
@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val database: MetraDatabase,
    private val ledgerDao: LedgerEntryDao,
    private val statisticsRepository: WorkStatisticsRepository,
    private val clock: Clock,
    private val strings: StringProvider,
) : LedgerRepository {

    override fun observeEntries(): Flow<List<LedgerEntry>> =
        ledgerDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeEntriesBetween(fromDay: Long, toDay: Long): Flow<List<LedgerEntry>> =
        ledgerDao.observeBetween(fromDay, toDay).map { rows -> rows.map { it.toDomain() } }

    override fun observeSummary(): Flow<LedgerSummary> =
        combine(
            statisticsRepository.observeTotals(ALL_TIME_FROM_DAY, ALL_TIME_TO_DAY),
            ledgerDao.observeNetCollected(),
        ) { totals, collected ->
            LedgerSummary(
                totalReceivable = totals.totalReceivable,
                netCollected = collected,
            )
        }

    override fun observeSummaryBetween(fromDay: Long, toDay: Long): Flow<LedgerSummary> =
        combine(
            statisticsRepository.observeTotals(fromDay, toDay),
            ledgerDao.observeNetCollectedBetween(fromDay, toDay),
        ) { totals, collected ->
            LedgerSummary(
                totalReceivable = totals.totalReceivable,
                netCollected = collected,
            )
        }

    override suspend fun getEntry(id: Long): LedgerEntry? = ledgerDao.getById(id)?.toDomain()

    override suspend fun getEntries(): List<LedgerEntry> =
        ledgerDao.getAll().map { it.toDomain() }

    override suspend fun upsert(entry: LedgerEntry): MetraResult<Long> {
        if (entry.amount <= 0L) {
            return failure(
                MetraError.Validation(
                    MetraError.Validation.Field.LEDGER_AMOUNT,
                    strings.string(R.string.msg_ledger_amount_invalid),
                ),
            )
        }
        val now = clock.nowEpochMilli()
        val id = database.withTransaction {
            if (entry.id == 0L) {
                ledgerDao.insert(entry.copy(createdAtEpochMilli = now, updatedAtEpochMilli = now).toEntity())
            } else {
                ledgerDao.update(entry.copy(updatedAtEpochMilli = now).toEntity())
                entry.id
            }
        }
        return success(id)
    }

    override suspend fun delete(id: Long): MetraResult<Unit> {
        database.withTransaction { ledgerDao.deleteById(id) }
        return success(Unit)
    }

    private companion object {
        /**
         * Epoch-day bounds wide enough to cover any real workday without
         * overflowing SQLite's integer arithmetic.
         */
        const val ALL_TIME_FROM_DAY = 0L
        const val ALL_TIME_TO_DAY = 40_000L
    }
}
