package ir.metra.app.domain.model

/**
 * One movement of money in the receivables ledger.
 *
 * Metra keeps accounting deliberately minimal: the only number the app owes the
 * user is "how much is the company still holding?". A [RECEIPT] answers part of
 * that. The legacy [PAYMENT] kind remains readable for old records, but new
 * personal expenses are recorded in the workday expense section.
 */
enum class LedgerKind {
    /** The company paid the worker. Reduces what is outstanding. */
    RECEIPT,

    /** The worker spent his own money on the job. Legacy only. */
    PAYMENT,

    /** A manually entered claim for salary or an expense outside a project. */
    CLAIM,
}

/** What a ledger movement settled. Purely a label for the user's own reading. */
enum class LedgerReason {
    METRAJE,
    EXPENSE,
    SALARY,
    OTHER,
    FUEL,
    TRANSPORT,
    WORKER,
    OUTSIDE_PROJECT,
}

/**
 * The «بابت» options that make sense for each kind of movement.
 *
 * A receipt from the company settles work, salary or reimbursed expenses. The
 * legacy personal-payment list is retained only so old records remain readable;
 * the editor no longer offers that operation to create new entries.
 */
fun reasonsFor(kind: LedgerKind): List<LedgerReason> = when (kind) {
    LedgerKind.RECEIPT -> listOf(
        LedgerReason.METRAJE,
        LedgerReason.SALARY,
        LedgerReason.EXPENSE,
        LedgerReason.OTHER,
    )
    LedgerKind.PAYMENT -> listOf(
        LedgerReason.FUEL,
        LedgerReason.TRANSPORT,
        LedgerReason.WORKER,
        LedgerReason.OTHER,
    )
    LedgerKind.CLAIM -> listOf(
        LedgerReason.SALARY,
        LedgerReason.OUTSIDE_PROJECT,
    )
}

data class LedgerEntry(
    val id: Long = 0L,
    val kind: LedgerKind,
    /** Always positive. Direction lives in [kind], never in the sign. */
    val amount: Long,
    val reason: LedgerReason,
    val entryDateEpochDay: Long,
    val method: String = "",
    val projectName: String = "",
    val notes: String = "",
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
) {
    init {
        require(amount >= 0L) { "amount must not be negative: $amount" }
        require(entryDateEpochDay > 0L) { "entryDateEpochDay must be positive" }
    }

    /** Signed contribution to the outstanding balance. */
    val signedAmount: Long
        get() = if (kind == LedgerKind.RECEIPT || kind == LedgerKind.CLAIM) amount else -amount
}

/**
 * The whole ledger reduced to the three numbers the user actually reads.
 *
 * [outstanding] is the figure that matters: what the company still owes. It is
 * the receivable built from work records minus everything collected so far, and
 * it may legitimately be negative if the worker has been overpaid.
 */
data class LedgerSummary(
    /** Built from work records: additional-meter payment plus reimbursable expenses. */
    val totalReceivable: Long,
    /** Net of receipts minus personal payments. */
    val netCollected: Long,
    /** The work component of [totalReceivable] (Metra's calculated meter payment). */
    val totalWork: Long = 0L,
    /** The reimbursable-expense component of [totalReceivable]. */
    val totalExpenses: Long = 0L,
) {
    val outstanding: Long
        get() = totalReceivable - netCollected
}
