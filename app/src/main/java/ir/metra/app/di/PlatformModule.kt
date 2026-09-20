package ir.metra.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.metra.app.core.backup.AppInfoProvider
import ir.metra.app.core.backup.AppInfoProviderImpl
import ir.metra.app.core.backup.BackupDirectoryProvider
import ir.metra.app.core.backup.ReportDirectoryProvider
import ir.metra.app.core.i18n.AndroidStringProvider
import ir.metra.app.core.i18n.StringProvider
import java.io.File
import javax.inject.Singleton

private val Context.metraDataStore: DataStore<Preferences> by preferencesDataStore(name = "metra_preferences")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.metraDataStore
}

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    /**
     * Reports are written under `files/reports`, which `file_paths.xml` exposes
     * through the FileProvider — the same path used for sharing.
     */
    @Provides
    @Singleton
    fun provideReportDirectoryProvider(@ApplicationContext context: Context): ReportDirectoryProvider =
        ReportDirectoryProvider {
            File(context.filesDir, "reports").apply { if (!exists()) mkdirs() }
        }

    /** Automatic pre-restore safety snapshots. */
    @Provides
    @Singleton
    fun provideBackupDirectoryProvider(@ApplicationContext context: Context): BackupDirectoryProvider =
        BackupDirectoryProvider {
            File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        }

    @Provides
    @Singleton
    fun provideAppInfoProvider(impl: AppInfoProviderImpl): AppInfoProvider = impl

    /** Resolves user-facing strings for ViewModels, which cannot use stringResource. */
    @Provides
    @Singleton
    fun provideStringProvider(impl: AndroidStringProvider): StringProvider = impl
}

@Module
@InstallIn(SingletonComponent::class)
object ContextModule {

    @Provides
    @Singleton
    fun provideRawContext(@ApplicationContext context: Context): Context = context
}
