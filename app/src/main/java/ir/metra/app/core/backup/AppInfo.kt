package ir.metra.app.core.backup

import android.content.Context
import ir.metra.app.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App identity, recorded inside backup files for forward compatibility. */
data class AppInfo(val versionName: String, val versionCode: Int)

fun interface AppInfoProvider {
    fun info(): AppInfo
}

@Singleton
class AppInfoProviderImpl @Inject constructor(
    private val context: Context,
) : AppInfoProvider {
    override fun info(): AppInfo {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = info.versionCode
        return AppInfo(versionName = info.versionName ?: BuildConfig.VERSION_NAME, versionCode = code)
    }
}

/** Supplies the directory that `FileProvider` exposes for sharing reports. */
fun interface ReportDirectoryProvider {
    fun reportsDirectory(): File
}

/** Supplies the directory where pre-restore safety snapshots are written. */
fun interface BackupDirectoryProvider {
    fun backupDirectory(): File
}
