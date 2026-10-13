package com.nedal.cinejoyremote.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

// Downloads the release APK via DownloadManager, then fires the system install
// prompt through FileProvider. Returns after the install screen opens
// (user confirms there — we cannot and should not install silently).
object ApkInstaller {

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

    suspend fun downloadAndPrompt(ctx: Context, info: UpdateInfo): Result<Unit> {
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

            // Suspend until this exact download completes.
            val success = suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                        try {
                            c.unregisterReceiver(this)
                        } catch (_: IllegalArgumentException) {
                        }
                        if (cont.isActive) cont.resume(true)
                    }
                }
                ctx.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
                cont.invokeOnCancellation {
                    runCatching { ctx.unregisterReceiver(receiver) }
                }
            }
            if (!success || !file.exists()) {
                return Result.failure(IllegalStateException("download did not complete"))
            }

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
}
