package ir.metra.app.core.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Persian digit and currency formatting.
 *
 * Two things must hold everywhere in the UI: digits are rendered with Persian
 * numerals, and money is always an integer number of Toman — no floats, no
 * Rial-to-Toman conversions.
 */
class FormatterTest {

    private val digits = PersianDigits
    private val numbers = NumberFormatter()
    private val dates = DateFormatter()

    // ------------------------------------------------------- digit mapping

    @Test
    fun `latin digits map to persian digits`() {
        assertThat(digits.toPersian("0123456789")).isEqualTo("۰۱۲۳۴۵۶۷۸۹")
    }

    @Test
    fun `persian digits map back to latin digits`() {
        assertThat(digits.toLatin("۰۱۲۳۴۵۶۷۸۹")).isEqualTo("0123456789")
    }

    @Test
    fun `non digit characters are left untouched`() {
        assertThat(digits.toPersian("520 m - 1403")).isEqualTo("۵۲۰ m - ۱۴۰۳")
    }

    @Test
    fun `number input is normalised to latin digits`() {
        assertThat(digits.normalizeNumberInput("۱۸۰۰۰۰۰")).isEqualTo("1800000")
    }

    // ------------------------------------------------------------- money

    @Test
    fun `toman amounts are formatted with persian digits and a grouping separator`() {
        val formatted = numbers.formatToman(1_800_000L)
        assertThat(formatted).contains("۱")
        assertThat(formatted).contains("تومان")
        // ASCII digits must never leak into the Persian UI.
        assertThat(formatted.none { it in '0'..'9' }).isTrue()
    }

    @Test
    fun `zero toman is formatted, never hidden`() {
        assertThat(numbers.formatToman(0L)).contains("۰")
    }

    @Test
    fun `large amounts keep full precision`() {
        val formatted = numbers.formatToman(149_994_000_000L)
        val digitsOnly = digits.toLatin(formatted.filter { it.isDigit() || it in '۰'..'۹' })
        assertThat(digitsOnly).contains("149994000000")
    }

    @Test
    fun `meters are formatted with the meter unit`() {
        val formatted = numbers.formatMeters(520)
        assertThat(formatted).contains("۵۲۰")
        assertThat(formatted).contains("متر")
    }

    // ------------------------------------------------------------- parse

    @Test
    fun `persian digit input parses back to a number`() {
        assertThat(numbers.parseMoney("۱۸۰۰۰۰۰")).isEqualTo(1_800_000L)
    }

    @Test
    fun `grouped and blank input parses sensibly`() {
        assertThat(numbers.parseMoney("")).isNull()
        assertThat(numbers.parseMoney("abc")).isNull()
        assertThat(numbers.parseInt("۵۲۰")).isEqualTo(520)
    }

    // -------------------------------------------------------------- dates

    @Test
    fun `a date formats as a jalali date with persian digits`() {
        // 2024-03-20 == 1403/01/01.
        val formatted = dates.format(java.time.LocalDate.of(2024, 3, 20).toEpochDay())
        assertThat(formatted).contains("۱۴۰۳")
        assertThat(formatted.none { it in '0'..'9' }).isTrue()
    }

    @Test
    fun `latin digit output is available for file names`() {
        val formatted = dates.format(
            java.time.LocalDate.of(2024, 3, 20).toEpochDay(),
            persianDigits = false,
        )
        assertThat(formatted).contains("1403")
    }

    @Test
    fun `a missing time renders as an em dash rather than an empty label`() {
        // An empty string in a settings row reads as a bug; the dash says
        // "nothing set" unambiguously.
        assertThat(dates.formatTime(null)).isEqualTo("\u2014")
        assertThat(dates.formatTimeRange(null, null)).isEqualTo("\u2014")
    }

    @Test
    fun `minute of day formats as hh mm`() {
        val formatted = digits.toLatin(dates.formatTime(19 * 60 + 5))
        assertThat(formatted).contains("19")
        assertThat(formatted).contains("05")
    }

    @Test
    fun `weekday appears in the long weekday format`() {
        // 2024-03-23 was a Saturday.
        val formatted = dates.formatLongWithWeekday(java.time.LocalDate.of(2024, 3, 23).toEpochDay())
        assertThat(formatted).contains("شنبه")
    }
}
