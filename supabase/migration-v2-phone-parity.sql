-- CineJoy Remote v2 — phone parity migration.
-- Run ONCE in the Supabase SQL editor (same project as schema.sql).
--
-- 1) cj_library gains poster/overview so the phone can show TMDB artwork.
-- 2) cj_episodes mirrors every show's episode list (files, sizes, state,
--    watched, subtitle status) so the phone can open a show and see details.

alter table cj_library add column if not exists poster text;
alter table cj_library add column if not exists overview text;

create table if not exists cj_episodes (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  show_id text not null,
  episode_key text not null,
  season integer not null default 0,
  episode integer not null default 0,
  name text not null default '',
  still text,
  air_date text,
  rating real,
  runtime integer,
  state text not null default 'missing',   -- done | partial | downloading | missing
  downloaded bigint,
  total bigint,
  watched boolean not null default false,
  subs integer not null default 0,
  ar_subs integer not null default 0,
  pub_ar integer not null default 0,
  needs_pub boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id, episode_key)
);

create index if not exists episodes_show_idx
  on cj_episodes (user_id, device_id, show_id, season, episode);

alter table cj_episodes enable row level security;

drop policy if exists "owner all" on cj_episodes;
create policy "owner all" on cj_episodes
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
