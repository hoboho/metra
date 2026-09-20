package ir.metra.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.notification.ReminderScheduler
import ir.metra.app.data.preferences.UserPreferencesRepository
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.UserProfile
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.UserRepository
import ir.metra.app.R
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.i18n.StringProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val fullName: String = "",
    val companyName: String = "",
    val thresholdMeters: String = PaymentRule.DEFAULT_THRESHOLD_METERS.toString(),
    val ratePerMeter: String = PaymentRule.DEFAULT_RATE_PER_METER.toString(),
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 20 * 60,
    val saving: Boolean = false,
    val completed: Boolean = false,
) {
    val totalSteps = 4
    val isLastStep: Boolean get() = step == totalSteps - 1
    val canContinue: Boolean get() = when (step) {
        1 -> fullName.isNotBlank()
        else -> true
    }
}

/**
 * First-run setup.
 *
 * Four short steps: welcome, identity, payment rule, reminder. Nothing is
 * mandatory except a name (used in report headers), and there is no login, no
 * account and no network call anywhere in the flow.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val strings: StringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onFieldChange(transform: (OnboardingState) -> OnboardingState) {
        _state.update(transform)
    }

    fun next() {
        _state.update { current ->
            if (current.isLastStep) current else current.copy(step = current.step + 1)
        }
    }

    fun back() {
        _state.update { current ->
            if (current.step == 0) current else current.copy(step = current.step - 1)
        }
    }

    /** Persists the answers and marks onboarding complete. */
    fun finish() {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val current = _state.value
            val threshold = current.thresholdMeters.toIntOrNull() ?: PaymentRule.DEFAULT_THRESHOLD_METERS
            val rate = current.ratePerMeter.toLongOrNull() ?: PaymentRule.DEFAULT_RATE_PER_METER

            userRepository.saveProfile(
                UserProfile(
                    fullName = current.fullName,
                    companyName = current.companyName,
                    onboardingCompleted = true,
                ),
            )

            val settings = settingsRepository.getSettings().copy(
                defaultThresholdMeters = threshold.coerceAtLeast(0),
                defaultRatePerMeter = rate.coerceAtLeast(0),
                reminderEnabled = current.reminderEnabled,
                reminderMinuteOfDay = current.reminderMinuteOfDay,
            )
            settingsRepository.saveSettings(settings)

            // Seed the first payment rule version so history starts versioned.
            paymentRuleRepository.addRule(
                PaymentRule(
                    thresholdMeters = threshold.coerceAtLeast(0),
                    ratePerMeter = rate.coerceAtLeast(0),
                    effectiveFromEpochDay = JalaliCalendar.today().toEpochDay(),
                    label = strings.string(R.string.label_rule_initial),
                    createdAtEpochMilli = System.currentTimeMillis(),
                ),
            )

            preferencesRepository.setReminder(current.reminderEnabled, current.reminderMinuteOfDay)
            preferencesRepository.setOnboardingCompleted(true)
            if (current.reminderEnabled) {
                reminderScheduler.schedule(current.reminderMinuteOfDay)
            }

            _state.update { it.copy(saving = false, completed = true) }
        }
    }
}
