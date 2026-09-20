package ir.metra.app.domain.model

/**
 * One line item of spending attached to a [WorkRecord].
 *
 * Expenses are first-class data: a workday may hold any number of them and they
 * are never conflated with meter-based income.
 */
data class Expense(
    val id: Long = 0L,
    val workRecordId: Long,
    val amount: Long,
    val category: ExpenseCategory,
    val description: String = "",
    val receiptPhotoUri: String? = null,
    val createdAtEpochMilli: Long,
) {
    init {
        // Enforced here rather than only in WorkDraftValidator so that no code
        // path can persist a negative expense. The message is user-facing:
        // ViewModels surface it directly when a dialog tries to build one.
        require(amount >= 0) { "مبلغ هزینه نمی‌تواند منفی باشد" }
    }
}

/**
 * Expense categories. The ordinal is persisted, so **append only** — never
 * reorder or remove entries, or historical rows will silently change meaning.
 */
enum class ExpenseCategory {
    TRANSPORTATION,
    FOOD,
    ACCOMMODATION,
    MATERIALS,
    TOOLS,
    OTHER,
}
