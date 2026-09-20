package ir.metra.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Payment rule access.
 *
 * `applicableOn` implements the "effective from" contract: the newest rule whose
 * effective date is on or before the workday wins.
 */
@Dao
interface PaymentRuleDao {

    @Query(
        """
        SELECT * FROM payment_rules
        WHERE effective_from_epoch_day <= :epochDay
        ORDER BY effective_from_epoch_day DESC
        LIMIT 1
        """,
    )
    suspend fun applicableOn(epochDay: Long): PaymentRuleEntity?

    @Query(
        """
        SELECT * FROM payment_rules
        WHERE effective_from_epoch_day <= :epochDay
        ORDER BY effective_from_epoch_day DESC
        LIMIT 1
        """,
    )
    fun observeApplicableOn(epochDay: Long): Flow<PaymentRuleEntity?>

    @Query("SELECT * FROM payment_rules ORDER BY effective_from_epoch_day DESC")
    fun observeAll(): Flow<List<PaymentRuleEntity>>

    @Query("SELECT * FROM payment_rules ORDER BY effective_from_epoch_day DESC")
    suspend fun getAll(): List<PaymentRuleEntity>

    @Query("SELECT * FROM payment_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PaymentRuleEntity?

    @Query("SELECT COUNT(*) FROM payment_rules")
    suspend fun count(): Int

    /**
     * Falls back to the earliest known rule when a workday predates every rule,
     * so very old records still get a sensible rate instead of paying zero.
     */
    @Query("SELECT * FROM payment_rules ORDER BY effective_from_epoch_day ASC LIMIT 1")
    suspend fun earliest(): PaymentRuleEntity?

    @Upsert
    suspend fun upsert(rule: PaymentRuleEntity): Long

    @Query("DELETE FROM payment_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM payment_rules")
    suspend fun deleteAll()
}

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = :id LIMIT 1")
    fun observe(id: Long = AppSettingsEntity.SINGLETON_ID): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = :id LIMIT 1")
    suspend fun get(id: Long = AppSettingsEntity.SINGLETON_ID): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET reminder_enabled = :enabled, reminder_minute_of_day = :minute, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateReminder(
        enabled: Boolean,
        minute: Int,
        updatedAt: Long,
        id: Long = AppSettingsEntity.SINGLETON_ID,
    )

    @Query("UPDATE app_settings SET use_previous_workday_info = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setUsePreviousWorkdayInfo(
        enabled: Boolean,
        updatedAt: Long,
        id: Long = AppSettingsEntity.SINGLETON_ID,
    )
}

@Dao
interface ReportConfigurationDao {

    @Query("SELECT * FROM report_configurations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ReportConfigurationEntity>>

    @Query("SELECT * FROM report_configurations ORDER BY updated_at DESC")
    suspend fun getAll(): List<ReportConfigurationEntity>

    @Upsert
    suspend fun upsert(configuration: ReportConfigurationEntity): Long

    @Query("DELETE FROM report_configurations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM report_configurations")
    suspend fun deleteAll()
}

@Dao
interface BackupMetadataDao {

    @Query("SELECT * FROM backup_metadata ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BackupMetadataEntity>>

    @Query("SELECT * FROM backup_metadata ORDER BY created_at DESC")
    suspend fun getAll(): List<BackupMetadataEntity>

    @Upsert
    suspend fun upsert(metadata: BackupMetadataEntity): Long

    @Query("DELETE FROM backup_metadata")
    suspend fun deleteAll()
}
