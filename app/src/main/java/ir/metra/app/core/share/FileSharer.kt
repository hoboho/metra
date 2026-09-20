package ir.metra.app.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shares generated files through the **native Android share sheet**.
 *
 * This is deliberately a thin wrapper over `ACTION_SEND` + `FileProvider`:
 * WhatsApp, Telegram, Gmail, Drive and every other registered handler appear
 * automatically, and none of that integration is reinvented here.
 */
@Singleton
class FileSharer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Builds a chooser intent for [file]. */
    fun shareIntent(file: File, subject: String, mimeType: String): Intent {
        val uri = file.toShareUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "اشتراک‌گذاری").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Launches the share sheet.
     *
     * Fails with a Persian [MetraError.ShareFailed] message when no activity can
     * handle the intent, rather than crashing the caller.
     */
    fun share(file: File, subject: String, mimeType: String): MetraResult<Unit> {
        if (!file.exists() || file.length() == 0L) {
            return failure(MetraError.ShareFailed)
        }
        return try {
            context.startActivity(shareIntent(file, subject, mimeType))
            success(Unit)
        } catch (notFound: ActivityNotFoundException) {
            failure(MetraError.ShareFailed)
        } catch (security: SecurityException) {
            failure(MetraError.ShareFailed)
        }
    }

    /** Exposes [this] through the app's FileProvider authority. */
    fun File.toShareUri(): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

    companion object {
        const val MIME_PDF = "application/pdf"
        const val MIME_CSV = "text/csv"
        const val MIME_JSON = "application/json"
        const val MIME_BACKUP = "application/octet-stream"
    }
}
