package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.WorkRecord
import org.junit.Test

/**
 * Expense aggregation.
 *
 * Expenses must never be mixed into meter income, and an empty expense list must
 * read as zero rather than null.
 */
class ExpenseCalculatorTest {

    private fun expense(amount: Long, category: ExpenseCategory = ExpenseCategory.OTHER) = Expense(
        workRecordId = 1L,
        amount = amount,
        category = category,
        createdAtEpochMilli = 0L,
    )

    @Test
    fun `empty expense list totals zero`() {
        assertThat(ExpenseCalculator.dailyExpenseTotal(emptyList())).isEqualTo(0L)
    }

    @Test
    fun `single expense is returned unchanged`() {
        assertThat(ExpenseCalculator.dailyExpenseTotal(listOf(expense(250_000)))).isEqualTo(250_000L)
    }

    @Test
    fun `multiple expenses sum exactly as in the product example`() {
        // Transportation 250,000 + Food 180,000 + Materials 100,000 = 530,000.
        val expenses = listOf(
            expense(250_000, ExpenseCategory.TRANSPORTATION),
            expense(180_000, ExpenseCategory.FOOD),
            expense(100_000, ExpenseCategory.MATERIALS),
        )
        assertThat(ExpenseCalculator.dailyExpenseTotal(expenses)).isEqualTo(530_000L)
    }

    @Test
    fun `large expense totals do not overflow`() {
        val expenses = List(50) { expense(300_000_000L) }
        assertThat(ExpenseCalculator.dailyExpenseTotal(expenses)).isEqualTo(15_000_000_000L)
    }

    @Test
    fun `totalExpenses sums the maintained per-day aggregates`() {
        val records = listOf(
            workRecord(expenseTotal = 530_000L),
            workRecord(expenseTotal = 0L),
            workRecord(expenseTotal = 95_000L),
        )
        assertThat(ExpenseCalculator.totalExpenses(records)).isEqualTo(625_000L)
    }

    @Test
    fun `daysWithExpenses counts only days that actually spent something`() {
        val records = listOf(
            workRecord(expenseTotal = 530_000L),
            workRecord(expenseTotal = 0L),
            workRecord(expenseTotal = 95_000L),
        )
        assertThat(ExpenseCalculator.daysWithExpenses(records)).isEqualTo(2)
    }

    private fun workRecord(expenseTotal: Long) = WorkRecord(
        projectName = "پروژه",
        workDateEpochDay = 0L,
        dailyMeters = 500,
        additionalMeters = 100,
        thresholdMetersSnapshot = 400,
        ratePerMeterSnapshot = 15_000L,
        additionalMeterPayment = 1_500_000L,
        expenseTotal = expenseTotal,
        createdAtEpochMilli = 0L,
        updatedAtEpochMilli = 0L,
    )
}
