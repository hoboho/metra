package ir.metra.app.feature.shooting.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.metra.app.feature.shooting.data.ShootingDatabase
import ir.metra.app.feature.shooting.data.ShootingRepository
import ir.metra.app.domain.repository.ShootingSummaryRepository
import ir.metra.app.domain.repository.ShootingBackupRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShootingModule {
    @Provides
    @Singleton
    fun provideShootingDatabase(@ApplicationContext context: Context): ShootingDatabase =
        Room.databaseBuilder(context, ShootingDatabase::class.java, ShootingDatabase.NAME).build()

    @Provides
    fun provideShootingSummaryRepository(repository: ShootingRepository): ShootingSummaryRepository = repository

    @Provides
    fun provideShootingBackupRepository(repository: ShootingRepository): ShootingBackupRepository = repository
}
