package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.domain.MirrorRepository
import com.nedal.cinejoyremote.domain.RemoteEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class DevicesViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
) : ViewModel() {
    val devices: StateFlow<List<Device>> =
        mirror.observeDevices()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selectedDeviceId: StateFlow<String?> = _selected

    fun select(deviceId: String) {
        _selected.value = deviceId
    }

    fun autoSelect(devices: List<Device>) {
        if (_selected.value == null && devices.isNotEmpty()) _selected.value = devices.first().deviceId
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    private val devicesVm: DevicesViewModel,
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> =
        devicesVm.selectedDeviceId.flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(emptyList()) else mirror.observeDownloads(deviceId)
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // showId -> poster URL, joined client-side from the library mirror so
    // download cards can show real posters before the v3 mirror columns exist
    // (episode_key is "showId:season:episode").
    val posters: StateFlow<Map<String, String>> =
        devicesVm.selectedDeviceId.flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(emptyMap()) else mirror.observeLibrary(deviceId)
                .map { shows -> shows.mapNotNull { s -> s.poster?.let { s.showId to it } }.toMap() }
        }.catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun command(deviceId: String, action: String, taskId: String? = null) = viewModelScope.launch {
        val payload = if (taskId != null) mapOf("taskId" to taskId) else emptyMap()
        val result = ServiceLocator.sendCommand(deviceId, action, payload)
        result.exceptionOrNull()?.let { _error.value = it.message }
    }

    // Pause/resume the whole queue on the PC (parity with the desktop toolbar).
    fun queuePause(paused: Boolean) = viewModelScope.launch {
        val d = devicesVm.selectedDeviceId.value ?: return@launch
        val result = ServiceLocator.sendCommand(d, "queue-pause", mapOf("paused" to paused.toString()))
        result.exceptionOrNull()?.let { _error.value = it.message }
    }

    fun clearError() {
        _error.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    devicesVm: DevicesViewModel,
) : ViewModel() {
    val shows: StateFlow<List<LibraryShow>> =
        devicesVm.selectedDeviceId.flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(emptyList()) else mirror.observeLibrary(deviceId)
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    // Set when the PC confirms a catalog-add, so the UI can open the show.
    private val _addedShowId = MutableStateFlow<String?>(null)
    val addedShowId: StateFlow<String?> = _addedShowId

    fun command(deviceId: String, action: String, payload: Map<String, String> = emptyMap()) =
        viewModelScope.launch {
            val result = ServiceLocator.sendCommand(deviceId, action, payload)
            result.exceptionOrNull()?.let { _error.value = it.message }
        }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun clearNotice() { _notice.value = null }

    // "Refresh" button: ask the PC to re-mirror library + episodes + downloads
    // immediately; the polling flows pick the new rows up within a second.
    fun refresh(deviceId: String) = viewModelScope.launch {
        _refreshing.value = true
        try {
            val r = ServiceLocator.mirrorRepository.sendCommandForResult(
                deviceId, "mirror-refresh", emptyMap(), timeoutMs = 15_000,
            )
            val err = r.exceptionOrNull()
            if (err != null) _error.value = err.message
            else _notice.value = "Library refreshed"
        } finally {
            _refreshing.value = false
        }
    }

    // Paste-a-link flow: waits for the PC's verdict so failures surface
    // instead of silently doing nothing.
    fun addShow(deviceId: String, url: String) = viewModelScope.launch {
        _adding.value = true
        _error.value = null
        try {
            val r = mirror.sendCommandForResult(
                deviceId, "catalog-add", mapOf("url" to url.trim()), timeoutMs = 60_000,
            )
            val err = r.exceptionOrNull()
            if (err != null) {
                _error.value = err.message
            } else {
                _addedShowId.value = r.getOrNull()?.get("showId")?.jsonPrimitive?.contentOrNull
            }
        } finally {
            _adding.value = false
        }
    }

    fun clearAdded() {
        _addedShowId.value = null
    }

    fun clearError() {
        _error.value = null
    }
}

// What the source picker shows while the PC resolves servers/qualities.
sealed interface SourceInfoState {
    data object Idle : SourceInfoState
    data object Loading : SourceInfoState
    data class Ready(val source: String, val data: JsonObject) : SourceInfoState
    data class Failed(val message: String) : SourceInfoState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShowDetailViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    private val devicesVm: DevicesViewModel,
) : ViewModel() {
    private val _showId = MutableStateFlow<String?>(null)
    val showId: StateFlow<String?> = _showId

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _sourceInfo = MutableStateFlow<SourceInfoState>(SourceInfoState.Idle)
    val sourceInfo: StateFlow<SourceInfoState> = _sourceInfo

    fun load(showId: String) {
        if (_showId.value != showId) {
            _showId.value = showId
            _sourceInfo.value = SourceInfoState.Idle
        }
    }

    val episodes: StateFlow<List<Episode>> =
        combine(devicesVm.selectedDeviceId, _showId) { d, s -> d to s }
            .flatMapLatest { (d, s) ->
                if (d == null || s == null) flowOf(emptyList()) else mirror.observeEpisodes(d, s)
            }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val show: StateFlow<LibraryShow?> =
        combine(devicesVm.selectedDeviceId, _showId) { d, s -> d to s }
            .flatMapLatest { (d, s) ->
                if (d == null || s == null) flowOf(null)
                else mirror.observeLibrary(d).map { list -> list.find { it.showId == s } }
            }.catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun exec(
        action: String,
        payload: Map<String, String> = emptyMap(),
        timeoutMs: Long = 45_000,
    ): Result<JsonObject> {
        val d = devicesVm.selectedDeviceId.value
            ?: return Result.failure(IllegalStateException("no PC selected"))
        _busy.value = action
        try {
            return mirror.sendCommandForResult(d, action, payload, timeoutMs)
        } finally {
            _busy.value = null
        }
    }

    private fun op(action: String, payload: Map<String, String> = emptyMap(), success: String? = null) =
        viewModelScope.launch {
            val r = exec(action, payload)
            val err = r.exceptionOrNull()
            if (err != null) _error.value = err.message
            else success?.let { _notice.value = it }
        }

    fun queueMissing() = op("queue-missing", mapOf("showId" to (_showId.value ?: "")), "Queueing missing episodes")
    fun queueSeason(season: Int) = op("queue-season", mapOf("showId" to (_showId.value ?: ""), "season" to season.toString()), "Queueing season $season")
    fun removeFromPc() = op("catalog-remove", mapOf("showId" to (_showId.value ?: "")), "Removed from the PC")
    fun watchedToggle(key: String) = op("watched-toggle", mapOf("key" to key))

    // Subtitle sweeps on the PC can run for minutes (hundreds of files), so
    // this is fire-and-forget: the PC posts an Activity event when it ends
    // and the episode row refreshes with the new subtitle counts.
    fun subsDownload(key: String) = viewModelScope.launch {
        val d = devicesVm.selectedDeviceId.value ?: return@launch
        val r = ServiceLocator.sendCommand(d, "subs-download", mapOf("key" to key, "langs" to "ar,en"))
        val err = r.exceptionOrNull()
        if (err != null) _error.value = err.message
        else _notice.value = "Subtitle download started — you'll get an Activity alert when it's done"
    }
    fun subsPublish(key: String) = op("subs-publish", mapOf("key" to key), "Subtitles published next to the video")
    fun subsPublishAll() = op("subs-publish-all", success = "All subtitles published")
    fun subsClear(key: String) = op("subs-clear", mapOf("key" to key, "lang" to "ar"), "Arabic subtitles cleared")
    fun deleteEpisode(key: String) = op("ep-delete", mapOf("key" to key), "Files deleted")

    fun queueDownload(key: String, opts: Map<String, String> = emptyMap()) = viewModelScope.launch {
        val r = exec("start-download", opts + mapOf("key" to key))
        val err = r.exceptionOrNull()
        if (err != null) _error.value = err.message
        else {
            _notice.value = "Queued on the PC"
            _sourceInfo.value = SourceInfoState.Idle
        }
    }

    // Source picker data for one source (cinejoy | movy | akwam). The PC can
    // take up to a minute to resolve every server, hence the long timeout.
    fun loadSource(key: String, source: String) = viewModelScope.launch {
        _sourceInfo.value = SourceInfoState.Loading
        // Cinejoy captures alone can burn ~80s on a bad title; 120s keeps the
        // phone from giving up while the PC is still resolving.
        val r = exec("src-info", mapOf("key" to key, "source" to source), timeoutMs = 120_000)
        val err = r.exceptionOrNull()
        _sourceInfo.value = if (err != null) {
            SourceInfoState.Failed(err.message ?: "could not reach the PC")
        } else {
            SourceInfoState.Ready(source, r.getOrNull() ?: JsonObject(emptyMap()))
        }
    }

    fun clearSourceInfo() {
        _sourceInfo.value = SourceInfoState.Idle
    }

    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }
}

// PC-side maintenance tools surfaced in Settings: log tail, engine restart and
// Seerr inbox scan — the same executors the desktop app's buttons use.
class PcToolsViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    private val devicesVm: DevicesViewModel,
) : ViewModel() {
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private fun op(
        action: String,
        payload: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000,
        onResult: ((JsonObject) -> Unit)? = null,
        success: String? = null,
    ) = viewModelScope.launch {
        val d = devicesVm.selectedDeviceId.value
        if (d == null) {
            _error.value = "No PC selected"
            return@launch
        }
        _busy.value = action
        try {
            val r = mirror.sendCommandForResult(d, action, payload, timeoutMs)
            val err = r.exceptionOrNull()
            if (err != null) {
                _error.value = err.message
            } else {
                r.getOrNull()?.let { res -> onResult?.invoke(res) }
                success?.let { _notice.value = it }
            }
        } finally {
            _busy.value = null
        }
    }

    fun fetchLog() = op("get-log", timeoutMs = 20_000, onResult = { res ->
        _log.value = res["lines"]?.jsonPrimitive?.contentOrNull ?: "(no log output)"
    })

    fun restartEngine() = op("engine-restart", timeoutMs = 60_000, success = "Engine restarted")
    fun scanSeerr() = op("seerr-scan", timeoutMs = 60_000, success = "Seerr inbox scanned")

    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }
    fun closeLog() { _log.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModel(
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
    devicesVm: DevicesViewModel,
) : ViewModel() {
    val events: StateFlow<List<RemoteEvent>> =
        devicesVm.selectedDeviceId.flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(emptyList()) else mirror.observeEvents(deviceId)
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
