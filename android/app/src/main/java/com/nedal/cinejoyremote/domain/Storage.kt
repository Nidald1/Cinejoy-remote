package com.nedal.cinejoyremote.domain

import kotlinx.serialization.Serializable

// Payload of the PC's "storage-scan" command. Field names match src/storage.js
// exactly; every field is defaulted so an older PC that does not know a key
// yet still decodes.
@Serializable
data class StorageVolume(
    val path: String = "",
    val total: Long = 0,
    val free: Long = 0,
    val used: Long = 0,
)

@Serializable
data class StorageRoot(
    val kind: String = "",
    val path: String = "",
    val bytes: Long = 0,
    val files: Long = 0,
    val error: String? = null,
)

@Serializable
data class StorageFileEntry(
    val path: String = "",
    val name: String = "",
    val size: Long = 0,
    val watched: Boolean = false,
    val positionSeconds: Double = 0.0,
    val runtimeSeconds: Double = 0.0,
    val lastPlayedAt: String? = null,
)

@Serializable
data class StorageMovie(
    val title: String = "",
    val path: String = "",
    val bytes: Long = 0,
    val watched: Boolean = false,
    val positionSeconds: Double = 0.0,
    val runtimeSeconds: Double = 0.0,
    val lastPlayedAt: String? = null,
    val deletePaths: List<String> = emptyList(),
    val files: List<StorageFileEntry> = emptyList(),
)

@Serializable
data class StorageEpisode(
    val season: Int = 0,
    val episode: Int = 0,
    val name: String = "",
    val bytes: Long = 0,
    val watched: Boolean = false,
    val positionSeconds: Double = 0.0,
    val runtimeSeconds: Double = 0.0,
    val lastPlayedAt: String? = null,
    val deletePaths: List<String> = emptyList(),
    val files: List<StorageFileEntry> = emptyList(),
)

@Serializable
data class StorageSeason(
    val season: Int = 0,
    val path: String = "",
    val bytes: Long = 0,
    val watched: Boolean = false,
    val deletePaths: List<String> = emptyList(),
    val episodes: List<StorageEpisode> = emptyList(),
)

@Serializable
data class StorageShow(
    val title: String = "",
    val path: String = "",
    val bytes: Long = 0,
    val seasons: List<StorageSeason> = emptyList(),
)

@Serializable
data class StorageLargeFile(
    val path: String = "",
    val name: String = "",
    val size: Long = 0,
    val parent: String = "",
)

@Serializable
data class StorageSuggestion(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val subtitle: String = "",
    val bytes: Long = 0,
    val count: Int = 0,
    val paths: List<String> = emptyList(),
)

@Serializable
data class StorageTotals(
    val bytes: Long = 0,
    val movies: Long = 0,
    val series: Long = 0,
    val files: Long = 0,
)

@Serializable
data class StorageJunk(
    val bytes: Long = 0,
    val count: Int = 0,
)

@Serializable
data class StorageJellyfin(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val server: String = "",
    val user: String = "",
    val error: String? = null,
    val syncedAt: String? = null,
)

@Serializable
data class StorageSnapshot(
    val scannedAt: String = "",
    val roots: List<StorageRoot> = emptyList(),
    val volumes: List<StorageVolume> = emptyList(),
    val totals: StorageTotals = StorageTotals(),
    val movies: List<StorageMovie> = emptyList(),
    val shows: List<StorageShow> = emptyList(),
    val largeFiles: List<StorageLargeFile> = emptyList(),
    val suggestions: List<StorageSuggestion> = emptyList(),
    val junk: StorageJunk = StorageJunk(),
    val jellyfin: StorageJellyfin = StorageJellyfin(),
    val warnings: List<String> = emptyList(),
)
