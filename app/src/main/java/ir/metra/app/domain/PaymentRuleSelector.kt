package ir.metra.app.domain

import ir.metra.app.domain.model.PaymentRule

/**
 * Picks the payment rule that applies to a workday.
 *
 * A rule applies from its [PaymentRule.effectiveFromEpochDay] up to (but not
 * including) the effective date of the next rule. This is what keeps historical
 * records stable: adding a new rule for next year cannot change what a record
 * from last year was paid at.
 */
object PaymentRuleSelector {

    /**
     * The rule in effect on [workDateEpochDay].
     *
     * When the workday predates every known rule the earliest rule is used —
     * there is no sensible alternative and silently paying nothing would be
     * worse. Returns null only when no rules exist at all.
     */
    fun applicableRule(rules: List<PaymentRule>, workDateEpochDay: Long): PaymentRule? {
        if (rules.isEmpty()) return null
        val sorted = rules.sortedBy { it.effectiveFromEpochDay }
        return sorted.lastOrNull { it.effectiveFromEpochDay <= workDateEpochDay } ?: sorted.first()
    }

    /**
     * Same as [applicableRule] but for "what rule would apply to a brand new
     * record created today".
     */
    fun currentRule(rules: List<PaymentRule>, todayEpochDay: Long): PaymentRule? =
        applicableRule(rules, todayEpochDay)
}
