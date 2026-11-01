package ir.metra.app.feature.shooting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.ui.components.JalaliDatePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyExpensesScreen(viewModel: CompanyExpenseViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var projectMenu by remember { mutableStateOf(false) }
    var activityMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("ثبت هزینه‌های قابل تسویه", style = MaterialTheme.typography.titleLarge)
            ExposedDropdownMenuBox(projectMenu, { projectMenu = it }) {
                OutlinedTextField(s.projectName, {}, readOnly = true, label = { Text("پروژه") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(projectMenu, { projectMenu = false }) { s.projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { viewModel.selectProject(p.id); projectMenu = false }) } }
            }
            ExposedDropdownMenuBox(activityMenu, { activityMenu = it }) {
                OutlinedTextField(s.activity, {}, readOnly = true, label = { Text("فعالیت") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activityMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(activityMenu, { activityMenu = false }) { listOf("GENERAL", "TRENCH", "SHOOTING").forEach { v -> DropdownMenuItem(text = { Text(when (v) { "GENERAL" -> "عمومی پروژه"; "TRENCH" -> "ترنچ"; else -> "شوتینگ" }) }, onClick = { viewModel.update { it.copy(activity = v) }; activityMenu = false }) } }
            }
            ExposedDropdownMenuBox(categoryMenu, { categoryMenu = it }) {
                OutlinedTextField(s.category, {}, readOnly = true, label = { Text("دسته هزینه") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(categoryMenu, { categoryMenu = false }) { listOf("سوخت خودرو", "سوخت دستگاه", "مخارج خودرو", "مخارج دستگاه", "اقلام مصرفی", "خورد و خوراک", "هزینه‌های بدون فاکتور").forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.update { it.copy(category = v) }; categoryMenu = false }) } }
            }
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("تاریخ هزینه: " + DateFormatter().format(s.date)) }
            OutlinedTextField(s.amount, { v -> viewModel.update { it.copy(amount = v.filter(Char::isDigit)) } }, label = { Text("مبلغ (تومان)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.description, { v -> viewModel.update { it.copy(description = v) } }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.receiptReference, { v -> viewModel.update { it.copy(receiptReference = v) } }, label = { Text("مرجع رسید (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("ثبت هزینه") }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("هزینه‌ها و تسویه‌ها", style = MaterialTheme.typography.titleMedium)
        }
        items(s.expenses) { row ->
            val (expense, settled) = row
            val outstanding = (expense.amount - settled).coerceAtLeast(0)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(expense.projectName + " · " + expense.category, style = MaterialTheme.typography.titleSmall)
                    Text("مبلغ: " + NumberFormatter().formatToman(expense.amount))
                    Text("تسویه‌شده: " + NumberFormatter().formatToman(settled))
                    Text("مانده: " + NumberFormatter().formatToman(outstanding), color = MaterialTheme.colorScheme.primary)
                    if (outstanding > 0) {
                        OutlinedTextField(value = s.settlementAmounts[expense.id].orEmpty(), onValueChange = { v -> viewModel.update { it.copy(settlementAmounts = it.settlementAmounts + (expense.id to v.filter(Char::isDigit))) } }, label = { Text("مبلغ تسویه جدید") }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { viewModel.settle(expense.id, JalaliCalendar.today().toEpochDay()) }, modifier = Modifier.fillMaxWidth()) { Text("ثبت تسویه جزئی / کامل") }
                    }
                    TextButton(onClick = { viewModel.delete(expense.id) }) { Icon(Icons.Default.Delete, null); Text("حذف هزینه") }
                }
            }
        }
    }
    if (showDatePicker) JalaliDatePickerDialog(s.date, { showDatePicker = false }) { selected -> viewModel.update { it.copy(date = selected) } }
}
