package ir.metra.app.core.date

/**
 * Gregorian-to-Jalali conversion and Jalali arithmetic.
 *
 * **Storage rule:** the database only ever stores epoch days (or epoch millis).
 * Jalali values are a *presentation* derived on demand, so no Persian date
 * string is ever the primary representation of a date.
 *
 * Conversion is delegated to the well-tested PersianDate library, but its
 * `dayOfWeek()` / `dayName()` helpers are deliberately not used — they disagree
 * with the Gregorian weekday by one day. [dayOfWeek] below is computed from the
 * epoch day instead and is covered by unit tests.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    init {
        require(month in 1..12) { "Jalali month must be in 1..12, was $month" }
        require(day in 1..31) { "Jalali day must be in 1..31, was $day" }
    }

    override fun compareTo(other: JalaliDate): Int = when {
        year != other.year -> year.compareTo(other.year)
        month != other.month -> month.compareTo(other.month)
        else -> day.compareTo(other.day)
    }

    override fun toString(): String = "%04d/%02d/%02d".format(year, month, day)

    companion object {
        /** Persian month names, index 0 = فروردین. */
        val monthNames: List<String> = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        /** Persian weekday names, index 0 = شنبه (Saturday). */
        val weekdayNames: List<String> = listOf(
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
        )

        /** Short weekday names, index 0 = شنبه (Saturday). */
        val shortWeekdayNames: List<String> = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

        /** Days in each of the first six months. */
        private const val FIRST_HALF_DAYS = 31
        private const val SECOND_HALF_DAYS = 30

        /**
         * Jalali leap year.
         *
         * Delegates to the PersianDate library on purpose. The alternative
         * 2820-year arithmetic rule disagrees with the library's 33-year cycle
         * at 1403/1404 and 1436/1437; since every Gregorian<->Jalali conversion
         * in the app goes through the same library, using it here keeps month
         * lengths and conversions consistent. Pinned by JalaliCalendarTest.
         */
        fun isLeapYear(jalaliYear: Int): Boolean =
            saman.zamani.persiandate.PersianDate.isJalaliLeap(jalaliYear)

        /** Days in a Jalali month (Esfand is 29 or 30). */
        fun daysInMonth(jalaliYear: Int, jalaliMonth: Int): Int {
            require(jalaliMonth in 1..12) { "Jalali month must be in 1..12" }
            return when {
                jalaliMonth <= 6 -> FIRST_HALF_DAYS
                jalaliMonth <= 11 -> SECOND_HALF_DAYS
                else -> if (isLeapYear(jalaliYear)) 30 else 29
            }
        }

        /** Month name for a 1-based Jalali month. */
        fun monthName(jalaliMonth: Int): String = monthNames[jalaliMonth - 1]

        /** "مهر ۱۴۰۵" style month label. */
        /** «فروردین ۱۴۰۳» — month names are used in charts, so the year is Persian too. */
        fun monthLabel(jalaliYear: Int, jalaliMonth: Int): String =
            "${monthName(jalaliMonth)} ${ir.metra.app.core.format.PersianDigits.toPersian(jalaliYear.toString())}"
    }
}
