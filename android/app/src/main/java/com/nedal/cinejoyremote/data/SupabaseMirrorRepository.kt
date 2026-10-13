package com.nedal.cinejoyremote.data

import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.domain.Episode
import com.nedal.cinejoyremote.domain.LibraryShow
import com.nedal.cinejoyremote.domain.MirrorRepository
import com.nedal.cinejoyremote.domain.RemoteEvent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val POLL_MS = 1500L

// v1 uses polling flows. Realtime channels can replace poll() internals later
// without touching ViewModels (see blueprint Step 5).
class SupabaseMirrorRepository(private val client: SupabaseClient) : MirrorRepository {

    private fun userId(): String =
        client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("not signed in")

    private fun <T> poll(query: suspend () -> T): Flow<T> = flow {
        while (true) {
            try {
                emit(query())
            } catch (_: Exception) {
                // Transient network loss: keep the last UI state, retry next tick.
            }
            delay(POLL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override fun observeDevices(): Flow<List<Device>> = poll {
        client.from("cj_devices").select {
            filter { eq("user_id", userId()) }
            order("last_seen", Order.DESCENDING)
        }.decodeList<DeviceDto>().map { it.toDomain() }
    }

    override fun observeDownloads(deviceId: String): Flow<List<DownloadTask>> = poll {
        client.from("cj_download_state").select {
            filter {
                eq("user_id", userId())
                eq("device_id", deviceId)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList<DownloadTaskDto>().map { it.toDomain() }
    }

    override fun observeLibrary(deviceId: String): Flow<List<LibraryShow>> = poll {
        client.from("cj_library").select {
            filter {
                eq("user_id", userId())
                eq("device_id", deviceId)
            }
            order("title", Order.ASCENDING)
        }.decodeList<LibraryShowDto>().map { it.toDomain() }
    }

    override fun observeEpisodes(deviceId: String, showId: String): Flow<List<Episode>> = poll {
        client.from("cj_episodes").select {
            filter {
                eq("user_id", userId())
                eq("device_id", deviceId)
                eq("show_id", showId)
            }
            order("season", Order.ASCENDING)
            order("episode", Order.ASCENDING)
        }.decodeList<EpisodeDto>().map { it.toDomain() }
    }

    override fun observeEvents(deviceId: String): Flow<List<RemoteEvent>> = poll {
        client.from("cj_events").select {
            filter {
                eq("user_id", userId())
                eq("device_id", deviceId)
            }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<RemoteEventDto>().map { it.toDomain() }
    }

    // Realtime INSERT stream on cj_events for this device. Server-side filter
    // is user-scoped (RLS enforces it); device + kind filter client-side so a
    // second PC's events never alert. Emits only new rows — no backfill, so a
    // resubscribe can't replay old alerts.
    override fun observeEventInserts(deviceId: String): Flow<RemoteEvent> = channelFlow {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("not signed in")
        val realtimeChannel = client.channel("cj-events-$deviceId")
        val inserts = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "cj_events"
            filter = "user_id=eq.$userId"
        }
        realtimeChannel.subscribe()
        try {
            inserts.collect { insert ->
                val record = insert.record
                if (record["device_id"]?.jsonPrimitive?.contentOrNull != deviceId) return@collect
                val kind = record["kind"]?.jsonPrimitive?.contentOrNull ?: return@collect
                if (kind != "started" && kind != "finished" && kind != "error") return@collect
                send(
                    RemoteEvent(
                        id = record["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                        kind = kind,
                        title = record["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        body = record["body"]?.jsonPrimitive?.contentOrNull ?: "",
                        createdAt = record["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                    ),
                )
            }
        } finally {
            runCatching { realtimeChannel.unsubscribe() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sendCommand(
        deviceId: String,
        action: String,
        payload: Map<String, String>,
    ): Result<Unit> = runCatching {
        client.from("cj_commands").insert(
            CommandInsertDto(
                userId = userId(),
                deviceId = deviceId,
                action = action,
                payload = payload.toPayloadJson(),
            )
        )
    }

    override suspend fun sendCommandForResult(
        deviceId: String,
        action: String,
        payload: Map<String, String>,
        timeoutMs: Long,
    ): Result<JsonObject> = runCatching {
        val inserted = client.from("cj_commands").insert(
            CommandInsertDto(
                userId = userId(),
                deviceId = deviceId,
                action = action,
                payload = payload.toPayloadJson(),
            )
        ) {
            select()
        }.decodeSingle<CommandRowDto>()

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(700)
            val rows = client.from("cj_commands").select {
                filter { eq("id", inserted.id) }
            }.decodeList<CommandRowDto>()
            val row = rows.firstOrNull() ?: continue
            when (row.status) {
                "done" -> {
                    val res = row.result ?: return@runCatching JsonObject(emptyMap())
                    val ok = (res["ok"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
                    if (ok == false) {
                        val msg = (res["error"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?: "command failed"
                        throw IllegalStateException(msg)
                    }
                    return@runCatching res
                }
                "error" -> {
                    val res = row.result
                    val msg = ((res?.get("error") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull)
                        ?: "command failed on the PC"
                    throw IllegalStateException(msg)
                }
            }
            // still pending/running — keep polling
        }
        throw IllegalStateException("PC did not answer in time — is the app running?")
    }
}
