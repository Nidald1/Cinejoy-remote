package com.nedal.cinejoyremote.ui.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.presentation.SpeedTest
import com.nedal.cinejoyremote.ui.v3.V3Radius
import com.nedal.cinejoyremote.ui.v3.V3Space
import com.nedal.cinejoyremote.ui.v3.V3Type
import com.nedal.cinejoyremote.ui.v3.v3
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------- helpers
internal fun JsonObject.str(key: String): String? = runCatching {
    this[key]?.jsonPrimitive?.contentOrNull
}.getOrNull()?.takeIf { it != "null" }

internal fun JsonObject.int(key: String): Int? = runCatching {
    this[key]?.jsonPrimitive?.intOrNull
}.getOrNull()

internal fun JsonObject.bool(key: String): Boolean? = runCatching {
    this[key]?.jsonPrimitive?.booleanOrNull
}.getOrNull()

internal fun JsonObject.arr(key: String): List<JsonObject> =
    runCatching { (this[key] as? JsonArray) ?: return emptyList() }
        .getOrNull()
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        ?: emptyList()

internal fun heightOf(label: String?): Int? =
    Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("\\b(\\d{3,4})\\b").find(label ?: "")?.groupValues?.get(1)?.toIntOrNull()

/**
 * The three source panes, lifted out of the detail screen so the fetch-queue
 * screen can render them for any episode. Pure presentation: the caller owns
 * the data and what Start download does, so nothing about the PC-side fetch
 * process (same `src-info` payload, same `start-download` payload) changes.
 *
 * [stateKey] scopes the remembered quality/server selection — pass something
 * unique per episode (e.g. the episode key) so two episodes shown on the same
 * screen never share a selection.
 */
@Composable
fun V3SourcePane(
    source: String,
    data: JsonObject,
    stateKey: String,
    starting: Boolean,
    onQueue: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    speed: Map<String, SpeedTest> = emptyMap(),
    onSpeed: (id: String, url: String) -> Unit = { _, _ -> },
) {
    val c = v3
    if (data.bool("ok") == false) {
        Text(
            data.str("error") ?: "The PC could not resolve this source.",
            style = V3Type.body14,
            color = c.error,
        )
        return
    }
    when (source) {
        "movy" -> V3MovyPane(data, stateKey, starting, onQueue, speed, onSpeed)
        "akwam" -> V3AkwamPane(data, stateKey, starting, onQueue, speed, onSpeed)
        else -> V3CinejoyPane(data, stateKey, starting, onQueue, onRefresh, speed, onSpeed)
    }
}

/**
 * ⚡ chip: starts a phone-side throughput probe for the URL it's attached to
 * and shows the live result (MB/s, or ms when the transfer was too small for
 * a throughput figure to mean anything). Shared by the fetch screen's source
 * rows and the per-server rows inside the panes.
 */
@Composable
fun SpeedChip(test: SpeedTest?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = v3
    val running = test?.running == true
    Row(
        modifier
            .clip(RoundedCornerShape(V3Radius.pill))
            .background(if (running) c.accent.copy(alpha = 0.14f) else c.surface)
            .clickable(enabled = !running, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Speed,
            contentDescription = "Speed test",
            tint = when {
                running -> c.accent
                test?.failed == true -> c.error
                else -> c.textSecondary
            },
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            speedLabel(test),
            style = V3Type.caption,
            color = when {
                running -> c.accent
                test?.failed == true -> c.error
                test?.bytesPerSec != null && !test.pingOnly -> c.success
                else -> c.textSecondary
            },
        )
    }
}

internal fun speedLabel(test: SpeedTest?): String = when {
    test == null -> "test"
    test.bytesPerSec == null && test.running -> "testing…"
    test.failed -> "failed"
    test.pingOnly -> "${test.ttfbMs ?: 0} ms"
    test.bytesPerSec == null -> "test"
    test.bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(test.bytesPerSec / 1_000_000.0)
    test.bytesPerSec >= 1_000 -> "${test.bytesPerSec / 1_000} KB/s"
    else -> "${test.bytesPerSec} B/s"
}

@Composable
fun V3StartButton(starting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !starting,
        shape = RoundedCornerShape(V3Radius.sm),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        if (starting) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp), strokeWidth = 2.dp, color = v3.onAccent,
            )
            Spacer(Modifier.width(V3Space.sm))
            Text("Queueing…", style = V3Type.chip)
        } else {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(V3Space.sm))
            Text("Start download", style = V3Type.chip)
        }
    }
}

// --- Cinejoy: one captured master, choose a variant (or Auto).
@Composable
private fun V3CinejoyPane(
    data: JsonObject,
    stateKey: String,
    starting: Boolean,
    onQueue: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    speed: Map<String, SpeedTest>,
    onSpeed: (id: String, url: String) -> Unit,
) {
    val c = v3
    val streamError = data.str("streamError")
    val server = data.str("server") ?: "nebula.bright67.online"
    if (streamError != null) {
        Text(
            "Link unavailable ($streamError). Try Refresh, or use Movy/AKWAM for this item.",
            style = V3Type.body14,
            color = c.error,
        )
        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(V3Radius.sm)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(V3Space.sm))
            Text("Refresh", color = c.text)
        }
        return
    }

    val variants = data.arr("variants")
    var selected by remember(stateKey) { mutableIntStateOf(-1) } // -1 = Auto
    val options = listOf(-1) + variants.mapIndexed { i, _ -> i }

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Server: $server · ${if (variants.isEmpty()) "single stream" else "${variants.size} quality variant(s)"}",
                style = V3Type.caption,
                color = c.textTertiary,
                modifier = Modifier.weight(1f),
            )
            data.str("url")?.takeIf { it.startsWith("http") }?.let { url ->
                val id = "$stateKey|cinejoy"
                SpeedChip(test = speed[id], onClick = { onSpeed(id, url) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(V3Space.sm), modifier = Modifier.fillMaxWidth()) {
            options.take(6).forEach { i ->
                val label = if (i < 0) "Auto" else (variants[i].int("height")?.let { "${it}p" } ?: "stream")
                V3Pill(label = label, selected = selected == i, onClick = { selected = i })
            }
        }
        if (selected >= 0) {
            variants[selected].str("size")?.takeIf { it.isNotBlank() }?.let { size ->
                Text("Estimated size: $size", style = V3Type.caption, color = c.textTertiary)
            }
        }
        V3StartButton(starting) {
            val height = if (selected < 0) null else variants[selected].int("height")
            val label = if (selected < 0) "Auto (best)" else height?.let { "${it}p" } ?: "stream"
            onQueue(
                buildMap {
                    put("source", "cinejoy")
                    put("qualityLabel", label)
                    height?.let { put("quality", it.toString()) }
                },
            )
        }
    }
}

// --- Movy: pick a server, then a quality from that server.
@Composable
private fun V3MovyPane(
    data: JsonObject,
    stateKey: String,
    starting: Boolean,
    onQueue: (Map<String, String>) -> Unit,
    speed: Map<String, SpeedTest>,
    onSpeed: (id: String, url: String) -> Unit,
) {
    val c = v3
    val servers = data.arr("servers")
    if (servers.isEmpty()) {
        Text("No movy servers found for this item.", style = V3Type.body14, color = c.textSecondary)
        return
    }
    val usable = servers.filter { it.bool("ok") == true && it.arr("sources").isNotEmpty() }
    var selectedServer by remember(stateKey) {
        mutableStateOf(usable.firstOrNull()?.str("key") ?: servers.firstOrNull()?.str("key"))
    }
    val current = servers.firstOrNull { it.str("key") == selectedServer }
    val sources = current?.arr("sources") ?: emptyList()
    val labels = current?.arr("sources")?.mapNotNull { it.str("quality") }?.distinct()
        ?: current?.arr("qualities")?.mapNotNull { it.str("it") } ?: emptyList()
    var selectedLabel by remember(stateKey, selectedServer) {
        mutableStateOf(labels.firstOrNull())
    }

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.sm)) {
        if (data.bool("rateLimited") == true) {
            Text("Movy rate-limited some servers — wait a moment and Refresh.", style = V3Type.caption, color = c.warning)
        }

        // The server list scrolls inside a capped-height box so the quality
        // chips and the Start button stay visible no matter how many servers.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(V3Space.sm),
        ) {
            servers.forEach { srv ->
                val key = srv.str("key") ?: return@forEach
                val ok = srv.bool("ok") == true && srv.arr("sources").isNotEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(V3Radius.sm))
                        .background(if (selectedServer == key) c.accent.copy(alpha = 0.14f) else c.surface)
                        .clickable(enabled = ok) { selectedServer = key }
                        .padding(horizontal = V3Space.md, vertical = V3Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedServer == key, onClick = null, enabled = ok)
                    Column(Modifier.weight(1f)) {
                        Text(key, style = V3Type.itemTitle, color = c.text)
                        if (!ok) {
                            Text(srv.str("error") ?: "no sources", style = V3Type.caption, color = c.textTertiary)
                        } else {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(V3Space.xs),
                            ) {
                                srv.arr("sources").mapNotNull { it.str("quality") }.distinct().take(6).forEach { q ->
                                    val size = srv.arr("sources").firstOrNull { it.str("quality") == q }?.str("size")
                                    Text(
                                        if (size.isNullOrBlank()) q else "$q · $size",
                                        style = V3Type.caption,
                                        color = c.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                    if (ok) {
                        val url = srv.arr("sources").firstNotNullOfOrNull { s ->
                            s.str("url")?.takeIf { u -> u.startsWith("http") }
                        }
                        if (url != null) {
                            val id = "$stateKey|movy|$key"
                            SpeedChip(
                                test = speed[id],
                                onClick = { onSpeed(id, url) },
                                modifier = Modifier.padding(start = V3Space.sm),
                            )
                        }
                    }
                }
            }
        }

        if (sources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
                ) {
                    labels.distinct().take(6).forEach { label ->
                        V3Pill(label = label, selected = selectedLabel == label, onClick = { selectedLabel = label })
                    }
                }
                sources.firstOrNull { it.str("quality") == selectedLabel }?.str("size")
                    ?.takeIf { it.isNotBlank() }?.let { size ->
                        Text("File size: $size", style = V3Type.caption, color = c.textTertiary)
                    }
            }
        }

        V3StartButton(starting && selectedServer != null) {
            val src = sources.firstOrNull { it.str("quality") == selectedLabel }
            onQueue(
                buildMap {
                    put("source", "movy")
                    selectedServer?.let { put("server", it) }
                    selectedLabel?.let { put("qualityLabel", it) }
                    (src?.int("height") ?: heightOf(selectedLabel))?.let { put("quality", it.toString()) }
                },
            )
        }
    }
}

// --- AKWAM: every quality/server row is its own download option.
@Composable
private fun V3AkwamPane(
    data: JsonObject,
    stateKey: String,
    starting: Boolean,
    onQueue: (Map<String, String>) -> Unit,
    speed: Map<String, SpeedTest>,
    onSpeed: (id: String, url: String) -> Unit,
) {
    val c = v3
    val entries = data.arr("entries")
    if (entries.isEmpty()) {
        Text("No AKWAM qualities found for this item.", style = V3Type.body14, color = c.textSecondary)
        return
    }
    var selectedIndex by remember(stateKey) { mutableIntStateOf(0) }
    val entry = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(V3Space.sm)) {
        entries.forEachIndexed { i, e ->
            val label = e.str("label") ?: "Option ${i + 1}"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(V3Radius.sm))
                    .background(if (selectedIndex == i) c.accent.copy(alpha = 0.14f) else c.surface)
                    .clickable { selectedIndex = i }
                    .padding(horizontal = V3Space.md, vertical = V3Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selectedIndex == i, onClick = null)
                Column(Modifier.weight(1f)) {
                    Text(label, style = V3Type.itemTitle, color = c.text)
                    Text(
                        listOfNotNull(
                            e.str("size")?.takeIf { it.isNotBlank() },
                            e.str("server")?.let { "server $it" },
                        ).joinToString(" · ").ifBlank { "quality option" },
                        style = V3Type.caption,
                        color = c.textTertiary,
                    )
                }
                val url = e.str("url")?.takeIf { u -> u.startsWith("http") }
                    ?: e.str("downloadPage")?.takeIf { u -> u.startsWith("http") }
                if (url != null) {
                    val id = "$stateKey|akwam|$i"
                    SpeedChip(
                        test = speed[id],
                        onClick = { onSpeed(id, url) },
                        modifier = Modifier.padding(start = V3Space.sm),
                    )
                }
            }
        }

        V3StartButton(starting && entry != null) {
            val e = entry ?: return@V3StartButton
            onQueue(
                buildMap {
                    put("source", "akwam")
                    e.str("server")?.let { put("server", it) }
                    e.str("label")?.let { put("qualityLabel", it) }
                    heightOf(e.str("label"))?.let { put("quality", it.toString()) }
                    e.str("downloadPage")?.let { put("sourceEntry", it) }
                },
            )
        }
    }
}
