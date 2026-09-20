package ir.metra.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentRulesUiState(
    val rules: List<PaymentRuleUi> = emptyList(),
    /**
     * Pre-fill values for a new rule, read from the user's stored work defaults
     * rather than baked into the UI — the threshold and rate are configurable,
     * so the dialog must not assume 400 / 15 000.
     */
    val defaultThresholdMeters: Int = PaymentRule.DEFAULT_THRESHOLD_METERS,
    val defaultRatePerMeter: Long = PaymentRule.DEFAULT_RATE_PER_METER,
    val message: String? = null,
)

/**
 * Payment rule administration.
 *
 * Editing an existing rule is intentionally not offered: rates are versioned, so
 * the only supported operation is appending a new one with an effective date.
 */
@HiltViewModel
class PaymentRulesViewModel @Inject constructor(
    private val paymentRuleRepository: PaymentRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val strings: StringProvider,
    private val dateFormatter: DateFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentRulesUiState())
    val state: StateFlow<PaymentRulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        defaultThresholdMeters = settings.defaultThresholdMeters,
                        defaultRatePerMeter = settings.defaultRatePerMeter,
                    )
                }
            }
        }
        viewModelScope.launch {
            paymentRuleRepository.observeRules().collect { rules ->
                val currentEffective = paymentRuleRepository
                    .applicableRuleOn(JalaliCalendar.today().toEpochDay())
                    .effectiveFromEpochDay
                _state.update {
                    it.copy(
                        rules = rules.map { rule ->
                            rule.toUi(isCurrent = rule.effectiveFromEpochDay == currentEffective)
                        },
                    )
                }
            }
        }
    }

    fun addRule(thresholdMeters: Int, ratePerMeter: Long, effectiveFromEpochDay: Long) {
        viewModelScope.launch {
            if (thresholdMeters < 0 || ratePerMeter < 0) {
                _state.update { it.copy(message = strings.string(R.string.msg_values_not_negative)) }
                return@launch
            }
            val result = paymentRuleRepository.addRule(
                PaymentRule(
                    thresholdMeters = thresholdMeters,
                    ratePerMeter = ratePerMeter,
                    effectiveFromEpochDay = effectiveFromEpochDay,
                    label = strings.string(R.string.label_rule_generic),
                    createdAtEpochMilli = clock.nowEpochMilli(),
                ),
            )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(message = strings.string(R.string.msg_rule_added)) }
                },
                onFailure = { error -> _state.update { it.copy(message = error.metraError.userMessage) } },
            )
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
