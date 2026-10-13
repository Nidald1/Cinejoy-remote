-- CineJoy Remote schema. Run once in the Supabase SQL editor.
-- Everything is scoped to auth.users via user_id + RLS.

create table if not exists cj_devices (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  name text not null default 'PC',
  app_version text,
  last_seen timestamptz not null default now(),
  primary key (user_id, device_id)
);

create table if not exists cj_download_state (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  task_id text not null,
  episode_key text not null default '',
  title text not null default '',
  episode_label text not null default '',
  state text not null default 'queued',
  percent double precision,
  downloaded bigint,
  total bigint,
  speed bigint,
  eta text,
  source text,
  server text,
  quality text,
  output_path text,
  last_err text,
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id, task_id)
);
create index if not exists download_state_live_idx
  on cj_download_state (user_id, device_id, state, updated_at desc);

create table if not exists cj_library (
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  show_id text not null,
  type text not null default 'movie',
  title text not null default '',
  year text,
  episodes integer not null default 0,
  added_at timestamptz,
  primary key (user_id, device_id, show_id)
);

create table if not exists cj_commands (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  action text not null,
  payload jsonb not null default '{}',
  status text not null default 'pending',
  result jsonb,
  created_at timestamptz not null default now()
);
create index if not exists commands_queue_idx
  on cj_commands (user_id, device_id, status, created_at);

create table if not exists cj_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id text not null,
  kind text not null,
  title text not null default '',
  body text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists cj_push_tokens (
  user_id uuid not null references auth.users (id) on delete cascade,
  token text not null,
  platform text not null default 'android',
  updated_at timestamptz not null default now(),
  primary key (user_id, token)
);

alter table cj_devices enable row level security;
alter table cj_download_state enable row level security;
alter table cj_library enable row level security;
alter table cj_commands enable row level security;
alter table cj_events enable row level security;
alter table cj_push_tokens enable row level security;

drop policy if exists "owner all" on cj_devices;
drop policy if exists "owner all" on cj_download_state;
drop policy if exists "owner all" on cj_library;
drop policy if exists "owner all" on cj_commands;
drop policy if exists "owner all" on cj_events;
drop policy if exists "owner all" on cj_push_tokens;

create policy "owner all" on cj_devices for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "owner all" on cj_download_state for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "owner all" on cj_library for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "owner all" on cj_commands for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "owner all" on cj_events for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "owner all" on cj_push_tokens for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Realtime for live phone UI + PC command queue.
alter publication supabase_realtime add table cj_download_state;
alter publication supabase_realtime add table cj_library;
alter publication supabase_realtime add table cj_commands;
alter publication supabase_realtime add table cj_events;
