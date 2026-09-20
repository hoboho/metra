package ir.metra.app.core.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Money and quantity presentation.
 *
 * **Currency rule:** Metra's user-facing currency is Toman. Values are stored as
 * integer Toman and are *never* silently converted to or from Rial — a
 * conversion would have to be an explicit, labelled action by the user.
 */
@Singleton
class NumberFormatter @Inject constructor() {

    private val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val integerFormat = DecimalFormat("#,##0", symbols)
    private val oneDecimalFormat = DecimalFormat("#,##0.#", symbols)

    /** "1,800,000" — ASCII digits, machine-readable. */
    fun formatPlain(value: Long): String = integerFormat.format(value)

    /** "۱٬۸۰۰٬۰۰۰" — Persian digits with Persian thousands separators. */
    fun formatPersian(value: Long): String =
        integerFormat.format(value).replace(',', PersianThousandsSeparator)
            .let { PersianDigits.toPersian(it) }

    /** Persian integer, or "۰" for zero. */
    fun formatPersianOrZero(value: Long): String = formatPersian(value)

    /** Meters, e.g. "۵۲۰ متر". */
    fun formatMeters(meters: Int): String = "${PersianDigits.toPersian(integerFormat.format(meters))} $MeterUnit"

    /** Meters without the unit suffix. */
    fun formatMetersValue(meters: Int): String =
        PersianDigits.toPersian(integerFormat.format(meters))

    /** Money with the Toman suffix, e.g. "۱٬۸۰۰٬۰۰۰ تومان". */
    fun formatToman(amount: Long): String = "${formatPersian(amount)} $TomanUnit"

    /** Rate per meter, e.g. "۱۵٬۰۰۰ تومان بر متر". */
    fun formatRate(ratePerMeter: Long): String = "${formatPersian(ratePerMeter)} $RateUnit"

    /** One decimal place, Persian digits — used for averages. */
    fun formatAverage(value: Double): String =
        PersianDigits.toPersian(oneDecimalFormat.format(value))

    /** Two decimal places with a percent sign, Persian digits. */
    fun formatPercent(value: Double): String =
        PersianDigits.toPersian(DecimalFormat("#,##0.00", symbols).format(value)) + '٪'

    /** Signed percentage change, e.g. "+۱۲٫۵٪" / "-۳٪". Null renders as "—". */
    fun formatSignedPercent(value: Double?): String {
        if (value == null) return EmDash
        val rounded = kotlin.math.round(value * 10) / 10.0
        val body = oneDecimalFormat.format(kotlin.math.abs(rounded))
            .replace(',', PersianThousandsSeparator)
            .replace('.', PersianDecimalSeparator)
            .let { PersianDigits.toPersian(it) }
        val sign = when {
            rounded > 0 -> "+"
            rounded < 0 -> "−"
            else -> ""
        }
        return "$sign$body٪"
    }

    /** Parses user input (Persian or ASCII) into Toman, or null when blank/invalid. */
    fun parseMoney(input: String): Long? {
        val normalized = PersianDigits.normalizeNumberInput(input)
        if (normalized.isEmpty() || normalized == ".") return null
        val value = normalized.toLongOrNull() ?: normalized.toDoubleOrNull()?.toLong() ?: return null
        return if (value < 0) null else value
    }

    /** Parses user input into a non-negative integer meter/worker count. */
    fun parseInt(input: String): Int? {
        val normalized = PersianDigits.normalizeNumberInput(input)
        if (normalized.isEmpty()) return null
        val value = normalized.toIntOrNull() ?: normalized.toDoubleOrNull()?.toInt() ?: return null
        return if (value < 0) null else value
    }

    companion object {
        const val TomanUnit = "تومان"
        const val RialUnit = "ریال"
        const val MeterUnit = "متر"
        const val RateUnit = "تومان بر متر"
        const val EmDash = "—"
        private const val PersianThousandsSeparator = '٬'
        private const val PersianDecimalSeparator = '٫'
    }
}
