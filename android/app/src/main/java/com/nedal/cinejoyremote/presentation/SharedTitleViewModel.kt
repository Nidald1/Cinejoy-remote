package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nedal.cinejoyremote.domain.MirrorRepository
import com.nedal.cinejoyremote.share.SharedTitle
import com.nedal.cinejoyremote.share.TmdbFetcher
import com.nedal.cinejoyremote.share.TmdbHint
import com.nedal.cinejoyremote.share.TmdbRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface SharedTitleState {
    data object Loading : SharedTitleState
    data class Error(val message: String) : SharedTitleState
    data class Ready(val title: SharedTitle) : SharedTitleState
}

enum class SharedAction { LIBRARY, DOWNLOAD }

class SharedTitleViewModel(
    // Null for library rows without a tmdb id: the hint below resolves it by
    // search first, so the screen behaves identically either way.
    private val ref: TmdbRef?,
    private val devicesVm: DevicesViewModel,
    private val hint: TmdbHint? = null,
    private val mirror: MirrorRepository = ServiceLocator.mirrorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SharedTitleState>(SharedTitleState.Loading)
    val state: StateFlow<SharedTitleState> = _state.asStateFlow()

    private val _busy = MutableStateFlow<SharedAction?>(null)
    val busy: StateFlow<SharedAction?> = _busy.asStateFlow()

    // One-line result shown as a snackbar by the screen, then cleared.
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // The ref that actions (download / add to library) actually run against:
    // the constructor ref when given, or the one resolved by search from a hint.
    private var activeRef: TmdbRef? = null

    init {
        viewModelScope.launch {
            val resolved = ref ?: hint?.let { TmdbFetcher.searchRef(it) }
            activeRef = resolved
            _state.value = if (resolved == null) {
                SharedTitleState.Error("not found on TMDb")
            } else {
                TmdbFetcher.fetch(resolved).fold(
                    onSuccess = { SharedTitleState.Ready(it) },
                    onFailure = { SharedTitleState.Error(it.message ?: "network error") },
                )
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun addToLibrary() = run(SharedAction.LIBRARY)

    fun download() = run(SharedAction.DOWNLOAD)

    private fun run(action: SharedAction) = viewModelScope.launch {
        val deviceId = devicesVm.selectedDeviceId.value
        if (deviceId == null) {
            _message.value = "No PC selected"
            return@launch
        }
        _busy.value = action
        try {
            val url = activeRef?.url
            if (url == null) {
                _message.value = "Title not found on TMDb"
                return@launch
            }
            // Both actions start by adding the title to the PC's library, exactly
            // like the paste-a-link flow does.
            val added = mirror.sendCommandForResult(
                deviceId, "catalog-add", mapOf("url" to url), timeoutMs = 60_000,
            )
            if (added.isFailure) {
                _message.value = added.exceptionOrNull()?.message ?: "could not add to library"
                return@launch
            }
            if (action == SharedAction.LIBRARY) {
                _message.value = "Added to library"
                return@launch
            }
            val showId = added.getOrNull()?.get("showId")?.jsonPrimitive?.contentOrNull
            if (showId == null) {
                _message.value = "PC did not return a show id"
                return@launch
            }
            val queued = mirror.sendCommandForResult(
                deviceId, "queue-missing", mapOf("showId" to showId), timeoutMs = 60_000,
            )
            _message.value = if (queued.isSuccess) "Added to download queue"
            else queued.exceptionOrNull()?.message ?: "could not start download"
        } finally {
            _busy.value = null
        }
    }

    companion object {
        fun factory(
            ref: TmdbRef?,
            devicesVm: DevicesViewModel,
            hint: TmdbHint? = null,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SharedTitleViewModel(ref, devicesVm, hint) }
            }
    }
}
