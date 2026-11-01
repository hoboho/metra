package ir.metra.app.feature.shooting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "company_expenses", indices = [Index("project_id"), Index("expense_date_epoch_day"), Index("activity")])
data class CompanyExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    @ColumnInfo(name = "project_name") val projectName: String,
    @ColumnInfo(name = "expense_date_epoch_day") val expenseDateEpochDay: Long,
    val activity: String = "GENERAL",
    val category: String,
    val amount: Long,
    val description: String = "",
    @ColumnInfo(name = "receipt_reference") val receiptReference: String = "",
    @ColumnInfo(name = "created_at_epoch_milli") val createdAtEpochMilli: Long,
)

@Entity(
    tableName = "expense_settlements",
    foreignKeys = [ForeignKey(entity = CompanyExpenseEntity::class, parentColumns = ["id"], childColumns = ["expense_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expense_id"), Index("settlement_date_epoch_day")],
)
data class ExpenseSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "expense_id") val expenseId: Long,
    @ColumnInfo(name = "settlement_date_epoch_day") val settlementDateEpochDay: Long,
    val amount: Long,
    val notes: String = "",
    @ColumnInfo(name = "created_at_epoch_milli") val createdAtEpochMilli: Long,
)
