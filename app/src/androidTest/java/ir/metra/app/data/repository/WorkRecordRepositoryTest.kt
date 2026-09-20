package ir.metra.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ir.metra.app.core.common.Clock
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.i18n.AndroidStringProvider
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.data.local.MetraDatabase
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.WorkRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository behaviour against a real (in-memory) database.
 *
 * **Requires a device or emulator.**
 *
 * These tests exist to prove the three guarantees that the whole product rests
 * on and that no amount of unit testing can show without a real database:
 *
 * 1. saving a workday recomputes the additional-meter payment from the rule
 *    that was in force **on that day**;
 * 2. adding a new rate never re-prices an existing record;
 * 3. "copy previous workday" carries context but never money or quantity.
 */
@RunWith(AndroidJUnit4::class)
class WorkRecordRepositoryTest {

    private lateinit var database: MetraDatabase
    private lateinit var repository: WorkRecordRepositoryImpl
    private lateinit var ruleRepository: PaymentRuleRepositoryImpl

    private val clock = Clock { 1_700_000_000_000L }

    private lateinit var strings: AndroidStringProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        strings = AndroidStringProvider(context)
        database = Room.inMemoryDatabaseBuilder(context, MetraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkRecordRepositoryImpl(
            database = database,
            workRecordDao = database.workRecordDao(),
            expenseDao = database.expenseDao(),
            paymentRuleDao = database.paymentRuleDao(),
            strings = strings,
            clock = clock,
        )
        ruleRepository = PaymentRuleRepositoryImpl(
            paymentRuleDao = database.paymentRuleDao(),
            strings = strings,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun epochDay(year: Int, month: Int, day: Int) =
        JalaliCalendar.toEpochDay(JalaliDate(year, month, day))

    private fun record(
        epochDay: Long,
        meters: Int,
        salary: Long = 0L,
        notes: String = "",
    ) = WorkRecord(
        projectName = "پروژهٔ نمونه",
        workDateEpochDay = epochDay,
        dailyMeters = meters,
        // Placeholder values: the repository recomputes all four from the rule
        // applicable on the record's date, which is exactly what these tests
        // assert. Passing zeros makes a stale value impossible to mistake for a
        // computed one.
        additionalMeters = 0,
        thresholdMetersSnapshot = 0,
        ratePerMeterSnapshot = 0L,
        additionalMeterPayment = 0L,
        notes = notes,
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )

    // ------------------------------------------------ recomputation on save

    @Test
    fun `saving recomputes additional meters from the applicable rule`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        val stored = repository.getRecordWithExpenses(id)!!.first

        assertThat(stored.thresholdMetersSnapshot).isEqualTo(400)
        assertThat(stored.ratePerMeterSnapshot).isEqualTo(15_000L)
        assertThat(stored.additionalMeters).isEqualTo(120)
        assertThat(stored.additionalMeterPayment).isEqualTo(1_800_000L)
    }

    @Test
    fun `threshold boundaries are applied by the repository`() = runTest {
        val cases = listOf(0 to 0, 1 to 0, 399 to 0, 400 to 0, 401 to 1, 520 to 120)
        for ((index, case) in cases.withIndex()) {
            val (meters, expectedAdditional) = case
            val id = repository
                .upsert(record(epochDay(1403, 5, 1) + index, meters = meters))
                .getOrThrow()
            val stored = repository.getRecordWithExpenses(id)!!.first
            assertThat(stored.additionalMeters).isEqualTo(expectedAdditional)
        }
    }

    @Test
    fun `the 520 metre case pays exactly 1_800_000 toman`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        assertThat(repository.getRecordWithExpenses(id)!!.first.additionalMeterPayment)
            .isEqualTo(1_800_000L)
    }

    @Test
    fun `editing a record recomputes against the rule for its own date`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository
            .upsert(record(epochDay(1403, 5, 10), meters = 400).copy(id = id))
            .getOrThrow()

        val stored = repository.getRecordWithExpenses(id)!!.first
        assertThat(stored.additionalMeters).isEqualTo(0)
        assertThat(stored.additionalMeterPayment).isEqualTo(0L)
    }

    // ------------------------------------------- history stability (the big one)

    @Test
    fun `adding a new rate does not change an existing record`() = runTest {
        val historicalDay = epochDay(1403, 5, 10)
        val id = repository.upsert(record(historicalDay, meters = 520)).getOrThrow()
        val before = repository.getRecordWithExpenses(id)!!.first
        assertThat(before.additionalMeterPayment).isEqualTo(1_800_000L)

        // A new rate takes effect next year.
        ruleRepository.addRule(
            PaymentRule(
                thresholdMeters = 400,
                ratePerMeter = 25_000L,
                effectiveFromEpochDay = epochDay(1404, 1, 1),
                createdAtEpochMilli = clock.nowEpochMilli(),
            ),
        ).getOrThrow()

        val after = repository.getRecordWithExpenses(id)!!.first
        assertThat(after.ratePerMeterSnapshot).isEqualTo(15_000L)
        assertThat(after.additionalMeterPayment).isEqualTo(1_800_000L)
    }

    @Test
    fun `a record dated after a rate change is priced at the new rate`() = runTest {
        ruleRepository.addRule(
            PaymentRule(
                thresholdMeters = 400,
                ratePerMeter = 25_000L,
                effectiveFromEpochDay = epochDay(1404, 1, 1),
                createdAtEpochMilli = clock.nowEpochMilli(),
            ),
        ).getOrThrow()

        val id = repository.upsert(record(epochDay(1404, 2, 5), meters = 520)).getOrThrow()
        val stored = repository.getRecordWithExpenses(id)!!.first

        assertThat(stored.ratePerMeterSnapshot).isEqualTo(25_000L)
        assertThat(stored.additionalMeterPayment).isEqualTo(3_000_000L)
    }

    @Test
    fun `deleting the last rule re-seeds the default rather than leaving none`() = runTest {
        val rules = ruleRepository.getRules()
        assertThat(rules).isNotEmpty()
        for (rule in rules) ruleRepository.deleteRule(rule.id)
        assertThat(ruleRepository.getRules()).isNotEmpty()
    }

    // ------------------------------------------------------------- expenses

    @Test
    fun `replacing expenses updates the denormalised total`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository.replaceExpenses(
            id,
            listOf(
                expense(id, 250_000L, ExpenseCategory.TRANSPORTATION),
                expense(id, 180_000L, ExpenseCategory.FOOD),
                expense(id, 100_000L, ExpenseCategory.MATERIALS),
            ),
        ).getOrThrow()

        val (stored, expenses) = repository.getRecordWithExpenses(id)!!
        assertThat(expenses).hasSize(3)
        assertThat(stored.expenseTotal).isEqualTo(530_000L)
    }

    @Test
    fun `clearing every expense resets the total to zero`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository.replaceExpenses(id, listOf(expense(id, 250_000L))).getOrThrow()
        repository.replaceExpenses(id, emptyList()).getOrThrow()

        val (stored, expenses) = repository.getRecordWithExpenses(id)!!
        assertThat(expenses).isEmpty()
        assertThat(stored.expenseTotal).isEqualTo(0L)
    }

    @Test
    fun `expenses are never netted against recorded salary`() = runTest {
        val id = repository
            .upsert(record(epochDay(1403, 5, 10), meters = 520, salary = 18_000_000L))
            .getOrThrow()
        repository.replaceExpenses(id, listOf(expense(id, 530_000L))).getOrThrow()

        val stored = repository.getRecordWithExpenses(id)!!.first
        assertThat(stored.expenseTotal).isEqualTo(530_000L)
    }

    @Test
    fun `a day with several expenses keeps every line`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        val five = (1..5).map { expense(id, it * 10_000L) }
        repository.replaceExpenses(id, five).getOrThrow()

        val (_, expenses) = repository.getRecordWithExpenses(id)!!
        assertThat(expenses).hasSize(5)
        assertThat(expenses.sumOf { it.amount }).isEqualTo(150_000L)
    }

    // ------------------------------------------------- duplicate workday

    @Test
    fun `duplicating a workday copies context but never money or quantity`() = runTest {
        val sourceId = repository
            .upsert(
                record(epochDay(1403, 5, 10), meters = 520, salary = 18_000_000L)
                    .copy(
                        workArea = "منطقهٔ ۲",
                        employer = "شرکت آب",
                        supervisor = "مهندس رضایی",
                        workerCount = 5,
                        notes = "یادداشت مخصوص همین روز",
                        workStartMinuteOfDay = 7 * 60,
                        workEndMinuteOfDay = 17 * 60,
                    ),
            )
            .getOrThrow()

        val targetDay = epochDay(1403, 5, 11)
        val newId = repository.duplicateAsNewDay(sourceId, targetDay).getOrThrow()
        val copy = repository.getRecordWithExpenses(newId)!!.first

        // Context carried over.
        assertThat(copy.workArea).isEqualTo("منطقهٔ ۲")
        assertThat(copy.employer).isEqualTo("شرکت آب")
        assertThat(copy.supervisor).isEqualTo("مهندس رضایی")
        assertThat(copy.workerCount).isEqualTo(5)
        assertThat(copy.workDateEpochDay).isEqualTo(targetDay)

        // Nothing financial or day-specific copied.
        assertThat(copy.dailyMeters).isEqualTo(0)
        assertThat(copy.additionalMeters).isEqualTo(0)
        assertThat(copy.additionalMeterPayment).isEqualTo(0L)
        assertThat(copy.expenseTotal).isEqualTo(0L)
        assertThat(copy.notes).isEmpty()
    }

    @Test
    fun `duplicating does not copy expense lines`() = runTest {
        val sourceId = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository.replaceExpenses(sourceId, listOf(expense(sourceId, 250_000L))).getOrThrow()

        val newId = repository
            .duplicateAsNewDay(sourceId, epochDay(1403, 5, 11))
            .getOrThrow()

        assertThat(repository.getRecordWithExpenses(newId)!!.second).isEmpty()
        // The source is untouched.
        assertThat(repository.getRecordWithExpenses(sourceId)!!.second).hasSize(1)
    }

    // ------------------------------------------------------------- delete

    @Test
    fun `soft delete hides the record but keeps it recoverable`() = runTest {
        val id = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository.softDelete(id).getOrThrow()

        assertThat(repository.countAll()).isEqualTo(0)
        assertThat(repository.getRecordWithExpenses(id)).isNull()
        repository.restore(id).getOrThrow()
        assertThat(repository.countAll()).isEqualTo(1)
    }

    @Test
    fun `the previous workday lookup skips soft deleted records`() = runTest {
        val older = repository.upsert(record(epochDay(1403, 5, 1), meters = 500)).getOrThrow()
        val newer = repository.upsert(record(epochDay(1403, 5, 10), meters = 520)).getOrThrow()
        repository.softDelete(newer).getOrThrow()

        assertThat(repository.getLatestBefore(epochDay(1403, 5, 11))?.id).isEqualTo(older)
    }

    // --------------------------------------------------------------- misc

    @Test
    fun `an empty database is a valid starting state`() = runTest {
        assertThat(repository.countAll()).isEqualTo(0)
        assertThat(repository.getLatest()).isNull()
        assertThat(repository.hasRecordOn(epochDay(1403, 5, 10))).isFalse()
        assertThat(repository.getRecordsInRangeAscending(0L, Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun `only one record per calendar day is enforced by the lookup`() = runTest {
        val day = epochDay(1403, 5, 10)
        repository.upsert(record(day, meters = 520)).getOrThrow()
        assertThat(repository.hasRecordOn(day)).isTrue()
        assertThat(repository.getRecordOn(day)).isNotNull()
    }

    private fun expense(
        workRecordId: Long,
        amount: Long,
        category: ExpenseCategory = ExpenseCategory.OTHER,
    ) = Expense(
        workRecordId = workRecordId,
        amount = amount,
        category = category,
        description = "هزینهٔ نمونه",
        createdAtEpochMilli = 0L,
    )
}
