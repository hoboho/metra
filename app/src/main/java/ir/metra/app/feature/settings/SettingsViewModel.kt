package ir.metra.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.notification.ReminderNotifier
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.notification.ReminderScheduler
import ir.metra.app.data.preferences.ThemeMode
import ir.metra.app.data.preferences.UserPreferences
import ir.metra.app.data.preferences.UserPreferencesRepository
import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserProfile = UserProfile.EMPTY,
    val settings: AppSettings = AppSettings.DEFAULT,
    val preferences: UserPreferences = UserPreferences.DEFAULT,
    val projects: List<Project> = emptyList(),
    val rules: List<PaymentRuleUi> = emptyList(),
    val reminderTimeLabel: String = "",
    val database: DatabaseInfoUi = DatabaseInfoUi.EMPTY,

    val message: String? = null,
    val saving: Boolean = false,
)

data class PaymentRuleUi(
    val id: Long,
    val label: String,
    val thresholdText: String,
    val rateText: String,
    val effectiveFromLabel: String,
    val isCurrent: Boolean,
)

data class DatabaseInfoUi(
    val workRecords: String = "",
    val projects: String = "",
    val expenses: String = "",
    val rules: String = "",
    val backups: String = "",
) {
    companion object {
        val EMPTY = DatabaseInfoUi()
    }
}

/**
 * Settings.
 *
 * Two persistence targets, on purpose: work defaults and rates go to Room (so a
 * backup carries them), while theme/app-lock/reminder scheduling stay in
 * DataStore because they describe this device.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val projectRepository: ProjectRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val workRecordRepository: WorkRecordRepository,
    private val reminderScheduler: ReminderScheduler,
    private val reminderNotifier: ReminderNotifier,
    private val clock: Clock,
    private val dateFormatter: DateFormatter,
    private val strings: StringProvider,
    private val numberFormatter: NumberFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            userRepository.observeProfile().collect { profile ->
                _state.update { it.copy(profile = profile) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        settings = settings,
                        reminderTimeLabel = dateFormatter.formatTime(settings.reminderMinuteOfDay),
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _state.update {
                    it.copy(
                        preferences = preferences,

                    )
                }
            }
        }
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        viewModelScope.launch {
            paymentRuleRepository.observeRules().collect { rules ->
                val today = JalaliCalendar.today().toEpochDay()
                _state.update {
                    it.copy(
                        rules = rules.map { rule ->
                            rule.toUi(isCurrent = rule.effectiveFromEpochDay <= today)
                        },
                    )
                }
            }
        }
        refreshDatabaseInfo()
    }

    // ------------------------------------------------------------- profile

    fun onProfileChange(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            val updated = transform(_state.value.profile)
            _state.update { it.copy(profile = updated) }
            userRepository.saveProfile(updated).onFailure { error ->
                _state.update { it.copy(message = error.metraError.userMessage) }
            }
        }
    }

    // ------------------------------------------------------- work defaults

    fun onSettingsChange(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = transform(_state.value.settings)
            _state.update { it.copy(settings = updated) }
            settingsRepository.saveSettings(updated).onFailure { error ->
                _state.update { it.copy(message = error.metraError.userMessage) }
            }
        }
    }

    // --------------------------------------------------------- appearance

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    // ---------------------------------------------------------- reminders

    fun onReminderChange(enabled: Boolean, minuteOfDay: Int) {
        viewModelScope.launch {
            settingsRepository.setReminder(enabled, minuteOfDay)
            preferencesRepository.setReminder(enabled, minuteOfDay)
            if (enabled) {
                if (reminderNotifier.notificationsEnabled()) {
                    reminderNotifier.ensureChannel()
                    reminderScheduler.schedule(minuteOfDay)
                    _state.update { it.copy(message = strings.string(R.string.msg_reminder_enabled)) }
                } else {
                    _state.update {
                        it.copy(message = strings.string(R.string.msg_reminder_permission))
                    }
                }
            } else {
                reminderScheduler.cancel()
            }
        }
    }


    // ------------------------------------------------------------- prefill

    fun onUsePreviousWorkdayChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUsePreviousWorkdayInfo(enabled)
            preferencesRepository.setUsePreviousWorkdayInfo(enabled)
        }
    }

    // ------------------------------------------------------- database info

    fun refreshDatabaseInfo() {
        viewModelScope.launch {
            val workRecords = workRecordRepository.countAll()
            val projects = projectRepository.getProjects().size
            val rules = paymentRuleRepository.getRules().size
            _state.update {
                it.copy(
                    database = DatabaseInfoUi(
                        workRecords = numberFormatter.formatPersian(workRecords.toLong()),
                        projects = numberFormatter.formatPersian(projects.toLong()),
                        expenses = "—",
                        rules = numberFormatter.formatPersian(rules.toLong()),
                        backups = "—",
                    ),
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun PaymentRule.toUi(isCurrent: Boolean): PaymentRuleUi = PaymentRuleUi(
        id = id,
        label = label ?: strings.string(R.string.label_rule_generic),
        thresholdText = strings.string(R.string.unit_meters_suffix, thresholdMeters.toString()),
        rateText = strings.string(R.string.unit_rate_per_meter, ratePerMeter.toString()),
        effectiveFromLabel = dateFormatter.formatLong(effectiveFromEpochDay),
        isCurrent = isCurrent,
    )
}
