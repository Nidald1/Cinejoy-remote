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
    val poster: String? = null,
    val overview: String? = null,
    val addedAt: String? = null,
)

data class Episode(
    val key: String,
    val showId: String,
    val season: Int,
    val episode: Int,
    val name: String,
    val still: String? = null,
    val airDate: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val state: String = "missing", // done | partial | downloading | missing
    val downloaded: Long? = null,
    val total: Long? = null,
    val watched: Boolean = false,
    val subs: Int = 0,
    val arSubs: Int = 0,
    val pubAr: Int = 0,
    val needsPub: Boolean = false,
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
