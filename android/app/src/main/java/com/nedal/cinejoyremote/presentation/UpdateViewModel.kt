package com.nedal.cinejoyremote.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.update.ApkInstaller
import com.nedal.cinejoyremote.update.UpdateInfo
import com.nedal.cinejoyremote.update.UpdateResult
import com.nedal.cinejoyremote.update.checkForUpdate
import com.nedal.cinejoyremote.update.currentVersionName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo) : UpdateUiState
    data class Ready(val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    // The launch auto-check surfaces the popup; Settings drives the state
    // machine directly. "seen" remembers which versions the popup already
    // showed for so a launch check never nags twice about the same release.
    private val _popupVisible = MutableStateFlow(false)
    val popupVisible: StateFlow<Boolean> = _popupVisible

    private var downloadJob: Job? = null

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

    // Silent launch check: only surfaces the popup when a newer version
    // exists AND the user hasn't dismissed this exact version before.
    fun autoCheck(ctx: Context) = viewModelScope.launch {
        if (_state.value != UpdateUiState.Idle) return@launch
        when (val r = checkForUpdate(ctx.applicationContext)) {
            is UpdateResult.Available -> {
                _state.value = UpdateUiState.Available(r.info)
                if (!wasDismissed(ctx, r.info.version)) _popupVisible.value = true
            }
            is UpdateResult.UpToDate -> _state.value = UpdateUiState.UpToDate
            else -> { /* silent on launch — Settings shows real errors */ }
        }
    }

    fun dismissPopup(ctx: Context, version: String) {
        prefs(ctx).edit().putString(KEY_DISMISSED, version).apply()
        _popupVisible.value = false
    }

    fun hidePopup() {
        _popupVisible.value = false
    }

    fun showPopup() {
        _popupVisible.value = true
    }

    fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        val app = ctx.applicationContext
        if (!ApkInstaller.canInstall(app)) {
            runCatching {
                app.startActivity(ApkInstaller.permissionIntent(app).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            _state.value = UpdateUiState.NeedsPermission(info)
            return
        }
        downloadJob?.cancel()
        _state.value = UpdateUiState.Downloading(info, null)
        downloadJob = viewModelScope.launch {
            val result = ApkInstaller.downloadAndPrompt(app, info) { fraction ->
                _state.value = UpdateUiState.Downloading(info, fraction)
            }
            result.exceptionOrNull()?.let {
                _state.value = UpdateUiState.Error(it.message ?: "download failed")
            }
            // Success: the system install sheet is showing on top of us.
            if (result.isSuccess) _state.value = UpdateUiState.Ready(info)
        }
    }

    fun cancelDownload(info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateUiState.Available(info)
    }

    fun clear() {
        _state.value = UpdateUiState.Idle
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("cinejoy_updates", Context.MODE_PRIVATE)

    private fun wasDismissed(ctx: Context, version: String): Boolean =
        prefs(ctx).getString(KEY_DISMISSED, "") == version

    private companion object {
        const val KEY_DISMISSED = "dismissed_version"
    }
}
