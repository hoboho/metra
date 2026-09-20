package ir.metra.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.metra.app.core.common.AppDispatchers
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.DefaultDispatcher
import ir.metra.app.core.common.IoDispatcher
import ir.metra.app.core.common.MainDispatcher
import ir.metra.app.core.common.SystemClock
import ir.metra.app.core.date.DateRangeResolver
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.data.local.AppSettingsDao
import ir.metra.app.data.local.BackupMetadataDao
import ir.metra.app.data.local.ExpenseDao
import ir.metra.app.data.local.LedgerEntryDao
import ir.metra.app.data.local.MetraDatabase
import ir.metra.app.data.local.PaymentRuleDao
import ir.metra.app.data.local.ProjectDao
import ir.metra.app.data.local.ReportConfigurationDao
import ir.metra.app.data.local.UserProfileDao
import ir.metra.app.data.local.WorkRecordDao
import ir.metra.app.domain.repository.LedgerRepository
import ir.metra.app.data.repository.LedgerRepositoryImpl
import ir.metra.app.data.repository.ExpenseRepositoryImpl
import ir.metra.app.data.repository.PaymentRuleRepositoryImpl
import ir.metra.app.data.repository.ProjectRepositoryImpl
import ir.metra.app.data.repository.SettingsRepositoryImpl
import ir.metra.app.data.repository.UserRepositoryImpl
import ir.metra.app.data.repository.WorkRecordRepositoryImpl
import ir.metra.app.data.repository.WorkStatisticsRepositoryImpl
import ir.metra.app.domain.repository.ExpenseRepository
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import ir.metra.app.domain.repository.WorkStatisticsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Application-wide singletons.
 *
 * Dispatchers and the clock are provided behind qualifiers so tests can swap in
 * a test scheduler and a frozen clock without touching production code.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    fun provideAppDispatchers(
        @IoDispatcher io: CoroutineDispatcher,
        @DefaultDispatcher default: CoroutineDispatcher,
        @MainDispatcher main: CoroutineDispatcher,
    ): AppDispatchers = AppDispatchers(io = io, default = default, main = main)

    @Provides
    @Singleton
    fun provideNumberFormatter(): NumberFormatter = NumberFormatter()

    @Provides
    @Singleton
    fun provideDateFormatter(): DateFormatter = DateFormatter()

    @Provides
    @Singleton
    fun provideDateRangeResolver(): DateRangeResolver = DateRangeResolver()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The single Room instance.
     *
     * No destructive migration fallback is registered on purpose: schema changes
     * must be delivered as explicit migrations so user data can never be wiped
     * by an upgrade. See docs/ARCHITECTURE.md.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MetraDatabase =
        Room.databaseBuilder(context, MetraDatabase::class.java, MetraDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideUserProfileDao(db: MetraDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideProjectDao(db: MetraDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideWorkRecordDao(db: MetraDatabase): WorkRecordDao = db.workRecordDao()

    @Provides
    fun provideExpenseDao(db: MetraDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideLedgerEntryDao(db: MetraDatabase): LedgerEntryDao = db.ledgerEntryDao()

    @Provides
    fun providePaymentRuleDao(db: MetraDatabase): PaymentRuleDao = db.paymentRuleDao()

    @Provides
    fun provideAppSettingsDao(db: MetraDatabase): AppSettingsDao = db.appSettingsDao()

    @Provides
    fun provideReportConfigurationDao(db: MetraDatabase): ReportConfigurationDao = db.reportConfigurationDao()

    @Provides
    fun provideBackupMetadataDao(db: MetraDatabase): BackupMetadataDao = db.backupMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @dagger.Binds
    @Singleton
    abstract fun bindWorkRecordRepository(impl: WorkRecordRepositoryImpl): WorkRecordRepository

    @dagger.Binds
    @Singleton
    abstract fun bindWorkStatisticsRepository(impl: WorkStatisticsRepositoryImpl): WorkStatisticsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @dagger.Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @dagger.Binds
    @Singleton
    abstract fun bindPaymentRuleRepository(impl: PaymentRuleRepositoryImpl): PaymentRuleRepository

    @dagger.Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @dagger.Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository
}
