package ir.metra.app.feature.shooting.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    @Query("SELECT * FROM shooting_reports ORDER BY work_date_epoch_day DESC, id DESC")
    suspend fun getAllReports(): List<ShootingReportEntity>
    @Query("SELECT * FROM shooting_reports WHERE id = :reportId")
    suspend fun getReport(reportId: Long): ShootingReportEntity?
    @Query("SELECT * FROM shooting_operations WHERE report_id = :reportId ORDER BY id")
    suspend fun getOperations(reportId: Long): List<ShootingOperationEntity>
    @Query("DELETE FROM shooting_operations WHERE report_id = :reportId")
    suspend fun deleteOperationsForReport(reportId: Long)
    @Update
    suspend fun updateReport(report: ShootingReportEntity)
    @Query("DELETE FROM shooting_reports")
    suspend fun deleteAllReports()
    @Query("SELECT * FROM cable_types ORDER BY core_count")
    fun observeCableTypes(): Flow<List<CableTypeEntity>>
    @Query("SELECT * FROM cable_types ORDER BY core_count")
    suspend fun getCableTypes(): List<CableTypeEntity>
    @Query("SELECT * FROM cable_types ORDER BY core_count")
    suspend fun getAllCableTypes(): List<CableTypeEntity>
    @Query("SELECT * FROM cable_types WHERE core_count = :coreCount LIMIT 1")
    suspend fun getCableTypeByCoreCount(coreCount: Int): CableTypeEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM cable_types WHERE id = :id)")
    suspend fun cableTypeExists(id: Long): Boolean
    @Update
    suspend fun updateCableType(item: CableTypeEntity)
    @Query("DELETE FROM cable_types")
    suspend fun deleteAllCableTypes()
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
}

@Database(entities = [ShootingReportEntity::class, ShootingOperationEntity::class, CableTypeEntity::class], version = 1, exportSchema = true)
abstract class ShootingDatabase : RoomDatabase() {
    abstract fun shootingDao(): ShootingDao
    companion object { const val NAME = "shooting.db" }
}
