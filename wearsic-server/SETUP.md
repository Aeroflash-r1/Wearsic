# Wearsic Server — Source Module (rewrite of the compiled-jar server)

This `wearsic-server/` folder now contains the **source** for the server, which
previously existed only as a compiled jar (`lib/wearsic-server-1.0.0.jar`)
with no source in the repo. The Kotlin/Ktor implementation is a from-scratch
rewrite built against the same dependency versions proven in the Termux
deployment (Ktor 2.3.12, NewPipeExtractor v0.26.4, kotlinx-coroutines 1.7.1,
sqlite-jdbc 3.46.1.0) and produces the same `bin/`+`lib/` output shape that
`run-termux.sh` expects.

The module is registered in the root `settings.gradle.kts` as
`:wearsic-server` alongside the `:app` Android module.

## Build

```bash
./gradlew :wearsic-server:build        # compile + assemble
./gradlew :wearsic-server:installDist  # produce build/install/wearsic-server/{bin,lib}
```

`run-termux.sh` already looks for
`build/install/wearsic-server/bin/wearsic-server` as its second discovery
path, so after an `installDist` the server can be started from the source
build directly:

```bash
cd wearsic-server
./run-termux.sh
```

To make the source build the default (over the legacy `bin/`+`lib/` jars),
copy the fresh output over them:

```bash
cp -r wearsic-server/build/install/wearsic-server/{bin,lib} wearsic-server/
```

## Database compatibility — resolved

The original compiled server's SQLite schema was not documented anywhere in
the repo. The DDL in `Database.kt` was written to **mirror the real schema**
verified from the deployed `wearsic.db` (favorites / playlists /
playlist_tracks with `ON DELETE CASCADE` / settings), so an existing database
keeps working — `CREATE TABLE IF NOT EXISTS` is a no-op on those tables and
the new `settings` table (used to persist the YouTube cookie) is additive.

## What's different from the old (bytecode-patched) server

- **Search**: filtered (`music_songs`) and unfiltered search run concurrently
  via real Kotlin coroutines — no reflection, no per-request thread pool.
- **Stream resolution**: tries the iOS-spoofed YouTube client first (the one
  that actually works), falls back to the default client only if that fails.
  The static `setFetchIosClient` toggle is reset in a `finally` block so it
  never leaks into unrelated extractions.
- **HTTP transport**: NewPipeExtractor's `Downloader` runs on a pooled,
  keep-alive Ktor CIO client (shared connection pool) instead of raw
  `HttpURLConnection`.
- **Cookie handling**: read from `WEARSIC_YOUTUBE_COOKIE` on boot (env wins),
  falling back to the value persisted in SQLite, and updatable at runtime via
  `POST /api/config/youtube-cookie` — persisted so it survives restarts.
- **Bitrate selection**: preserved the ~70 kbps target-bitrate logic (picks
  the audio stream closest to the target, not the highest quality) — a
  deliberate storage/bandwidth choice for a watch client.
- **Everything else** (routes, response shapes, `TrackDto` fields, the
  `videoId == "*"` deletes-whole-playlist behavior) matches
  `API_CONTRACT.md`, so the Android client needs zero changes.

## Verified (2026-09-03)

`./gradlew :wearsic-server:build` passes. A live smoke test exercised:
`/health`, favorites POST/GET round-trip, playlist create + add track +
detail, playlist deletion via `videoId == "*"`, YouTube cookie set + survival
across a restart, and a live `/api/search` returning real YouTube results.
