package com.nedal.cinejoyremote.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK over plain HTTPS with retries, then fires the
 * system install prompt through FileProvider.
 *
 * DownloadManager was replaced: GitHub serves release assets through a redirect
 * to its CDN, and a transient 5xx there surfaced as a dead end — the popup said
 * "download failed (code 504)" with no retry (DownloadManager reports raw HTTP
 * codes). Here every attempt follows the redirects itself, retries 5xx /
 * timeouts with backoff, and falls back to the deterministic
 * /releases/download/<tag>/<asset> URL when the API-provided one fails. A
 * release whose asset is still uploading therefore succeeds on a later attempt
 * instead of failing once.
 */
object ApkInstaller {

    private const val MAX_ATTEMPTS = 4
    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 30_000
    private const val MIN_APK_BYTES = 1_000_000L

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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
        val name = info.assetName.ifBlank { "CineDownloader-${info.version.trimStart('v', 'V')}.apk" }
        val file = File(dir, name)
        val part = File(dir, "$name.part")
        // API URL first, then the URL the release workflow always produces.
        val candidates = buildList {
            info.apkUrl.takeIf { it.startsWith("https://") }?.let { add(it) }
            add("https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/${info.version}/$name")
        }.distinct()

        var lastError = "download failed"
        for (attempt in 1..MAX_ATTEMPTS) {
            val url = candidates[(attempt - 1) % candidates.size]
            try {
                part.delete()
                downloadTo(url, part, onProgress)
                val bytes = part.length()
                if (bytes < MIN_APK_BYTES) {
                    throw IOException("GitHub returned ${bytes} bytes (release asset still uploading?)")
                }
                if (file.exists() && !file.delete()) throw IOException("could not replace the old update file")
                if (!part.renameTo(file)) throw IOException("could not save the update file")
                onProgress(1f)
                return@withContext install(ctx, file, info)
            } catch (e: Exception) {
                lastError = e.message ?: lastError
                part.delete()
                if (attempt < MAX_ATTEMPTS) delay(1_500L * attempt)
            }
        }
        Result.failure(IllegalStateException(lastError))
    }

    private fun downloadTo(url: String, target: File, onProgress: (Float?) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("User-Agent", "CineJoy-Remote-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub answered HTTP $code")
            }
            val total = conn.contentLengthLong
            var read = 0L
            var lastTick = 0L
            conn.inputStream.use { input ->
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 200) {
                            lastTick = now
                            onProgress(
                                if (total > 0) (read.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
                            )
                        }
                    }
                    out.flush()
                }
            }
            if (read <= 0) throw IOException("download was empty")
        } finally {
            conn.disconnect()
        }
    }

    private fun install(ctx: Context, file: File, info: UpdateInfo): Result<Unit> {
        // Trust the file, not the release tag. v5.7.0 shipped an APK built
        // from 5.6.3 sources, so "updating" reinstalled the same build: the
        // app kept reporting 5.6.3 and the prompt kept offering 5.7.0
        // forever. Refuse a download that is not actually newer, and say so.
        val archive = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (archive != null && archive.packageName != ctx.packageName) {
            file.delete()
            return Result.failure(IllegalStateException("the downloaded APK is not CineJoy Remote"))
        }
        val archiveCode = archive?.let { versionCodeOf(it) }
        if (archiveCode != null && archiveCode <= installedVersionCode(ctx)) {
            val shipped = archive.versionName ?: "an older build"
            file.delete()
            return Result.failure(
                IllegalStateException(
                    "release ${info.version} still ships $shipped " +
                        "(installed: ${currentVersionName(ctx)}) - nothing newer to install",
                ),
            )
        }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return try {
            ctx.startActivity(install)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("could not open the installer: ${e.message ?: "no app can install packages"}"),
            )
        }
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun installedVersionCode(ctx: Context): Long = try {
        versionCodeOf(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (_: Exception) {
        0L
    }
}
