package ir.metra.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Metra's local database — the single source of truth.
 *
 * **Migration policy:** `fallbackToDestructiveMigration` is deliberately *not*
 * used. Schema changes must ship with an explicit migration registered in
 * [MetraDatabaseModule], and `room.schemaLocation` exports the versioned JSON
 * schemas that `MetraDatabaseMigrationTest` validates against.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        ProjectEntity::class,
        WorkRecordEntity::class,
        ExpenseEntity::class,
        PaymentRuleEntity::class,
        AppSettingsEntity::class,
        ReportConfigurationEntity::class,
        BackupMetadataEntity::class,
        LedgerEntryEntity::class,

    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MetraDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun projectDao(): ProjectDao
    abstract fun workRecordDao(): WorkRecordDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun paymentRuleDao(): PaymentRuleDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun reportConfigurationDao(): ReportConfigurationDao
    abstract fun backupMetadataDao(): BackupMetadataDao
    abstract fun ledgerEntryDao(): LedgerEntryDao

    companion object {
        const val NAME = "metra2.db"
    }
}
