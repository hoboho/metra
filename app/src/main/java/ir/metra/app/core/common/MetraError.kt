package ir.metra.app.core.common

/**
 * Typed failure values surfaced to the UI.
 *
 * Every message is Persian because it is shown to the user verbatim; the enum
 * name is what code branches on, so behaviour never depends on a translated
 * string.
 */
sealed class MetraError(open val userMessage: String) {

    /** Thrown input does not satisfy the validation rules. */
    data class Validation(val field: Field, override val userMessage: String) : MetraError(userMessage) {
        enum class Field { DATE, PROJECT_NAME, DAILY_METERS, WORKERS, MONEY, EXPENSE, TIME_RANGE, LEDGER_AMOUNT, OTHER }
    }

    /** A referenced row no longer exists (e.g. its project was deleted). */
    data class NotFound(val what: String) : MetraError("مورد درخواستی یافت نشد: $what")

    /** PDF could not be produced. */
    data class PdfGeneration(val cause: String) : MetraError("ایجاد فایل PDF ناموفق بود: $cause")

    /** The share sheet could not be launched. */
    data object ShareFailed : MetraError("امکان اشتراک‌گذاری فایل وجود ندارد")

    /** Backup export failed. */
    data class Backup(val cause: String) : MetraError("تهیهٔ نسخهٔ پشتیبان ناموفق بود: $cause")

    /** Restore failed — the current data was left untouched. */
    data class Restore(val cause: String) : MetraError("بازیابی اطلاعات ناموفق بود: $cause")

    /** The backup file is malformed or its version is unsupported. */
    data class InvalidBackup(val cause: String) : MetraError("فایل پشتیبان معتبر نیست: $cause")

    /** Wrong passphrase for an encrypted backup. */
    data object WrongPassphrase : MetraError("رمز عبور فایل پشتیبان نادرست است")

    /** Not enough free storage for the requested operation. */
    data object StorageFull : MetraError("فضای ذخیره‌سازی کافی نیست")

    /** Notification permission was denied by the user. */
    data object NotificationPermissionDenied :
        MetraError("دسترسی اعلان‌ها داده نشده است؛ یادآوری روزانه فعال نمی‌شود")


    /** App lock could not be verified. */
    data object AuthenticationFailed : MetraError("احراز هویت ناموفق بود")

    /** Catch-all for unexpected I/O failures. */
    data class Unknown(val cause: String) : MetraError("خطای غیرمنتظره: $cause")
}

/**
 * Convenience alias for repository and use-case results.
 *
 * Modelled as a sealed result rather than exceptions so ordinary user mistakes
 * (an empty project name, a negative meter count) can never crash the app.
 */
typealias MetraResult<T> = Result<T>

fun <T> success(value: T): MetraResult<T> = Result.success(value)

fun <T> failure(error: MetraError): MetraResult<T> = Result.failure(MetraException(error))

/** Wraps a [MetraError] so it survives `Result`'s `Throwable` requirement. */
class MetraException(val error: MetraError) : Exception(error.userMessage)

/** Unwraps the typed error, defaulting to [MetraError.Unknown] for foreign throwables. */
val Throwable.metraError: MetraError
    get() = (this as? MetraException)?.error ?: MetraError.Unknown(message ?: javaClass.simpleName)

/** Maps a failure through [transform] while leaving successes untouched. */
inline fun <T, R> MetraResult<T>.mapFailure(transform: (MetraError) -> MetraError): MetraResult<R> =
    fold(
        onSuccess = { @Suppress("UNCHECKED_CAST") (this as MetraResult<R>) },
        onFailure = { failure<R>(transform(it.metraError)) },
    )
