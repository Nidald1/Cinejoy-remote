-- CineJoy Remote v6 — Jellyfin playback mirror.
-- Run ONCE in the Supabase SQL editor (same project as schema.sql).
--
-- The PC polls Jellyfin and upserts one row per catalog item that is watched
-- or in progress. The phone subscribes for live progress bars under each
-- episode/movie. `item_key` is the same "<showId>:<season>:<episode>" key the
-- rest of the app uses, so cj_episodes never needs to be rewritten per tick.

create table if not exists cj_media_state (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  item_key text not null,
  kind text not null default 'episode',       -- movie | episode
  title text not null default '',
  show_id text not null default '',
  season integer not null default 0,
  episode integer not null default 0,
  played boolean not null default false,
  position_seconds double precision not null default 0,
  runtime_seconds double precision not null default 0,
  last_played_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id, item_key)
);

create index if not exists media_state_show_idx
  on cj_media_state (user_id, device_id, show_id);

create index if not exists media_state_device_idx
  on cj_media_state (user_id, device_id, updated_at desc);

alter table cj_media_state enable row level security;

drop policy if exists "owner all" on cj_media_state;
create policy "owner all" on cj_media_state
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Realtime publication: adding a table twice errors, so guard it.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'cj_media_state'
  ) then
    alter publication supabase_realtime add table cj_media_state;
  end if;
end $$;
