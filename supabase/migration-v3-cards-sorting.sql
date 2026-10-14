-- CineJoy Remote v3 — download card redesign + sorting migration.
-- Run ONCE in the Supabase SQL editor (same project as schema.sql).
--
-- 1) cj_download_state gains poster so download cards can show artwork.
-- 2) cj_download_state gains created_at so the phone/PC can sort by
--    "Date Added" (newest first / oldest first). Rows are upserted on every
--    progress tick, so this must be set once at insert and preserved.

alter table cj_download_state add column if not exists poster text;
alter table cj_download_state add column if not exists created_at timestamptz not null default now();
