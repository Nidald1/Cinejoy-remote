-- CineJoy Remote v5 — Wake-on-LAN relay.
-- Run ONCE in the Supabase SQL editor (after migration-v4-wake.sql).
--
-- Why this table exists: Wake-on-LAN magic packets are a LAN broadcast, so
-- they can only be sent by a device that is ON the network. The phone is not
-- when the user is outside it, and the home gateway is behind CGNAT (no
-- inbound path at all), so a packet sent from the phone on mobile data dies
-- at the carrier.
--
-- The fix is a relay: any always-on device on the LAN (a phone left plugged
-- in, a Raspberry Pi) runs the CineJoy app's relay poller, which watches this
-- table and fires the magic packet locally when a request appears. The row is
-- the request; cj_devices still holds the mac/broadcast address to send to,
-- so the request never carries network identity that could go stale.

create table if not exists cj_wake_requests (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  -- Who asked. Purely informational: it tells the operator reading the table
  -- (or a relay log) which phone fired the wake.
  requested_by text not null default '',
  -- pending  -> relay has not picked it up yet
  -- sent     -> relay broadcast the magic packet (NOT proof the PC woke; a
  --             sleeping PC cannot answer, so "sent" is as far as certainty
  --             goes and the UI words it that way)
  -- claimed  -> a relay is working on it (avoids two relays double-sending)
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  handled_at timestamptz
);
create index if not exists wake_requests_pending_idx
  on cj_wake_requests (status, created_at desc);

alter table cj_wake_requests enable row level security;

drop policy if exists "owner all" on cj_wake_requests;
create policy "owner all" on cj_wake_requests
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Realtime so a relay can react in well under a second instead of waiting out
-- its polling interval.
alter publication supabase_realtime add table cj_wake_requests;
