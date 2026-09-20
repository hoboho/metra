package ir.metra.app.domain.model

import ir.metra.app.domain.PaymentCalculator

/**
 * A single workday.
 *
 * Two kinds of money live on this record and must never be blurred:
 *
 * 1. **Calculated by Metra** — [additionalMeters] / [additionalMeterPayment],
 *    derived deterministically from [dailyMeters] plus the rule snapshot
 *    ([thresholdMetersSnapshot], [ratePerMeterSnapshot]).
 * 2. **Reimbursed by the company** — [expenseTotal]. Costs the worker paid out
 *    of pocket and is owed back, so it is *added* to what the company owes,
 *    never subtracted from it.
 *
 * Company-paid salary is not a per-day figure and lives on its own record; see
 * all. Metra never recomputes payroll.
 *
 * [expenseTotal] is a maintained aggregate of this record's [Expense] rows. It
 * is denormalised deliberately: reports scan years of rows, and a per-day
 * sub-select would be paid for on every aggregate query. The repository keeps it
 * in sync inside the same transaction as any expense write.
 */
data class WorkRecord(
    val id: Long = 0L,
    val projectId: Long? = null,

    /** Historical snapshots — copied from the project at save time. */
    val projectName: String,
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: Int = 0,

    /** Machine-readable date. UI derives the Jalali presentation from this. */
    val workDateEpochDay: Long,

    /** Metra-calculated work quantities. */
    val dailyMeters: Int,
    val additionalMeters: Int,
    val thresholdMetersSnapshot: Int,
    val ratePerMeterSnapshot: Long,
    val additionalMeterPayment: Long,

    /**
     * Maintained aggregate of this record's expenses.
     *
     * These are reimbursable: the company owes them back, so this figure is part
     * of what is receivable rather than a deduction from it.
     */
    val expenseTotal: Long = 0L,
    val notes: String = "",

    /** Optional local time of day, minutes from midnight. */
    val workStartMinuteOfDay: Int? = null,
    val workEndMinuteOfDay: Int? = null,

    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
    val isDeleted: Boolean = false,
) {
    init {
        require(dailyMeters >= 0) { "dailyMeters must be >= 0" }
        require(additionalMeters >= 0) { "additionalMeters must be >= 0" }
        require(additionalMeterPayment >= 0) { "additionalMeterPayment must be >= 0" }
        require(thresholdMetersSnapshot >= 0) { "thresholdMetersSnapshot must be >= 0" }
        require(ratePerMeterSnapshot >= 0) { "ratePerMeterSnapshot must be >= 0" }
        require(expenseTotal >= 0) { "expenseTotal must be >= 0" }
        require(workerCount >= 0) { "workerCount must be >= 0" }
    }

    /**
     * What the company owes for this workday.
     *
     * Metra's calculated meter payment plus the expenses it must reimburse.
     * Company salary is deliberately absent: it is a monthly figure, not a
     * per-day one.
     */
    val receivableFromCompany: Long
        get() = additionalMeterPayment + expenseTotal

    companion object {
        /**
         * Recomputes the Metra-derived fields from [dailyMeters] and the rule
         * that applies on [workDateEpochDay], leaving everything else untouched.
         */
        fun withCalculatedQuantities(record: WorkRecord, rule: PaymentRule): WorkRecord {
            val additional = PaymentCalculator.additionalMeters(record.dailyMeters, rule.thresholdMeters)
            return record.copy(
                additionalMeters = additional,
                thresholdMetersSnapshot = rule.thresholdMeters,
                ratePerMeterSnapshot = rule.ratePerMeter,
                additionalMeterPayment = PaymentCalculator.additionalPayment(additional, rule.ratePerMeter),
            )
        }
    }
}
