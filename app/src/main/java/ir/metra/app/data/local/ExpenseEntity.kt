package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single expense line attached to a workday.
 *
 * Deleting the parent work record cascades here: an expense without its workday
 * is not meaningful, and reports would otherwise have to special-case orphans.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = WorkRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["work_record_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["work_record_id"]), Index(value = ["category"])],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "work_record_id") val workRecordId: Long,
    @ColumnInfo(name = "amount") val amount: Long,
    /** Persisted as the enum name so the ordinal can never shift meaning. */
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "receipt_photo_uri") val receiptPhotoUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
)
