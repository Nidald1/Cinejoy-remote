package com.nedal.cinejoyremote.data

import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.domain.RemoteEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class DeviceDto(
    @SerialName("device_id") val deviceId: String,
    val name: String = "",
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
)

@Serializable
data class DownloadTaskDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("episode_key") val episodeKey: String = "",
    val title: String = "",
    @SerialName("episode_label") val episodeLabel: String = "",
    val state: String = "queued",
    val percent: Double? = null,
    val downloaded: Long? = null,
    val total: Long? = null,
    val speed: Long? = null,
    val eta: String? = null,
    val source: String? = null,
    val server: String? = null,
    val quality: String? = null,
    @SerialName("last_err") val lastErr: String? = null,
)

@Serializable
data class LibraryShowDto(
    @SerialName("show_id") val showId: String,
    val type: String = "tv",
    val title: String = "",
    val year: String? = null,
    val episodes: Int = 0,
    val poster: String? = null,
    val overview: String? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("episode_key") val episodeKey: String,
    @SerialName("show_id") val showId: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val name: String = "",
    val still: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val state: String = "missing",
    val downloaded: Long? = null,
    val total: Long? = null,
    val watched: Boolean = false,
    val subs: Int = 0,
    @SerialName("ar_subs") val arSubs: Int = 0,
    @SerialName("pub_ar") val pubAr: Int = 0,
    @SerialName("needs_pub") val needsPub: Boolean = false,
)

@Serializable
data class RemoteEventDto(
    val id: Long = 0,
    val kind: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CommandInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    val action: String,
    val payload: JsonObject = buildJsonObject {},
)

@Serializable
data class CommandRowDto(
    val id: Long = 0,
    val status: String = "pending",
    val result: JsonObject? = null,
)

fun Map<String, String>.toPayloadJson(): JsonObject = buildJsonObject {
    for ((k, v) in this@toPayloadJson) put(k, JsonPrimitive(v))
}

fun DeviceDto.toDomain() = Device(deviceId, name, appVersion, lastSeen)
fun DownloadTaskDto.toDomain() = DownloadTask(
    taskId, episodeKey, title, episodeLabel, state, percent,
    downloaded, total, speed, eta, source, server, quality, lastErr,
)
fun LibraryShowDto.toDomain() = LibraryShow(showId, type, title, year, episodes, poster, overview)
fun EpisodeDto.toDomain() = Episode(
    key = episodeKey, showId = showId, season = season, episode = episode,
    name = name, still = still, airDate = airDate, rating = rating,
    runtime = runtime, state = state, downloaded = downloaded, total = total,
    watched = watched, subs = subs, arSubs = arSubs, pubAr = pubAr,
    needsPub = needsPub,
)
fun RemoteEventDto.toDomain() = RemoteEvent(id, kind, title, body, createdAt)
