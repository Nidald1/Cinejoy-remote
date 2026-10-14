-- CineJoy Remote v4 — connection health + Wake-on-LAN.
-- Run ONCE in the Supabase SQL editor (same project as schema.sql).
--
-- cj_devices gains the network identity the phone needs to wake the PC:
--   mac    adapter MAC address (magic-packet target)
--   ip     last known IPv4 address
--   bcast  broadcast address derived from the PC's IP + netmask
-- The PC heartbeats these every minute, so the phone can read the last known
-- values precisely when the PC is asleep/off — the only time they matter.

alter table cj_devices add column if not exists mac text;
alter table cj_devices add column if not exists ip text;
alter table cj_devices add column if not exists bcast text;
