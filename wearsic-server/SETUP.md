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

The source below **supersedes** the patched jar — do not re-apply old
bytecode patches (see `../server-patches/PATCHES.md`, kept as history only):

- **Search**: YouTube Music-first (official titles/artists/durations with
  directly playable videoIds — no iTunes, no surrogate matching step); the
  NewPipeExtractor YouTube search runs only as fallback when YTM is
  unreachable. The top results' streams are pre-resolved in the background
  so taps play instantly.
- **Stream resolution**: tries the iOS-spoofed YouTube client first (the one
  that actually works), falls back to the default client only if that fails.
  Because `setFetchIosClient` is a process-global static, ALL extractions are
  serialized behind a mutex — the set→fetch→reset sequence is atomic.
- **Audio profile**: AAC-LC ~128 kbps preferred (hardware-decoded on the
  watch's SoC); the old 70 kbps/WebM patch behavior is intentionally NOT
  preserved. Rare Opus/WebM-only songs are transcoded to AAC by ffmpeg on the
  server (503 with guidance if ffmpeg is missing).
- **HTTP transport**: NewPipeExtractor's `Downloader` runs on a pooled,
  keep-alive Ktor CIO client instead of raw `HttpURLConnection`.
- **Cookie handling**: read from `WEARSIC_YOUTUBE_COOKIE` on boot (env wins),
  falling back to the value persisted in SQLite, and updatable at runtime via
  `POST /api/config/youtube-cookie` — persisted so it survives restarts.
  Never logged, never echoed by the API.
- **Concurrency/memory**: per-key `SingleFlight` deduplication (entries
  removed on completion — the old per-key Mutex map leaked one entry per key
  ever seen), bounded LRU caches with TTL on stream targets.
- **Errors**: Ktor StatusPages maps every failure to JSON
  (`{"error": "..."}`); malformed bodies answer 400 instead of empty 500s.
- **Auth**: `WEARSIC_API_KEY` compared with `MessageDigest.isEqual`
  (constant-time); startup warns loudly when the server is open.
- **Rate limiting**: `/api/stream` is token-bucket limited per client
  (30/min sustained, small bursts) so an open server isn't a free proxy.
- **Everything else** (routes, response shapes, `TrackDto` fields, the
  `videoId == "*"` deletes-whole-playlist behavior) matches
  `API_CONTRACT.md`, so the Android client needs zero changes.

## Verified

`./gradlew :wearsic-server:test` runs offline unit/integration tests
(YTM parsing/durations, search fallback, SingleFlight dedup, database CRUD incl.
wildcard playlist deletion and legacy surrogate matches, JSON contract,
Ktor routes/auth/errors/rate limit, transcoder plumbing). CI runs them on
every push, and the release pipeline boots the packaged server and asserts
`/health` reports the source version before publishing.
