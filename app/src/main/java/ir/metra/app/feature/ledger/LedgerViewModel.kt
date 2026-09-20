package ir.metra.app.feature.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.metra.app.R
import ir.metra.app.core.common.metraError
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.domain.model.LedgerEntry
import ir.metra.app.domain.model.LedgerKind
import ir.metra.app.domain.model.LedgerReason
import ir.metra.app.domain.repository.LedgerRepository
import ir.metra.app.domain.repository.ProjectRepository
import ir.metra.app.ui.navigation.LedgerEditorArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerListUiState(
    val outstandingText: String = "",
    val totalReceivableText: String = "",
    val collectedText: String = "",
    val entries: List<LedgerRow> = emptyList(),
    val message: String? = null,
)

data class LedgerRow(
    val id: Long,
    val kind: LedgerKind,
    val reason: LedgerReason,
    val amountText: String,
    val dateText: String,
    val subtitle: String,
)

data class LedgerEditorUiState(
    val entryId: Long = 0L,
    val kind: LedgerKind = LedgerKind.RECEIPT,
    val amount: String = "",
    val reason: LedgerReason = LedgerReason.METRAJE,
    val dateEpochDay: Long = JalaliCalendar.today().toEpochDay(),
    val dateText: String = "",
    val projectName: String = "",
    val method: String = "",
    val notes: String = "",
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * The receivables list.
 *
 * Reads straight from the repository's derived summary; it never recomputes the
 * outstanding balance itself, so the number on screen is by construction the
 * same number the dashboard shows.
 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val numberFormatter: NumberFormatter,
    private val dateFormatter: DateFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LedgerListUiState())
    val state: StateFlow<LedgerListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ledgerRepository.observeSummary().collect { summary ->
                _state.update {
                    it.copy(
                        outstandingText = numberFormatter.formatToman(summary.outstanding),
                        totalReceivableText = numberFormatter.formatToman(summary.totalReceivable),
                        collectedText = numberFormatter.formatToman(summary.netCollected),
                    )
                }
            }
        }
        viewModelScope.launch {
            ledgerRepository.observeEntries().collect { entries ->
                _state.update { current ->
                    current.copy(entries = entries.map { it.toRow() })
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            ledgerRepository.delete(id).fold(
                onSuccess = {
                    _state.update { it.copy(message = strings.string(R.string.ledger_deleted)) }
                },
                onFailure = { error ->
                    _state.update { it.copy(message = error.metraError.userMessage) }
                },
            )
        }
    }

    private fun LedgerEntry.toRow() = LedgerRow(
        id = id,
        kind = kind,
        reason = reason,
        amountText = numberFormatter.formatToman(amount),
        dateText = dateFormatter.format(entryDateEpochDay),
        subtitle = listOf(method, projectName)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
    )
}

/**
 * The add/edit receipt form.
 *
 * The amount is held as text until save so a half-typed number never becomes a
 * spurious validation error; parsing happens once, on submit.
 */
@HiltViewModel
class LedgerEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ledgerRepository: LedgerRepository,
    private val projectRepository: ProjectRepository,
    private val dateFormatter: DateFormatter,
    private val strings: StringProvider,
) : ViewModel() {

    private val args: LedgerEditorArgs = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(LedgerEditorUiState(entryId = args.entryId))
    val state: StateFlow<LedgerEditorUiState> = _state.asStateFlow()

    val projectNames = MutableStateFlow<List<String>>(emptyList())

    init {
        _state.update { it.copy(dateText = dateFormatter.format(it.dateEpochDay)) }
        viewModelScope.launch {
            projectRepository.observeActiveProjects().collect { projects ->
                projectNames.value = projects.map { it.name }
            }
        }
        if (args.entryId != 0L) {
            viewModelScope.launch {
                ledgerRepository.getEntry(args.entryId)?.let { entry ->
                    _state.update {
                        it.copy(
                            kind = entry.kind,
                            amount = entry.amount.toString(),
                            reason = entry.reason,
                            dateEpochDay = entry.entryDateEpochDay,
                            dateText = dateFormatter.format(entry.entryDateEpochDay),
                            projectName = entry.projectName,
                            method = entry.method,
                            notes = entry.notes,
                        )
                    }
                }
            }
        }
    }

    fun onKindChange(kind: LedgerKind) = _state.update { it.copy(kind = kind) }
    fun onAmountChange(value: String) =
        _state.update { it.copy(amount = value.filter(Char::isDigit).take(15), error = null) }
    fun onReasonChange(reason: LedgerReason) = _state.update { it.copy(reason = reason) }
    fun onDateChange(epochDay: Long) = _state.update {
        it.copy(dateEpochDay = epochDay, dateText = dateFormatter.format(epochDay))
    }
    fun onProjectChange(name: String) = _state.update { it.copy(projectName = name) }
    fun onMethodChange(method: String) = _state.update { it.copy(method = method) }
    fun onNotesChange(notes: String) = _state.update { it.copy(notes = notes) }

    /** Quick-amount chips: tapping adds to whatever is already typed. */
    fun addQuickAmount(toman: Long) {
        val current = _state.value.amount.toLongOrNull() ?: 0L
        _state.update { it.copy(amount = (current + toman).toString(), error = null) }
    }

    fun save() {
        val current = _state.value
        val amount = current.amount.toLongOrNull()
        if (amount == null || amount <= 0L) {
            _state.update { it.copy(error = strings.string(R.string.msg_ledger_amount_invalid)) }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entry = LedgerEntry(
                id = current.entryId,
                kind = current.kind,
                amount = amount,
                reason = current.reason,
                entryDateEpochDay = current.dateEpochDay,
                method = current.method.trim(),
                projectName = current.projectName.trim(),
                notes = current.notes.trim(),
                createdAtEpochMilli = now,
                updatedAtEpochMilli = now,
            )
            ledgerRepository.upsert(entry).fold(
                onSuccess = { _state.update { it.copy(saved = true) } },
                onFailure = { error ->
                    _state.update { it.copy(error = error.metraError.userMessage) }
                },
            )
        }
    }
}
