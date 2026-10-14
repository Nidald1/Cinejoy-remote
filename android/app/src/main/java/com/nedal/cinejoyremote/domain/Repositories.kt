package com.nedal.cinejoyremote.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String): Result<Boolean>
    suspend fun signOut()
}

// Phone polls the PC's mirror (v1). The interface is Flow-based so a
// Realtime implementation can replace polling without touching callers.
interface MirrorRepository {
    fun observeDevices(): Flow<List<Device>>
    // One-shot read of one PC's mirror row: the connection check and
    // Wake-on-LAN need the last known state now, not on the next poll tick.
    suspend fun fetchDevice(deviceId: String): Result<Device?>
    fun observeDownloads(deviceId: String): Flow<List<DownloadTask>>
    fun observeLibrary(deviceId: String): Flow<List<LibraryShow>>
    fun observeEvents(deviceId: String): Flow<List<RemoteEvent>>
    // Per-episode mirror for one show: file sizes, state, watched, subtitles.
    fun observeEpisodes(deviceId: String, showId: String): Flow<List<Episode>>
    suspend fun sendCommand(deviceId: String, action: String, payload: Map<String, String> = emptyMap()): Result<Unit>
    // Fire a command and wait for the PC's result (source picker, add-link,
    // subtitle ops). Times out after [timeoutMs] with a descriptive error.
    suspend fun sendCommandForResult(
        deviceId: String,
        action: String,
        payload: Map<String, String> = emptyMap(),
        timeoutMs: Long = 45_000,
    ): Result<JsonObject>
    // Live inserts on cj_events for this device (Supabase Realtime).
    // Powers start/finish/error notifications; no polling, no Firebase.
    fun observeEventInserts(deviceId: String): Flow<RemoteEvent>
}

class SignInUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = auth.signIn(email, password)
}

class SignUpUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = auth.signUp(email, password)
}

class SendCommandUseCase(private val mirror: MirrorRepository) {
    suspend operator fun invoke(deviceId: String, action: String, payload: Map<String, String> = emptyMap()) =
        mirror.sendCommand(deviceId, action, payload)
}
