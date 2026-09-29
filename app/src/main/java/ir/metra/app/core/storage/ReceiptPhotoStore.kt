package ir.metra.app.core.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns receipt photos on disk.
 *
 * A photo picked from the gallery arrives as a content Uri that the *source*
 * app owns; if the user deletes the original (or clears that app), the stored
 * Uri silently dangles and the "proof of expense" vanishes. So on attach we
 * copy the bytes into our own `filesDir/receipts/` and store that internal
 * path instead — from then on the photo lives and dies with Metra.
 */
@Singleton
class ReceiptPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").apply { mkdirs() }

    /**
     * Copies [uri] into app storage and returns the internal absolute path, or
     * null when the source stream could not be read (nothing is left behind).
     */
    fun copyFromUri(uri: Uri): String? = runCatching {
        val target = File(receiptsDir, "${UUID.randomUUID()}.jpg")
        val opened = context.contentResolver.openInputStream(uri) ?: return null
        opened.use { input -> target.outputStream().use { input.copyTo(it) } }
        target.absolutePath
    }.getOrNull()

    /**
     * Deletes a previously-copied receipt.
     *
     * Guarded to files inside [receiptsDir]: the column historically held
     * foreign content Uris, and a hard-delete must never turn into an
     * arbitrary `File.delete()` on something we do not own.
     */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.parentFile?.absolutePath == receiptsDir.absolutePath) {
            file.delete()
        }
    }
}
