package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// Shares one DevicesViewModel across screens so device selection sticks.
class AppViewModelFactory(private val devicesVm: DevicesViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DownloadsViewModel::class.java) ->
            DownloadsViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(RequestsViewModel::class.java) ->
            RequestsViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(ShowDetailViewModel::class.java) ->
            ShowDetailViewModel(devicesVm = devicesVm) as T
        // Shell-scoped: owns the multi-title source-fetch queue, so it must
        // outlive any single screen (created once in V3MainShell).
        modelClass.isAssignableFrom(FetchQueueViewModel::class.java) ->
            FetchQueueViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(PcToolsViewModel::class.java) ->
            PcToolsViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(ConnectionViewModel::class.java) ->
            ConnectionViewModel(devicesVm = devicesVm) as T
        else -> throw IllegalArgumentException("unknown ${modelClass.name}")
    }
}
