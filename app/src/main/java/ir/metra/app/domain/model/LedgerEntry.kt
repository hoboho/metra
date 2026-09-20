package ir.metra.app.domain.model

/**
 * One movement of money in the receivables ledger.
 *
 * Metra keeps accounting deliberately minimal: the only number the app owes the
 * user is "how much is the company still holding?". A [RECEIPT] answers part of
 * that; a [PAYMENT] records money the worker put in himself so the ledger stays
 * honest when he buys, say, a blade out of pocket.
 */
enum class LedgerKind {
    /** The company paid the worker. Reduces what is outstanding. */
    RECEIPT,

    /** The worker spent his own money on the job. Increases what is outstanding. */
    PAYMENT,
}

/** What a ledger movement settled. Purely a label for the user's own reading. */
enum class LedgerReason {
    METRAJE,
    EXPENSE,
    SALARY,
    OTHER,
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
        get() = if (kind == LedgerKind.RECEIPT) amount else -amount
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
) {
    val outstanding: Long
        get() = totalReceivable - netCollected
}
