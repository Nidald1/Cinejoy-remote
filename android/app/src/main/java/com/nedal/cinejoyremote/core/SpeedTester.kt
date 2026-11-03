package com.nedal.cinejoyremote.core

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * One measured sample of a source's/server's stream.
 *
 * [bytesPerSec] is the useful throughput of the transfer window; [ttfbMs] is
 * connect-to-first-byte latency. [pingOnly] marks a transfer that moved too
 * little data (a playlist or metadata file) for a throughput number to mean
 * anything — the UI should show latency instead of a fake MB/s.
 */
data class SpeedSample(
    val bytes: Long,
    val ms: Long,
    val ttfbMs: Long,
) {
    val bytesPerSec: Long get() = bytes * 1000L / (ms.coerceAtLeast(1))
    val pingOnly: Boolean get() = bytes < PING_ONLY_BELOW_BYTES

    companion object {
        // Below ~256 KB the number is dominated by latency, not throughput.
        const val PING_ONLY_BELOW_BYTES = 256L * 1024
    }
}

/**
 * Phone-side speed probe for the links the PC already handed us.
 *
 * Purely additive: it never touches the src-info/start-download flow — it
 * just GETs URLs the fetch screen already has, so the user can compare sources
 * and individual servers before starting a download.
 *
 * Reality of the sources (verified against live PC responses):
 *  - cinejoy: master.m3u8 → variant playlists → segments (CDN needs cinejoy.pk)
 *  - movy:    index .m3u8 playlists per (server, quality) → segments
 *  - 7movies: master.m3u8 behind embed.vidrift.net → variants → segments
 *  - akwam:   direct .mp4 URLs
 * Playlists are a few KB, so measuring them would report nonsense. Up to 4
 * playlist hops are followed (sniffing #EXTM3U, including mislabeled files such
 * as cinejoy's "playlist.jpg") until a real media object is reached, which is
 * then streamed for up to [MAX_MS]/[MAX_BYTES].
 *
 * Two details made every probe on a real stream report "failed" before:
 *  1. the connection's input stream was closed after each read, so the media
 *     read that followed threw "Stream closed" — one stream is now kept for the
 *     whole connection and closed only with the connection; and
 *  2. probes went out with a non-browser User-Agent and no Referer, which the
 *     source CDNs answer with 403 — headers now match what the players send.
 */
object SpeedTester {

    private const val MAX_BYTES = 6L * 1024 * 1024
    private const val MAX_MS = 5_000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_PLAYLIST_BYTES = 256 * 1024
    private const val MAX_HOPS = 4
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** Blocking — call it off the main thread (Dispatchers.IO). */
    fun measure(rawUrl: String, referer: String? = null): Result<SpeedSample> = runCatching {
        var url = rawUrl
        var attempt = 0
        while (attempt++ < MAX_HOPS) {
            val conn = open(url, refererFor(url, referer))
            val t0 = System.nanoTime()
            try {
                val input = conn.inputStream
                // The head decides playlist vs media; for media those bytes
                // already count toward the measurement.
                val head = readUpTo(input, 8 * 1024)
                val text0 = String(head, Charsets.UTF_8)
                val looksLikePlaylist = text0.trimStart().startsWith("#EXTM3U") ||
                    url.contains("m3u8", ignoreCase = true) ||
                    (conn.contentType ?: "").contains("mpegurl", ignoreCase = true)
                if (!looksLikePlaylist) return@runCatching streamSample(input, head, t0)

                val rest = readUpTo(input, MAX_PLAYLIST_BYTES - head.size)
                val child = (text0 + String(rest, Charsets.UTF_8))
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (child == null) {
                    // A playlist with no children: measure what we pulled
                    // (tiny, so the UI shows latency instead of a fake rate).
                    val bytes = (head.size + rest.size).toLong()
                    return@runCatching SpeedSample(bytes = bytes, ms = elapsedMs(t0), ttfbMs = elapsedMs(t0))
                }
                url = URL(URL(url), child).toString()
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        // Hops exhausted (pathological playlist loop): measure the last URL.
        val conn = open(url, refererFor(url, referer))
        val t0 = System.nanoTime()
        try {
            streamSample(conn.inputStream, ByteArray(0), t0)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun open(urlStr: String, referer: String?): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            if (referer != null) {
                setRequestProperty("Referer", referer)
                setRequestProperty("Origin", referer.trimEnd('/'))
            }
            // Ask for a bounded window; servers that ignore Range are fine —
            // the read loop stops at MAX_BYTES/MAX_MS anyway.
            setRequestProperty("Range", "bytes=0-${MAX_BYTES - 1}")
        }
        conn.connect()
        return conn
    }

    /**
     * Reads up to [want] bytes from the live connection stream. Deliberately
     * does NOT close it: the probe keeps reading the same stream, and closing
     * after a partial read is what made every media probe throw.
     */
    private fun readUpTo(input: InputStream, want: Int): ByteArray {
        if (want <= 0) return ByteArray(0)
        val buf = ByteArray(want)
        var got = 0
        while (got < want) {
            val n = input.read(buf, got, want - got)
            if (n < 0) break
            got += n
        }
        return if (got == want) buf else buf.copyOf(got)
    }

    private fun streamSample(input: InputStream, initial: ByteArray, t0: Long): SpeedSample {
        var bytes = initial.size.toLong()
        var ttfb = if (bytes > 0) elapsedMs(t0) else 0L
        var windowMs = elapsedMs(t0)
        val buf = ByteArray(64 * 1024)
        while (bytes < MAX_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            if (bytes == 0L) ttfb = elapsedMs(t0)
            bytes += n
            windowMs = elapsedMs(t0)
            if (windowMs >= MAX_MS) break
        }
        if (bytes == 0L) throw IllegalStateException("no data from server")
        if (ttfb <= 0L) ttfb = windowMs.coerceAtLeast(1)
        return SpeedSample(bytes = bytes, ms = windowMs.coerceAtLeast(1), ttfbMs = ttfb)
    }

    /**
     * The CDNs behind these sources reject foreign referers with 403, so send
     * the same one their player would. Unknown hosts get their own origin,
     * which is what a browser sends for a same-site media request.
     */
    private fun refererFor(url: String, explicit: String?): String? {
        if (explicit != null) return explicit
        val parsed = runCatching { URL(url) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        return when {
            host.contains("vidrift") -> "https://embed.vidrift.net/"
            host.contains("bright67") || host.contains("cinejoy") || host.contains("lightspeed") -> "https://cinejoy.pk/"
            host.contains("movy") -> "https://www.movy.sx/"
            else -> "${parsed.protocol}://$host/"
        }
    }

    private fun elapsedMs(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000
}
