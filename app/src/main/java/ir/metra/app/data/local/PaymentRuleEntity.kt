package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A versioned payment rule.
 *
 * Rows are append-only in practice: changing the rate inserts a new row with a
 * later `effective_from_epoch_day`. Work records snapshot the rule that applied
 * to them, so this table can grow without ever rewriting history.
 */
@Entity(
    tableName = "payment_rules",
    indices = [Index(value = ["effective_from_epoch_day"], unique = true)],
)
data class PaymentRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "threshold_meters") val thresholdMeters: Int,
    @ColumnInfo(name = "rate_per_meter") val ratePerMeter: Long,
    @ColumnInfo(name = "effective_from_epoch_day") val effectiveFromEpochDay: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "label") val label: String? = null,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
)
