package com.nedal.cinejoyremote.ui.v3

import com.nedal.cinejoyremote.ui.v3.components.arr
import com.nedal.cinejoyremote.ui.v3.components.bool
import com.nedal.cinejoyremote.ui.v3.components.heightOf
import com.nedal.cinejoyremote.ui.v3.components.int
import com.nedal.cinejoyremote.ui.v3.components.str
import kotlinx.serialization.json.JsonObject

/**
 * One thing the PC can download for an episode: a source, its quality label and
 * the exact `start-download` options that reproduce the choice. Built from the
 * same `src-info` payload the source panes render, so the wizard and the manual
 * picker can never disagree about what a source offers.
 */
data class BatchOffer(
    val id: String,
    val source: String,
    val label: String,
    val server: String?,
    val sizeLabel: String?,
    val sizeBytes: Long?,
    val height: Int?,
    val opts: Map<String, String>,
) {
    val sourceLabel: String get() = com.nedal.cinejoyremote.presentation.FetchJob.SOURCE_LABELS[source] ?: source
}

/** "2.1 GB" / "780 MB" → bytes, for sorting offers by real size. */
fun parseSizeBytes(text: String?): Long? {
    val match = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*([KMGT]i?B)", RegexOption.IGNORE_CASE)
        .find(text ?: "") ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val mult = when (match.groupValues[2].uppercase().first()) {
        'K' -> 1L shl 10
        'M' -> 1L shl 20
        'G' -> 1L shl 30
        'T' -> 1L shl 40
        else -> 1L
    }
    return (value * mult).toLong()
}

/**
 * Every download option a source returned, in the same shape its pane uses:
 * Cinejoy shares one capture with height variants, Movy nests per-server
 * quality rows, 7Movies/AKWAM/Atlantic each return flat entry lists.
 */
fun batchOffers(source: String, data: JsonObject): List<BatchOffer> {
    if (data.bool("ok") == false) return emptyList()
    return when (source) {
        "cinejoy" -> {
            if (data.str("streamError") != null) return emptyList()
            val server = data.str("server")
            val variants = data.arr("variants")
            val auto = BatchOffer(
                id = "cinejoy|auto",
                source = "cinejoy",
                label = "Auto (best)",
                server = server,
                sizeLabel = null,
                sizeBytes = null,
                height = null,
                opts = mapOf("source" to "cinejoy", "qualityLabel" to "Auto (best)"),
            )
            val rows = variants.mapIndexedNotNull { i, v ->
                val height = v.int("height") ?: return@mapIndexedNotNull null
                val label = "${height}p"
                val size = v.str("size")
                BatchOffer(
                    id = "cinejoy|$i",
                    source = "cinejoy",
                    label = label,
                    server = server,
                    sizeLabel = size,
                    sizeBytes = parseSizeBytes(size),
                    height = height,
                    opts = buildMap {
                        put("source", "cinejoy")
                        put("qualityLabel", label)
                        put("quality", height.toString())
                    },
                )
            }
            if (rows.isEmpty() && data.str("url") == null) emptyList() else rows + auto
        }

        "movy" -> data.arr("servers")
            .filter { it.bool("ok") == true }
            .flatMap { srv ->
                val server = srv.str("key") ?: return@flatMap emptyList<BatchOffer>()
                srv.arr("sources").mapIndexedNotNull { i, s ->
                    val quality = s.str("quality") ?: return@mapIndexedNotNull null
                    val height = s.int("height") ?: heightOf(quality)
                    val size = s.str("size")
                    BatchOffer(
                        id = "movy|$server|$i",
                        source = "movy",
                        label = quality,
                        server = server,
                        sizeLabel = size,
                        sizeBytes = parseSizeBytes(size),
                        height = height,
                        opts = buildMap {
                            put("source", "movy")
                            put("server", server)
                            put("qualityLabel", quality)
                            height?.let { put("quality", it.toString()) }
                        },
                    )
                }
            }

        "7movies" -> data.arr("entries").mapIndexedNotNull { i, e ->
            val qualities = e.str("qualitiesCsv")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            // The entry's ladder is usually "1080p,720p,…": offer its best rung
            // so the wizard's default really is the biggest file, not the first.
            val label = qualities.maxByOrNull { heightOf(it) ?: 0 } ?: e.str("label")
                ?: return@mapIndexedNotNull null
            val server = e.str("server")
            val height = heightOf(label) ?: e.int("height")
            val size = e.str("size")
            BatchOffer(
                id = "7movies|$i",
                source = "7movies",
                label = label,
                server = server ?: e.str("provider"),
                sizeLabel = size,
                sizeBytes = parseSizeBytes(size),
                height = height,
                opts = buildMap {
                    put("source", "7movies")
                    server?.let { put("server", it) }
                    put("qualityLabel", label)
                    height?.let { put("quality", it.toString()) }
                    e.str("id")?.let { put("sourceEntry", it) }
                },
            )
        }

        "atlantic" -> data.arr("entries").mapIndexedNotNull { i, e ->
            val label = e.str("quality") ?: e.str("kind") ?: return@mapIndexedNotNull null
            val server = e.str("server")
            val size = e.str("size")
            val height = e.int("height") ?: heightOf(label)
            BatchOffer(
                id = "atlantic|$i",
                source = "atlantic",
                label = label,
                server = listOfNotNull(server, e.str("audio")).joinToString(" · ").ifBlank { null },
                sizeLabel = size,
                sizeBytes = parseSizeBytes(size),
                height = height,
                opts = buildMap {
                    put("source", "atlantic")
                    server?.let { put("server", it) }
                    put("qualityLabel", label)
                    height?.let { put("quality", it.toString()) }
                    e.str("id")?.let { put("sourceEntry", it) }
                },
            )
        }

        "akwam" -> data.arr("entries").mapIndexedNotNull { i, e ->
            val label = e.str("label") ?: return@mapIndexedNotNull null
            val server = e.str("server")
            val size = e.str("size")
            val height = heightOf(label) ?: e.int("height")
            BatchOffer(
                id = "akwam|$i",
                source = "akwam",
                label = label,
                server = server,
                sizeLabel = size,
                sizeBytes = parseSizeBytes(size),
                height = height,
                opts = buildMap {
                    put("source", "akwam")
                    server?.let { put("server", it) }
                    put("qualityLabel", label)
                    height?.let { put("quality", it.toString()) }
                    e.str("downloadPage")?.let { put("sourceEntry", it) }
                },
            )
        }

        else -> emptyList()
    }
}

/** Biggest quality first, then biggest file: the default "best for me" row. */
fun bestFirst(offers: List<BatchOffer>): List<BatchOffer> =
    offers.sortedWith(
        compareByDescending<BatchOffer> { it.height ?: 0 }.thenByDescending { it.sizeBytes ?: 0L },
    )
