package ir.metra.app.domain.report

/**
 * RFC-4180 CSV escaping with a CSV-injection guard.
 *
 * Kept as a standalone, dependency-free function so it is unit-testable without
 * touching the filesystem or the Android framework.
 *
 * The injection guard quotes any field beginning with `=`, `+`, `-` or `@`.
 * Quoting (rather than the `'`-prefix sometimes seen in spreadsheets) is used
 * deliberately: Excel parses a quoted field as literal text, and it does not
 * treat a negative number as a formula either way, so numeric columns stay
 * intact while `=1+1` typed into a notes field cannot execute.
 */
object CsvEscaping {

    /** Characters that make a spreadsheet treat a cell as a formula. */
    private val FORMULA_LEADERS = charArrayOf('=', '+', '-', '@')

    fun escapeField(value: String): String {
        val startsWithFormula = value.isNotEmpty() && value[0] in FORMULA_LEADERS
        val needsQuoting = startsWithFormula ||
            value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else {
            value
        }
    }

    /** Joins one CSV row. */
    fun row(values: List<String>, separator: String = ","): String =
        values.joinToString(separator) { escapeField(it) }
}
