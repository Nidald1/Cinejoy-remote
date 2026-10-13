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
        modelClass.isAssignableFrom(EventsViewModel::class.java) ->
            EventsViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(ShowDetailViewModel::class.java) ->
            ShowDetailViewModel(devicesVm = devicesVm) as T
        modelClass.isAssignableFrom(PcToolsViewModel::class.java) ->
            PcToolsViewModel(devicesVm = devicesVm) as T
        else -> throw IllegalArgumentException("unknown ${modelClass.name}")
    }
}
