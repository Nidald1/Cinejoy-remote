package com.nedal.cinejoyremote.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// In-app updater, GitHub Releases only — no new dependencies
// (HttpURLConnection + org.json, both on every Android).
// The user creates the repo (see PAIRING.md) and sets these two constants;
// until then "Check for updates" reports "not configured" instead of failing.
const val GITHUB_OWNER = "Nidald1"
const val GITHUB_REPO = "cinejoy-remote"

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val assetName: String,
)

sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

fun currentVersionName(ctx: Context): String = try {
    @Suppress("DEPRECATION")
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
} catch (_: PackageManager.NameNotFoundException) {
    "0"
}

// Pure version compare for "1.2.9" style names (leading 'v' stripped).
// Returns >0 when [remote] is newer than [local].
fun compareVersions(local: String, remote: String): Int {
    fun parts(v: String) = v.trim().trimStart('v', 'V').split(".", "-", "+")
        .map { it.toIntOrNull() ?: it }
    val a = parts(local)
    val b = parts(remote)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(i) ?: 0
        val y = b.getOrNull(i) ?: 0
        val cmp = when {
            x is Int && y is Int -> x.compareTo(y)
            else -> x.toString().compareTo(y.toString())
        }
        if (cmp != 0) return -cmp // positive = remote newer
    }
    return 0
}

suspend fun checkForUpdate(ctx: Context): UpdateResult = withContext(Dispatchers.IO) {
    if (GITHUB_OWNER == "YOUR_GITHUB_USER") return@withContext UpdateResult.NotConfigured
    try {
        val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode == 404) {
                return@withContext UpdateResult.Failed("no releases published yet")
            }
            if (conn.responseCode != 200) {
                return@withContext UpdateResult.Failed("GitHub API HTTP ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "").trim()
            if (tag.isBlank()) return@withContext UpdateResult.Failed("release has no tag")
            if (compareVersions(currentVersionName(ctx), tag) <= 0) {
                return@withContext UpdateResult.UpToDate
            }
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var assetName = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "").ifBlank { null }
                        assetName = name
                        break
                    }
                }
            }
            if (apkUrl == null) return@withContext UpdateResult.Failed("release has no APK asset")
            UpdateResult.Available(
                UpdateInfo(
                    version = tag,
                    notes = json.optString("body", ""),
                    apkUrl = apkUrl,
                    assetName = assetName.ifBlank { "app.apk" },
                ),
            )
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        UpdateResult.Failed(e.message ?: "network error")
    }
}
