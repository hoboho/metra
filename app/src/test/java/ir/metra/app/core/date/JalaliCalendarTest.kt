package ir.metra.app.core.date

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Jalali conversion, weekday derivation and calendar arithmetic.
 *
 * Weekday is the risky one: the underlying PersianDate library's `dayOfWeek()`
 * is off by one, so the app computes it from the epoch day instead. That
 * decision is only safe if it is pinned against known dates, which is what
 * [dayOfWeek matches the Gregorian weekday] does.
 */
class JalaliCalendarTest {

    // ------------------------------------------------------- known anchors

    @Test
    fun `jalali new year anchors`() {
        assertThat(JalaliCalendar.toJalali(LocalDate.of(2024, 3, 20).toEpochDay()))
            .isEqualTo(JalaliDate(1403, 1, 1))
        assertThat(JalaliCalendar.toJalali(LocalDate.of(2025, 3, 21).toEpochDay()))
            .isEqualTo(JalaliDate(1404, 1, 1))
        assertThat(JalaliCalendar.toJalali(LocalDate.of(2026, 3, 21).toEpochDay()))
            .isEqualTo(JalaliDate(1405, 1, 1))
    }

    @Test
    fun `last day of a leap esfand`() {
        // 1403 is a leap year, so it ends on Esfand 30.
        assertThat(JalaliDate.isLeapYear(1403)).isTrue()
        assertThat(JalaliCalendar.toJalali(LocalDate.of(2025, 3, 20).toEpochDay()))
            .isEqualTo(JalaliDate(1403, 12, 30))
    }

    @Test
    fun `conversion round-trips over a wide range`() {
        var epochDay = LocalDate.of(2000, 1, 1).toEpochDay()
        val end = epochDay + 9000
        var mismatches = 0
        while (epochDay <= end) {
            if (JalaliCalendar.toEpochDay(JalaliCalendar.toJalali(epochDay)) != epochDay) mismatches++
            epochDay++
        }
        assertThat(mismatches).isEqualTo(0)
    }

    // ------------------------------------------------------------- weekday

    @Test
    fun `dayOfWeek matches the Gregorian weekday`() {
        // The Iranian week starts on Saturday. 2024-03-23 was a Saturday.
        val saturday = LocalDate.of(2024, 3, 23).toEpochDay()
        assertThat(JalaliCalendar.dayOfWeek(saturday)).isEqualTo(JalaliCalendar.SATURDAY)
        for (offset in 0L..6L) {
            assertThat(JalaliCalendar.dayOfWeek(saturday + offset)).isEqualTo((offset % 7).toInt())
        }
        // And the last slot is Friday.
        assertThat(JalaliCalendar.dayOfWeek(saturday + 6)).isEqualTo(JalaliCalendar.FRIDAY)
    }

    @Test
    fun `week boundaries start on Saturday and end on Friday`() {
        val wednesday = LocalDate.of(2024, 3, 27).toEpochDay() // Wednesday
        val start = JalaliCalendar.startOfWeek(wednesday)
        assertThat(JalaliCalendar.dayOfWeek(start)).isEqualTo(JalaliCalendar.SATURDAY)
        assertThat(JalaliCalendar.dayOfWeek(JalaliCalendar.endOfWeek(wednesday)))
            .isEqualTo(JalaliCalendar.FRIDAY)
        assertThat(JalaliCalendar.endOfWeek(wednesday) - start).isEqualTo(6L)
    }

    // -------------------------------------------------------------- months

    @Test
    fun `month lengths follow the jalali pattern`() {
        for (month in 1..6) assertThat(JalaliDate.daysInMonth(1403, month)).isEqualTo(31)
        for (month in 7..11) assertThat(JalaliDate.daysInMonth(1403, month)).isEqualTo(30)
        assertThat(JalaliDate.daysInMonth(1403, 12)).isEqualTo(30) // leap year
        assertThat(JalaliDate.daysInMonth(1404, 12)).isEqualTo(29) // common year
    }

    @Test
    fun `month boundaries resolve to the first and last day`() {
        val midMonth = LocalDate.of(2024, 4, 5).toEpochDay()
        val first = JalaliCalendar.firstDayOfJalaliMonth(midMonth)
        val last = JalaliCalendar.lastDayOfJalaliMonth(midMonth)
        assertThat(JalaliCalendar.toJalali(first).day).isEqualTo(1)
        val jalaliLast = JalaliCalendar.toJalali(last)
        assertThat(jalaliLast.day).isEqualTo(JalaliDate.daysInMonth(jalaliLast.year, jalaliLast.month))
    }

    @Test
    fun `plusJalaliMonths clamps the day when the target month is shorter`() {
        // Farvardin 31 -> Esfand (29/30 days) must clamp rather than throw.
        val farvardin31 = JalaliCalendar.toEpochDay(JalaliDate(1403, 1, 31))
        val esfand = JalaliCalendar.plusJalaliMonths(farvardin31, 11)
        val jalali = JalaliCalendar.toJalali(esfand)
        assertThat(jalali.month).isEqualTo(12)
        assertThat(jalali.day).isAtMost(JalaliDate.daysInMonth(jalali.year, jalali.month))
    }

    @Test
    fun `year boundaries resolve to the first and last day of the jalali year`() {
        val anyDay = LocalDate.of(2024, 8, 15).toEpochDay()
        assertThat(JalaliCalendar.toJalali(JalaliCalendar.firstDayOfJalaliYear(anyDay)))
            .isEqualTo(JalaliDate(1403, 1, 1))
        assertThat(JalaliCalendar.toJalali(JalaliCalendar.lastDayOfJalaliYear(anyDay)).month)
            .isEqualTo(12)
    }

    // ------------------------------------------------------------- records

    @Test
    fun `negative and epoch-origin dates still convert`() {
        assertThat(JalaliCalendar.toEpochDay(JalaliCalendar.toJalali(0L))).isEqualTo(0L)
        assertThat(JalaliCalendar.toEpochDay(JalaliCalendar.toJalali(-730L))).isEqualTo(-730L)
    }
}
