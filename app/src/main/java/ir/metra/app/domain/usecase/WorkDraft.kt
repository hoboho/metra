package ir.metra.app.domain.usecase

import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory

/**
 * The editable state of the daily work form.
 *
 * Every numeric field is nullable while the user is typing so an empty field is
 * distinguishable from an explicit zero, and so a half-entered form never
 * produces a bogus calculation.
 */
data class WorkDraft(
    val id: Long? = null,
    val projectId: Long? = null,
    val workDateEpochDay: Long,

    // Section 1 — day information
    val projectName: String = "",
    val workArea: String = "",
    val employer: String = "",
    val supervisor: String = "",
    val workerCount: Int? = null,

    // Section 2 — measured work
    val dailyMeters: Int? = null,

    // Section 3 — company-reported amounts

    // Section 4 — expenses (edited as a list, saved atomically)
    val expenses: List<Expense> = emptyList(),

    // Section 5 — notes
    val notes: String = "",

    // Section 6 — optional times
    val workStartMinuteOfDay: Int? = null,
    val workEndMinuteOfDay: Int? = null,
)

/** A validation problem tied to a specific form field. */
data class FieldIssue(val field: DraftField, val message: String)

enum class DraftField {
    DATE,
    PROJECT_NAME,
    WORKER_COUNT,
    DAILY_METERS,
    FIXED_SALARY,
    MISSION_ALLOWANCE,
    OVERTIME,
    OTHER_INCOME,
    EXPENSE,
    TIME_RANGE,
}

/**
 * Validates a draft against the product rules.
 *
 * Required: date, project name, daily meters. Everything else is optional, but
 * nothing may be negative and an end time must not precede its start time.
 */
object WorkDraftValidator {

    fun validate(draft: WorkDraft): List<FieldIssue> {
        val issues = ArrayList<FieldIssue>()

        if (draft.projectName.isBlank()) {
            issues += FieldIssue(DraftField.PROJECT_NAME, "نام پروژه الزامی است")
        }
        if (draft.dailyMeters == null) {
            issues += FieldIssue(DraftField.DAILY_METERS, "کارکرد روزانه الزامی است")
        } else if (draft.dailyMeters < 0) {
            issues += FieldIssue(DraftField.DAILY_METERS, "کارکرد نمی‌تواند منفی باشد")
        }
        if (draft.workerCount != null && draft.workerCount < 0) {
            issues += FieldIssue(DraftField.WORKER_COUNT, "تعداد کارگر نمی‌تواند منفی باشد")
        }

        for (expense in draft.expenses) {
            if (expense.amount < 0) {
                issues += FieldIssue(DraftField.EXPENSE, "مبلغ هزینه نمی‌تواند منفی باشد")
            }
        }

        val start = draft.workStartMinuteOfDay
        val end = draft.workEndMinuteOfDay
        if (start != null && end != null && end < start) {
            issues += FieldIssue(DraftField.TIME_RANGE, "ساعت پایان نمی‌تواند پیش از ساعت شروع باشد")
        }
        return issues
    }

    private fun checkMoney(value: Long?, field: DraftField, label: String, issues: MutableList<FieldIssue>) {
        if (value != null && value < 0) {
            issues += FieldIssue(field, "$label نمی‌تواند منفی باشد")
        }
    }
}
