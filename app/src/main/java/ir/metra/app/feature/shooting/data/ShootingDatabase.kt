package ir.metra.app.feature.shooting.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "shooting_reports", indices = [Index("project_id"), Index("work_date_epoch_day")])
data class ShootingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    @ColumnInfo(name = "project_name") val projectName: String,
    @ColumnInfo(name = "work_date_epoch_day") val workDateEpochDay: Long,
    @ColumnInfo(name = "work_area") val workArea: String,
    val employer: String,
    val supervisor: String,
    @ColumnInfo(name = "worker_count") val workerCount: Int = 0,
    val notes: String = "",
    @ColumnInfo(name = "total_meters") val totalMeters: Long = 0,
    @ColumnInfo(name = "created_at_epoch_milli") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at_epoch_milli") val updatedAtEpochMilli: Long,
)

@Entity(
    tableName = "shooting_operations",
    foreignKeys = [ForeignKey(entity = ShootingReportEntity::class, parentColumns = ["id"], childColumns = ["report_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("report_id"), Index("cable_type_id")],
)
data class ShootingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "report_id") val reportId: Long,
    @ColumnInfo(name = "start_point_type") val startPointType: String,
    @ColumnInfo(name = "start_point_code") val startPointCode: String,
    @ColumnInfo(name = "start_meter") val startMeter: Long,
    @ColumnInfo(name = "end_point_type") val endPointType: String,
    @ColumnInfo(name = "end_point_code") val endPointCode: String,
    @ColumnInfo(name = "end_meter") val endMeter: Long,
    @ColumnInfo(name = "cable_type_id") val cableTypeId: Long,
    @ColumnInfo(name = "operation_meters") val operationMeters: Long,
)

@Entity(tableName = "cable_types", indices = [Index(value = ["core_count"], unique = true)])
data class CableTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "core_count") val coreCount: Int,
    val label: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)

@Dao
interface ShootingDao {
    @Query("SELECT * FROM shooting_reports ORDER BY work_date_epoch_day DESC, id DESC")
    fun observeReports(): Flow<List<ShootingReportEntity>>
    @Query("SELECT * FROM shooting_operations WHERE report_id = :reportId ORDER BY id")
    suspend fun getOperations(reportId: Long): List<ShootingOperationEntity>
    @Query("SELECT * FROM cable_types WHERE is_active = 1 ORDER BY core_count")
    fun observeCableTypes(): Flow<List<CableTypeEntity>>
    @Query("SELECT * FROM cable_types WHERE is_active = 1 ORDER BY core_count")
    suspend fun getCableTypes(): List<CableTypeEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCableTypes(items: List<CableTypeEntity>)
    @Insert
    suspend fun insertReport(report: ShootingReportEntity): Long
    @Insert
    suspend fun insertOperations(items: List<ShootingOperationEntity>)
    @Query("DELETE FROM shooting_reports WHERE id = :id")
    suspend fun deleteReport(id: Long)
    @Query("SELECT * FROM shooting_reports WHERE work_date_epoch_day BETWEEN :start AND :end ORDER BY work_date_epoch_day")
    suspend fun reportsInRange(start: Long, end: Long): List<ShootingReportEntity>
    @Query("SELECT COALESCE(SUM(total_meters), 0) FROM shooting_reports WHERE work_date_epoch_day BETWEEN :start AND :end")
    suspend fun totalMeters(start: Long, end: Long): Long
    @Query("SELECT * FROM company_expenses WHERE expense_date_epoch_day BETWEEN :start AND :end ORDER BY expense_date_epoch_day DESC, id DESC")
    suspend fun expensesInRange(start: Long, end: Long): List<CompanyExpenseEntity>
    @Query("SELECT * FROM company_expenses ORDER BY expense_date_epoch_day DESC, id DESC")
    suspend fun allExpenses(): List<CompanyExpenseEntity>
    @Query("SELECT * FROM expense_settlements WHERE expense_id = :expenseId ORDER BY settlement_date_epoch_day, id")
    suspend fun settlements(expenseId: Long): List<ExpenseSettlementEntity>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expense_settlements WHERE expense_id = :expenseId")
    suspend fun settledAmount(expenseId: Long): Long
    @Insert
    suspend fun insertCompanyExpense(expense: CompanyExpenseEntity): Long
    @Insert
    suspend fun insertSettlement(settlement: ExpenseSettlementEntity): Long
    @Query("DELETE FROM company_expenses WHERE id = :id")
    suspend fun deleteCompanyExpense(id: Long)
    @Query("DELETE FROM expense_settlements")
    suspend fun deleteAllSettlements()
    @Query("DELETE FROM company_expenses")
    suspend fun deleteAllCompanyExpenses()
    @Query("SELECT * FROM expense_settlements ORDER BY settlement_date_epoch_day, id")
    suspend fun allSettlements(): List<ExpenseSettlementEntity>
    @Query("SELECT * FROM shooting_reports WHERE work_date_epoch_day BETWEEN :start AND :end AND (:projectId IS NULL OR project_id = :projectId) ORDER BY work_date_epoch_day, id")
    suspend fun reportsInRangeForProject(start: Long, end: Long, projectId: Long?): List<ShootingReportEntity>
    @Insert
    suspend fun insertCompanyExpenses(items: List<CompanyExpenseEntity>)
    @Insert
    suspend fun insertSettlements(items: List<ExpenseSettlementEntity>)
}

@Database(entities = [ShootingReportEntity::class, ShootingOperationEntity::class, CableTypeEntity::class, CompanyExpenseEntity::class, ExpenseSettlementEntity::class], version = 2, exportSchema = true)
abstract class ShootingDatabase : RoomDatabase() {
    abstract fun shootingDao(): ShootingDao
    companion object {
        const val NAME = "shooting.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS company_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, project_id INTEGER, project_name TEXT NOT NULL, expense_date_epoch_day INTEGER NOT NULL, activity TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL, description TEXT NOT NULL, receipt_reference TEXT NOT NULL, created_at_epoch_milli INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_company_expenses_project_id ON company_expenses(project_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_company_expenses_expense_date_epoch_day ON company_expenses(expense_date_epoch_day)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_company_expenses_activity ON company_expenses(activity)")
                db.execSQL("CREATE TABLE IF NOT EXISTS expense_settlements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, expense_id INTEGER NOT NULL, settlement_date_epoch_day INTEGER NOT NULL, amount INTEGER NOT NULL, notes TEXT NOT NULL, created_at_epoch_milli INTEGER NOT NULL, FOREIGN KEY(expense_id) REFERENCES company_expenses(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_settlements_expense_id ON expense_settlements(expense_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_settlements_settlement_date_epoch_day ON expense_settlements(settlement_date_epoch_day)")
            }
        }
    }
}
