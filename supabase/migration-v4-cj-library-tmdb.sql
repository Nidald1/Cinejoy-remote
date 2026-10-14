-- v4: TMDb id on the library mirror so the phone can enrich the Netflix-style
-- title header (Settings -> Title page) with backdrop, genres, Arabic synopsis
-- and cast. Published by the PC's remote-sync from catalogSummary(); without
-- it the header just falls back to the library's own poster/overview.
alter table cj_library add column if not exists tmdb_id bigint;
