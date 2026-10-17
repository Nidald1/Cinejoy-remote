package com.nedal.cinejoyremote.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nedal.cinejoyremote.MainActivity
import com.nedal.cinejoyremote.domain.RemoteEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Supabase-only alerts: the PC writes a cj_events row on started/finished/
// error, Realtime delivers the insert, and this posts the local notification.
// Framework APIs only (minSdk 26) — no Firebase, no extra dependencies.
const val ALERTS_CHANNEL_ID = "cinejoy_alerts"

object EventNotifier {

    fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ALERTS_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Download alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Download started, finished, and failed alerts from your PC" },
        )
    }

    fun show(ctx: Context, event: RemoteEvent) {
        ensureChannel(ctx)
        val openApp = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tap = PendingIntent.getActivity(
            ctx,
            (event.id % Int.MAX_VALUE).toInt(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A `request` event's body is a JSON document ({"reqId":…,"title":…}),
        // not prose: posting it verbatim dumped raw JSON into the notification
        // shade. Format it into words; every other kind already carries a
        // human-readable title/body pair from the PC.
        val (contentTitle, contentText) = format(event)
        val notification = Notification.Builder(ctx, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            ?.notify((event.id % Int.MAX_VALUE).toInt(), notification)
    }

    // "request" events are parsed out of their JSON body; a payload we can't
    // parse falls back to the raw title/body rather than dropping the alert.
    private fun format(event: RemoteEvent): Pair<String, String?> {
        if (event.kind != "request") {
            return event.title.ifBlank { "CineJoy Remote" } to event.body.ifBlank { null }
        }
        val obj = runCatching {
            Json.parseToJsonElement(event.body) as? JsonObject
        }.getOrNull()
        if (obj == null) {
            return "New Seerr request" to event.body.ifBlank { null }
        }
        fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() && it != "null" }
        val title = str("title") ?: "a title"
        val year = str("year")?.take(4)
        val isTv = str("type") == "tv"
        return "New ${if (isTv) "series" else "movie"} request" to
            listOfNotNull(title, year).joinToString(" · ").plus(" — open the app to accept")
    }
}
