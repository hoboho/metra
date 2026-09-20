package ir.metra.app.domain.repository

import ir.metra.app.core.common.MetraResult
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.WorkRecord
import kotlinx.coroutines.flow.Flow

/** Work log filtering criteria. Null means "no constraint on this dimension". */
data class WorkRecordFilter(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val projectId: Long? = null,
    val employer: String? = null,
    val supervisor: String? = null,
    val workArea: String? = null,
    val minMeters: Int? = null,
    val maxMeters: Int? = null,
    val onlyWithAdditionalMeters: Boolean = false,
    val onlyWithExpenses: Boolean = false,
    val textQuery: String? = null,
) {
    val hasActiveConstraints: Boolean
        get() = projectId != null || employer != null || supervisor != null ||
            workArea != null || minMeters != null || maxMeters != null ||
            onlyWithAdditionalMeters || onlyWithExpenses || !textQuery.isNullOrBlank()

    fun withBlankStringsNormalised(): WorkRecordFilter = copy(
        employer = employer?.takeIf { it.isNotBlank() },
        supervisor = supervisor?.takeIf { it.isNotBlank() },
        workArea = workArea?.takeIf { it.isNotBlank() },
        textQuery = textQuery?.takeIf { it.isNotBlank() },
    )
}

/** How the work log is ordered. */
enum class WorkRecordSort {
    DATE_DESC,
    DATE_ASC,
    METERS_DESC,
    METERS_ASC,
    ADDITIONAL_PAYMENT_DESC,
    EXPENSES_DESC,
}

interface WorkRecordRepository {

    // ------------------------------------------------------------------ reads

    fun observeRecordWithExpenses(id: Long): Flow<Pair<WorkRecord, List<Expense>>?>

    suspend fun getRecordWithExpenses(id: Long): Pair<WorkRecord, List<Expense>>?

    fun observeRecordsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<WorkRecord>>

    fun observeFiltered(filter: WorkRecordFilter, sort: WorkRecordSort): Flow<List<WorkRecord>>

    fun observeSearch(query: String): Flow<List<WorkRecord>>

    suspend fun getRecordsInRangeAscending(startEpochDay: Long, endEpochDay: Long): List<WorkRecord>

    /** Paged read so multi-year reports never load the whole table. */
    suspend fun getPage(startEpochDay: Long, endEpochDay: Long, limit: Int, offset: Int): List<WorkRecord>

    suspend fun countInRange(startEpochDay: Long, endEpochDay: Long): Int

    suspend fun countAll(): Int

    suspend fun hasRecordOn(epochDay: Long): Boolean

    suspend fun getRecordOn(epochDay: Long): WorkRecord?

    /** The prefill source: the most recent workday strictly before [epochDay]. */
    suspend fun getLatestBefore(epochDay: Long): WorkRecord?

    suspend fun getLatest(): WorkRecord?

    fun observeDistinctEmployers(): Flow<List<String>>
    fun observeDistinctSupervisors(): Flow<List<String>>
    fun observeDistinctWorkAreas(): Flow<List<String>>

    // ---------------------------------------------------------------- writes

    /**
     * Inserts or updates a work record.
     *
     * The Metra-calculated fields are recomputed from [record.dailyMeters] and
     * the payment rule applicable on the record's date, so a caller cannot
     * persist an inconsistent additional-meter figure.
     *
     * @return the row id.
     */
    suspend fun upsert(record: WorkRecord): MetraResult<Long>

    /** Replaces a record's expense lines and refreshes its cached total atomically. */
    suspend fun replaceExpenses(workRecordId: Long, expenses: List<Expense>): MetraResult<Unit>

    suspend fun addExpense(expense: Expense): MetraResult<Long>

    suspend fun deleteExpense(expenseId: Long): MetraResult<Unit>

    suspend fun softDelete(workRecordId: Long): MetraResult<Unit>

    suspend fun restore(workRecordId: Long): MetraResult<Unit>

    /** Permanently removes a record and, through the FK cascade, its expenses. */
    suspend fun hardDelete(workRecordId: Long): MetraResult<Unit>

    suspend fun purgeDeleted(): MetraResult<Int>

    /** Duplicates a record onto [targetEpochDay] without any money values. */
    suspend fun duplicateAsNewDay(workRecordId: Long, targetEpochDay: Long): MetraResult<Long>
}
