package ir.metra.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.metra.app.R
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.ui.components.expenseCategoryLabel
import ir.metra.app.ui.components.MetraNumberField

/**
 * Add/edit one expense line.
 *
 * Kept inline in the workday form so the most common path — log meters, add a
 * cost, save — stays on one screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    initialAmount: String,
    initialCategory: ExpenseCategory,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, category: ExpenseCategory, description: String) -> Unit,
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf(initialDescription) }
    var expanded by remember { mutableStateOf(false) }
    val amountValue = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amountValue, category, description) },
                enabled = amountValue > 0L,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.expense_new)) },
        text = {
            Column {
                MetraNumberField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.expense_amount),
                    suffix = stringResource(R.string.toman),
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = expenseCategoryLabel(category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.expense_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(expenseCategoryLabel(entry)) },
                                onClick = {
                                    category = entry
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.expense_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

