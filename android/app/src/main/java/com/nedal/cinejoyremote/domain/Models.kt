package com.nedal.cinejoyremote.domain

// Pure Kotlin domain models. The UI never sees DTOs or DB rows.

data class Device(
    val deviceId: String,
    val name: String,
    val appVersion: String?,
    val lastSeen: String?,
    // v4 mirror columns (migration-v4-wake.sql): the PC's last known network
    // identity, mirrored on every heartbeat so the phone can wake it while it
    // is asleep — the only moment these values are needed.
    val mac: String? = null,
    val ip: String? = null,
    val bcast: String? = null,
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
    val poster: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
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
    val tmdb: Long? = null, // TMDb id from the PC mirror — enriches the Netflix header
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

// A pending Seerr request mirrored by the PC (Notifications screen). The PC
// holds the request until this phone accepts (download + approve in Seerr) or
// rejects (decline in Seerr, so the requester can ask again).
data class SeerrRequest(
    val rowId: Long,
    val reqId: String,
    val type: String, // movie | tv
    val title: String,
    val year: String?,
    val seasons: String?, // "S1, S3" for series
    val poster: String?,
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
