package ir.metra.app.feature.shooting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.ui.components.JalaliDatePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootingScreen(viewModel: ShootingViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    LaunchedEffect(s.saved) { if(s.saved) { snack.showSnackbar("گزارش شوتینگ ذخیره شد"); viewModel.consumeSaved() } }
    LaunchedEffect(s.error) { s.error?.let { snack.showSnackbar(it); viewModel.consumeError() } }
    Scaffold(snackbarHost={ SnackbarHost(snack) }, topBar={ TopAppBar(title={ Text("شوتینگ فیبر نوری") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        var projectMenu by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(projectMenu,{projectMenu=it}) {
                            OutlinedTextField(s.projectName,{},readOnly=true,label={Text("پروژه")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(projectMenu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(projectMenu,{projectMenu=false}) { s.projects.forEach { p -> DropdownMenuItem(text={Text(p.name)},onClick={viewModel.selectProject(p.id);projectMenu=false}) } }
                        }
                        OutlinedButton(onClick={showDatePicker=true}, modifier=Modifier.fillMaxWidth()) { Text("تاریخ: " + DateFormatter().formatLong(s.date)) }
                        Field("محدوده کار",s.workArea) { viewModel.header("area",it) }
                        Field("کارفرما",s.employer) { viewModel.header("employer",it) }
                        Field("ناظر",s.supervisor) { viewModel.header("supervisor",it) }
                        Field("تعداد نیرو",s.workerCount,true) { viewModel.header("workers",it) }
                        Field("توضیحات",s.notes) { viewModel.header("notes",it) }
                    }
                }
            }
            itemsIndexed(s.operations) { index, op ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("عملیات " + (index+1),style=MaterialTheme.typography.titleMedium)
                            IconButton(onClick={viewModel.removeOperation(index)}) { Icon(Icons.Default.Delete,contentDescription="حذف") }
                        }
                        var startMenu by remember(index) { mutableStateOf(false) }
                        ExposedDropdownMenuBox(startMenu,{startMenu=it}) {
                            OutlinedTextField(op.startType,{},readOnly=true,label={Text("نقطه شروع")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(startMenu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(startMenu,{startMenu=false}) { listOf("ODC","FAT","حوضچه","منهول").forEach { v -> DropdownMenuItem(text={Text(v)},onClick={viewModel.operation(index,op.copy(startType=v));startMenu=false}) } }
                        }
                        Field("کد نقطه شروع",op.startCode) { viewModel.operation(index,op.copy(startCode=it)) }
                        Field("متراژ شروع کابل",op.startMeter,true) { viewModel.operation(index,op.copy(startMeter=it)) }
                        var endMenu by remember(index) { mutableStateOf(false) }
                        ExposedDropdownMenuBox(endMenu,{endMenu=it}) {
                            OutlinedTextField(op.endType,{},readOnly=true,label={Text("نقطه پایان")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(endMenu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(endMenu,{endMenu=false}) { listOf("ODC","FAT","حوضچه","منهول").forEach { v -> DropdownMenuItem(text={Text(v)},onClick={viewModel.operation(index,op.copy(endType=v));endMenu=false}) } }
                        }
                        Field("کد نقطه پایان",op.endCode) { viewModel.operation(index,op.copy(endCode=it)) }
                        Field("متراژ پایان کابل",op.endMeter,true) { viewModel.operation(index,op.copy(endMeter=it)) }
                        var cableMenu by remember(index) { mutableStateOf(false) }
                        val cable = s.cables.firstOrNull { it.id==op.cableTypeId }
                        ExposedDropdownMenuBox(cableMenu,{cableMenu=it}) {
                            OutlinedTextField(cable?.label.orEmpty(),{},readOnly=true,label={Text("نوع کابل")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(cableMenu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(cableMenu,{cableMenu=false}) { s.cables.forEach { c -> DropdownMenuItem(text={Text(c.label)},onClick={viewModel.operation(index,op.copy(cableTypeId=c.id));cableMenu=false}) } }
                        }
                        val a=op.startMeter.toLongOrNull() ?: 0L
                        val b=op.endMeter.toLongOrNull() ?: a
                        Text("متراژ عملیات: " + NumberFormatter().formatPersian(kotlin.math.abs(b-a)) + " متر",color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { OutlinedButton(onClick=viewModel::addOperation,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Add,null); Text("افزودن مسیر") } }
            item {
                val total=s.operations.sumOf { kotlin.math.abs((it.endMeter.toLongOrNull() ?: 0L)-(it.startMeter.toLongOrNull() ?: 0L)) }
                Text("جمع متراژ روز: " + NumberFormatter().formatPersian(total) + " متر",style=MaterialTheme.typography.titleLarge)
                Button(onClick=viewModel::save,enabled=!s.saving && s.projects.isNotEmpty() && s.cables.isNotEmpty(),modifier=Modifier.fillMaxWidth()) { Text(if(s.saving) "در حال ذخیره…" else "ذخیره گزارش") }
            }
            item { Text("گزارش‌های ثبت‌شده",style=MaterialTheme.typography.titleMedium) }
            itemsIndexed(s.reports) { _, r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Column { Text(r.projectName); Text(NumberFormatter().formatPersian(r.totalMeters)+" متر") }
                        IconButton(onClick={viewModel.delete(r.id)}) { Icon(Icons.Default.Delete,"حذف گزارش") }
                    }
                }
            }
        }
    }
    if (showDatePicker) JalaliDatePickerDialog(initialEpochDay=s.date,onDismiss={showDatePicker=false},onDateSelected={viewModel.updateDate(it)})
}
@Composable
private fun Field(label:String,value:String,numeric:Boolean=false,onChange:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange={onChange(if(numeric) it.filter(Char::isDigit) else it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=label!="توضیحات")
}
