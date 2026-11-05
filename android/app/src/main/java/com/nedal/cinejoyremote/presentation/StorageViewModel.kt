package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.domain.MirrorRepository
import com.nedal.cinejoyremote.domain.StorageSnapshot
import com.nedal.cinejoyremote.presentation.ServiceLocator.mirrorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface StorageUiState {
    data object Loading : StorageUiState
    data class Ready(val snapshot: StorageSnapshot) : StorageUiState
    data class Error(val message: String) : StorageUiState
}

/**
 * Drives the Storage screen. The phone owns no files: every scan and delete is
 * a command to the PC, which validates paths against D:\Movies / D:\Series and
 * moves them to the Recycle Bin (never a permanent delete).
 */
class StorageViewModel(
    private val mirror: MirrorRepository = mirrorRepository,
    private val devicesVm: DevicesViewModel,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow<StorageUiState>(StorageUiState.Loading)
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun load(force: Boolean = false) {
        if (_busy.value || _deleting.value) return
        val device = devicesVm.selectedDeviceId.value
        if (device == null) {
            _state.value = StorageUiState.Error("Select a PC first")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            if (_state.value !is StorageUiState.Ready) _state.value = StorageUiState.Loading
            val result = mirror.sendCommandForResult(
                device,
                "storage-scan",
                mapOf("force" to force.toString()),
                timeoutMs = 150_000,
            )
            result.onSuccess { payload ->
                val snap = runCatching {
                    json.decodeFromString<StorageSnapshot>(payload.toString())
                }.getOrNull()
                _state.value = if (snap == null) {
                    StorageUiState.Error("The PC sent an unreadable storage report")
                } else {
                    StorageUiState.Ready(snap)
                }
            }.onFailure {
                _state.value = StorageUiState.Error(it.message ?: "Could not read the PC's disks")
            }
            _busy.value = false
        }
    }

    fun delete(paths: List<String>) {
        if (paths.isEmpty() || _deleting.value) return
        val device = devicesVm.selectedDeviceId.value
        if (device == null) {
            _notice.value = "Select a PC first"
            return
        }
        viewModelScope.launch {
            _deleting.value = true
            val result = mirror.sendCommandForResult(
                device,
                "storage-delete",
                mapOf("paths" to paths.joinToString("\n")),
                timeoutMs = 150_000,
            )
            result.onSuccess { out ->
                val deleted = out["deleted"]?.jsonPrimitive?.intOrNull ?: 0
                _notice.value = if (deleted == 1) {
                    "Moved 1 item to the Recycle Bin"
                } else {
                    "Moved $deleted items to the Recycle Bin"
                }
            }.onFailure {
                _notice.value = it.message ?: "Delete failed on the PC"
            }
            _deleting.value = false
            if (result.isSuccess) load(force = true)
        }
    }
}
