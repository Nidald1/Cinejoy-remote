package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.core.SpeedSample
import com.nedal.cinejoyremote.core.SpeedTester
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.domain.MirrorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Fetch state for ONE (episode, source) pair.
 *
 * [data] is kept even while a refresh is in flight ([loading] = true), so the
 * screen can keep showing the last resolved servers/qualities instead of
 * dropping back to a spinner — links go stale every few minutes and the user
 * asked to still see what they have while a fresh lookup runs.
 */
data class SourceSlot(
    val data: JsonObject? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long = 0L,
) {
    val hasData get() = data != null
}

/**
 * Result of one speed probe, per source or per server (see
 * [FetchQueueViewModel.speed] for the key scheme).
 */
data class SpeedTest(
    val running: Boolean = false,
    val bytesPerSec: Long? = null,
    val ttfbMs: Long? = null,
    val pingOnly: Boolean = false,
    val failed: Boolean = false,
    val at: Long = 0L,
)

/** One queued episode/movie plus the per-source slots for the 3 sources. */
data class FetchJob(
    val key: String,
    val showId: String,
    val title: String,
    val subtitle: String,
    val still: String?,
    val isMovie: Boolean,
    val sources: Map<String, SourceSlot> = SOURCE_KEYS.associateWith { SourceSlot() },
    val queued: Boolean = false, // a start-download command was accepted by the PC
) {
    val anyLoading get() = SOURCE_KEYS.any { sources[it]?.loading == true }
    val anyData get() = SOURCE_KEYS.any { sources[it]?.hasData == true }
    val readyCount get() = SOURCE_KEYS.count { sources[it]?.hasData == true }

    companion object {
        val SOURCE_KEYS = listOf("cinejoy", "movy", "akwam")
    }
}

/**
 * App-shell-scoped queue for "get me the links for these titles".
 *
 * Why this exists: the old source picker lived in a bottom sheet backed by
 * [ShowDetailViewModel], which is scoped to ONE detail screen. Dismissing the
 * sheet or navigating away threw away an in-flight Cinejoy capture (up to
 * 120s), and its single `_sourceInfo` slot meant switching source threw away
 * the previous source's result — going back to Cinejoy re-captured from
 * scratch. This VM is created once at the shell (see V3MainShell) so fetching
 * continues while the user browses anywhere else, and every (episode, source)
 * pair keeps its own slot, so all 3 sources are visible together and cached.
 *
 * NOTHING about the PC-side fetch process changes: the same `src-info`
 * command, the same payload, the same 120s deadline, and the same
 * `start-download` payload are sent through the same mirror. What this class
 * adds is purely scheduling and bookkeeping on the phone:
 *
 *  - dedupe: one in-flight command per (episode, source) — a second tap joins
 *    the running lookup instead of firing a duplicate at the PC;
 *  - a Cinejoy lane (Semaphore(1)): the PC serializes captures anyway
 *    (`captureExclusive` in its engine — "one request at a time"), so firing
 *    N Cinejoy lookups at once would leave the last one waiting behind N-1
 *    captures and blow past the 120s phone deadline. Holding them here means
 *    each command's 120s only starts when the PC is actually ready for it.
 *    Movy/AKWAM are plain HTTP sweeps on the PC, so they use a wider lane.
 *  - refresh: results are re-fetched when older than [SOURCE_TTL_MS], which
 *    matches the PC's own 2-minute capture cache / 10-minute movy sweep.
 */
class FetchQueueViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    private val devicesVm: DevicesViewModel,
) : ViewModel() {

    private val _jobs = MutableStateFlow<List<FetchJob>>(emptyList())
    val jobs: StateFlow<List<FetchJob>> = _jobs

    private val _busyKey = MutableStateFlow<String?>(null) // job whose start-download is in flight
    val busyKey: StateFlow<String?> = _busyKey

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** Jobs with at least one source still resolving — drives the top-bar badge. */
    val activeCount: StateFlow<Int> = _jobs.map { list -> list.count { it.anyLoading } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Jobs that have at least one resolved source and were not started yet. */
    val availableCount: StateFlow<Int> = _jobs.map { list ->
        list.count { !it.queued && it.anyData }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // One Cinejoy capture at a time — mirrors the PC's own capture chain so a
    // deep queue can't starve any single command's 120s deadline.
    private val cineLane = Semaphore(1)
    private val webLane = Semaphore(3) // movy + akwam are concurrent HTTP on the PC

    // keys of in-flight commands, e.g. "show:1|s2e3|cinejoy"
    private val inFlight = mutableSetOf<String>()

    /** Queue an episode/movie and start resolving all 3 sources. Idempotent. */
    fun enqueue(show: LibraryShow, ep: Episode) = viewModelScope.launch {
        val title = show.title
        val subtitle = if (show.type == "movie") {
            ep.name.ifBlank { "Full movie" }
        } else {
            "S%02dE%02d".format(ep.season, ep.episode) +
                if (ep.name.isBlank()) "" else " · ${ep.name}"
        }
        _jobs.update { list ->
            if (list.any { it.key == ep.key }) return@update list
            listOf(
                FetchJob(
                    key = ep.key,
                    showId = show.showId,
                    title = title,
                    subtitle = subtitle,
                    still = ep.still ?: show.poster,
                    isMovie = show.type == "movie",
                ),
            ) + list // newest first
        }
        FetchJob.SOURCE_KEYS.forEach { source -> resolve(ep.key, source) }
    }

    fun remove(key: String) {
        _jobs.update { list -> list.filterNot { it.key == key } }
    }

    fun clearFinished() {
        _jobs.update { list -> list.filterNot { it.queued } }
    }

    /**
     * Re-fetch a single source. Fired by the per-source Refresh button and by
     * the screen on entry when a result is older than [SOURCE_TTL_MS].
     */
    fun refresh(key: String, source: String) = resolve(key, source, force = true)

    /**
     * Refresh every stale source of every job. Called when the fetch screen
     * becomes visible: links expire every few minutes, so what the user is
     * looking at should be current, but a fresh copy must never blank out the
     * one already on screen ([SourceSlot.data] is kept while loading).
     */
    fun refreshStale() {
        val now = System.currentTimeMillis()
        _jobs.value.forEach { job ->
            if (job.queued) return@forEach
            FetchJob.SOURCE_KEYS.forEach { source ->
                val slot = job.sources[source] ?: return@forEach
                val ttl = if (source == "cinejoy") CINE_TTL_MS else SOURCE_TTL_MS
                if (slot.error == null && now - slot.fetchedAt > ttl) refresh(job.key, source)
            }
        }
    }

    /** Same `start-download` command and payload the old picker sent. */
    fun startDownload(key: String, opts: Map<String, String>) = viewModelScope.launch {
        val d = devicesVm.selectedDeviceId.value
        if (d == null) {
            _error.value = "No PC selected"
            return@launch
        }
        _busyKey.value = key
        try {
            val r = mirror.sendCommandForResult(
                d, "start-download", opts + mapOf("key" to key), timeoutMs = 45_000,
            )
            val err = r.exceptionOrNull()
            if (err != null) {
                _error.value = err.message
            } else {
                _notice.value = "Queued on the PC"
                _jobs.update { list -> list.map { if (it.key == key) it.copy(queued = true) else it } }
            }
        } finally {
            _busyKey.value = null
        }
    }

    fun openShow(key: String): String? = _jobs.value.find { it.key == key }?.showId

    // ---------------------------------------------------------------- speed tests

    /**
     * Live speed results, keyed: "$jobKey|$source" for a whole source,
     * "$jobKey|$source|$server" for one server/entry under it. Tests hit only
     * URLs the fetch response already carries — the fetch process itself is
     * not involved, this is a side-by-side read of the same links.
     */
    private val _speed = MutableStateFlow<Map<String, SpeedTest>>(emptyMap())
    val speed: StateFlow<Map<String, SpeedTest>> = _speed

    private val speedLane = Semaphore(4) // at most 4 probes in flight
    private val speedInFlight = mutableSetOf<String>()

    /** Test one URL under [id]; a second tap while running joins the probe. */
    fun speedTest(id: String, url: String) {
        synchronized(speedInFlight) { if (!speedInFlight.add(id)) return }
        _speed.update { it + (id to SpeedTest(running = true)) }
        viewModelScope.launch {
            try {
                speedLane.withPermit {
                    val sample = withContext(Dispatchers.IO) { SpeedTester.measure(url) }.getOrElse {
                        _speed.update { m -> m + (id to SpeedTest(failed = true, at = System.currentTimeMillis())) }
                        return@withPermit
                    }
                    _speed.update { m ->
                        m + (id to SpeedTest(
                            bytesPerSec = sample.bytesPerSec,
                            ttfbMs = sample.ttfbMs,
                            pingOnly = sample.pingOnly,
                            at = System.currentTimeMillis(),
                        ))
                    }
                }
            } finally {
                synchronized(speedInFlight) { speedInFlight.remove(id) }
            }
        }
    }

    /**
     * Fan out across every server/entry one source returned: movy's servers
     * each get their own probe, akwam's quality entries too, and cinejoy
     * (single capture) is just its master stream. Fired by the source row's
     * ⚡ button; per-server ⚡ buttons re-probe a single URL.
     */
    fun speedTestSource(key: String, source: String) {
        val data = _jobs.value.findJob(key)?.sources?.get(source)?.data ?: return
        speedTargets(key, source, data).forEach { (id, url) -> speedTest(id, url) }
    }

    private fun speedTargets(key: String, source: String, data: JsonObject): List<Pair<String, String>> {
        fun http(o: JsonObject, k: String): String? =
            (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }
        return when (source) {
            "cinejoy" -> listOfNotNull(http(data, "url")?.let { "$key|$source" to it })
            "movy" -> ((data["servers"] as? JsonArray) ?: return emptyList()).mapNotNull { el ->
                val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val srv = (o["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val url = ((o["sources"] as? JsonArray) ?: return@mapNotNull null)
                    .firstNotNullOfOrNull { s -> (s as? JsonObject)?.let { http(it, "url") } }
                    ?: return@mapNotNull null
                "$key|$source|$srv" to url
            }
            "akwam" -> ((data["entries"] as? JsonArray) ?: return emptyList()).mapIndexedNotNull { i, el ->
                val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
                val url = http(o, "url") ?: http(o, "downloadPage") ?: return@mapIndexedNotNull null
                "$key|$source|$i" to url
            }
            else -> emptyList()
        }
    }

    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }

    // ------------------------------------------------------------------ internals

    private fun patch(key: String, source: String, slot: SourceSlot) {
        _jobs.update { list ->
            list.map { job ->
                if (job.key != key) job
                else job.copy(sources = job.sources + (source to slot))
            }
        }
    }

    /**
     * The one place a `src-info` command is issued. Identical action/payload/
     * timeout to the old picker — only the queueing around it is new.
     */
    private fun resolve(key: String, source: String, force: Boolean = false) {
        val id = "$key|$source"
        synchronized(inFlight) {
            if (!inFlight.add(id)) return // already running: join it, don't double-fire
        }
        // Mark loading NOW, not after the lane hands out a permit: a job queued
        // behind 3 Cinejoy captures is still working, and the row would
        // otherwise read "idle" for minutes. The last good copy is kept so the
        // screen never blanks a result the user is already looking at.
        val prev = _jobs.value.findJob(key)?.sources?.get(source)
        patch(key, source, SourceSlot(loading = true, data = prev?.data, error = null, fetchedAt = prev?.fetchedAt ?: 0L))
        val lane = if (source == "cinejoy") cineLane else webLane
        viewModelScope.launch {
            try {
                lane.withPermit { runLookup(key, source) }
            } finally {
                synchronized(inFlight) { inFlight.remove(id) }
            }
        }
    }

    private suspend fun runLookup(key: String, source: String) {
        val d = devicesVm.selectedDeviceId.value
        if (d == null) {
            val prev = _jobs.value.findJob(key)?.sources?.get(source)
            patch(key, source, SourceSlot(data = prev?.data, error = "No PC selected", fetchedAt = prev?.fetchedAt ?: 0L))
            return
        }
        // Cinejoy captures alone can burn ~80s on a bad title; 120s keeps the
        // phone from giving up while the PC is still resolving. Unchanged.
        val r = mirror.sendCommandForResult(
            d, "src-info", mapOf("key" to key, "source" to source), timeoutMs = 120_000,
        )
        val err = r.exceptionOrNull()
        if (err != null) {
            val prev = _jobs.value.findJob(key)?.sources?.get(source)
            patch(
                key, source,
                SourceSlot(
                    data = prev?.data, // keep the last good copy on failure too
                    error = err.message ?: "could not reach the PC",
                    fetchedAt = prev?.fetchedAt ?: 0L,
                ),
            )
        } else {
            patch(
                key, source,
                SourceSlot(
                    data = r.getOrNull() ?: JsonObject(emptyMap()),
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun List<FetchJob>.findJob(key: String) = firstOrNull { it.key == key }

    companion object {
        // The PC caches a Cinejoy capture for 2 minutes and a movy sweep for 10;
        // re-asking sooner is free, much later costs a full resolve.
        const val CINE_TTL_MS = 2 * 60_000L
        const val SOURCE_TTL_MS = 5 * 60_000L
    }
}
