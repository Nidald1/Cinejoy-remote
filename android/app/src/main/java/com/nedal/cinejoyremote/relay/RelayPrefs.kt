package com.nedal.cinejoyremote.relay

import android.content.Context
import android.content.Context.MODE_PRIVATE

/**
 * Whether this device should act as a Wake-on-LAN relay.
 *
 * Kept separate from the service so the choice survives a reboot without the
 * app being opened. A relay is meant to be a device that stays home plugged
 * in — deliberately not the phone the user carries out with them.
 */
object RelayPrefs {
    private const val FILE = "relay"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
