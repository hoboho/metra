package ir.metra.app.core.date

import saman.zamani.persiandate.PersianDate
import java.time.LocalDate

/**
 * Conversions between the machine-readable Gregorian epoch day stored in the
 * database and the Jalali dates shown in the UI.
 *
 * Everything here is pure and side-effect free, so date behaviour can be pinned
 * down by JVM unit tests against known anchors (Nowruz, leap years, ...).
 */
object JalaliCalendar {

    /** Saturday = 0 ... Friday = 6. */
    const val SATURDAY = 0
    const val FRIDAY = 6

    /** Gregorian epoch day -> Jalali date. */
    fun toJalali(epochDay: Long): JalaliDate {
        val gregorian = LocalDate.ofEpochDay(epochDay)
        val jalali = PersianDate()
            .initGrgDate(gregorian.year, gregorian.monthValue, gregorian.dayOfMonth)
        return JalaliDate(jalali.shYear, jalali.shMonth, jalali.shDay)
    }

    /** Jalali date -> Gregorian epoch day. */
    fun toEpochDay(jalali: JalaliDate): Long {
        val converted = PersianDate().initJalaliDate(jalali.year, jalali.month, jalali.day)
        return LocalDate.of(converted.grgYear, converted.grgMonth, converted.grgDay).toEpochDay()
    }

    /** Convenience: Gregorian epoch day arithmetic helpers used by range queries. */
    fun plusDays(epochDay: Long, days: Long): Long = epochDay + days

    fun today(): LocalDate = LocalDate.now()

    /** Epoch day for a Gregorian date. */
    fun epochDayOf(date: LocalDate): Long = date.toEpochDay()

    /** Gregorian date for an epoch day. */
    fun localDateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /**
     * Persian weekday index: Saturday = 0 ... Friday = 6.
     *
     * Computed from the epoch day rather than from PersianDate, whose
     * `dayOfWeek()` returns a value that disagrees with the Gregorian weekday.
     */
    fun dayOfWeek(epochDay: Long): Int {
        // 1970-01-01 (epochDay 0) was a Thursday => index 5 in a Sat-first week.
        val index = ((epochDay + 5) % 7 + 7) % 7
        return index.toInt()
    }

    /** Persian weekday name, e.g. "شنبه". */
    fun weekdayName(epochDay: Long): String = JalaliDate.weekdayNames[dayOfWeek(epochDay)]

    /** Short Persian weekday name, e.g. "ش". */
    fun shortWeekdayName(epochDay: Long): String = JalaliDate.shortWeekdayNames[dayOfWeek(epochDay)]

    /** Jalali date for today, in the device default zone. */
    fun todayJalali(): JalaliDate = toJalali(LocalDate.now().toEpochDay())

    /** Add Jalali months, clamping the day to the length of the target month. */
    fun plusJalaliMonths(epochDay: Long, months: Int): Long {
        val jalali = toJalali(epochDay)
        val totalMonths = (jalali.year * 12 + (jalali.month - 1)) + months
        val year = Math.floorDiv(totalMonths, 12)
        val month = Math.floorMod(totalMonths, 12) + 1
        val day = jalali.day.coerceAtMost(JalaliDate.daysInMonth(year, month))
        return toEpochDay(JalaliDate(year, month, day))
    }

    /** First day of the Jalali month containing [epochDay]. */
    fun firstDayOfJalaliMonth(epochDay: Long): Long {
        val jalali = toJalali(epochDay)
        return toEpochDay(JalaliDate(jalali.year, jalali.month, 1))
    }

    /** Last day of the Jalali month containing [epochDay]. */
    fun lastDayOfJalaliMonth(epochDay: Long): Long {
        val jalali = toJalali(epochDay)
        return toEpochDay(JalaliDate(jalali.year, jalali.month, JalaliDate.daysInMonth(jalali.year, jalali.month)))
    }

    /**
     * Start of the Persian week (Saturday) containing [epochDay].
     */
    fun startOfWeek(epochDay: Long): Long = epochDay - dayOfWeek(epochDay)

    /** End of the Persian week (Friday) containing [epochDay]. */
    fun endOfWeek(epochDay: Long): Long = startOfWeek(epochDay) + 6

    /** First day of the Jalali year containing [epochDay]. */
    fun firstDayOfJalaliYear(epochDay: Long): Long {
        val jalali = toJalali(epochDay)
        return toEpochDay(JalaliDate(jalali.year, 1, 1))
    }

    /** Last day of the Jalali year containing [epochDay]. */
    fun lastDayOfJalaliYear(epochDay: Long): Long {
        val jalali = toJalali(epochDay)
        return toEpochDay(JalaliDate(jalali.year, 12, JalaliDate.daysInMonth(jalali.year, 12)))
    }

    /** Days in the Jalali month containing [epochDay]. */
    fun daysInCurrentJalaliMonth(epochDay: Long): Int {
        val jalali = toJalali(epochDay)
        return JalaliDate.daysInMonth(jalali.year, jalali.month)
    }
}
