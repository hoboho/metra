package ir.metra.app.core.format

/**
 * Persian digit and number presentation.
 *
 * Digits are only ever converted at the presentation boundary: internal values,
 * database columns and CSV/PDF data stay in ASCII digits where they must be
 * machine-readable.
 */
object PersianDigits {

    private val PERSIAN = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private const val PERSIAN_THOUSANDS_SEPARATOR = '٬' // U+066C ARABIC THOUSANDS SEPARATOR
    private const val PERSIAN_DECIMAL_SEPARATOR = '٫' // U+066B ARABIC DECIMAL SEPARATOR

    /** Normalizes a grouped money field without mistaking a thousands comma for a decimal. */
    fun normalizeGroupedNumberInput(input: String): String {
        val latin = toLatin(input)
        val decimalIndex = latin.indexOf('.')
        return if (decimalIndex >= 0) {
            latin.substring(0, decimalIndex).filter(Char::isDigit) + "." +
                latin.substring(decimalIndex + 1).filter(Char::isDigit)
        } else {
            latin.filter(Char::isDigit)
        }
    }

    /** Formats an integer input with Persian thousands separators for readable money fields. */
    fun toPersianGrouped(input: String): String {
        val normalized = normalizeGroupedNumberInput(input)
        if (normalized.isEmpty()) return ""
        val parts = normalized.split('.', limit = 2)
        val digits = parts[0]
        val grouped = digits.reversed().chunked(3).joinToString("٬").reversed()
        val result = if (parts.size == 2) "$grouped.${parts[1]}" else grouped
        return toPersian(result)
    }

    /** Converts ASCII digits to their Persian equivalents, leaving other text alone. */
    fun toPersian(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        for (char in input) {
            builder.append(if (char in '0'..'9') PERSIAN[char - '0'] else char)
        }
        return builder.toString()
    }

    /** Converts Persian and Arabic-Indic digits back to ASCII. */
    fun toLatin(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        for (char in input) {
            val ascii = when (char) {
                in '۰'..'۹' -> '0' + (char - '۰')
                in '٠'..'٩' -> '0' + (char - '٠')
                PERSIAN_THOUSANDS_SEPARATOR -> ','
                PERSIAN_DECIMAL_SEPARATOR -> '.'
                else -> char
            }
            builder.append(ascii)
        }
        return builder.toString()
    }

    /**
     * Strips everything that is not a digit or a single decimal separator, so
     * user-typed values such as "۱٬۵۰۰" or "1,500" parse reliably.
     */
    fun normalizeNumberInput(input: String): String {
        val latin = toLatin(input)
        val builder = StringBuilder(latin.length)
        var seenSeparator = false
        for (char in latin) {
            when {
                char in '0'..'9' -> builder.append(char)
                (char == '.' || char == ',') && !seenSeparator -> {
                    builder.append('.')
                    seenSeparator = true
                }
            }
        }
        return builder.toString()
    }
}
