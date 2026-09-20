package ir.metra.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room-level behaviour of the workday table.
 *
 * **Requires a device or emulator** — these are instrumented tests and cannot
 * run on the JVM. The pure business rules they complement are covered by the
 * JVM unit tests under `app/src/test`.
 *
 * What is asserted here is deliberately the part that only a real SQLite
 * database can prove: soft deletes hiding rows from every read path, cascade
 * behaviour on project deletion, denormalised expense totals and the aggregate
 * queries that back the dashboard.
 */
@RunWith(AndroidJUnit4::class)
class WorkRecordDaoTest {

    private lateinit var database: MetraDatabase
    private lateinit var workRecordDao: WorkRecordDao
    private lateinit var projectDao: ProjectDao
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MetraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRecordDao = database.workRecordDao()
        projectDao = database.projectDao()
        expenseDao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newRecord(
        epochDay: Long = 19_700L,
        meters: Int = 520,
        additional: Int = 120,
        projectId: Long? = null,
    ) = WorkRecordEntity(
        projectId = projectId,
        projectNameSnapshot = "پروژهٔ نمونه",
        workDateEpochDay = epochDay,
        dailyMeters = meters,
        additionalMeters = additional,
        thresholdMetersSnapshot = 400,
        ratePerMeterSnapshot = 15_000L,
        additionalMeterPayment = additional * 15_000L,
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )

    private fun newProject(name: String = "پروژهٔ نمونه") = ProjectEntity(
        name = name,
        employer = "شرکت نمونه",
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )

    // ------------------------------------------------------- basic storage

    @Test
    fun `insert assigns an id and the row reads back unchanged`() = runTest {
        val id = workRecordDao.upsert(newRecord())
        assertThat(id).isGreaterThan(0L)
        val stored = workRecordDao.getById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.dailyMeters).isEqualTo(520)
        assertThat(stored.projectNameSnapshot).isEqualTo("پروژهٔ نمونه")
    }

    @Test
    fun `a record without a project is storable`() = runTest {
        // project_id is nullable by design: a workday may predate any project.
        val id = workRecordDao.upsert(newRecord(projectId = null))
        assertThat(workRecordDao.getById(id)?.projectId).isNull()
    }

    @Test
    fun `upsert with an existing id updates rather than inserting`() = runTest {
        val id = workRecordDao.upsert(newRecord())
        val before = workRecordDao.countAll()
        workRecordDao.upsert(newRecord().copy(id = id, dailyMeters = 600))
        assertThat(workRecordDao.countAll()).isEqualTo(before)
        assertThat(workRecordDao.getById(id)?.dailyMeters).isEqualTo(600)
    }

    @Test
    fun `an empty database reports zero everywhere`() = runTest {
        assertThat(workRecordDao.countAll()).isEqualTo(0)
        assertThat(workRecordDao.getAllAscending()).isEmpty()
        assertThat(workRecordDao.getLatest()).isNull()
        assertThat(workRecordDao.maxMetersInRange(0L, Long.MAX_VALUE)).isNull()
        val summary = workRecordDao.getRangeSummary(0L, Long.MAX_VALUE)
        assertThat(summary.totals.workdays).isEqualTo(0)
        assertThat(summary.totals.totalMeters).isEqualTo(0L)
    }

    // -------------------------------------------------------- soft delete

    @Test
    fun `soft delete hides the row from every read path`() = runTest {
        val id = workRecordDao.upsert(newRecord())
        workRecordDao.softDelete(id, updatedAt = 1L)

        assertThat(workRecordDao.getById(id)).isNull()
        assertThat(workRecordDao.countAll()).isEqualTo(0)
        assertThat(workRecordDao.getAllAscending()).isEmpty()
        // The row still exists; only the flag changed.
        assertThat(workRecordDao.countIncludingDeleted()).isEqualTo(1)
    }

    @Test
    fun `a soft deleted record can be restored`() = runTest {
        val id = workRecordDao.upsert(newRecord())
        workRecordDao.softDelete(id, updatedAt = 1L)
        workRecordDao.restore(id, updatedAt = 2L)

        assertThat(workRecordDao.getById(id)).isNotNull()
        assertThat(workRecordDao.countAll()).isEqualTo(1)
    }

    @Test
    fun `soft deleted rows are excluded from aggregates`() = runTest {
        val kept = workRecordDao.upsert(newRecord(epochDay = 19_700L, meters = 520))
        val removed = workRecordDao.upsert(newRecord(epochDay = 19_701L, meters = 300))
        workRecordDao.softDelete(removed, updatedAt = 1L)

        val summary = workRecordDao.getRangeSummary(19_000L, 20_000L)
        assertThat(summary.totals.workdays).isEqualTo(1)
        assertThat(summary.totals.totalMeters).isEqualTo(520L)
        assertThat(kept).isGreaterThan(0L)
    }

    @Test
    fun `purge deletes only the soft deleted rows`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L))
        val removed = workRecordDao.upsert(newRecord(epochDay = 19_701L))
        workRecordDao.softDelete(removed, updatedAt = 1L)

        assertThat(workRecordDao.purgeDeleted()).isEqualTo(1)
        assertThat(workRecordDao.countIncludingDeleted()).isEqualTo(1)
        assertThat(workRecordDao.countAll()).isEqualTo(1)
    }

    // ------------------------------------------------------------ expenses

    @Test
    fun `expenses are returned with their workday`() = runTest {
        val recordId = workRecordDao.upsert(newRecord())
        expenseDao.upsert(expenseFor(recordId, 250_000L, ExpenseCategory.TRANSPORTATION))
        expenseDao.upsert(expenseFor(recordId, 180_000L, ExpenseCategory.FOOD))

        val withExpenses = workRecordDao.getWithExpenses(recordId)
        assertThat(withExpenses).isNotNull()
        assertThat(withExpenses!!.expenses).hasSize(2)
        assertThat(expenseDao.totalForWorkRecord(recordId)).isEqualTo(430_000L)
    }

    @Test
    fun `a workday with no expenses totals zero`() = runTest {
        val recordId = workRecordDao.upsert(newRecord())
        assertThat(expenseDao.totalForWorkRecord(recordId)).isEqualTo(0L)
        assertThat(workRecordDao.getWithExpenses(recordId)?.expenses).isEmpty()
    }

    @Test
    fun `deleting a workday removes its expenses`() = runTest {
        val recordId = workRecordDao.upsert(newRecord())
        expenseDao.upsert(expenseFor(recordId, 250_000L))
        workRecordDao.hardDelete(recordId)
        assertThat(expenseDao.getByWorkRecord(recordId)).isEmpty()
    }

    @Test
    fun `an expense category survives a round trip through the database`() = runTest {
        val recordId = workRecordDao.upsert(newRecord())
        expenseDao.upsert(expenseFor(recordId, 95_000L, ExpenseCategory.TOOLS))
        // Persisted as the enum name, so reordering the enum cannot shift meaning.
        assertThat(expenseDao.getByWorkRecord(recordId).first().category)
            .isEqualTo(ExpenseCategory.TOOLS.name)
    }

    // -------------------------------------------------- project deletion

    @Test
    fun `deleting a project keeps its workdays and clears the link`() = runTest {
        val projectId = projectDao.upsert(newProject())
        val recordId = workRecordDao.upsert(newRecord(projectId = projectId))

        projectDao.delete(newProject().copy(id = projectId))

        // ON DELETE SET NULL: history survives, the dangling reference does not.
        val record = workRecordDao.getById(recordId)
        assertThat(record).isNotNull()
        assertThat(record!!.projectId).isNull()
        // The snapshot keeps the report readable.
        assertThat(record.projectNameSnapshot).isEqualTo("پروژهٔ نمونه")
    }

    // --------------------------------------------------------- aggregates

    @Test
    fun `range summary sums meters additional meters and payment`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L, meters = 520, additional = 120))
        workRecordDao.upsert(newRecord(epochDay = 19_701L, meters = 400, additional = 0))
        workRecordDao.upsert(newRecord(epochDay = 19_702L, meters = 401, additional = 1))

        val summary = workRecordDao.getRangeSummary(19_700L, 19_702L)
        assertThat(summary.totals.workdays).isEqualTo(3)
        assertThat(summary.totals.totalMeters).isEqualTo(1321L)
        assertThat(summary.totals.totalAdditionalMeters).isEqualTo(121L)
        assertThat(summary.totals.totalAdditionalPayment).isEqualTo(1_815_000L)
    }

    @Test
    fun `range summary ignores records outside the range`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L, meters = 520))
        workRecordDao.upsert(newRecord(epochDay = 25_000L, meters = 999))

        val summary = workRecordDao.getRangeSummary(19_000L, 20_000L)
        assertThat(summary.totals.workdays).isEqualTo(1)
        assertThat(summary.totals.totalMeters).isEqualTo(520L)
    }

    @Test
    fun `pagination returns disjoint ordered pages`() = runTest {
        for (day in 0L until 25L) {
            workRecordDao.upsert(newRecord(epochDay = 19_700L + day, meters = 400 + day.toInt()))
        }
        val first = workRecordDao.getPage(19_700L, 19_800L, limit = 10, offset = 0)
        val second = workRecordDao.getPage(19_700L, 19_800L, limit = 10, offset = 10)
        val third = workRecordDao.getPage(19_700L, 19_800L, limit = 10, offset = 20)

        assertThat(first).hasSize(10)
        assertThat(second).hasSize(10)
        assertThat(third).hasSize(5)
        assertThat((first + second + third).map { it.id }.distinct()).hasSize(25)
    }

    @Test
    fun `monthly breakdown groups by jalali month start`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L, meters = 520))
        workRecordDao.upsert(newRecord(epochDay = 19_701L, meters = 480))

        val months = workRecordDao.getMonthlyBreakdown()
        assertThat(months).isNotEmpty()
        assertThat(months.sumOf { it.totals.workdays }).isEqualTo(2)
    }

    // ----------------------------------------------------------- queries

    @Test
    fun `search matches persian text in project name and notes`() = runTest {
        workRecordDao.upsert(newRecord().copy(notes = "یادداشت خاص"))
        workRecordDao.upsert(newRecord(epochDay = 19_701L).copy(notes = "چیز دیگر"))

        val results = workRecordDao.search("یادداشت").first()
        assertThat(results).hasSize(1)
        assertThat(results.first().notes).contains("یادداشت خاص")
    }

    @Test
    fun `latest before a date returns the previous workday`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L))
        workRecordDao.upsert(newRecord(epochDay = 19_705L))

        assertThat(workRecordDao.getLatestBefore(19_705L)?.workDateEpochDay).isEqualTo(19_700L)
        assertThat(workRecordDao.getLatestBefore(19_000L)).isNull()
    }

    @Test
    fun `duplicate detection counts records on the same date`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L))
        assertThat(workRecordDao.countOnDate(19_700L)).isEqualTo(1)
        assertThat(workRecordDao.getByExactDate(19_700L)).isNotNull()
        assertThat(workRecordDao.getByExactDate(19_999L)).isNull()
    }

    @Test
    fun `max meters in range is reported`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L, meters = 520))
        workRecordDao.upsert(newRecord(epochDay = 19_701L, meters = 800))
        workRecordDao.upsert(newRecord(epochDay = 19_702L, meters = 300))

        assertThat(workRecordDao.maxMetersInRange(19_700L, 19_702L)).isEqualTo(800)
    }

    @Test
    fun `distinct employers and supervisors are collected for filters`() = runTest {
        workRecordDao.upsert(newRecord().copy(employerSnapshot = "کارفرمای الف", supervisorSnapshot = "ناظر یک"))
        workRecordDao.upsert(
            newRecord(epochDay = 19_701L)
                .copy(employerSnapshot = "کارفرمای ب", supervisorSnapshot = "ناظر یک"),
        )
        assertThat(workRecordDao.observeDistinctEmployers().first()).containsExactly("کارفرمای الف", "کارفرمای ب")
        assertThat(workRecordDao.observeDistinctSupervisors().first()).containsExactly("ناظر یک")
    }

    @Test
    fun `flow observers emit the current rows`() = runTest {
        workRecordDao.upsert(newRecord(epochDay = 19_700L))
        val observed = workRecordDao.observeInRange(19_000L, 20_000L).first()
        assertThat(observed).hasSize(1)
    }

    private fun expenseFor(
        workRecordId: Long,
        amount: Long,
        category: ExpenseCategory = ExpenseCategory.OTHER,
    ) = ExpenseEntity(
        workRecordId = workRecordId,
        amount = amount,
        category = category.name,
        description = "هزینهٔ نمونه",
        createdAtEpochMilli = 0L,
    )
}
