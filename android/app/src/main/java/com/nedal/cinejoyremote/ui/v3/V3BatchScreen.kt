package com.nedal.cinejoyremote.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.presentation.DownloadsViewModel
import com.nedal.cinejoyremote.presentation.FetchJob
import com.nedal.cinejoyremote.presentation.FetchQueueViewModel
import com.nedal.cinejoyremote.presentation.ShowDetailViewModel
import com.nedal.cinejoyremote.presentation.SubtitleLang
import com.nedal.cinejoyremote.presentation.SubtitleLangState
import com.nedal.cinejoyremote.presentation.defaultSubtitleLangs
import com.nedal.cinejoyremote.ui.v3.components.V3Divider
import com.nedal.cinejoyremote.ui.v3.components.V3Pill

/**
 * Multi-episode download wizard.
 *
 * 1. episodes (checkboxes, All/None, season picker for multi-season titles),
 * 2. source ("Any" or one of the five the PC can resolve),
 * 3. one list row per episode; tapping a row opens that episode's own source
 *    screen, where any number of offers can be ticked (the same episode can be
 *    downloaded from several sources at once). Back returns to the list, which
 *    shows what was chosen — source, quality and size — so nothing needs
 *    scrolling through every offer again,
 * 4. subtitles for the same batch (languages and provider), then download.
 *
 * After queueing it stays on screen as a live progress list: every download
 * task's state/percent/speed, plus the subtitle sweep note.
 *
 * Data comes from the shell-scoped [FetchQueueViewModel] (same `src-info`
 * lookups the sand-clock screen runs); starts use the same `start-download`
 * payload the source panes send, one per ticked offer.
 */
@Composable
fun V3BatchScreen(
    vm: ShowDetailViewModel,
    fetchVm: FetchQueueViewModel,
    downloadsVm: DownloadsViewModel,
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
    val tasks by downloadsVm.tasks.collectAsState()
    val error by fetchVm.error.collectAsState()
    val notice by fetchVm.notice.collectAsState()
    val subLangsState by vm.subLangs.collectAsState()

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); fetchVm.clearError() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); fetchVm.clearNotice() } }

    var step by rememberSaveable { mutableIntStateOf(1) }
    var season by rememberSaveable { mutableIntStateOf(startSeason) }
    var pickedCsv by rememberSaveable { mutableStateOf("") }
    var sourceChoice by rememberSaveable { mutableStateOf("any") }
    var chosenCsv by rememberSaveable { mutableStateOf("") }
    var queuedCount by rememberSaveable { mutableIntStateOf(0) }
    // Episode whose own source screen is open (blank = the episode list).
    var editingKey by rememberSaveable { mutableStateOf("") }
    // Subtitle half of the flow: one language choice for the whole batch.
    var subsOn by rememberSaveable { mutableStateOf(true) }
    var subsCsv by rememberSaveable { mutableStateOf("ar,en") }
    var subsScope by rememberSaveable { mutableStateOf("all") }
    var subsSummary by rememberSaveable { mutableStateOf("") }
    var pendingItems by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }
    var pendingKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    val picked = remember(pickedCsv) { pickedCsv.split('\n').filter { it.isNotBlank() }.toSet() }
    val chosen: Map<String, List<String>> = remember(chosenCsv) {
        chosenCsv.split('\n').filter { it.contains('\t') }.associate {
            it.substringBefore('\t') to
                it.substringAfter('\t').split(',').filter { id -> id.isNotBlank() }
        }
    }
    val subsPicked = remember(subsCsv) { subsCsv.split(',').filter { it.isNotBlank() }.toSet() }

    fun setPicked(next: Set<String>) { pickedCsv = next.joinToString("\n") }
    fun writeChosen(next: Map<String, List<String>>) {
        chosenCsv = next.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("\n") { "${it.key}\t${it.value.joinToString(",")}" }
    }
    fun toggleChoice(key: String, offerId: String) {
        val next = chosen.toMutableMap()
        val ids = next[key].orEmpty().toMutableSet()
        if (!ids.add(offerId)) ids.remove(offerId)
        if (ids.isEmpty()) next.remove(key) else next[key] = ids.toList()
        writeChosen(next)
    }
    fun setChoices(key: String, ids: List<String>) {
        val next = chosen.toMutableMap()
        if (ids.isEmpty()) next.remove(key) else next[key] = ids
        writeChosen(next)
    }

    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && season !in seasons) season = seasons.first()
    }
    val seasonEps = remember(episodes, season) {
        episodes.filter { it.season == season }.sortedBy { it.episode }
    }
    // Opened from a season button: start with that season's episodes ticked.
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

    val orderedPicked = seasonEps.map { it.key }.filter { it in picked } +
        picked.filter { key -> seasonEps.none { it.key == key } }

    // Every ticked offer becomes its own download; the same episode from two
    // sources is two files and the PC queues them separately.
    val downloadItems = orderedPicked.flatMap { key ->
        val offers = offersFor(key)
        chosen[key].orEmpty().mapNotNull { id ->
            offers.firstOrNull { it.id == id }?.let { key to it.opts }
        }
    }

    // Default each episode to its best offer when links arrive. Manual ticks are
    // kept; an episode with several ticks stays multi-source.
    LaunchedEffect(jobs, sourceChoice, pickedCsv, step) {
        if (step < 3) return@LaunchedEffect
        val map = chosen.toMutableMap()
        var changed = false
        for (key in picked) {
            if (!map[key].isNullOrEmpty()) continue
            val best = offersFor(key).firstOrNull() ?: continue
            map[key] = listOf(best.id)
            changed = true
        }
        if (changed) writeChosen(map)
    }

    // Subtitle rows describe the first selected episode; every ticked episode is
    // swept with the same language codes, each with its own lookup and files.
    val subReady = subLangsState as? SubtitleLangState.Ready
    val subRows = if (subsScope == "all") subReady?.languages.orEmpty() else subReady?.bySource?.get(subsScope).orEmpty()
    val subFirstKey = orderedPicked.firstOrNull()
    LaunchedEffect(step, subFirstKey) {
        if (step == 4 && subFirstKey != null) vm.loadSubtitleLanguages(subFirstKey)
    }
    LaunchedEffect(subReady, step) {
        if (step != 4) return@LaunchedEffect
        if (subsCsv.isBlank() && subReady != null && subReady.languages.isNotEmpty()) {
            subsCsv = defaultSubtitleLangs(subReady.languages).joinToString(",")
        }
    }

    val editing = editingKey.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V3Space.xs, vertical = V3Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    when {
                        editing != null -> editingKey = ""
                        step in 2..4 -> step -= 1
                        else -> onBack()
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        editing != null -> jobs.firstOrNull { it.key == editing }?.subtitle ?: "Choose sources"
                        else -> show?.title ?: "Download"
                    },
                    style = V3Type.sectionTitle,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        editing != null -> "Tick every source you want this episode from"
                        step == 1 -> "Step 1 of 4 · Choose episodes"
                        step == 2 -> "Step 2 of 4 · Choose source"
                        step == 3 -> "Step 3 of 4 · Sources per episode"
                        step == 4 -> "Step 4 of 4 · Subtitles"
                        else -> "Live progress"
                    },
                    style = V3Type.caption,
                    color = c.textSecondary,
                )
            }
        }

        when {
            editing != null -> EpisodePicker(
                label = jobs.firstOrNull { it.key == editing }?.subtitle ?: editing,
                offers = offersFor(editing),
                selected = chosen[editing].orEmpty().toSet(),
                loading = jobs.firstOrNull { it.key == editing }?.anyLoading == true,
                onToggle = { id -> toggleChoice(editing, id) },
                onBestPerSource = { setChoices(editing, bestPerSource(offersFor(editing)).map { it.id }) },
                onClear = { setChoices(editing, emptyList()) },
                onDone = { editingKey = "" },
            )

            step == 1 -> StepEpisodes(
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

            step == 2 -> StepSource(
                pickedCount = picked.size,
                season = season,
                source = sourceChoice,
                onSource = { sourceChoice = it; chosenCsv = "" },
                onFetch = {
                    val selected = seasonEps.filter { it.key in picked }
                    selected.forEach { ep -> show?.let { s -> fetchVm.enqueue(s, ep) } }
                    // Episodes already queued keep their result; only stale
                    // links are re-resolved, so nothing is fetched twice.
                    fetchVm.refreshStale()
                    chosenCsv = ""
                    step = 3
                },
            )

            step == 3 -> StepEpisodeList(
                picked = orderedPicked,
                jobs = jobs,
                offersFor = { key -> offersFor(key) },
                chosen = chosen,
                totalDownloads = downloadItems.size,
                onOpen = { key -> editingKey = key },
                onNext = {
                    pendingItems = downloadItems
                    pendingKeys = orderedPicked.filter { !chosen[it].isNullOrEmpty() }
                    step = 4
                },
            )

            step == 4 -> StepSubtitles(
                count = pendingKeys.size,
                state = subLangsState,
                rows = subRows,
                subsOn = subsOn,
                onSubsOn = { subsOn = it },
                picked = subsPicked,
                onToggle = { code ->
                    subsCsv = (if (code in subsPicked) subsPicked - code else subsPicked + code).joinToString(",")
                },
                onAll = { subsCsv = subRows.map { it.code }.joinToString(",") },
                onNone = { subsCsv = "" },
                scope = subsScope,
                onScope = { subsScope = it },
                onDownload = {
                    fetchVm.startMany(pendingItems)
                    if (subsOn && subsPicked.isNotEmpty()) {
                        vm.downloadSubtitles(pendingKeys, subsPicked.toList(), batchSubtitleSources(subsScope))
                        subsSummary = subsPicked.joinToString(", ") { it.uppercase() }
                    } else {
                        subsSummary = ""
                    }
                    queuedCount = pendingItems.size
                    step = 5
                },
            )

            else -> StepProgress(
                keys = pendingKeys,
                labels = orderedPicked.associateWith { key ->
                    jobs.firstOrNull { it.key == key }?.subtitle ?: key
                },
                tasks = tasks,
                queued = queuedCount,
                subs = subsSummary,
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
                    "Pick where the links should come from. Sources differ, so the next screen lists " +
                        "each episode's options with real file sizes — open an episode and tick every " +
                        "source you want it from.",
                    style = V3Type.caption,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = V3Space.xs, bottom = V3Space.md),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                    SourceOption(
                        label = "Any source — best available",
                        detail = "Every source is checked; per episode you pick what to download",
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
private fun StepEpisodeList(
    picked: List<String>,
    jobs: List<FetchJob>,
    offersFor: (String) -> List<BatchOffer>,
    chosen: Map<String, List<String>>,
    totalDownloads: Int,
    onOpen: (String) -> Unit,
    onNext: () -> Unit,
) {
    val c = v3
    val withChoice = picked.count { chosen[it].orEmpty().isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = V3Space.gutter)) {
            Text(
                "$withChoice of ${picked.size} episodes have a source",
                style = V3Type.itemTitle,
                color = c.text,
                modifier = Modifier.padding(top = V3Space.sm),
            )
            Text(
                "Tap an episode to open its sources. Tick more than one and it downloads once per " +
                    "source — each file keeps its own quality and size.",
                style = V3Type.caption,
                color = c.textSecondary,
                modifier = Modifier.padding(top = V3Space.xs, bottom = V3Space.sm),
            )
        }
        V3Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(picked, key = { it }) { key ->
                val job = jobs.firstOrNull { it.key == key }
                val offers = offersFor(key)
                val chosenOffers = offers.filter { it.id in chosen[key].orEmpty() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(key) }
                        .padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            job?.subtitle ?: key,
                            style = V3Type.itemTitle,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                offers.isNotEmpty() -> "${offers.size} options"
                                job?.anyLoading == true -> "resolving…"
                                else -> "no links"
                            },
                            style = V3Type.caption12,
                            color = when {
                                offers.isNotEmpty() -> c.textSecondary
                                job?.anyLoading == true -> c.accent
                                else -> c.error
                            },
                        )
                    }
                    Text(
                        if (chosenOffers.isEmpty()) "No source chosen — tap to pick"
                        else chosenOffers.joinToString("  +  ") {
                            "${it.sourceLabel} ${it.label} · ${it.sizeLabel ?: "size unknown"}"
                        },
                        style = V3Type.caption,
                        color = if (chosenOffers.isEmpty()) c.textTertiary else c.success,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                V3Divider()
            }
        }
        FooterButton(
            label = when {
                totalDownloads == 0 -> "Choose a source for at least one episode"
                else -> "Next — subtitles ($totalDownloads download${if (totalDownloads == 1) "" else "s"})"
            },
            enabled = totalDownloads > 0,
            onClick = onNext,
        )
    }
}

@Composable
private fun EpisodePicker(
    label: String,
    offers: List<BatchOffer>,
    selected: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onBestPerSource: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    val c = v3
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = V3Space.gutter)) {
            Text(
                label,
                style = V3Type.itemTitle,
                color = c.text,
                modifier = Modifier.padding(top = V3Space.sm),
            )
            Text(
                "Tick every source you want this episode from. Each tick is a separate download " +
                    "with its own quality and size; nothing is shared between them.",
                style = V3Type.caption,
                color = c.textSecondary,
                modifier = Modifier.padding(top = V3Space.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                TextButton(onClick = onBestPerSource, enabled = offers.isNotEmpty()) {
                    Text("Best of each source", style = V3Type.caption, color = c.activeText)
                }
                TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                    Text("Clear", style = V3Type.caption, color = c.textSecondary)
                }
            }
        }
        V3Divider()
        if (offers.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(V3Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.accent)
                    Spacer(Modifier.height(V3Space.sm))
                    Text("Resolving this episode's links…", style = V3Type.caption, color = c.textSecondary)
                } else {
                    Text("No links found for this episode.", style = V3Type.body14, color = c.error)
                    Text(
                        "Go back and try another source, or refresh from the sand-clock screen.",
                        style = V3Type.caption12,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = V3Space.xs),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(offers, key = { it.id }) { offer ->
                    val on = offer.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (on) c.accent.copy(alpha = 0.12f) else c.background)
                            .clickable { onToggle(offer.id) }
                            .padding(horizontal = V3Space.md, vertical = V3Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${offer.sourceLabel} · ${offer.label}",
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
            }
        }
        FooterButton(
            label = if (selected.isEmpty()) "Done — no source" else "Done (${selected.size} selected)",
            enabled = true,
            onClick = onDone,
        )
    }
}

// The subtitle half of the wizard: one language choice for the batch, then the
// PC sweeps each episode with its own lookup and publishes beside every video.
@Composable
private fun StepSubtitles(
    count: Int,
    state: SubtitleLangState,
    rows: List<SubtitleLang>,
    subsOn: Boolean,
    onSubsOn: (Boolean) -> Unit,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onNone: () -> Unit,
    scope: String,
    onScope: (String) -> Unit,
    onDownload: () -> Unit,
) {
    val c = v3
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = V3Space.gutter)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = V3Space.sm).clickable { onSubsOn(!subsOn) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = subsOn, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text("Also download subtitles", style = V3Type.itemTitle, color = c.text)
                        Text(
                            "For these same ${count} episode${if (count == 1) "" else "s"}, queued together with the videos.",
                            style = V3Type.caption12,
                            color = c.textTertiary,
                        )
                    }
                }
            }
            if (subsOn) {
                item {
                    Text(
                        "Every episode downloads its own subtitles. Counts below are for the first " +
                            "selected episode; the PC reports each episode separately.",
                        style = V3Type.caption12,
                        color = c.textTertiary,
                        modifier = Modifier.padding(vertical = V3Space.sm),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(V3Space.sm)) {
                        listOf("all" to "Both", "cinejoy" to "Cinejoy", "atlantic" to "Atlantic").forEach { (key, label) ->
                            V3Pill(label = label, selected = scope == key, onClick = { onScope(key) })
                        }
                    }
                    when (state) {
                        is SubtitleLangState.Loading -> Text(
                            "Asking the PC which subtitles exist…",
                            style = V3Type.caption,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = V3Space.sm),
                        )
                        is SubtitleLangState.Failed -> Text(
                            state.message,
                            style = V3Type.caption,
                            color = c.error,
                            modifier = Modifier.padding(top = V3Space.sm),
                        )
                        else -> {
                            if (rows.isEmpty()) {
                                Text(
                                    "No subtitles found for this episode on ${if (scope == "cinejoy") "Cinejoy" else if (scope == "atlantic") "Atlantic" else "Cinejoy or Atlantic"}.",
                                    style = V3Type.caption,
                                    color = c.textTertiary,
                                    modifier = Modifier.padding(top = V3Space.sm),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(V3Space.xs)) {
                                TextButton(onClick = onAll) { Text("All", style = V3Type.caption, color = c.activeText) }
                                TextButton(onClick = onNone) { Text("None", style = V3Type.caption, color = c.textSecondary) }
                            }
                        }
                    }
                }
                items(rows, key = { it.code }) { lang ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(lang.code) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = lang.code in picked, onCheckedChange = null)
                        Text(
                            if (lang.name.isBlank()) lang.code else lang.name,
                            style = V3Type.body14,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            listOfNotNull(
                                "${lang.count} available".takeIf { lang.count > 0 },
                                "${lang.existing} downloaded".takeIf { lang.existing > 0 },
                            ).joinToString(" · ").ifBlank { "none yet" },
                            style = V3Type.caption12,
                            color = c.textTertiary,
                        )
                    }
                }
            }
        }
        FooterButton(
            label = when {
                !subsOn -> "Download $count episode${if (count == 1) "" else "s"}"
                picked.isEmpty() -> "Pick at least one subtitle language"
                else -> "Download $count episode${if (count == 1) "" else "s"} + subtitles"
            },
            enabled = !subsOn || picked.isNotEmpty(),
            onClick = onDownload,
        )
    }
}

@Composable
private fun StepProgress(
    keys: List<String>,
    labels: Map<String, String>,
    tasks: List<DownloadTask>,
    queued: Int,
    subs: String,
    onDownloads: () -> Unit,
    onBack: () -> Unit,
) {
    val c = v3
    val mine = tasks.filter { it.episodeKey in keys }
    val done = mine.count { it.state == "done" }
    val errors = mine.count { it.state == "error" }
    val active = mine.count { it.state in listOf("queued", "resolving", "downloading", "stopping") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = V3Space.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(V3Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$queued download${if (queued == 1) "" else "s"} queued",
                        style = V3Type.itemTitle,
                        color = c.text,
                    )
                    Text(
                        buildList {
                            if (active > 0) add("$active active")
                            if (done > 0) add("$done done")
                            if (errors > 0) add("$errors failed")
                            if (isEmpty()) add("waiting for the PC")
                        }.joinToString(" · "),
                        style = V3Type.caption,
                        color = if (errors > 0) c.error else c.textSecondary,
                    )
                }
            }
            if (subs.isNotBlank()) {
                Text(
                    "Subtitles ($subs) are sweeping on the PC for these episodes — the Activity tab " +
                        "reports when they finish and they are published next to every video.",
                    style = V3Type.caption12,
                    color = c.success,
                    modifier = Modifier.padding(top = V3Space.xs),
                )
            }
            Spacer(Modifier.height(V3Space.sm))
        }
        V3Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(keys, key = { "p-$it" }) { key ->
                val epTasks = mine.filter { it.episodeKey == key }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter, vertical = V3Space.sm),
                    verticalArrangement = Arrangement.spacedBy(V3Space.xs),
                ) {
                    Text(
                        labels[key] ?: key,
                        style = V3Type.itemTitle,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (epTasks.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = c.accent)
                            Spacer(Modifier.width(V3Space.sm))
                            Text("Waiting for the PC…", style = V3Type.caption12, color = c.textTertiary)
                        }
                    }
                    epTasks.forEach { task -> TaskRow(task) }
                }
                V3Divider()
            }
        }
        Column {
            V3Divider()
            Button(
                onClick = onDownloads,
                shape = RoundedCornerShape(V3Radius.sm),
                modifier = Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter).padding(top = V3Space.gutter).height(46.dp),
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(V3Space.sm))
                Text("Open Downloads", style = V3Type.chip)
            }
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(V3Radius.sm),
                modifier = Modifier.fillMaxWidth().padding(horizontal = V3Space.gutter).padding(vertical = V3Space.sm).height(46.dp),
            ) {
                Text("Back to the title", style = V3Type.chip)
            }
        }
    }
}

@Composable
private fun TaskRow(task: DownloadTask) {
    val c = v3
    val accent = when (task.state) {
        "done" -> c.success
        "error" -> c.error
        "queued", "resolving", "downloading", "stopping" -> c.accent
        else -> c.textTertiary
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    task.source?.uppercase() ?: "PC",
                    task.quality ?: "auto",
                ).joinToString(" · "),
                style = V3Type.caption,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                task.state.replaceFirstChar { it.uppercase() } +
                    (task.percent?.let { " · ${"%.0f".format(it)}%" } ?: ""),
                style = V3Type.caption12,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            buildList {
                val dl = fmtBytes(task.downloaded)
                val total = fmtBytes(task.total)
                if (dl != null && total != null) add("$dl of $total") else total?.let { add(it) }
                task.speed?.let { add("${fmtBytes(it)}/s") }
                task.eta?.let { add(it) }
            }.joinToString(" · ").ifBlank { "starting…" },
            style = V3Type.caption12,
            color = c.textTertiary,
        )
        task.percent?.let { pct ->
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = accent,
                trackColor = c.surface,
            )
        }
        task.lastErr?.takeIf { task.state == "error" }?.let {
            Text(it, style = V3Type.caption12, color = c.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// "all" means both subtitle providers, the same scope the title picker sends.
private fun batchSubtitleSources(scope: String): List<String> = when (scope) {
    "cinejoy" -> listOf("cinejoy")
    "atlantic" -> listOf("atlantic")
    else -> listOf("cinejoy", "atlantic")
}

private fun bestPerSource(offers: List<BatchOffer>): List<BatchOffer> {
    val seen = mutableSetOf<String>()
    return bestFirst(offers).filter { seen.add(it.source) }
}

private fun fmtBytes(bytes: Long?): String? {
    val n = bytes ?: return null
    return when {
        n >= (1L shl 30) -> "%.1f GB".format(n / (1L shl 30).toDouble())
        n >= (1L shl 20) -> "%.0f MB".format(n / (1L shl 20).toDouble())
        n >= (1L shl 10) -> "${n / (1L shl 10)} KB"
        else -> "$n B"
    }
}

@Composable
private fun FooterButton(label: String, enabled: Boolean, onClick: () -> Unit) {
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
