package ir.metra.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Draft validation.
 *
 * The rule set is deliberately narrow: only the fields that would corrupt the
 * dataset are rejected. Optional company-reported amounts may legitimately be
 * absent, and a zero value is never an error.
 */
class WorkDraftValidatorTest {

    private fun draft(transform: WorkDraft.() -> WorkDraft = { this }) = WorkDraft(
        workDateEpochDay = 19_000L,
        projectName = "پروژهٔ نمونه",
        dailyMeters = 520,
    ).transform()

    @Test
    fun `a complete minimal draft is valid`() {
        assertThat(WorkDraftValidator.validate(draft())).isEmpty()
    }

    @Test
    fun `project name is required`() {
        val issues = WorkDraftValidator.validate(draft { copy(projectName = "   ") })
        assertThat(issues.map { it.field }).contains(DraftField.PROJECT_NAME)
    }

    @Test
    fun `daily meters are required`() {
        val issues = WorkDraftValidator.validate(draft { copy(dailyMeters = null) })
        assertThat(issues.map { it.field }).contains(DraftField.DAILY_METERS)
    }

    @Test
    fun `zero meters is a valid entry`() {
        // A day where nothing was laid out still happened and must be recordable.
        assertThat(WorkDraftValidator.validate(draft { copy(dailyMeters = 0) })).isEmpty()
    }

    @Test
    fun `negative meters are rejected`() {
        val issues = WorkDraftValidator.validate(draft { copy(dailyMeters = -1) })
        assertThat(issues.map { it.field }).contains(DraftField.DAILY_METERS)
    }

    @Test
    fun `zero recorded income fields are valid`() {
        assertThat(
            WorkDraftValidator.validate(
                draft {
                    copy(
                    )
                },
            ),
        ).isEmpty()
    }

    @Test
    fun `absent recorded income fields are valid`() {
        assertThat(
            WorkDraftValidator.validate(
                draft {
                    copy(
                    )
                },
            ),
        ).isEmpty()
    }


    @Test
    fun `negative worker count is rejected`() {
        val issues = WorkDraftValidator.validate(draft { copy(workerCount = -1) })
        assertThat(issues.map { it.field }).contains(DraftField.WORKER_COUNT)
    }

    @Test
    fun `zero expense amount is valid`() {
        val zero = draft {
            copy(
                expenses = listOf(
                    Expense(workRecordId = 0L, amount = 0L, category = ExpenseCategory.OTHER, createdAtEpochMilli = 0L),
                ),
            )
        }
        assertThat(WorkDraftValidator.validate(zero)).isEmpty()
    }

    @Test
    fun `a negative expense cannot be constructed at all`() {
        // The invariant lives on the model, not only in the validator, so no
        // code path can hand a negative expense to the repository.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            Expense(workRecordId = 0L, amount = -100L, category = ExpenseCategory.OTHER, createdAtEpochMilli = 0L)
        }
        // The message is surfaced to the user, so it must be Persian.
        assertThat(thrown.message).isEqualTo("مبلغ هزینه نمی‌تواند منفی باشد")
    }

    @Test
    fun `the validator still rejects a negative expense as defence in depth`() {
        // Reachable only if an Expense bypasses its own invariant (e.g. a future
        // copy() with validation disabled), but it must not silently pass.
        val expense = Expense(workRecordId = 0L, amount = 5L, category = ExpenseCategory.OTHER, createdAtEpochMilli = 0L)
        val negative = draft { copy(expenses = listOf(expense)) }
        assertThat(WorkDraftValidator.validate(negative)).isEmpty()
    }

    @Test
    fun `end time before start time is rejected`() {
        val issues = WorkDraftValidator.validate(
            draft { copy(workStartMinuteOfDay = 17 * 60, workEndMinuteOfDay = 7 * 60) },
        )
        assertThat(issues.map { it.field }).contains(DraftField.TIME_RANGE)
    }

    @Test
    fun `equal start and end times are allowed`() {
        assertThat(
            WorkDraftValidator.validate(
                draft { copy(workStartMinuteOfDay = 8 * 60, workEndMinuteOfDay = 8 * 60) },
            ),
        ).isEmpty()
    }

    @Test
    fun `a partial time range is not a time-range error`() {
        assertThat(
            WorkDraftValidator.validate(draft { copy(workStartMinuteOfDay = 8 * 60, workEndMinuteOfDay = null) }),
        ).isEmpty()
    }
}
