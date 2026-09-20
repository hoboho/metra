package ir.metra.app.core.format

import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.date.JalaliDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persian date presentation built on top of the stored epoch-day values.
 *
 * Two digit styles are supported: Persian digits for the app UI, ASCII digits
 * for CSV/PDF contexts where the consumer may not have a Persian-capable font.
 */
@Singleton
class DateFormatter @Inject constructor() {

    /** "۱۴۰۵/۰۶/۲۱" */
    fun format(epochDay: Long, persianDigits: Boolean = true): String {
        val jalali = JalaliCalendar.toJalali(epochDay)
        val raw = "%04d/%02d/%02d".format(jalali.year, jalali.month, jalali.day)
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "۲۱ شهریور ۱۴۰۵" */
    fun formatLong(epochDay: Long, persianDigits: Boolean = true): String {
        val jalali = JalaliCalendar.toJalali(epochDay)
        val raw = "${jalali.day} ${JalaliDate.monthName(jalali.month)} ${jalali.year}"
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "شنبه ۲۱ شهریور ۱۴۰۵" */
    fun formatLongWithWeekday(epochDay: Long, persianDigits: Boolean = true): String =
        "${JalaliCalendar.weekdayName(epochDay)} ${formatLong(epochDay, persianDigits = false)}"
            .let { if (persianDigits) PersianDigits.toPersian(it) else it }

    /** "۲۱ شهریور" — compact label for charts and calendar cells. */
    fun formatDayMonth(epochDay: Long, persianDigits: Boolean = true): String {
        val jalali = JalaliCalendar.toJalali(epochDay)
        val raw = "${jalali.day} ${JalaliDate.monthName(jalali.month)}"
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "شهریور ۱۴۰۵" */
    fun formatMonth(epochDay: Long, persianDigits: Boolean = true): String {
        val jalali = JalaliCalendar.toJalali(epochDay)
        val raw = JalaliDate.monthLabel(jalali.year, jalali.month)
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "۱۴۰۵" */
    fun formatYear(epochDay: Long, persianDigits: Boolean = true): String {
        val raw = JalaliCalendar.toJalali(epochDay).year.toString()
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "۱۴:۳۰" from minutes-since-midnight. Null renders as the em dash. */
    fun formatTime(minuteOfDay: Int?, persianDigits: Boolean = true): String {
        if (minuteOfDay == null) return NumberFormatter.EmDash
        val hours = minuteOfDay / 60
        val minutes = minuteOfDay % 60
        val raw = "%02d:%02d".format(hours, minutes)
        return if (persianDigits) PersianDigits.toPersian(raw) else raw
    }

    /** "۱۴:۳۰ - ۱۸:۰۰", or the em dash when either bound is missing. */
    fun formatTimeRange(startMinuteOfDay: Int?, endMinuteOfDay: Int?, persianDigits: Boolean = true): String {
        if (startMinuteOfDay == null && endMinuteOfDay == null) return NumberFormatter.EmDash
        return "${formatTime(startMinuteOfDay, persianDigits)} - ${formatTime(endMinuteOfDay, persianDigits)}"
    }

    /** "۱۴۰۵/۰۶/۲۱ - ۱۴۰۵/۰۷/۰۱" for report headers. */
    fun formatRange(startEpochDay: Long, endEpochDay: Long, persianDigits: Boolean = true): String =
        "${format(startEpochDay, persianDigits)} - ${format(endEpochDay, persianDigits)}"
}
