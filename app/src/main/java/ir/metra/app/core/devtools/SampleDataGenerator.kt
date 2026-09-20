package ir.metra.app.core.devtools

import ir.metra.app.core.common.Clock
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.domain.ExpenseCalculator
import ir.metra.app.domain.PaymentCalculator
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.WorkRecord
import ir.metra.app.domain.repository.PaymentRuleRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Debug-only sample data generator.
 *
 * **This is never called from production code.** No screen, no ViewModel and no
 * startup path invokes it; it exists so a developer can populate a throwaway
 * database with realistic Persian data for screenshots and manual QA. Shipping
 * fake work records into a real user's database would be a data-integrity bug.
 *
 * The generator deliberately exercises the interesting cases: days below, at and
 * above the threshold, days with several expenses, and days with company-reported
 * income so the "recorded vs calculated" split is visible.
 */
@Singleton
class SampleDataGenerator @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val workRecordRepository: WorkRecordRepository,
    private val paymentRuleRepository: PaymentRuleRepository,
    private val clock: Clock,
) {

    private val projects = listOf(
        "پروژهٔ خط انتقال آب" to Triple("شرکت آب و فاضلاب", "منطقهٔ ۲", "مهندس رضایی"),
        "پروژهٔ فیبر نوری شمال" to Triple("شرکت مخابرات", "منطقهٔ ۵", "مهندس کاظمی"),
        "پروژهٔ کابل فشار قوی" to Triple("شرکت برق منطقه‌ای", "منطقهٔ ۱", "مهندس موسوی"),
    )

    /**
     * Inserts [days] synthetic workdays spread over the preceding months.
     *
     * @return the number of work records created.
     */
    suspend fun generate(days: Int = 90, seed: Long = 20260101L): Int {
        val random = Random(seed)
        val now = clock.nowEpochMilli()
        val rule = paymentRuleRepository.applicableRuleOn(JalaliCalendar.today().toEpochDay())
        val today = JalaliCalendar.today().toEpochDay()

        val projectIds = projects.map { (name, context) ->
            projectRepository.upsert(
                Project(
                    name = name,
                    employer = context.first,
                    workArea = context.second,
                    defaultSupervisor = context.third,
                    defaultWorkerCount = 4,
                    notes = "پروژهٔ نمونه برای تست",
                    createdAtEpochMilli = now,
                    updatedAtEpochMilli = now,
                ),
            ).getOrNull()
        }.filterNotNull()

        if (projectIds.isEmpty()) return 0

        var created = 0
        for (offset in days downTo 1) {
            val epochDay = today - offset
            // Skip some days so the data has realistic gaps.
            if (random.nextInt(100) < 18) continue

            val projectId = projectIds[random.nextInt(projectIds.size)]
            val project = projectRepository.getProject(projectId) ?: continue

            // Spread the meters across the three interesting bands.
            val meters = when (random.nextInt(10)) {
                0 -> 0
                1 -> 1
                2 -> 399
                3 -> 400
                4 -> 401
                else -> 400 + random.nextInt(260)
            }

            val additional = PaymentCalculator.additionalMeters(meters, rule.thresholdMeters)
            val payment = PaymentCalculator.additionalPayment(additional, rule.ratePerMeter)

            val expenses = buildExpenses(random, epochDay)
            val hasCompanyIncome = epochDay % 30 == 0L

            val record = WorkRecord(
                projectId = project.id,
                projectName = project.name,
                workArea = project.workArea,
                employer = project.employer,
                supervisor = project.defaultSupervisor,
                workerCount = 3 + random.nextInt(4),
                workDateEpochDay = epochDay,
                dailyMeters = meters,
                additionalMeters = additional,
                thresholdMetersSnapshot = rule.thresholdMeters,
                ratePerMeterSnapshot = rule.ratePerMeter,
                additionalMeterPayment = payment,
                expenseTotal = ExpenseCalculator.dailyExpenseTotal(expenses),
                notes = if (random.nextInt(5) == 0) "یادداشت نمونه برای روز کاری" else "",
                workStartMinuteOfDay = 7 * 60,
                workEndMinuteOfDay = 17 * 60,
                createdAtEpochMilli = now,
                updatedAtEpochMilli = now,
            )

            val savedId = workRecordRepository.upsert(record).getOrNull() ?: continue
            if (expenses.isNotEmpty()) {
                workRecordRepository.replaceExpenses(savedId, expenses.map { it.copy(workRecordId = savedId) })
            }
            created++
        }
        return created
    }

    private fun buildExpenses(random: Random, epochDay: Long): List<Expense> {
        val count = random.nextInt(3)
        if (count == 0) return emptyList()
        val categories = listOf(
            ExpenseCategory.TRANSPORTATION to 250_000L,
            ExpenseCategory.FOOD to 180_000L,
            ExpenseCategory.MATERIALS to 420_000L,
            ExpenseCategory.TOOLS to 95_000L,
        )
        return (0 until count).map { index ->
            val (category, base) = categories[random.nextInt(categories.size)]
            Expense(
                workRecordId = 0L,
                amount = base + random.nextInt(60_000),
                category = category,
                description = "هزینهٔ نمونه",
                createdAtEpochMilli = clock.nowEpochMilli() + index,
            )
        }
    }
}
