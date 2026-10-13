package com.nedal.cinejoyremote.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.update.ApkInstaller
import com.nedal.cinejoyremote.update.UpdateInfo
import com.nedal.cinejoyremote.update.UpdateResult
import com.nedal.cinejoyremote.update.checkForUpdate
import com.nedal.cinejoyremote.update.currentVersionName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object Downloading : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    fun versionName(ctx: Context): String = currentVersionName(ctx)

    fun check(ctx: Context) = viewModelScope.launch {
        _state.value = UpdateUiState.Checking
        _state.value = when (val r = checkForUpdate(ctx.applicationContext)) {
            is UpdateResult.NotConfigured -> UpdateUiState.Error("update repo not configured yet — see the pairing guide")
            is UpdateResult.UpToDate -> UpdateUiState.UpToDate
            is UpdateResult.Available -> UpdateUiState.Available(r.info)
            is UpdateResult.Failed -> UpdateUiState.Error(r.message)
        }
    }

    fun downloadAndInstall(ctx: Context, info: UpdateInfo) = viewModelScope.launch {
        if (!ApkInstaller.canInstall(ctx)) {
            ctx.startActivity(ApkInstaller.permissionIntent(ctx).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            _state.value = UpdateUiState.Error("allow \"install unknown apps\", then tap Download again")
            return@launch
        }
        _state.value = UpdateUiState.Downloading
        val result = ApkInstaller.downloadAndPrompt(ctx.applicationContext, info)
        result.exceptionOrNull()?.let {
            _state.value = UpdateUiState.Error(it.message ?: "download failed")
        }
        // On success the system install screen is showing; keep state so a
        // back-press returns to the Available card, not a stale spinner.
        if (result.isSuccess) _state.value = UpdateUiState.Available(info)
    }

    fun clear() {
        _state.value = UpdateUiState.Idle
    }
}
