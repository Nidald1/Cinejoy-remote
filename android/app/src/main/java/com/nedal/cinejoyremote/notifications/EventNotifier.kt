package com.nedal.cinejoyremote.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nedal.cinejoyremote.MainActivity
import com.nedal.cinejoyremote.domain.RemoteEvent

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
        val notification = Notification.Builder(ctx, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(event.title.ifBlank { "CineJoy Remote" })
            .setContentText(event.body.ifBlank { null })
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            ?.notify((event.id % Int.MAX_VALUE).toInt(), notification)
    }
}
