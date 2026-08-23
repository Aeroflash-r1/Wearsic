# Wearsic Server — Bytecode Patch Guide

The server ships as a compiled jar (`lib/wearsic-server-1.0.0.jar`, Kotlin, no
source available). All features below were added via binary patches. This file
documents every patch so future agents/humans can re-apply them after any
rebuild or extractor update.

## Applied patches (all inside lib/wearsic-server-1.0.0.jar)

| # | Class | Patch | Why |
|---|-------|-------|-----|
| 1 | `ExtractorService` (method `bitrateDistance`) | `sipush 128` → `sipush 70` | Audio quality target 128→70 kbps → picks Opus ~70k, ~50% smaller files |
| 2 | `ExtractorService$resolveAudioStream$$inlined$compareBy$1` + `$streamTarget$2` | constant pool Utf8 `"audio/mp4"` → `"audio/webm"` (+1 byte growth) | Prefer WebM so Opus is actually selected instead of low-grade AAC |
| 3 | `ExtractorService$streamTarget$2` | CONSTANT_Long entry `900000` → `21600000` | Stream URL cache TTL 15 min → 6 h = instant replays |
| 4 | `Database` (method `deletePlaylistTrack`) | Full method body replaced (ASM, COMPUTE_FRAMES): `tid == "*"` now deletes the whole playlist row (FK cascades tracks) | Playlist deletion from the watch — the jar had no delete route/method |

Patch #2 note: growing a Utf8 entry shifts bytes but class files reference
strings by index, so it is safe. Verify with javap afterwards.

Patch #4 tool: `PatchDeletePlaylist.java` in this folder (compile against
gradle-bundled asm-9.9.jar). Re-run on a fresh Database.class if you rebuild.

## Non-jar components shipped in wearsic-server-termux-FIXED.zip

- `run-termux.sh` — auto-heal supervisor: restarts server on crash, /health
  poll every 30 s with force-restart after ~90 s unhealthy, termux-wake-lock,
  log rotation (wearsic-server.log), full JIT + 256 MB heap.
- `update-newpipe.sh` — when YouTube breaks extraction: fetches latest
  NewPipeExtractor from GitHub releases via JitPack, patches launcher
  CLASSPATH (jar names are hardcoded there!), SMOKE-TESTS a throwaway server
  instance against live search, commits only if search works.
- Launcher self-heals execute permissions lost by zip tools.

## Verified live endpoints (server v1.0.0 jar lineage)

- GET /api/suggestions?q=            -> {"suggestions":[...]}
- GET /api/related/{videoId}         -> {"results":[TrackDto]} (filter >10 min mixes!)
- GET /api/search/albums?q=          -> [AlbumDto{id=playlistURL,...}]
- GET /api/playlist?url=<url>        -> {id,name,tracks:[...]}
- POST /api/playlists {"name"}       -> PlaylistDto (no DELETE route for playlists themselves)
- Auth: env WEARSIC_API_KEY + header X-Wearsic-Key (empty env = open)

## App-side notes

- Artwork URLs from search are w60-h60; app rewrites to w544-h544 in
  WearsicNetworkModels.TrackDto.toDomainTrack().
- ExoPlayer buffer window is 10 min (WearsicMediaService) so whole songs flow
  through the disk cache while playing.
- Offline guarantee comes from auto-download (every played song, cap 15,
  toggle in Settings) — NOT from ExoPlayer cache alone.
