package ir.metra.app.domain.usecase

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.domain.ExpenseCalculator
import ir.metra.app.domain.PaymentCalculator
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.SettingsRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the pre-filled values came from, so the UI can say so honestly.
 */
sealed interface PrefillSource {
    /** No previous workday exists; the form starts empty. */
    data object None : PrefillSource

    /** Copied from the most recent earlier workday. */
    data class PreviousWorkday(val epochDay: Long, val projectName: String) : PrefillSource

    /** Copied from the selected project's defaults. */
    data class ProjectDefaults(val projectName: String) : PrefillSource

    /** Project defaults first, previous workday filling any remaining gaps. */
    data class Both(val epochDay: Long, val projectName: String) : PrefillSource
}

/**
 * Values copied into a new form, with their provenance.
 *
 * Only *contextual* fields are ever copied. Meters, money and notes are never
 * carried over, because a copied number would silently become a false record.
 */
data class PrefillResult(
    val source: PrefillSource,
    val projectId: Long? = null,
    val projectName: String = "",
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: Int? = null,
    val workStartMinuteOfDay: Int? = null,
    val workEndMinuteOfDay: Int? = null,
    val thresholdMeters: Int = PaymentRule.DEFAULT_THRESHOLD_METERS,
    val ratePerMeter: Long = PaymentRule.DEFAULT_RATE_PER_METER,
)

/**
 * Builds the pre-filled state for a new workday.
 *
 * Precedence: the **selected project's** defaults win, then the **previous
 * workday** fills whatever the project left empty, then the user's global
 * defaults. Copying from the previous workday can be turned off in settings.
 */
@Singleton
class BuildPrefill @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
) {

    suspend fun invoke(
        targetEpochDay: Long,
        selectedProjectId: Long? = null,
        usePreviousWorkday: Boolean? = null,
    ): PrefillResult {
        val settings = settingsRepository.getSettings()
        val wantPrevious = usePreviousWorkday ?: settings.usePreviousWorkdayInfo
        val rule = paymentRuleRepository.applicableRuleOn(targetEpochDay)

        val project: Project? = selectedProjectId
            ?.let { projectRepository.getProject(it) }
            ?: settings.defaultProjectId?.let { projectRepository.getProject(it) }

        val previous = if (wantPrevious) workRecordRepository.getLatestBefore(targetEpochDay) else null

        val projectName = project?.name?.takeIf { it.isNotBlank() }
            ?: previous?.projectName.orEmpty()
            ?: settings.defaultWorkArea.let { "" }
        val workArea = project?.workArea?.takeIf { it.isNotBlank() }
            ?: previous?.workArea?.takeIf { it.isNotBlank() }
            ?: settings.defaultWorkArea
        val employer = project?.employer?.takeIf { it.isNotBlank() } ?: previous?.employer.orEmpty()
        val supervisor = project?.defaultSupervisor?.takeIf { it.isNotBlank() }
            ?: previous?.supervisor?.takeIf { it.isNotBlank() }
            ?: settings.defaultSupervisor
        val workerCount = project?.defaultWorkerCount?.takeIf { it > 0 }
            ?: previous?.workerCount?.takeIf { it > 0 }
            ?: settings.defaultWorkerCount.takeIf { it > 0 }

        val source = when {
            project != null && previous != null ->
                PrefillSource.Both(previous.workDateEpochDay, previous.projectName)

            project != null -> PrefillSource.ProjectDefaults(project.name)
            previous != null -> PrefillSource.PreviousWorkday(previous.workDateEpochDay, previous.projectName)
            else -> PrefillSource.None
        }

        return PrefillResult(
            source = source,
            projectId = project?.id ?: previous?.projectId,
            projectName = projectName,
            workArea = workArea,
            employer = employer,
            supervisor = supervisor,
            workerCount = workerCount,
            // Times are contextual, so they are carried over too — but only from
            // the previous workday, never from project defaults.
            workStartMinuteOfDay = previous?.workStartMinuteOfDay,
            workEndMinuteOfDay = previous?.workEndMinuteOfDay,
            thresholdMeters = rule.thresholdMeters,
            ratePerMeter = rule.ratePerMeter,
        )
    }
}

/**
 * Persists a draft as a work record.
 *
 * This is the only place a work record is created or updated, so it is the only
 * place that has to guarantee the invariants:
 *  - the draft is valid,
 *  - the additional-meter figures come from the rule in force on that date,
 *  - the expense total matches the expense rows.
 */
@Singleton
class SaveWorkRecord @Inject constructor(
    private val workRecordRepository: WorkRecordRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val clock: Clock,
) {

    suspend fun invoke(draft: WorkDraft): MetraResult<Long> {
        val issues = WorkDraftValidator.validate(draft)
        if (issues.isNotEmpty()) {
            return failure(MetraError.Validation(MetraError.Validation.Field.OTHER, issues.first().message))
        }

        val rule = paymentRuleRepository.applicableRuleOn(draft.workDateEpochDay)
        val meters = draft.dailyMeters ?: 0
        val additional = PaymentCalculator.additionalMeters(meters, rule.thresholdMeters)
        val payment = PaymentCalculator.additionalPayment(additional, rule.ratePerMeter)

        val expenses = normaliseExpenses(draft.expenses)
        val now = clock.nowEpochMilli()
        val existing = draft.id?.let { workRecordRepository.getRecordWithExpenses(it)?.first }

        val record = WorkRecord(
            id = draft.id ?: 0L,
            projectId = draft.projectId,
            projectName = draft.projectName.trim(),
            workArea = draft.workArea.trim(),
            employer = draft.employer.trim(),
            supervisor = draft.supervisor.trim(),
            workerCount = draft.workerCount ?: 0,
            workDateEpochDay = draft.workDateEpochDay,
            dailyMeters = meters,
            additionalMeters = additional,
            thresholdMetersSnapshot = rule.thresholdMeters,
            ratePerMeterSnapshot = rule.ratePerMeter,
            additionalMeterPayment = payment,
            expenseTotal = ExpenseCalculator.dailyExpenseTotal(expenses),
            notes = draft.notes.trim(),
            workStartMinuteOfDay = draft.workStartMinuteOfDay,
            workEndMinuteOfDay = draft.workEndMinuteOfDay,
            createdAtEpochMilli = existing?.createdAtEpochMilli ?: now,
            updatedAtEpochMilli = now,
        )

        val savedId = workRecordRepository.upsert(record).getOrNull()
            ?: return failure(MetraError.Unknown("ذخیرهٔ رکورد کارکرد ناموفق بود"))

        workRecordRepository.replaceExpenses(savedId, expenses.map { it.copy(workRecordId = savedId) })
            .getOrNull()
            ?: return failure(MetraError.Unknown("ذخیرهٔ هزینه‌ها ناموفق بود"))

        return success(savedId)
    }

    /** Drops unsaved placeholder rows and keeps user-entered ones in order. */
    private fun normaliseExpenses(expenses: List<Expense>): List<Expense> {
        val now = clock.nowEpochMilli()
        return expenses
            .filter { it.amount > 0L || it.description.isNotBlank() }
            .mapIndexed { index, expense ->
                expense.copy(
                    id = 0L,
                    createdAtEpochMilli = if (expense.createdAtEpochMilli == 0L) now + index else expense.createdAtEpochMilli,
                )
            }
    }
}

/**
 * Live preview of the Metra calculation for the form.
 *
 * Kept as a use case rather than inline Compose logic so the exact same rule
 * drives the form, the record and the tests.
 */
data class MeterCalculation(
    val dailyMeters: Int,
    val thresholdMeters: Int,
    val additionalMeters: Int,
    val ratePerMeter: Long,
    val additionalPayment: Long,
) {
    val qualifies: Boolean get() = additionalMeters > 0
}

@Singleton
class CalculateMeterPayment @Inject constructor() {

    fun invoke(dailyMeters: Int?, rule: PaymentRule): MeterCalculation {
        val meters = (dailyMeters ?: 0).coerceAtLeast(0)
        val additional = PaymentCalculator.additionalMeters(meters, rule.thresholdMeters)
        return MeterCalculation(
            dailyMeters = meters,
            thresholdMeters = rule.thresholdMeters,
            additionalMeters = additional,
            ratePerMeter = rule.ratePerMeter,
            additionalPayment = PaymentCalculator.additionalPayment(additional, rule.ratePerMeter),
        )
    }
}
