package com.nedal.cinejoyremote.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// Wake-on-LAN magic packet: 6 × 0xFF followed by the target MAC repeated 16
// times, broadcast to the PC's subnet on the discard/diagnostic ports (9 and
// 7 — routers and NICs accept either).
//
// One send cannot be acknowledged: a sleeping PC does not answer, so success
// here means "packet left the phone", never "PC is up". The caller says so.
object WolSender {
    const val PORT = 9
    private const val FALLBACK_PORT = 7

    fun packet(mac: String): ByteArray {
        val clean = mac.lowercase().replace(Regex("[^0-9a-f]"), "")
        require(clean.length == 12) { "bad MAC address: $mac" }
        val bytes = ByteArray(12)
        for (i in 0 until 12) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (rep in 0 until 16) for (i in 0 until 6) payload[6 + rep * 6 + i] = bytes[i]
        return payload
    }

    // Sends to every non-blank target; an unreachable one is skipped so a stale
    // broadcast address never blocks the valid one.
    fun send(mac: String, targets: List<String>, port: Int = PORT) {
        val payload = packet(mac)
        var sent = 0
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (host in targets.filter { it.isNotBlank() }) {
                val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: continue
                for (p in if (port == PORT) intArrayOf(port, FALLBACK_PORT) else intArrayOf(port)) {
                    runCatching {
                        socket.send(DatagramPacket(payload, payload.size, addr, p))
                        sent++
                    }
                }
            }
        }
        check(sent > 0) { "no wake packet could be sent (targets: ${targets.joinToString()})" }
    }
}
