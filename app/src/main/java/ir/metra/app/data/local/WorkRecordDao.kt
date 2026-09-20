package ir.metra.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import ir.metra.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

/** The summed columns shared by every aggregate query in this DAO. */
data class TotalsColumns(
    val workdays: Int,
    val totalMeters: Long,
    val totalAdditionalMeters: Long,
    val totalAdditionalPayment: Long,
    val totalExpenses: Long,
)

/** Totals for one date range (a dashboard period or a report period). */
data class RangeSummaryRow(
    @androidx.room.Embedded val totals: TotalsColumns,
)

/**
 * Aggregated month bucket computed in SQL.
 *
 * Monthly figures are summed by the database rather than by materialising every
 * row in memory: with several years of records that difference is the whole
 * performance budget of the dashboard.
 */
data class MonthSummaryRow(
    val monthStartEpochDay: Long,
    @androidx.room.Embedded val totals: TotalsColumns,
)

/** Per-project totals, used for the project breakdown section of reports. */
data class ProjectSummaryRow(
    val projectName: String,
    val workdays: Int,
    val totalMeters: Long,
    val totalAdditionalMeters: Long,
    val totalAdditionalPayment: Long,
    val totalExpenses: Long,
)

/** A workday together with its expense lines. */
data class WorkRecordWithExpenses(
    @Embedded val record: WorkRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "work_record_id")
    val expenses: List<ExpenseEntity>,
)

/**
 * Work record access.
 *
 * Every read excludes soft-deleted rows unless it explicitly asks for them.
 * Filters are expressed as nullable parameters so a single query serves the
 * whole filter sheet — passing null means "don't constrain on this field".
 */
@Dao
interface WorkRecordDao {

    // ---------------------------------------------------------------- single

    @Transaction
    @Query("SELECT * FROM work_records WHERE id = :id AND is_deleted = 0 LIMIT 1")
    fun observeWithExpenses(id: Long): Flow<WorkRecordWithExpenses?>

    @Transaction
    @Query("SELECT * FROM work_records WHERE id = :id LIMIT 1")
    suspend fun getWithExpenses(id: Long): WorkRecordWithExpenses?

    @Query("SELECT * FROM work_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkRecordEntity?

    // ----------------------------------------------------------------- lists

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        ORDER BY work_date_epoch_day DESC, id DESC
        """,
    )
    fun observeInRange(start: Long, end: Long): Flow<List<WorkRecordEntity>>

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        ORDER BY work_date_epoch_day ASC, id ASC
        """,
    )
    suspend fun getInRangeAscending(start: Long, end: Long): List<WorkRecordEntity>

    @Query("SELECT * FROM work_records WHERE is_deleted = 0 ORDER BY work_date_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<WorkRecordEntity>>

    @Query("SELECT * FROM work_records WHERE is_deleted = 0 ORDER BY work_date_epoch_day ASC, id ASC")
    suspend fun getAllAscending(): List<WorkRecordEntity>

    /**
     * Paged read for very large reports. Never load the whole table at once.
     */
    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        ORDER BY work_date_epoch_day ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getPage(start: Long, end: Long, limit: Int, offset: Int): List<WorkRecordEntity>

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        ORDER BY work_date_epoch_day DESC, id DESC
        """,
    )
    fun observeRangeDesc(start: Long, end: Long): Flow<List<WorkRecordEntity>>

    // ------------------------------------------------------------- searching

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0
          AND (
                project_name_snapshot LIKE '%' || :query || '%'
             OR work_area_snapshot LIKE '%' || :query || '%'
             OR employer_snapshot LIKE '%' || :query || '%'
             OR supervisor_snapshot LIKE '%' || :query || '%'
             OR notes LIKE '%' || :query || '%'
          )
        ORDER BY work_date_epoch_day DESC, id DESC
        """,
    )
    fun search(query: String): Flow<List<WorkRecordEntity>>

    /**
     * The one query behind the whole work-log filter sheet.
     *
     * Each nullable parameter is a switch: `(:param IS NULL OR column = :param)`
     * collapses to true when the user has not constrained that dimension.
     */
    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0
          AND work_date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
          AND (:projectId IS NULL OR project_id = :projectId)
          AND (:employer IS NULL OR employer_snapshot = :employer)
          AND (:supervisor IS NULL OR supervisor_snapshot = :supervisor)
          AND (:workArea IS NULL OR work_area_snapshot = :workArea)
          AND (:minMeters IS NULL OR daily_meters >= :minMeters)
          AND (:maxMeters IS NULL OR daily_meters <= :maxMeters)
          AND (:onlyWithAdditionalMeters = 0 OR additional_meters > 0)
          AND (:onlyWithExpenses = 0 OR expense_total > 0)
          AND (:textQuery IS NULL OR
                project_name_snapshot LIKE '%' || :textQuery || '%' OR
                work_area_snapshot LIKE '%' || :textQuery || '%' OR
                employer_snapshot LIKE '%' || :textQuery || '%' OR
                supervisor_snapshot LIKE '%' || :textQuery || '%' OR
                notes LIKE '%' || :textQuery || '%')
        ORDER BY work_date_epoch_day DESC, id DESC
        """,
    )
    fun filtered(
        fromEpochDay: Long,
        toEpochDay: Long,
        projectId: Long?,
        employer: String?,
        supervisor: String?,
        workArea: String?,
        minMeters: Int?,
        maxMeters: Int?,
        onlyWithAdditionalMeters: Boolean,
        onlyWithExpenses: Boolean,
        textQuery: String?,
    ): Flow<List<WorkRecordEntity>>

    // ----------------------------------------------------------- aggregates

    @Query(
        """
        SELECT
            COUNT(*)                        AS workdays,
            COALESCE(SUM(daily_meters), 0)  AS totalMeters,
            COALESCE(SUM(additional_meters), 0)        AS totalAdditionalMeters,
            COALESCE(SUM(additional_meter_payment), 0) AS totalAdditionalPayment,
            COALESCE(SUM(expense_total), 0)     AS totalExpenses
        FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        """,
    )
    fun observeMonthSummary(start: Long, end: Long): Flow<RangeSummaryRow>

    @Query(
        """
        SELECT
            COUNT(*)                        AS workdays,
            COALESCE(SUM(daily_meters), 0)  AS totalMeters,
            COALESCE(SUM(additional_meters), 0)        AS totalAdditionalMeters,
            COALESCE(SUM(additional_meter_payment), 0) AS totalAdditionalPayment,
            COALESCE(SUM(expense_total), 0)     AS totalExpenses
        FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
          AND (:projectId IS NULL OR project_id = :projectId)
        """,
    )
    suspend fun getRangeSummary(start: Long, end: Long, projectId: Long? = null): RangeSummaryRow

    @Query(
        """
        SELECT
            project_name_snapshot           AS projectName,
            COUNT(*)                        AS workdays,
            COALESCE(SUM(daily_meters), 0)  AS totalMeters,
            COALESCE(SUM(additional_meters), 0)        AS totalAdditionalMeters,
            COALESCE(SUM(additional_meter_payment), 0) AS totalAdditionalPayment,
            COALESCE(SUM(expense_total), 0) AS totalExpenses
        FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        GROUP BY project_name_snapshot
        ORDER BY totalMeters DESC
        """,
    )
    suspend fun getProjectBreakdown(start: Long, end: Long): List<ProjectSummaryRow>

    @Query(
        """
        SELECT
            MIN(work_date_epoch_day) AS monthStartEpochDay,
            COUNT(*)                 AS workdays,
            COALESCE(SUM(daily_meters), 0)  AS totalMeters,
            COALESCE(SUM(additional_meters), 0)        AS totalAdditionalMeters,
            COALESCE(SUM(additional_meter_payment), 0) AS totalAdditionalPayment,
            COALESCE(SUM(expense_total), 0)     AS totalExpenses
        FROM work_records
        WHERE is_deleted = 0
        GROUP BY strftime('%Y-%m', date(work_date_epoch_day, 'unixepoch'))
        ORDER BY monthStartEpochDay ASC
        """,
    )
    suspend fun getMonthlyBreakdown(): List<MonthSummaryRow>

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day = :epochDay
        ORDER BY id DESC LIMIT 1
        """,
    )
    suspend fun getByExactDate(epochDay: Long): WorkRecordEntity?

    @Query(
        """
        SELECT COUNT(*) FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day = :epochDay
        """,
    )
    suspend fun countOnDate(epochDay: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end
        """,
    )
    suspend fun countInRange(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM work_records WHERE is_deleted = 0")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM work_records")
    suspend fun countIncludingDeleted(): Int

    /** The most recent workday strictly before [epochDay]: the prefill source. */
    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day < :epochDay
        ORDER BY work_date_epoch_day DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestBefore(epochDay: Long): WorkRecordEntity?

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0 AND work_date_epoch_day < :epochDay
        ORDER BY work_date_epoch_day DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatestBefore(epochDay: Long): Flow<WorkRecordEntity?>

    @Query(
        """
        SELECT * FROM work_records
        WHERE is_deleted = 0
        ORDER BY work_date_epoch_day DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatest(): WorkRecordEntity?

    /** Distinct values for the filter dropdowns. */
    @Query("SELECT DISTINCT employer_snapshot FROM work_records WHERE is_deleted = 0 AND employer_snapshot != '' ORDER BY employer_snapshot")
    fun observeDistinctEmployers(): Flow<List<String>>

    @Query("SELECT DISTINCT supervisor_snapshot FROM work_records WHERE is_deleted = 0 AND supervisor_snapshot != '' ORDER BY supervisor_snapshot")
    fun observeDistinctSupervisors(): Flow<List<String>>

    @Query("SELECT DISTINCT work_area_snapshot FROM work_records WHERE is_deleted = 0 AND work_area_snapshot != '' ORDER BY work_area_snapshot")
    fun observeDistinctWorkAreas(): Flow<List<String>>

    @Query("SELECT MAX(daily_meters) FROM work_records WHERE is_deleted = 0 AND work_date_epoch_day BETWEEN :start AND :end")
    suspend fun maxMetersInRange(start: Long, end: Long): Int?

    // --------------------------------------------------------------- writing

    @Upsert
    suspend fun upsert(record: WorkRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<WorkRecordEntity>): List<Long>

    @Update
    suspend fun update(record: WorkRecordEntity)

    @Query("UPDATE work_records SET expense_total = :total, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateExpenseTotal(id: Long, total: Long, updatedAt: Long)

    /** Soft delete: keeps the row and its expenses recoverable. */
    @Query("UPDATE work_records SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE work_records SET is_deleted = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /** Hard delete; cascades to expenses through the foreign key. */
    @Query("DELETE FROM work_records WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM work_records WHERE is_deleted = 1")
    suspend fun purgeDeleted(): Int

    @Query("DELETE FROM work_records")
    suspend fun deleteAll()
}
