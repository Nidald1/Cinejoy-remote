package com.nedal.cinejoyremote.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

// Downloads the release APK via DownloadManager, then fires the system install
// prompt through FileProvider. Progress is polled straight from the
// DownloadManager row (0..1, or null while the total size is unknown), which
// also surfaces the real failure reason instead of a generic "did not finish".
object ApkInstaller {

    private const val POLL_MS = 300L
    private const val TIMEOUT_MS = 15 * 60 * 1000L

    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }

    suspend fun downloadAndPrompt(
        ctx: Context,
        info: UpdateInfo,
        onProgress: (Float?) -> Unit = {},
    ): Result<Unit> {
        return try {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
            val file = File(dir, info.assetName)
            if (file.exists()) file.delete()

            val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
                setTitle("CineJoy Remote ${info.version}")
                setDescription("Downloading update")
                setDestinationUri(Uri.fromFile(file))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setMimeType("application/vnd.android.package-archive")
            }
            val manager = ctx.getSystemService(DownloadManager::class.java)
                ?: return Result.failure(IllegalStateException("download service unavailable"))
            val downloadId = manager.enqueue(request)
            val query = DownloadManager.Query().setFilterById(downloadId)

            // Poll until the row reports success/failure (also feeds progress).
            val started = System.currentTimeMillis()
            var lastUpdate = 0L
            while (true) {
                if (System.currentTimeMillis() - started > TIMEOUT_MS) {
                    manager.remove(downloadId)
                    return Result.failure(IllegalStateException("download timed out"))
                }
                var status = -1
                var reason = -1
                manager.query(query)?.use { c ->
                    if (c.moveToFirst()) {
                        status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        if (status == DownloadManager.STATUS_RUNNING ||
                            status == DownloadManager.STATUS_PENDING
                        ) {
                            val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 150) {
                                lastUpdate = now
                                onProgress(if (total > 0) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else null)
                            }
                        }
                    }
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED -> {
                        return Result.failure(IllegalStateException(failureReason(reason)))
                    }
                }
                delay(POLL_MS)
            }
            if (!file.exists()) {
                return Result.failure(IllegalStateException("download did not complete"))
            }
            onProgress(1f)

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            ctx.startActivity(install)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun failureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage on the phone"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage not available"
        DownloadManager.ERROR_CANNOT_RESUME -> "download interrupted — try again"
        DownloadManager.ERROR_FILE_ERROR -> "could not save the file"
        else -> "download failed (code $reason)"
    }
}
