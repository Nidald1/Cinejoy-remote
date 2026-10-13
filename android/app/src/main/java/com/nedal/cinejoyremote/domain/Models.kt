package com.nedal.cinejoyremote.domain

// Pure Kotlin domain models. The UI never sees DTOs or DB rows.

data class Device(
    val deviceId: String,
    val name: String,
    val appVersion: String?,
    val lastSeen: String?,
)

data class DownloadTask(
    val taskId: String,
    val episodeKey: String,
    val title: String,
    val episodeLabel: String,
    val state: String,
    val percent: Double?,
    val downloaded: Long?,
    val total: Long?,
    val speed: Long?,
    val eta: String?,
    val source: String?,
    val server: String?,
    val quality: String?,
    val lastErr: String?,
)

data class LibraryShow(
    val showId: String,
    val type: String,
    val title: String,
    val year: String?,
    val episodes: Int,
)

data class RemoteEvent(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: String,
)

sealed interface AuthState {
    data object Loading : AuthState
    data class SignedIn(val email: String?) : AuthState
    data object SignedOut : AuthState
}
