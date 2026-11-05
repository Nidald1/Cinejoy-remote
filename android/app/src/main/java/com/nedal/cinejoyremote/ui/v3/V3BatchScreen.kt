package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.presentation.FetchJob
import com.nedal.cinejoyremote.presentation.FetchQueueViewModel
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.ui.v3.components.V3Divider
import com.nedal.cinejoyremote.ui.v3.components.V3Pill

/**
 * Multi-episode download wizard.
 *
 * The season button used to queue every missing episode with the PC's default
 * source and quality — no way to choose, no sizes, no chance to skip an episode.
 * This screen turns that into three explicit steps:
 *
 *  1. which episodes (checkboxes, All/None, season picker for multi-season),
 *  2. which source ("Any" or one of the five the PC can resolve),
 *  3. the links: every episode's qualities with their real file sizes, biggest
 *     first, one choice per episode, then a single batch download.
 *
 * It reuses the shell-scoped [FetchQueueViewModel]: the same `src-info` lookups
 * the sand-clock screen runs, so all selected episodes resolve at once and the
 * results stay cached. Starting sends the same `start-download` payload the
 * source panes send, one per episode, each with its own source/quality/server.
 */
@Composable
fun V3BatchScreen(
    vm: ShowDetailViewModel,
    fetchVm: FetchQueueViewModel,
    showId: String,
    startSeason: Int,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val c = v3
    LaunchedEffect(showId) { vm.load(showId) }
    val show by vm.show.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val jobs by fetchVm.jobs.collectAsState()
    val error by fetchVm.error.collectAsState()
    val notice by fetchVm.notice.collectAsState()
    val busyKey by fetchVm.busyKey.collectAsState()

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); fetchVm.clearError() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); fetchVm.clearNotice() } }

    var step by rememberSaveable { mutableIntStateOf(1) }
    var season by rememberSaveable { mutableIntStateOf(startSeason) }
    var pickedCsv by rememberSaveable { mutableStateOf("") }
    var sourceChoice by rememberSaveable { mutableStateOf("any") }
    var chosenCsv by rememberSaveable { mutableStateOf("") }
    var queuedCount by rememberSaveable { mutableIntStateOf(0) }
    var expandedCsv by rememberSaveable { mutableStateOf("") }

    val picked = remember(pickedCsv) { pickedCsv.split('\n').filter { it.isNotBlank() }.toSet() }
    val chosen = remember(chosenCsv) {
        chosenCsv.split('\n').filter { it.contains('\t') }.associate {
            it.substringBefore('\t') to it.substringAfter('\t')
        }
    }
    val expanded = remember(expandedCsv) { expandedCsv.split('\n').filter { it.isNotBlank() }.toSet() }

    fun setPicked(next: Set<String>) { pickedCsv = next.joinToString("\n") }
    fun setChoice(key: String, offerId: String) {
        val next = chosen.toMutableMap()
        next[key] = offerId
        chosenCsv = next.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }
    fun toggleExpanded(key: String) {
        expandedCsv = if (key in expanded) (expanded - key).joinToString("\n") else (expanded + key).joinToString("\n")
    }

    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && season !in seasons) season = seasons.first()
    }
    val seasonEps = remember(episodes, season) {
        episodes.filter { it.season == season }.sortedBy { it.episode }
    }

    // Opened from a season button: start with that season's episodes ticked, so
    // the common case is "Next → Next → Download" instead of re-ticking them.
    LaunchedEffect(seasonEps) {
        if (picked.isEmpty() && seasonEps.isNotEmpty()) setPicked(seasonEps.map { it.key }.toSet())
    }

    val sourceList = if (sourceChoice == "any") FetchJob.SOURCE_KEYS else listOf(sourceChoice)
    fun offersFor(key: String): List<BatchOffer> {
        val job = jobs.firstOrNull { it.key == key } ?: return emptyList()
        return bestFirst(
            sourceList.flatMap { src -> job.sources[src]?.data?.let { batchOffers(src, it) }.orEmpty() },
        )
    }
    fun offersFor(key: String, source: String): List<BatchOffer> {
        val job = jobs.firstOrNull { it.key == key } ?: return emptyList()
        return bestFirst(job.sources[source]?.data?.let { batchOffers(source, it) }.orEmpty())
    }

    // Default each episode to its best offer as soon as links arrive, so a batch
    // is fully specified without touching every episode. A manual pick is kept.
    LaunchedEffect(jobs, sourceChoice, pickedCsv, step) {
        if (step < 3) return@LaunchedEffect
        val map = chosen.toMutableMap()
        var changed = false
        for (key in picked) {
            if (map[key] != null) continue
            val best = offersFor(key).firstOrNull() ?: continue
            map[key] = best.id
            changed = true
        }
        if (changed) chosenCsv = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    val orderedPicked = seasonEps.map { it.key }.filter { it in picked } +
        picked.filter { key -> seasonEps.none { it.key == key } }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V3Space.xs, vertical = V3Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (step in 2..3) step -= 1 else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    show?.title ?: "Download",
                    style = V3Type.sectionTitle,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (step) {
                        1 -> "Step 1 of 3 · Choose episodes"
                        2 -> "Step 2 of 3 · Choose source"
                        3 -> "Step 3 of 3 · Pick quality and download"
                        else -> "Queued on the PC"
                    },
                    style = V3Type.caption,
                    color = c.textSecondary,
                )
            }
        }

        when (step) {
            1 -> StepEpisodes(
                seasons = seasons,
                season = season,
                onSeason = { season = it },
                eps = seasonEps,
                picked = picked,
                onToggle = { key -> setPicked(if (key in picked) picked - key else picked + key) },
                onAll = { setPicked(seasonEps.map { it.key }.toSet()) },
                onNone = { setPicked(emptySet()) },
                onNext = { step = 2 },
            )

            2 -> StepSource(
                pickedCount = picked.size,
                season = season,
                source = sourceChoice,
                onSource = { sourceChoice = it; chosenCsv = "" },
                onFetch = {
                    val selected = seasonEps.filter { it.key in picked }
                    selected.forEach { ep -> show?.let { s -> fetchVm.enqueue(s, ep) } }
                    // Episodes already queued earlier keep their result; only
                    // stale links are re-resolved, so nothing is fetched twice.
                    fetchVm.refreshStale()
                    chosenCsv = ""
                    step = 3
                },
            )

            3 -> {
                val resolving = orderedPicked.count { key ->
                    jobs.firstOrNull { it.key == key }?.anyLoading == true
                }
                val readyCount = orderedPicked.count { key ->
                    jobs.firstOrNull { it.key == key }?.let { job ->
                        sourceList.any { job.sources[it]?.hasData == true }
                    } == true
                }
                StepLinks(
                    picked = orderedPicked,
                    jobs = jobs,
                    sourceList = sourceList,
                    sourceChoice = sourceChoice,
                    onSource = { sourceChoice = it; chosenCsv = "" },
                    offersFor = { key -> offersFor(key) },
                    chosen = chosen,
                    onChoose = { key, id -> setChoice(key, id) },
                    expanded = expanded,
                    onExpand = { key -> toggleExpanded(key) },
                    resolving = resolving,
                    readyCount = readyCount,
                    busyKey = busyKey,
                    onDownload = { items ->
                        fetchVm.startMany(items)
                        queuedCount = items.size
                        step = 4
                    },
                )
            }

            else -> StepDone(
                count = queuedCount,
                onDownloads = onOpenDownloads,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun StepEpisodes(
    seasons: List<Int>,
    season: Int,
    onSeason: (Int) -> Unit,
    eps: List<Episode>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onNone: () -> Unit,
    onNext: () -> Unit,
) {
    val c = v3
    Column(Modifier.fillMaxSize()) {
        if (seasons.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = V3Space.gutter, vertical = V3Space.xs),
                horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
            ) {
                seasons.forEach { s ->
                    V3Pill(
                        label = if (s <= 0) "Specials" else "Season $s",
                        selected = s == season,
                        onClick = { onSeason(s) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter, vertical = V3Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Episodes (${picked.size} of ${eps.size})",
                style = V3Type.itemTitle,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAll) { Text("All", style = V3Type.caption, color = c.activeText) }
            TextButton(onClick = onNone) { Text("None", style = V3Type.caption, color = c.textSecondary) }
        }
        V3Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(eps, key = { it.key }) { ep ->
                val on = ep.key in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(ep.key) }
                        .padding(horizontal = V3Space.md, vertical = V3Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The whole row toggles, so the box must not toggle twice.
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "E${ep.episode}" + (if (ep.name.isBlank()) "" else " · ${ep.name}"),
                            style = V3Type.body14,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when (ep.state) {
                                "done" -> "Already downloaded — a new file will be added"
                                "partial" -> "Partial download"
                                "downloading" -> "Downloading now"
                                else -> "Not downloaded yet"
                            },
                            style = V3Type.caption12,
                            color = when (ep.state) {
                                "done" -> c.success
                                "downloading" -> c.accent
                                else -> c.textTertiary
                            },
                        )
                    }
                }
            }
        }
        FooterButton(
            label = if (picked.isEmpty()) "Select at least one episode" else "Continue — choose source (${picked.size})",
            enabled = picked.isNotEmpty(),
            onClick = onNext,
        )
    }
}

@Composable
private fun StepSource(
    pickedCount: Int,
    season: Int,
    source: String,
    onSource: (String) -> Unit,
    onFetch: () -> Unit,
) {
    val c = v3
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = V3Space.gutter)) {
            item {
                Text(
                    "$pickedCount episode${if (pickedCount == 1) "" else "s"} from season $season",
                    style = V3Type.itemTitle,
                    color = c.text,
                    modifier = Modifier.padding(top = V3Space.sm),
                )
                Text(
                    "Pick where the links should come from. Sources differ: the next step lists " +
                        "every quality each one returned with its real file size, so you can take " +
                        "the best file per episode.",
                    style = V3Type.caption,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = V3Space.xs, bottom = V3Space.md),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                    SourceOption(
                        label = "Any source — best available",
                        detail = "Every source is checked and its qualities are shown together",
                        selected = source == "any",
                        onClick = { onSource("any") },
                    )
                    FetchJob.SOURCE_KEYS.forEach { key ->
                        SourceOption(
                            label = FetchJob.SOURCE_LABELS[key] ?: key,
                            detail = when (key) {
                                "cinejoy" -> "Single capture, height variants"
                                "movy" -> "Several servers, each with its own qualities"
                                "akwam" -> "Quality options with download pages"
                                "7movies" -> "Audio tracks with height ladders"
                                else -> "Provider streams with audio tracks"
                            },
                            selected = source == key,
                            onClick = { onSource(key) },
                        )
                    }
                }
            }
            item {
                Text(
                    "Links are fetched for every selected episode at the same time. " +
                        "This can take a minute on Cinejoy, which captures one episode at a time.",
                    style = V3Type.caption12,
                    color = c.textTertiary,
                    modifier = Modifier.padding(vertical = V3Space.md),
                )
            }
        }
        FooterButton(
            label = "Fetch links for $pickedCount episode${if (pickedCount == 1) "" else "s"}",
            enabled = pickedCount > 0,
            onClick = onFetch,
        )
    }
}

@Composable
private fun SourceOption(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val c = v3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V3Radius.sm))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else c.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = V3Space.md, vertical = V3Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = V3Type.body14, color = c.text)
            Text(detail, style = V3Type.caption12, color = c.textTertiary)
        }
    }
}

@Composable
private fun StepLinks(
    picked: List<String>,
    jobs: List<com.nedal.cinejoyremote.presentation.FetchJob>,
    sourceList: List<String>,
    sourceChoice: String,
    onSource: (String) -> Unit,
    offersFor: (String) -> List<BatchOffer>,
    chosen: Map<String, String>,
    onChoose: (String, String) -> Unit,
    expanded: Set<String>,
    onExpand: (String) -> Unit,
    resolving: Int,
    readyCount: Int,
    busyKey: String?,
    onDownload: (List<Pair<String, Map<String, String>>>) -> Unit,
) {
    val c = v3
    val items = picked.mapNotNull { key ->
        val offer = offersFor(key).firstOrNull { it.id == chosen[key] } ?: return@mapNotNull null
        key to offer.opts
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = V3Space.gutter)) {
            Text(
                when {
                    resolving > 0 -> "$readyCount ready · $resolving still resolving"
                    else -> "$readyCount of ${picked.size} resolved"
                },
                style = V3Type.itemTitle,
                color = c.text,
                modifier = Modifier.padding(top = V3Space.sm),
            )
            Text(
                "Qualities are real: the biggest listed is the best file that source returned. " +
                    "Pick one per episode, then download them all at once.",
                style = V3Type.caption,
                color = c.textSecondary,
                modifier = Modifier.padding(top = V3Space.xs, bottom = V3Space.sm),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(V3Space.sm),
            ) {
                V3Pill(
                    label = "All sources",
                    selected = sourceChoice == "any",
                    onClick = { onSource("any") },
                )
                FetchJob.SOURCE_KEYS.forEach { key ->
                    V3Pill(
                        label = FetchJob.SOURCE_LABELS[key] ?: key,
                        selected = sourceChoice == key,
                        onClick = { onSource(key) },
                    )
                }
            }
            Spacer(Modifier.height(V3Space.sm))
        }
        V3Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(picked, key = { it }) { key ->
                val job = jobs.firstOrNull { it.key == key }
                val offers = offersFor(key)
                val shown = if (key in expanded) offers else offers.take(4)
                EpisodeOffers(
                    key = key,
                    job = job,
                    offers = shown,
                    totalOffers = offers.size,
                    selectedId = chosen[key],
                    busy = busyKey == key,
                    expanded = key in expanded,
                    onExpand = { onExpand(key) },
                    onChoose = { id -> onChoose(key, id) },
                )
            }
        }
        FooterButton(
            label = when {
                items.isEmpty() -> "Pick a quality for at least one episode"
                else -> "Download ${items.size} episode${if (items.size == 1) "" else "s"}"
            },
            enabled = items.isNotEmpty(),
            onClick = { onDownload(items) },
        )
    }
}

@Composable
private fun EpisodeOffers(
    key: String,
    job: com.nedal.cinejoyremote.presentation.FetchJob?,
    offers: List<BatchOffer>,
    totalOffers: Int,
    selectedId: String?,
    busy: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val c = v3
    Column(
        Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
        verticalArrangement = Arrangement.spacedBy(V3Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    job?.subtitle ?: key,
                    style = V3Type.itemTitle,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        offers.isNotEmpty() -> "$totalOffers option${if (totalOffers == 1) "" else "s"}"
                        job?.anyLoading == true -> "Resolving links…"
                        else -> "No links found for this episode"
                    },
                    style = V3Type.caption12,
                    color = when {
                        offers.isNotEmpty() -> c.success
                        job?.anyLoading == true -> c.accent
                        else -> c.error
                    },
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
        }
        offers.forEach { offer ->
            val on = offer.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(V3Radius.sm))
                    .background(if (on) c.accent.copy(alpha = 0.14f) else c.surface)
                    .clickable { onChoose(offer.id) }
                    .padding(horizontal = V3Space.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = on, onClick = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        offer.sourceLabel + " · " + offer.label,
                        style = V3Type.body14,
                        color = c.text,
                    )
                    Text(
                        listOfNotNull(offer.server, offer.sizeLabel ?: "size unknown")
                            .joinToString(" · "),
                        style = V3Type.caption12,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (totalOffers > offers.size) {
            TextButton(onClick = onExpand) {
                Text(
                    if (expanded) "Show fewer" else "Show ${totalOffers - offers.size} more",
                    style = V3Type.caption,
                    color = c.activeText,
                )
            }
        }
        V3Divider()
    }
}

@Composable
private fun StepDone(count: Int, onDownloads: () -> Unit, onBack: () -> Unit) {
    val c = v3
    Column(
        Modifier.fillMaxSize().padding(V3Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(V3Space.md))
        Text(
            "$count episode${if (count == 1) "" else "s"} queued on the PC",
            style = V3Type.sectionTitle,
            color = c.text,
        )
        Text(
            "Each one keeps the source and quality chosen here. The PC downloads them in order; " +
                "progress is on the Downloads tab.",
            style = V3Type.body14,
            color = c.textSecondary,
            modifier = Modifier.padding(top = V3Space.sm),
        )
        Spacer(Modifier.height(V3Space.lg))
        Button(onClick = onDownloads, shape = RoundedCornerShape(V3Radius.sm), modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(V3Space.sm))
            Text("Open Downloads", style = V3Type.chip)
        }
        Spacer(Modifier.height(V3Space.sm))
        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(V3Radius.sm), modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text("Back to the title", style = V3Type.chip)
        }
    }
}

@Composable
private fun FooterButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = v3
    Column {
        V3Divider()
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(V3Radius.sm),
            modifier = Modifier.fillMaxWidth().padding(V3Space.gutter).height(48.dp),
        ) {
            Text(label, style = V3Type.chip, fontWeight = FontWeight.SemiBold)
        }
    }
}
