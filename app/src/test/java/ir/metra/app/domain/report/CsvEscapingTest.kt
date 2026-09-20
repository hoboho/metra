package ir.metra.app.domain.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * CSV output correctness.
 *
 * Two failure modes matter here: Persian text that Excel misreads because the
 * byte-order mark is missing, and user-typed text that a spreadsheet executes
 * as a formula.
 */
class CsvEscapingTest {

    @Test
    fun `plain values are emitted untouched`() {
        assertThat(CsvEscaping.escapeField("پروژهٔ نمونه")).isEqualTo("پروژهٔ نمونه")
        assertThat(CsvEscaping.escapeField("520")).isEqualTo("520")
    }

    @Test
    fun `an empty field stays empty rather than becoming quoted noise`() {
        assertThat(CsvEscaping.escapeField("")).isEmpty()
    }

    @Test
    fun `ascii commas are quoted`() {
        assertThat(CsvEscaping.escapeField("a,b")).isEqualTo("\"a,b\"")
        assertThat(CsvEscaping.escapeField("تهران, منطقه ۲")).isEqualTo("\"تهران, منطقه ۲\"")
    }

    @Test
    fun `the persian comma is not a csv delimiter and needs no quoting`() {
        // «،» (U+060C) is punctuation, not a field separator. Quoting it would
        // add noise to every Persian description.
        assertThat(CsvEscaping.escapeField("تهران، منطقه ۲")).isEqualTo("تهران، منطقه ۲")
    }

    @Test
    fun `embedded quotes are doubled and the field is quoted`() {
        assertThat(CsvEscaping.escapeField("5\" pipe")).isEqualTo("\"5\"\" pipe\"")
    }

    @Test
    fun `embedded newlines are quoted so the row count stays correct`() {
        val escaped = CsvEscaping.escapeField("line1\nline2")
        assertThat(escaped).isEqualTo("\"line1\nline2\"")
        // A newline inside quotes must not split the record.
        assertThat(CsvEscaping.row(listOf("line1\nline2", "x"))).isEqualTo("\"line1\nline2\",x")
    }

    @Test
    fun `carriage returns are quoted`() {
        assertThat(CsvEscaping.escapeField("a\rb")).isEqualTo("\"a\rb\"")
    }

    // ------------------------------------------------- formula injection

    @Test
    fun `formula-leading values are neutralised`() {
        assertThat(CsvEscaping.escapeField("=1+1")).isEqualTo("\"=1+1\"")
        assertThat(CsvEscaping.escapeField("+1")).isEqualTo("\"+1\"")
        assertThat(CsvEscaping.escapeField("@SUM(A1)")).isEqualTo("\"@SUM(A1)\"")
    }

    @Test
    fun `a negative number is not corrupted by the injection guard`() {
        // Regression: prefixing these with a quote used to write '-900000,
        // which Excel shows as text and breaks the column's arithmetic.
        assertThat(CsvEscaping.escapeField("-900000")).isEqualTo("\"-900000\"")
        assertThat(CsvEscaping.escapeField("-0.5")).isEqualTo("\"-0.5\"")
    }

    @Test
    fun `a formula containing a comma is escaped exactly once`() {
        assertThat(CsvEscaping.escapeField("=SUM(A1,A2)")).isEqualTo("\"=SUM(A1,A2)\"")
    }

    // ------------------------------------------------------------ rows

    @Test
    fun `a row joins with the configured separator`() {
        assertThat(CsvEscaping.row(listOf("a", "b,c", "d"))).isEqualTo("a,\"b,c\",d")
        assertThat(CsvEscaping.row(listOf("a", "b"), separator = ";")).isEqualTo("a;b")
    }

    @Test
    fun `the header row has thirteen persian columns`() {
        assertThat(ReportFileWriter.CSV_HEADER).hasSize(13)
        assertThat(ReportFileWriter.CSV_HEADER.first()).isEqualTo("تاریخ")
        assertThat(ReportFileWriter.CSV_HEADER.last()).isEqualTo("توضیحات")
        // Expenses are reimbursable and salary is monthly, so the per-day export
        // must show a receivable, not a net deduction.
        assertThat(ReportFileWriter.CSV_HEADER).contains("هزینه قابل مطالبه")
        assertThat(ReportFileWriter.CSV_HEADER).contains("جمع دریافتنی از شرکت")
        assertThat(ReportFileWriter.CSV_HEADER.filter { it.contains("خالص") }).isEmpty()
    }

    @Test
    fun `the byte order mark is present so excel reads utf-8`() {
        assertThat(ReportFileWriter.BYTE_ORDER_MARK).isEqualTo("\uFEFF")
    }
}
