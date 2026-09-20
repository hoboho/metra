package ir.metra.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local preferences.
 *
 * The split is deliberate: anything that describes the *user's data* (work
 * defaults, rates, currency) lives in Room so backups carry it; anything that
 * describes the *device* (theme, app lock, reminder scheduling) lives here.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 20 * 60,
    val usePreviousWorkdayInfo: Boolean = true,
    val lastReminderNotifiedEpochDay: Long? = null,

    val onboardingCompleted: Boolean = false,
) {
    companion object {
        val DEFAULT = UserPreferences()
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<UserPreferences> = dataStore.data.map { stored ->
        UserPreferences(
            themeMode = stored[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,

            reminderEnabled = stored[Keys.REMINDER_ENABLED] ?: false,
            reminderMinuteOfDay = stored[Keys.REMINDER_MINUTE] ?: 20 * 60,
            usePreviousWorkdayInfo = stored[Keys.USE_PREVIOUS_WORKDAY] ?: true,
            lastReminderNotifiedEpochDay = stored[Keys.LAST_REMINDED_EPOCH_DAY],

            onboardingCompleted = stored[Keys.ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int) = edit {
        it[Keys.REMINDER_ENABLED] = enabled
        it[Keys.REMINDER_MINUTE] = minuteOfDay
    }

    suspend fun setUsePreviousWorkdayInfo(enabled: Boolean) =
        edit { it[Keys.USE_PREVIOUS_WORKDAY] = enabled }

    suspend fun markReminderNotified(epochDay: Long) =
        edit { it[Keys.LAST_REMINDED_EPOCH_DAY] = epochDay }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.ONBOARDING_COMPLETED] = completed }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")

        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute_of_day")
        val USE_PREVIOUS_WORKDAY = booleanPreferencesKey("use_previous_workday_info")
        val LAST_REMINDED_EPOCH_DAY = longPreferencesKey("last_reminded_epoch_day")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
