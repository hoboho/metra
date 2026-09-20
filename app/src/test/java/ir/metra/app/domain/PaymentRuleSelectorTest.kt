package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.PaymentRule
import org.junit.Test

/**
 * Rule selection by effective date.
 *
 * The point of these tests is the historical-stability guarantee: adding a new
 * rule for the future must never re-price a record from the past.
 */
class PaymentRuleSelectorTest {

    private fun rule(id: Long, effectiveFrom: Long, rate: Long, threshold: Int = 400) = PaymentRule(
        id = id,
        thresholdMeters = threshold,
        ratePerMeter = rate,
        effectiveFromEpochDay = effectiveFrom,
        createdAtEpochMilli = 0L,
    )

    @Test
    fun `returns null when no rules exist`() {
        assertThat(PaymentRuleSelector.applicableRule(emptyList(), 100L)).isNull()
    }

    @Test
    fun `picks the newest rule that is already in effect`() {
        val rules = listOf(
            rule(1, effectiveFrom = 0, rate = 15_000),
            rule(2, effectiveFrom = 100, rate = 18_000),
            rule(3, effectiveFrom = 200, rate = 21_000),
        )
        assertThat(PaymentRuleSelector.applicableRule(rules, 150)?.ratePerMeter).isEqualTo(18_000L)
        assertThat(PaymentRuleSelector.applicableRule(rules, 200)?.ratePerMeter).isEqualTo(21_000L)
        assertThat(PaymentRuleSelector.applicableRule(rules, 250)?.ratePerMeter).isEqualTo(21_000L)
    }

    @Test
    fun `a rule becomes applicable exactly on its effective date`() {
        val rules = listOf(
            rule(1, effectiveFrom = 0, rate = 15_000),
            rule(2, effectiveFrom = 100, rate = 18_000),
        )
        assertThat(PaymentRuleSelector.applicableRule(rules, 99)?.ratePerMeter).isEqualTo(15_000L)
        assertThat(PaymentRuleSelector.applicableRule(rules, 100)?.ratePerMeter).isEqualTo(18_000L)
    }

    @Test
    fun `a workday predating every rule falls back to the earliest rule`() {
        val rules = listOf(
            rule(1, effectiveFrom = 500, rate = 15_000),
            rule(2, effectiveFrom = 900, rate = 18_000),
        )
        // There is no rule that was "in force" before day 500; paying nothing
        // would silently destroy the record, so the earliest rule is used.
        assertThat(PaymentRuleSelector.applicableRule(rules, 100)?.ratePerMeter).isEqualTo(15_000L)
    }

    @Test
    fun `adding a future rule does not change what past dates resolve to`() {
        val original = listOf(rule(1, effectiveFrom = 0, rate = 15_000))
        val historical = PaymentRuleSelector.applicableRule(original, 50)?.ratePerMeter

        // A new rate is added for next year.
        val withFuture = original + rule(2, effectiveFrom = 1_000, rate = 25_000)
        val afterChange = PaymentRuleSelector.applicableRule(withFuture, 50)?.ratePerMeter

        assertThat(afterChange).isEqualTo(historical)
        assertThat(afterChange).isEqualTo(15_000L)
    }

    @Test
    fun `unsorted input is handled`() {
        val rules = listOf(
            rule(3, effectiveFrom = 200, rate = 21_000),
            rule(1, effectiveFrom = 0, rate = 15_000),
            rule(2, effectiveFrom = 100, rate = 18_000),
        )
        assertThat(PaymentRuleSelector.applicableRule(rules, 150)?.ratePerMeter).isEqualTo(18_000L)
    }

    @Test
    fun `currentRule matches the rule for today`() {
        val rules = listOf(
            rule(1, effectiveFrom = 0, rate = 15_000),
            rule(2, effectiveFrom = 100, rate = 18_000),
        )
        assertThat(PaymentRuleSelector.currentRule(rules, 120)?.id).isEqualTo(2L)
    }
}
