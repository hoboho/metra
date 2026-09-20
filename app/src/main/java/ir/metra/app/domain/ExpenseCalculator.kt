package ir.metra.app.domain

import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.WorkRecord

/**
 * Expense aggregation. Kept separate from [PaymentCalculator] so that meter
 * income and spending can never be summed into one another by accident.
 */
object ExpenseCalculator {

    /** Total of [expenses]. An empty list is zero, never null. */
    fun dailyExpenseTotal(expenses: List<Expense>): Long =
        expenses.fold(0L) { acc, expense -> acc + expense.amount }

    /** Total expenses across a set of workdays, using the stored aggregates. */
    fun totalExpenses(records: List<WorkRecord>): Long =
        records.fold(0L) { acc, record -> acc + record.expenseTotal }

    /** Number of workdays in [records] that carry at least one expense. */
    fun daysWithExpenses(records: List<WorkRecord>): Int =
        records.count { it.expenseTotal > 0L }
}
