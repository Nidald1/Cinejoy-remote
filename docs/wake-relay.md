# Remote Wake-on-LAN

Wake your PC from anywhere, including outside your home network.

## Why a relay is required

A Wake-on-LAN magic packet is a **broadcast on the local network**. It can only
be sent by a device that is on that network.

- **On Wi-Fi at home:** the phone is on the LAN, so it sends the packet itself.
  This has always worked.
- **On mobile data:** the packet has nowhere to go. Worse, the home gateway is
  behind carrier-grade NAT (Umniah; the gateway's WAN address is a private
  `10.x` one), so there is no inbound path at all — port forwarding cannot
  help, and the carrier drops every unsolicited packet. Verified empirically:
  ten external probe nodes all timed out on both TCP and UDP.

So some device has to *stay on the LAN* and send the packet on the user's
behalf. That is the **relay**.

The Archer AX23 cannot be it. It is in Access Point mode, which strips the
router feature set — no WOL in its web UI or Tether, and its TMP port (20002)
is refused. The Nokia gateway that does the NAT has no WOL either.

## How it works

```
phone (anywhere)          Supabase              relay device (at home)      PC
      │  POST cj_wake_requests   │                       │                  │
      │ ───────────────────────► │  poll every 5s        │                  │
      │                          │ ────────────────────► │  magic packet    │
      │                          │                       │ ───────────────► │ wakes
```

1. **Wake PC** in Settings writes a row to `cj_wake_requests`. It also sends a
   magic packet directly, which is instant when that phone is on the same LAN.
   Both paths are harmless together: a magic packet is idempotent.
2. **The relay** (`WakeRelayService`) polls the table every 5 seconds, claims a
   pending row so two relays cannot both send, reads the PC's MAC and broadcast
   address from `cj_devices`, and broadcasts the packet.
3. The row is marked `sent`. That means the packet left the relay — **not** that
   the PC woke. A sleeping PC cannot answer, so nothing can confirm the second
   half, and the UI says so.

## Setting it up

1. Run `supabase/migration-v5-wake-relay.sql` once in the Supabase SQL editor.
   (`migration-v4-wake.sql` must already have run — it is what stores the PC's
   MAC and broadcast address.)
2. Install this app on a device that stays home and stays plugged in — an old
   phone is ideal. Sign in with the same account.
3. Settings → **Wake relay** → **Turn on relay**.
4. Verify: Settings → **Wake PC**. The relay's notification changes to
   "Sent wake packet to <mac>", and the PC wakes within a few seconds.

## Requirements on the PC

Already configured on this machine, listed so the failure modes are checkable:

- NIC: **Wake on Magic Packet** enabled
- NIC: **Shutdown Wake-On-Lan** enabled (wakes from S5 as well as sleep)
- Sleep state must be **S3**, not Modern Standby — S0ix disables WOL
- **Fast Startup** must be off, or shutdown behaves like hibernation
- The PC app must be signed in, so `cj_devices` carries a current MAC

## Battery and reliability

The relay holds a partial wake lock and shows a minimal ongoing notification —
required by Android for a foreground service, and the reason the relay survives
doze. It restarts itself after a reboot (`RelayBootReceiver`) and after an app
update, so the device stays a relay without the app ever being opened.

Android may still kill a backgrounded service. On the rooted POCO X3 Pro, also
disable battery optimisation for CineJoy Remote (Settings → Apps → CineJoy
Remote → Battery → Unrestricted) and enable any manufacturer "autostart" /
"no restrictions" option. Xiaomi and POCO devices are aggressive about killing
background apps; this is the single most common cause of a relay silently
going dark.
