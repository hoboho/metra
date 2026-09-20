package ir.metra.app.data.repository

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.data.local.PaymentRuleDao
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.PaymentRuleSelector
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.repository.PaymentRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRuleRepositoryImpl @Inject constructor(
    private val paymentRuleDao: PaymentRuleDao,
    private val clock: Clock,
    private val strings: StringProvider,
) : PaymentRuleRepository {

    override fun observeRules(): Flow<List<PaymentRule>> =
        paymentRuleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRules(): List<PaymentRule> =
        paymentRuleDao.getAll().map { it.toDomain() }

    override suspend fun getRule(id: Long): PaymentRule? = paymentRuleDao.getById(id)?.toDomain()

    override suspend fun applicableRuleOn(epochDay: Long): PaymentRule {
        paymentRuleDao.applicableOn(epochDay)?.let { return it.toDomain() }

        // A workday predating every configured rule: fall back to the earliest
        // known rule, and seed the product default when the table is empty so
        // the app is usable on a fresh install.
        paymentRuleDao.earliest()?.let { return it.toDomain() }
        val seeded = seedDefaultRule(epochDay)
        return seeded
    }

    override fun observeApplicableRuleOn(epochDay: Long): Flow<PaymentRule?> =
        paymentRuleDao.observeApplicableOn(epochDay).map { it?.toDomain() }

    override suspend fun addRule(rule: PaymentRule): MetraResult<Long> = runCatching {
        if (rule.thresholdMeters < 0) {
            return failure(
                MetraError.Validation(MetraError.Validation.Field.OTHER, strings.string(R.string.msg_threshold_not_negative)),
            )
        }
        if (rule.ratePerMeter < 0) {
            return failure(MetraError.Validation(MetraError.Validation.Field.MONEY, strings.string(R.string.msg_rate_not_negative)))
        }
        val now = clock.nowEpochMilli()
        val id = paymentRuleDao.upsert(rule.toEntity().copy(createdAtEpochMilli = now))
        success(if (id == -1L) rule.id else id)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "add rule")) })

    override suspend fun deleteRule(id: Long): MetraResult<Unit> = runCatching {
        // Deleting the only rule would leave new records with nothing to price
        // against, so re-seed the default rather than leaving the table empty.
        paymentRuleDao.deleteById(id)
        if (paymentRuleDao.count() == 0) {
            seedDefaultRule(ir.metra.app.core.date.JalaliCalendar.today().toEpochDay())
        }
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "delete rule")) })

    /** Inserts the shipped default rule (400 m at 15,000 Toman) if absent. */
    private suspend fun seedDefaultRule(epochDay: Long): PaymentRule {
        val now = clock.nowEpochMilli()
        val default = PaymentRule(
            thresholdMeters = PaymentRule.DEFAULT_THRESHOLD_METERS,
            ratePerMeter = PaymentRule.DEFAULT_RATE_PER_METER,
            effectiveFromEpochDay = epochDay,
            label = strings.string(R.string.label_rule_seeded),
            createdAtEpochMilli = now,
        )
        paymentRuleDao.upsert(default.toEntity())
        return default
    }
}
