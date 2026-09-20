package ir.metra.app.feature.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.metraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.repository.ExpenseRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpenseEditorUiState(
    val workRecordId: Long = 0L,
    val expenseId: Long = 0L,
    val amount: String = "",
    val category: ExpenseCategory = ExpenseCategory.TRANSPORTATION,
    val description: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val loading: Boolean = true,
)

/**
 * Standalone expense editor.
 *
 * The workday form has an inline dialog for the common case; this screen exists
 * for editing an expense from the expense list and for attaching a receipt photo
 * without leaving the record.
 */
@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val workRecordRepository: WorkRecordRepository,
    private val strings: StringProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseEditorUiState())
    val state: StateFlow<ExpenseEditorUiState> = _state.asStateFlow()

    fun load(workRecordId: Long, expenseId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(workRecordId = workRecordId, expenseId = expenseId) }
            if (expenseId != 0L) {
                val expense = expenseRepository.getExpensesFor(workRecordId)
                    .firstOrNull { existing -> existing.id == expenseId }
                if (expense != null) {
                    _state.update {
                        it.copy(
                            amount = expense.amount.toString(),
                            category = expense.category,
                            description = expense.description,
                            loading = false,
                        )
                    }
                    return@launch
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun onAmountChange(value: String) {
        _state.update { it.copy(amount = value, errorMessage = null) }
    }

    fun onCategoryChange(category: ExpenseCategory) {
        _state.update { it.copy(category = category) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            val amount = current.amount.toLongOrNull()
            if (amount == null || amount <= 0) {
                _state.update { it.copy(errorMessage = strings.string(R.string.msg_expense_amount_positive)) }
                return@launch
            }
            _state.update { it.copy(saving = true) }
            val expense = Expense(
                id = current.expenseId,
                workRecordId = current.workRecordId,
                amount = amount,
                category = current.category,
                description = current.description,
                createdAtEpochMilli = clock.nowEpochMilli(),
            )
            val result = if (current.expenseId == 0L) {
                expenseRepository.add(expense).map { Unit }
            } else {
                expenseRepository.update(expense)
            }
            result.fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { error ->
                    _state.update { it.copy(saving = false, errorMessage = error.metraError.userMessage) }
                },
            )
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }
}
