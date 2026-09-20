package ir.metra.app.data.repository

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.data.local.AppSettingsDao
import ir.metra.app.data.local.AppSettingsEntity
import ir.metra.app.data.local.UserProfileDao
import ir.metra.app.data.local.UserProfileEntity
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val clock: Clock,
) : UserRepository {

    override fun observeProfile(): Flow<UserProfile> =
        userProfileDao.observe().map { it?.toDomain() ?: UserProfile.EMPTY }

    override suspend fun getProfile(): UserProfile =
        userProfileDao.get()?.toDomain() ?: UserProfile.EMPTY

    override suspend fun saveProfile(profile: UserProfile): MetraResult<Unit> = runCatching {
        val now = clock.nowEpochMilli()
        val existing = userProfileDao.get()
        userProfileDao.upsert(
            profile.toEntity(
                nowEpochMilli = now,
                createdAtEpochMilli = existing?.createdAtEpochMilli ?: now,
            ),
        )
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "save profile")) })

    override suspend fun completeOnboarding(): MetraResult<Unit> = runCatching {
        val profile = getProfile()
        saveProfile(profile.copy(onboardingCompleted = true)).getOrThrow()
        Unit
    }.fold({ success(Unit) }, { failure(MetraError.Unknown(it.message ?: "onboarding")) })
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
    private val clock: Clock,
    private val strings: StringProvider,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        appSettingsDao.observe().map { it?.toDomain() ?: AppSettings.DEFAULT }

    override suspend fun getSettings(): AppSettings =
        appSettingsDao.get()?.toDomain() ?: seedDefaults()

    override suspend fun saveSettings(settings: AppSettings): MetraResult<Unit> = runCatching {
        if (settings.defaultThresholdMeters < 0) {
            return failure(MetraError.Validation(MetraError.Validation.Field.OTHER, strings.string(R.string.msg_threshold_not_negative)))
        }
        if (settings.defaultRatePerMeter < 0) {
            return failure(MetraError.Validation(MetraError.Validation.Field.MONEY, strings.string(R.string.msg_rate_not_negative)))
        }
        if (settings.reminderMinuteOfDay !in 0 until 24 * 60) {
            return failure(MetraError.Validation(MetraError.Validation.Field.OTHER, strings.string(R.string.msg_reminder_time_invalid)))
        }
        appSettingsDao.upsert(settings.toEntity(clock.nowEpochMilli()))
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "save settings")) })

    override suspend fun setUsePreviousWorkdayInfo(enabled: Boolean): MetraResult<Unit> = runCatching {
        ensureRowExists()
        appSettingsDao.setUsePreviousWorkdayInfo(enabled, clock.nowEpochMilli())
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "prefill toggle")) })

    override suspend fun setReminder(enabled: Boolean, minuteOfDay: Int): MetraResult<Unit> = runCatching {
        if (minuteOfDay !in 0 until 24 * 60) {
            return failure(MetraError.Validation(MetraError.Validation.Field.OTHER, strings.string(R.string.msg_reminder_time_invalid)))
        }
        ensureRowExists()
        appSettingsDao.updateReminder(enabled, minuteOfDay, clock.nowEpochMilli())
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "reminder")) })

    /** Creates the singleton settings row with product defaults on first use. */
    private suspend fun seedDefaults(): AppSettings {
        val defaults = AppSettings.DEFAULT.copy(
            defaultThresholdMeters = PaymentRule.DEFAULT_THRESHOLD_METERS,
            defaultRatePerMeter = PaymentRule.DEFAULT_RATE_PER_METER,
        )
        appSettingsDao.upsert(defaults.toEntity(clock.nowEpochMilli()))
        return defaults
    }

    private suspend fun ensureRowExists() {
        if (appSettingsDao.get() == null) seedDefaults()
    }
}
