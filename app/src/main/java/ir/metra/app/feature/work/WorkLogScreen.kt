package ir.metra.app.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.metra.app.R
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.core.format.PersianDigits
import ir.metra.app.domain.repository.WorkRecordSort
import ir.metra.app.ui.components.TagChip

/**
 * Work log: the full history with search, filters, sorting and a calendar view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogScreen(
    onOpenRecord: (Long) -> Unit,
    onOpenProjects: () -> Unit,
    viewModel: WorkLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worklog_title)) },
                actions = {
                    IconButton(onClick = { viewModel.onToggleListMode() }) {
                        Icon(
                            imageVector = if (state.listMode) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = if (state.listMode) {
                                stringResource(R.string.worklog_view_calendar)
                            } else {
                                stringResource(R.string.worklog_view_list)
                            },
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.worklog_filters))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.worklog_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChips(current = state.sort, onSelected = { viewModel.onSortChange(it) })
                if (state.activeFilterCount > 0) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.onClearFilters() },
                        label = {
                            Text(
                                "${stringResource(R.string.worklog_filters_active)} " +
                                    "(${PersianDigits.toPersian(state.activeFilterCount.toString())})",
                            )
                        },
                    )
                }
            }

            if (state.listMode) {
                WorkLogList(
                    rows = state.rows,
                    onOpenRecord = onOpenRecord,
                    onDelete = { pendingDelete = it },
                    onDuplicate = { viewModel.duplicateRecord(it) },
                )
            } else {
                WorkLogCalendar(
                    state = state,
                    onMonthShift = { viewModel.onCalendarMonthShift(it) },
                    onDayClick = { day -> day.recordId?.let(onOpenRecord) },
                )
            }
        }
    }

    if (showFilters) {
        WorkLogFilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onApply = { filter ->
                viewModel.onApplyFilter(filter)
                showFilters = false
            },
            onClear = {
                viewModel.onClearFilters()
                showFilters = false
            },
            onOpenProjects = onOpenProjects,
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.worklog_delete_title)) },
            text = { Text(stringResource(R.string.worklog_delete_message)) },
        )
    }
}

@Composable
private fun SortChips(current: WorkRecordSort, onSelected: (WorkRecordSort) -> Unit) {
    val options = listOf(
        WorkRecordSort.DATE_DESC to stringResource(R.string.worklog_sort_date_desc),
        WorkRecordSort.DATE_ASC to stringResource(R.string.worklog_sort_date_asc),
        WorkRecordSort.METERS_DESC to stringResource(R.string.worklog_sort_meters_desc),
        WorkRecordSort.ADDITIONAL_PAYMENT_DESC to stringResource(R.string.worklog_sort_payment_desc),
        WorkRecordSort.EXPENSES_DESC to stringResource(R.string.worklog_sort_expenses_desc),
    )
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (sort, label) ->
            FilterChip(
                selected = sort == current,
                onClick = { onSelected(sort) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WorkLogList(
    rows: List<WorkLogRow>,
    onOpenRecord: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.worklog_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows, key = { it.id }) { row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRecord(row.id) },
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.dateLabel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = listOf(row.weekdayLabel, row.projectName).joinToString(SEPARATOR),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = row.metersText + " " + stringResource(R.string.meter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (row.hasAdditional) {
                                Text(
                                    text = "+" + row.additionalMetersText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (row.hasAdditional) {
                                TagChip(row.additionalPaymentText)
                            }
                            if (row.hasExpenses) {
                                TagChip(row.expenseText)
                            }
                        }
                        Row {
                            IconButton(onClick = { onDuplicate(row.id) }) {
                                Icon(Icons.Filled.FileCopy, contentDescription = stringResource(R.string.action_duplicate))
                            }
                            IconButton(onClick = { onDelete(row.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (row.notesPreview.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = row.notesPreview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkLogCalendar(
    state: WorkLogUiState,
    onMonthShift: (Int) -> Unit,
    onDayClick: (CalendarDay) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthShift(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.calendar_month_navigation_previous),
                )
            }
            Text(state.calendarMonthLabel, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonthShift(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.calendar_month_navigation_next),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            JalaliDate.shortWeekdayNames.forEach { name ->
                Text(
                    text = PersianDigits.toPersian(name),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.calendarLeadingBlanks) {
                Box(modifier = Modifier.aspectRatio(0.8f))
            }
            items(state.calendarDays, key = { it.epochDay }) { day ->
                val hasRecord = day.recordId != null
                Box(
                    modifier = Modifier
                        .aspectRatio(0.8f)
                        .clickable(enabled = hasRecord) { onDayClick(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = when {
                                    day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    hasRecord -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = PersianDigits.toPersian(day.dayOfMonth.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (hasRecord) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (hasRecord) {
                                Text(
                                    text = PersianDigits.toPersian(day.meters.toString()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

/** Middle dot used to join the weekday and project in a calendar cell. */
private const val SEPARATOR = " · "
