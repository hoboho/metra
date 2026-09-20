package ir.metra.app.domain.repository

import ir.metra.app.core.common.MetraResult
import ir.metra.app.domain.model.LedgerEntry
import ir.metra.app.domain.model.LedgerSummary
import kotlinx.coroutines.flow.Flow

/**
 * The receivables ledger.
 *
 * Deliberately narrow: add a movement, list the movements, and answer "what is
 * still outstanding?". Anything richer (invoices, ageing, partial settlement
 * against specific workdays) is accounting software, which this app is not.
 */
interface LedgerRepository {

    fun observeEntries(): Flow<List<LedgerEntry>>

    fun observeEntriesBetween(fromDay: Long, toDay: Long): Flow<List<LedgerEntry>>

    /**
     * Combines the ledger with the receivable built from work records so the
     * screen never has to reconcile two streams itself.
     */
    fun observeSummary(): Flow<LedgerSummary>

    fun observeSummaryBetween(fromDay: Long, toDay: Long): Flow<LedgerSummary>

    suspend fun getEntry(id: Long): LedgerEntry?

    /** One-shot read of the whole ledger, used by backup export. */
    suspend fun getEntries(): List<LedgerEntry>

    /** Fails with a validation error when the amount is not a positive total. */
    suspend fun upsert(entry: LedgerEntry): MetraResult<Long>

    suspend fun delete(id: Long): MetraResult<Unit>
}
