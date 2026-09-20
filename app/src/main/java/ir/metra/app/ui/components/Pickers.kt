package ir.metra.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.metra.app.R
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import ir.metra.app.core.format.PersianDigits
import java.time.LocalTime

/**
 * A Persian (Jalali) month-grid date picker.
 *
 * Material's own `DatePicker` is Gregorian-only, so the grid is built here over
 * [JalaliCalendar]: Saturday is the first column and month lengths follow the
 * Jalali rules (Esfand 29/30), which is what a Persian user expects to see.
 */
@Composable
fun JalaliDatePickerDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val initialJalali = JalaliCalendar.toJalali(initialEpochDay)
    var year by remember { mutableIntStateOf(initialJalali.year) }
    var month by remember { mutableIntStateOf(initialJalali.month) }
    val todayEpochDay = remember { JalaliCalendar.today().toEpochDay() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDateSelected(todayEpochDay); onDismiss() }) {
                Text(stringResource(R.string.date_picker_today))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.date_picker_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        // RTL: the "previous" arrow points right.
                        if (month == 1) {
                            month = 12
                            year -= 1
                        } else {
                            month -= 1
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_month_navigation_previous))
                    }
                    Text(
                        text = PersianDigits.toPersian(JalaliDate.monthLabel(year, month)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = {
                        if (month == 12) {
                            month = 1
                            year += 1
                        } else {
                            month += 1
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_month_navigation_next))
                    }
                }

                Spacer(Modifier.height(4.dp))

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

                val daysInMonth = JalaliDate.daysInMonth(year, month)
                val firstOfMonthEpochDay = JalaliCalendar.toEpochDay(JalaliDate(year, month, 1))
                val leadingBlanks = JalaliCalendar.dayOfWeek(firstOfMonthEpochDay)
                val selectedJalali = JalaliCalendar.toJalali(initialEpochDay)

                val cells = ArrayList<Int?>()
                repeat(leadingBlanks) { cells.add(null) }
                for (day in 1..daysInMonth) cells.add(day)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(cells) { day ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .then(
                                    if (day == null) {
                                        Modifier
                                    } else {
                                        val isToday = JalaliCalendar.toEpochDay(JalaliDate(year, month, day)) == todayEpochDay
                                        val isSelected = day == selectedJalali.day &&
                                            month == selectedJalali.month && year == selectedJalali.year
                                        Modifier.clickable {
                                            onDateSelected(JalaliCalendar.toEpochDay(JalaliDate(year, month, day)))
                                            onDismiss()
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                val isSelected = day == selectedJalali.day &&
                                    month == selectedJalali.month && year == selectedJalali.year
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = PersianDigits.toPersian(day.toString()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** Minutes-since-midnight time picker built from two wheels. */
@Composable
fun MinuteOfDayPickerDialog(
    initialMinuteOfDay: Int?,
    title: String,
    onDismiss: () -> Unit,
    onTimeSelected: (Int) -> Unit,
) {
    var hour by remember { mutableIntStateOf((initialMinuteOfDay ?: 0) / 60) }
    var minute by remember { mutableIntStateOf((initialMinuteOfDay ?: 0) % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(hour * 60 + minute)
                onDismiss()
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberColumn(
                    selected = hour,
                    range = 0..23,
                    onSelected = { hour = it },
                )
                Text(":", style = MaterialTheme.typography.headlineMedium)
                NumberColumn(
                    selected = minute,
                    range = 0..59,
                    step = 5,
                    onSelected = { minute = it },
                )
            }
        },
    )
}

@Composable
private fun NumberColumn(
    selected: Int,
    range: IntRange,
    onSelected: (Int) -> Unit,
    step: Int = 1,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .size(width = 168.dp, height = 220.dp),
    ) {
        items(range.step(step).toList()) { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .aspectRatio(1.4f)
                    .clickable { onSelected(value) },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = PersianDigits.toPersian("%02d".format(value)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
