# CineJoy Remote — Construction Blueprint

**Objective:** Native Kotlin Android app that fully controls the CineJoy Downloader PC app
(Electron, `C:/Users/nedal/Desktop/sopranos_app`) from anywhere via Supabase, with push
notifications on download start/finish/error, and GitHub releases with in-app update checks.

**Decisions (user-confirmed):** Native Kotlin · Supabase email account on both ends ·
FCM push even when closed. Direct mode (no git/gh on this machine yet).

**Core design (read before any step):**
- Downloads NEVER move: all files, yt-dlp, and sessions stay on the PC.
- Supabase is only a mailbox + mirror. Uses a DEDICATED project
  `cinejoy-remote` (POS project is never touched — a cj_ deploy there was
  rolled back and verified clean). Project creation via Management API
  returned 403 (token is SQL-scoped), so the user creates the empty
  project in the dashboard. NOTE: the POS Management-API token is
  POS-scoped (SQL deploys to the new project return 403), so the user
  ran `schema.sql` in the new project's SQL editor and pasted the anon
  key — both verified live: all 6 `cj_` tables return HTTP 200 over REST.
  Connection details in `cinejoy-remote/supabase/project.json`
  (ref `pghsfyhdhqxzwlrhuutg`).
  Tables are `cj_`-prefixed to avoid the POS `devices` collision
  (all `user_id`-scoped, RLS enforced):
  - `cj_devices` — one row per PC (`user_id, device_id, name, last_seen, app_version`).
  - `cj_download_state` — live mirror of PC sessions
    (`user_id, device_id, task_id, episode_key, title, episode_label, state, percent,
      downloaded, total, speed, eta, source, server, quality, output_path, last_err, updated_at`).
  - `cj_library` — mirror of catalog shows (`user_id, device_id, show_id, type, title, year, episodes, added_at`).
  - `cj_commands` — phone→PC queue
    (`id, user_id, device_id, action, payload(jsonb), status[pending/running/done/error], result(jsonb), created_at`).
    Actions map 1:1 to existing PC functions: `pause, resume, retry, forget, start, re-download,
    queue-season, queue-missing, pause-all, resume-all, show-in-folder(no-op remote→ack),
    add-show, remove-show, toggle-watched, delete-episode, subs-download, subs-languages,
    subs-publish, subs-publish-all, subs-clear, engine-restart, seerr-scan`.
  - `cj_events` — PC→phone log (`user_id, device_id, kind[started|finished|error|info], title, body, created_at`).
  - `cj_push_tokens` — (`user_id, token, platform, updated_at`).
- Push path (Supabase-only, per user decision — no Firebase): PC detects
  start/finish/error edges in its mirror hook → writes `events` rows; the phone
  holds a Realtime INSERT subscription on `cj_events` and posts a local
  notification per new row. Works with the app open or backgrounded; a
  fully-closed (force-stopped) app cannot be woken without FCM — OS limit,
  accepted: the Activity feed keeps the permanent record.
- Phone gets live UI via Supabase Realtime on `download_state`/`library`/`events`;
  sends rows to `commands`; PC Realtime-subscribes `commands` and executes.
- Throttle: PC mirrors session state at most every 3s per task (progress emits are ~150ms).

## Step 1 — Supabase schema + RLS (no credentials needed to author)
Write `supabase/schema.sql` with all tables above + RLS policies (`auth.uid() = user_id`,
service-role bypass) + Realtime publication. **User action:** create free Supabase project,
run schema.sql in SQL editor, copy Project URL + anon key.
Exit: SQL file exists; user pastes URL/key back.

## Step 2 — PC remote module (needs Step 1 keys + Firebase service account)
New `src/remote.js` in sopranos_app, wired in `main.js`:
Supabase sign-in (session persisted in userData), device heartbeat, throttled state/library
mirror, `commands` subscriber executing the mapped functions, event writer + FCM sender hooked
into `notifyDownloadStarted`/`notifyDownloadFailure`/`finishSession`.
Needs npm dep `supabase-js`. **User action:** Firebase project → service-account JSON path.
Exit: `node --check` clean; local dry-run mirrors state to Supabase.

## Step 3 — Android scaffold (needs Step 1 keys; parallel with Step 2)
`cinejoy-remote/android/` Kotlin project: Supabase Auth (email), bottom-nav screens
Downloads / Library / Add / Activity(events) / Settings. Realtime download list with progress
bars + speed. Command sender with pending→done tracking.
Exit: assembles; login works; sees live PC state.

## Step 4 — Full command parity (needs Steps 2+3)
Wire every action in the table above to UI (row buttons, detail sheet, add-show dialog,
subtitle buttons, queue controls, engine restart, library refresh). `show-in-folder` returns
a friendly "on PC" ack.
Exit: each action tested phone→PC→result visible.

## Step 5 — Alerts end-to-end, Supabase-only (needs Steps 2+4, no extra keys)
Phone holds a Realtime INSERT subscription on `cj_events` (user-scoped filter,
device+kind filtered client-side) → local notification per new started/finished/
error row, tap opens the app; runtime POST_NOTIFICATIONS grant on Android 13+.
Test: background the app, start/finish a PC download, alert arrives in seconds.
Exit: backgrounded phone alerts on PC download start + finish. (Fully-closed
wake-up would need FCM — explicitly out of scope.)

## Step 6 — GitHub releases + self-update (needs Step 3)
New GitHub repo `cinejoy-remote`, Actions workflow building signed APK per tag,
phone Settings → "Check for updates" comparing GitHub Releases API → download + install prompt.
**User action:** create repo, add signing keystore secrets (guide provided).
Exit: tag push produces APK asset; phone detects and offers it.

## Step 7 — Ship + harden (needs all)
PC 1.2.9 (remote module, behind Settings toggle default ON once signed in), Android v1,
pairing guide, secrets rotation note. Regression: existing tests + Zootopia-style live download.

**Anti-patterns:** no media bytes through Supabase (mirror only); no anon-key writes without RLS;
no polling loops (Realtime everywhere); never commit service-account JSON or google-services
values to chat — files only, gitignored.
