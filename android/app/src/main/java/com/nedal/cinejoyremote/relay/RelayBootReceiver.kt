package com.nedal.cinejoyremote.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the wake relay after a reboot.
 *
 * A relay is only useful if it comes back on its own: the entire point is that
 * it stays on the network while the user is not, and phones reboot on their
 * own — system updates, battery drain, crashes. Without this the relay would
 * silently stop working the first time the phone restarted, and the failure
 * would only surface as "wake did nothing" the next time the user is away.
 */
class RelayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!RelayPrefs.enabled(context)) return
        ContextCompat.startForegroundService(context, Intent(context, WakeRelayService::class.java))
    }
}
