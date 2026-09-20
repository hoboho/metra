package ir.metra.app.domain

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.PaymentRule
import org.junit.Test

/**
 * The threshold behaviour of the additional-meter calculation.
 *
 * These are the product's core financial rules, so every boundary named in the
 * specification is pinned here: 0, 1, 399, 400, 401, 520 and a very large value.
 */
class PaymentCalculatorTest {

    private val defaultRule = PaymentRule(
        thresholdMeters = 400,
        ratePerMeter = 15_000L,
        effectiveFromEpochDay = 0L,
        createdAtEpochMilli = 0L,
    )

    // -------------------------------------------------- threshold boundaries

    @Test
    fun `zero meters produces zero additional meters and zero payment`() {
        assertThat(PaymentCalculator.additionalMeters(0, 400)).isEqualTo(0)
        assertThat(PaymentCalculator.additionalPayment(0, 15_000L)).isEqualTo(0L)
    }

    @Test
    fun `one meter below threshold produces nothing`() {
        assertThat(PaymentCalculator.additionalMeters(1, 400)).isEqualTo(0)
        assertThat(PaymentCalculator.additionalPayment(1, defaultRule)).isEqualTo(0L)
    }

    @Test
    fun `399 meters produces zero additional meters`() {
        assertThat(PaymentCalculator.additionalMeters(399, 400)).isEqualTo(0)
        assertThat(PaymentCalculator.additionalPayment(399, defaultRule)).isEqualTo(0L)
    }

    @Test
    fun `exactly 400 meters produces zero additional meters and zero payment`() {
        assertThat(PaymentCalculator.additionalMeters(400, 400)).isEqualTo(0)
        assertThat(PaymentCalculator.additionalPayment(400, defaultRule)).isEqualTo(0L)
    }

    @Test
    fun `401 meters produces exactly one additional meter`() {
        assertThat(PaymentCalculator.additionalMeters(401, 400)).isEqualTo(1)
        assertThat(PaymentCalculator.additionalPayment(401, defaultRule)).isEqualTo(15_000L)
    }

    @Test
    fun `520 meters produces 120 additional meters and 1_800_000 toman`() {
        assertThat(PaymentCalculator.additionalMeters(520, 400)).isEqualTo(120)
        assertThat(PaymentCalculator.additionalPayment(520, defaultRule)).isEqualTo(1_800_000L)
    }

    @Test
    fun `large meter values stay exact without overflow`() {
        // 10,000,000 m at 15,000 toman = 149,994,000,000 toman, far beyond Int range.
        val additional = PaymentCalculator.additionalMeters(10_000_000, 400)
        assertThat(additional).isEqualTo(9_999_600)
        val payment = PaymentCalculator.additionalPayment(additional, 15_000L)
        assertThat(payment).isEqualTo(9_999_600L * 15_000L)
        assertThat(payment).isEqualTo(149_994_000_000L)
    }

    // ------------------------------------------------------ threshold shapes

    @Test
    fun `zero threshold pays for every meter`() {
        assertThat(PaymentCalculator.additionalMeters(250, 0)).isEqualTo(250)
        assertThat(PaymentCalculator.additionalPayment(250, 0)).isEqualTo(0L)
    }

    @Test
    fun `custom threshold and rate are honoured`() {
        val rule = defaultRule.copy(thresholdMeters = 300, ratePerMeter = 22_500L)
        assertThat(PaymentCalculator.additionalMeters(450, rule.thresholdMeters)).isEqualTo(150)
        assertThat(PaymentCalculator.additionalPayment(450, rule)).isEqualTo(3_375_000L)
    }

    @Test
    fun `zero rate yields zero payment even above the threshold`() {
        assertThat(PaymentCalculator.additionalMeters(900, 400)).isEqualTo(500)
        assertThat(PaymentCalculator.additionalPayment(500, 0L)).isEqualTo(0L)
    }

    // -------------------------------------------------------------- guards

    @Test(expected = IllegalArgumentException::class)
    fun `negative daily meters are rejected`() {
        PaymentCalculator.additionalMeters(-1, 400)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative threshold is rejected`() {
        PaymentCalculator.additionalMeters(500, -10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative additional meters are rejected`() {
        PaymentCalculator.additionalPayment(-5, 15_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative rate is rejected`() {
        PaymentCalculator.additionalPayment(100, -1L)
    }

    // --------------------------------------------------- never a deduction

    @Test
    fun `falling short of the threshold never produces a negative amount`() {
        for (meters in 0..400) {
            val additional = PaymentCalculator.additionalMeters(meters, 400)
            val payment = PaymentCalculator.additionalPayment(additional, 15_000L)
            assertThat(additional).isAtLeast(0)
            assertThat(payment).isAtLeast(0L)
        }
    }
}
