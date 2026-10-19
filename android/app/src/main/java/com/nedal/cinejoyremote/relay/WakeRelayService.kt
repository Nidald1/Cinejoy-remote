package com.nedal.cinejoyremote.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nedal.cinejoyremote.MainActivity
import com.nedal.cinejoyremote.core.SupabaseProvider
import com.nedal.cinejoyremote.core.WolSender
import com.nedal.cinejoyremote.data.DeviceDto
import com.nedal.cinejoyremote.data.WakeRequestDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The always-on half of remote Wake-on-LAN.
 *
 * A magic packet is a LAN broadcast, so it can only originate from a device
 * that is on the network. When the user is outside, that is not the phone —
 * and the home gateway is behind CGNAT, so there is no inbound path either.
 * This service turns any device that stays home (a plugged-in phone, a Pi)
 * into the sender: it watches cj_wake_requests and broadcasts locally.
 *
 * "sent" means the packet left this device. A sleeping PC cannot answer, so
 * nothing here can promise the PC came up — callers word it that way.
 */
class WakeRelayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val claimMutex = Mutex()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Watching for wake requests"))
        // PARTIAL_WAKE_LOCK: without it the CPU may doze between polls and
        // the relay misses the window a woken PC needs its packet in.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cinejoy:relay").apply {
            setReferenceCounted(false)
            acquire()
        }
        scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        val client = SupabaseProvider.client
        while (scope.isActive) {
            try {
                val userId = client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    updateNotification("Sign in on this device to relay wake requests")
                    delay(30_000)
                    continue
                }
                // Requests from any of the user's PCs, newest first. A relay
                // device is not paired to one PC — it serves whichever one was
                // asked to wake.
                val pending = client.from("cj_wake_requests").select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "pending")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(5)
                }.decodeList<WakeRequestDto>()

                if (pending.isEmpty()) {
                    updateNotification("Watching for wake requests")
                } else {
                    for (req in pending) handle(userId, req)
                }
            } catch (t: Throwable) {
                // A missing table (migration-v5 not run) or a network blip
                // must not kill the relay — report and keep polling.
                updateNotification("Relay error: ${t.message?.take(60) ?: "unknown"}")
            }
            delay(POLL_MS)
        }
    }

    private suspend fun handle(userId: String, req: WakeRequestDto) = claimMutex.withLock {
        val client = SupabaseProvider.client
        // Claim before sending so two relays on the same LAN cannot both
        // broadcast for one request.
        val claimed = client.from("cj_wake_requests").update(
            mapOf("status" to "claimed")
        ) {
            filter {
                eq("id", req.id)
                eq("status", "pending")
            }
            select()
        }.decodeList<WakeRequestDto>()
        if (claimed.isEmpty()) return // another relay got there first

        val device = client.from("cj_devices").select {
            filter {
                eq("user_id", userId)
                eq("device_id", req.deviceId)
            }
        }.decodeList<DeviceDto>().firstOrNull()

        val mac = device?.mac
        if (mac.isNullOrBlank()) {
            // Nothing to send to — the PC has never mirrored its identity.
            finish(req.id, "no-mac")
            return
        }
        val targets = listOfNotNull(device.bcast, "255.255.255.255")
        val sent = runCatching { WolSender.send(mac, targets) }
        finish(req.id, if (sent.isSuccess) "sent" else "failed")
        updateNotification(
            if (sent.isSuccess) "Sent wake packet to $mac"
            else "Wake packet failed: ${sent.exceptionOrNull()?.message}"
        )
    }

    private suspend fun finish(id: Long, status: String) {
        runCatching {
            SupabaseProvider.client.from("cj_wake_requests").update(
                mapOf("status" to status, "handled_at" to java.time.OffsetDateTime.now().toString())
            ) { filter { eq("id", id) } }
        }
    }

    // ---------------------------------------------------------------- ui
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wake relay", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Keeps this device able to wake your PC" }
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Wake relay")
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "wake_relay"
        private const val NOTIFICATION_ID = 4213
        private const val POLL_MS = 5_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            if (_running.value) return
            _running.value = true
            val intent = Intent(context, WakeRelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            _running.value = false
            context.stopService(Intent(context, WakeRelayService::class.java))
        }
    }
}
