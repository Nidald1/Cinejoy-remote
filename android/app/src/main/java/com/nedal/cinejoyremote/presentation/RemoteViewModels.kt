package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.domain.MirrorRepository
import com.nedal.cinejoyremote.domain.RemoteEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    devicesVm: DevicesViewModel,
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> =
        devicesVm.selectedDeviceId.flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(emptyList()) else mirror.observeDownloads(deviceId)
        }.catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun command(deviceId: String, action: String, taskId: String? = null) = viewModelScope.launch {
        val payload = if (taskId != null) mapOf("taskId" to taskId) else emptyMap()
        val result = ServiceLocator.sendCommand(deviceId, action, payload)
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

    fun command(deviceId: String, action: String, payload: Map<String, String> = emptyMap()) =
        viewModelScope.launch {
            val result = ServiceLocator.sendCommand(deviceId, action, payload)
            result.exceptionOrNull()?.let { _error.value = it.message }
        }

    fun clearError() {
        _error.value = null
    }
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
