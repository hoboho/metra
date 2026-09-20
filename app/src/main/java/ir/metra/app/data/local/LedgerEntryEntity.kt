package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line in the receivables ledger: money that moved between the worker and
 * the company.
 *
 * This is deliberately a flat, append-only ledger rather than an invoice model.
 * Metra is a personal metering tool, not accounting software, so the only
 * question the ledger has to answer is "how much is still outstanding?". A
 * receipt reduces that number; a personal payment increases it.
 *
 * No foreign key to `work_records`: a payment from the company settles an
 * arbitrary slice of many workdays at once, so tying a receipt to one workday
 * would be a false precision. The optional `project_id` exists only to let the
 * user filter, and is nulled rather than cascaded if the project goes away.
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["entry_date_epoch_day"]),
        Index(value = ["kind"]),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** `RECEIPT` (company paid the worker) or `PAYMENT` (worker spent own money). */
    @ColumnInfo(name = "kind") val kind: String,

    /** Always stored positive; the sign is implied by [kind]. */
    @ColumnInfo(name = "amount") val amount: Long,

    /** What the money settled: `METRAJE`, `EXPENSE`, `SALARY`, `OTHER`. */
    @ColumnInfo(name = "reason") val reason: String,

    @ColumnInfo(name = "entry_date_epoch_day") val entryDateEpochDay: Long,

    /** Free text, e.g. `کارت به کارت`, `نقدی`, `چک`. */
    @ColumnInfo(name = "method") val method: String = "",

    @ColumnInfo(name = "project_name") val projectName: String = "",

    @ColumnInfo(name = "notes") val notes: String = "",

    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,
)
