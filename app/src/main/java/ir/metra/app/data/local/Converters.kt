package ir.metra.app.data.local

import androidx.room.TypeConverter

/**
 * Room type converters.
 *
 * Dates are stored as `Long` epoch days/millis — never as formatted strings — so
 * the database is locale- and calendar-independent.
 */
class Converters {

    @TypeConverter
    fun fromExpenseCategory(value: ir.metra.app.domain.model.ExpenseCategory): String = value.name

    @TypeConverter
    fun toExpenseCategory(value: String): ir.metra.app.domain.model.ExpenseCategory =
        runCatching { ir.metra.app.domain.model.ExpenseCategory.valueOf(value) }
            .getOrDefault(ir.metra.app.domain.model.ExpenseCategory.OTHER)
}
