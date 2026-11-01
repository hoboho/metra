package ir.metra.app.feature.shooting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.ui.components.JalaliDatePickerDialog

@Composable
fun CombinedReportScreen(viewModel: CombinedReportViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val today = JalaliCalendar.today().toEpochDay()
    var start by remember { mutableLongStateOf(JalaliCalendar.firstDayOfJalaliMonth(today)) }
    var end by remember { mutableLongStateOf(today) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    LaunchedEffect(start, end) { viewModel.load(start, end) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("گزارش ترکیبی ترنچ و شوتینگ", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) { Text("از " + DateFormatter().format(start)) }
            OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) { Text("تا " + DateFormatter().format(end)) }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ترنچ", style = MaterialTheme.typography.titleMedium)
                Text("روزهای ثبت‌شده: " + s.trench.size)
                Text("متراژ ترنچ: " + NumberFormatter().formatPersian(s.trenchMeters) + " متر")
                HorizontalDivider()
                Text("شوتینگ", style = MaterialTheme.typography.titleMedium)
                Text("گزارش‌های ثبت‌شده: " + s.shooting.size)
                Text("متراژ شوتینگ: " + NumberFormatter().formatPersian(s.shootingMeters) + " متر")
                HorizontalDivider()
                Text("هزینه‌های قابل تسویه شرکت", style = MaterialTheme.typography.titleMedium)
                Text("کل هزینه‌ها: " + NumberFormatter().formatToman(s.companyExpenseTotal))
                Text("تسویه‌شده: " + NumberFormatter().formatToman(s.companyExpenseSettled))
                Text("مانده قابل تسویه: " + NumberFormatter().formatToman(s.companyExpenseOutstanding))
            }
        }
        Text("گزارش‌های روزانه", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.trench) { r ->
                ListItem(headlineContent = { Text("ترنچ · " + r.projectName) }, supportingContent = { Text(DateFormatter().format(r.workDateEpochDay) + " · " + NumberFormatter().formatPersian(r.dailyMeters.toLong()) + " متر") })
            }
            items(s.shooting) { r ->
                ListItem(headlineContent = { Text("شوتینگ · " + r.projectName) }, supportingContent = { Text(DateFormatter().format(r.workDateEpochDay) + " · " + NumberFormatter().formatPersian(r.totalMeters) + " متر") })
            }
        }
    }
    if (pickStart) JalaliDatePickerDialog(start, { pickStart = false }) { start = minOf(it, end) }
    if (pickEnd) JalaliDatePickerDialog(end, { pickEnd = false }) { end = maxOf(it, start) }
}
