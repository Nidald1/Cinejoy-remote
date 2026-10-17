package com.nedal.cinejoyremote.core

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
 * Reality of the three sources (verified against live PC responses):
 *  - cinejoy: master.m3u8 → variant playlists → segments
 *  - movy:    index .m3u8 playlists per (server, quality) → segments
 *  - akwam:   direct .mp4 URLs
 * Playlists are a few KB, so measuring them would report nonsense. Up to 4
 * playlist hops are followed (sniffing #EXTM3U, including mislabeled files
 * such as cinejoy's "playlist.jpg") until a real media object is reached,
 * which is then streamed for up to [MAX_MS]/[MAX_BYTES].
 */
object SpeedTester {

    private const val MAX_BYTES = 6L * 1024 * 1024
    private const val MAX_MS = 5_000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_PLAYLIST_BYTES = 256 * 1024
    private const val MAX_HOPS = 4

    /** Blocking — call it off the main thread (Dispatchers.IO). */
    fun measure(rawUrl: String): Result<SpeedSample> = runCatching {
        var url = rawUrl
        var attempt = 0
        while (attempt++ < MAX_HOPS) {
            val conn = open(url)
            val t0 = System.nanoTime()
            sniff@ try {
                // Read the head first: only <8 KB decide playlist vs media,
                // and for media those bytes count toward the measurement.
                val head = readUpTo(conn, 8 * 1024, already = 0)
                val text0 = String(head, Charsets.UTF_8)
                val looksLikePlaylist = text0.trimStart().startsWith("#EXTM3U") ||
                    url.contains("m3u8", ignoreCase = true) ||
                    (conn.contentType ?: "").contains("mpegurl", ignoreCase = true)
                if (looksLikePlaylist) {
                    val rest = readUpTo(conn, MAX_PLAYLIST_BYTES - head.size, already = head.size)
                    conn.disconnect()
                    val text = text0 + String(rest, Charsets.UTF_8)
                    val child = text.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    if (child != null) {
                        url = URL(URL(url), child).toString()
                        continue // fetch the child next hop
                    }
                    // A playlist with no children: fall through and measure
                    // what we already pulled (it's tiny → pingOnly).
                    return@runCatching SpeedSample(bytes = (head.size + rest.size).toLong(), ms = elapsedMs(t0), ttfbMs = elapsedMs(t0))
                }
                // Real media on this very connection: keep streaming it.
                return@runCatching streamSample(conn, head, t0)
            } catch (e: Exception) {
                runCatching { conn.disconnect() }
                throw e
            }
        }
        // Hops exhausted (pathological playlist loop): measure the last URL.
        val conn = open(url)
        val t0 = System.nanoTime()
        try {
            streamSample(conn, ByteArray(0), t0)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun open(urlStr: String): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "CineJoyRemote/1.0")
            // Ask for a bounded window; servers that ignore Range are fine —
            // the read loop stops at MAX_BYTES/MAX_MS anyway.
            setRequestProperty("Range", "bytes=0-${MAX_BYTES - 1}")
        }
        conn.connect()
        return conn
    }

    /** Reads up to [want] bytes of the current response (0 on empty request). */
    private fun readUpTo(conn: HttpURLConnection, want: Int, already: Int): ByteArray {
        if (want <= 0) return ByteArray(0)
        val buf = ByteArray(want)
        var got = 0
        conn.inputStream.use { input ->
            while (got < want) {
                val n = input.read(buf, got, want - got)
                if (n < 0) break
                got += n
            }
        }
        return if (got == want) buf else buf.copyOf(got)
    }

    private fun streamSample(conn: HttpURLConnection, initial: ByteArray, t0: Long): SpeedSample {
        var bytes = initial.size.toLong()
        var ttfb = if (bytes > 0) elapsedMs(t0) else 0L
        var windowMs = elapsedMs(t0)
        conn.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            while (bytes < MAX_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                if (bytes == 0L) ttfb = elapsedMs(t0)
                bytes += n
                windowMs = elapsedMs(t0)
                if (windowMs >= MAX_MS) break
            }
        }
        runCatching { conn.disconnect() }
        if (bytes == 0L) throw IllegalStateException("no data from server")
        return SpeedSample(bytes = bytes, ms = windowMs.coerceAtLeast(1), ttfbMs = ttfb)
    }

    private fun elapsedMs(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000
}