package ir.metra.app.domain

import ir.metra.app.domain.model.PaymentRule
import kotlin.math.max

/**
 * The single source of truth for Metra's earning rules.
 *
 * These functions are pure, allocation-free and free of Android types so they
 * can be unit tested on the JVM. **No business calculation may be duplicated in
 * the UI or in a ViewModel.**
 *
 * Rules implemented:
 *
 * ```
 * dailyMeters  < threshold  ->  additional = 0,  payment = 0
 * dailyMeters == threshold  ->  additional = 0,  payment = 0
 * dailyMeters  > threshold  ->  additional = dailyMeters - threshold
 *                               payment    = additional * ratePerMeter
 * ```
 *
 * The threshold is a *qualification* line, never a deduction: falling short of
 * it has no negative effect and never produces a negative amount.
 */
object PaymentCalculator {

    /**
     * Meters above the threshold, floored at zero.
     *
     * @param dailyMeters meters worked that day; must be >= 0.
     * @param thresholdMeters the qualification line; must be >= 0.
     * @return additional meters, never negative.
     */
    fun additionalMeters(dailyMeters: Int, thresholdMeters: Int): Int {
        require(dailyMeters >= 0) { "dailyMeters must be >= 0, was $dailyMeters" }
        require(thresholdMeters >= 0) { "thresholdMeters must be >= 0, was $thresholdMeters" }
        return max(dailyMeters - thresholdMeters, 0)
    }

    /**
     * Toman earned for [additional] meters at [ratePerMeter].
     *
     * @return payment in Toman, never negative.
     */
    fun additionalPayment(additionalMeters: Int, ratePerMeter: Long): Long {
        require(additionalMeters >= 0) { "additionalMeters must be >= 0, was $additionalMeters" }
        require(ratePerMeter >= 0) { "ratePerMeter must be >= 0, was $ratePerMeter" }
        return additionalMeters.toLong() * ratePerMeter
    }

    /** Convenience overload that takes the rule directly. */
    fun additionalPayment(dailyMeters: Int, rule: PaymentRule): Long =
        additionalPayment(additionalMeters(dailyMeters, rule.thresholdMeters), rule.ratePerMeter)
}
