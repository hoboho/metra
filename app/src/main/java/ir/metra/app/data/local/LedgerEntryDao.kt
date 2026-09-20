package ir.metra.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes for the receivables ledger.
 *
 * Every read filters nothing by default: the ledger is small enough (a handful
 * of receipts a month) that paging would cost more complexity than it saves.
 */
@Dao
interface LedgerEntryDao {

    @Query("SELECT * FROM ledger_entries ORDER BY entry_date_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY entry_date_epoch_day DESC, id DESC")
    suspend fun getAll(): List<LedgerEntryEntity>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE entry_date_epoch_day BETWEEN :fromDay AND :toDay
        ORDER BY entry_date_epoch_day DESC, id DESC
        """,
    )
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<LedgerEntryEntity>>

    /**
     * Signed running total: receipts count positive, personal payments negative.
     * `COALESCE` keeps an empty ledger at zero instead of null.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'RECEIPT' THEN amount ELSE -amount END), 0)
        FROM ledger_entries
        """,
    )
    fun observeNetCollected(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'RECEIPT' THEN amount ELSE -amount END), 0)
        FROM ledger_entries
        WHERE entry_date_epoch_day BETWEEN :fromDay AND :toDay
        """,
    )
    fun observeNetCollectedBetween(fromDay: Long, toDay: Long): Flow<Long>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getById(id: Long): LedgerEntryEntity?

    @Insert
    suspend fun insert(entry: LedgerEntryEntity): Long

    @Update
    suspend fun update(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM ledger_entries")
    suspend fun count(): Int
}
