package ir.metra.app.domain.model

/**
 * A versioned payment rule.
 *
 * Rules are never mutated in place: changing the rate creates a new rule with a
 * new [effectiveFromEpochDay]. Every [WorkRecord] stores a *snapshot* of the rule
 * that applied on its work day, so editing the current rate can never silently
 * rewrite history.
 *
 * All money values are integer Toman. Never floating point.
 *
 * @property thresholdMeters daily meters that must be exceeded before any
 *   additional payment accrues. Meters at or below the threshold produce zero.
 * @property ratePerMeter Toman paid for each meter above [thresholdMeters].
 */
data class PaymentRule(
    val id: Long = 0L,
    val thresholdMeters: Int,
    val ratePerMeter: Long,
    val effectiveFromEpochDay: Long,
    val currencyCode: String = CURRENCY_TOMAN,
    val label: String? = null,
    val createdAtEpochMilli: Long,
) {
    init {
        require(thresholdMeters >= 0) { "thresholdMeters must be >= 0" }
        require(ratePerMeter >= 0) { "ratePerMeter must be >= 0" }
    }

    companion object {
        /** Default Toman display currency. */
        const val CURRENCY_TOMAN = "IRR-TOMAN"

        /** Product defaults: 400 m threshold at 15,000 Toman per meter. */
        const val DEFAULT_THRESHOLD_METERS = 400
        const val DEFAULT_RATE_PER_METER = 15_000L
    }
}
