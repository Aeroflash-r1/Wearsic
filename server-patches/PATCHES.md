# Wearsic Server — Bytecode Patch Guide (LEGACY / HISTORICAL)

> **STATUS: OBSOLETE — DO NOT RE-APPLY.**
>
> The server now ships as real Kotlin source in `wearsic-server/src/`, which
> is the canonical implementation. Every behavior below was re-implemented in
> source (with deliberate changes, noted in `wearsic-server/SETUP.md`):
>
> | Patch (jar era) | Source-era equivalent |
> |---|---|
> | 70 kbps / WebM preference (1, 2) | **Superseded**: AAC-LC ~128 kbps + server-side ffmpeg transcode |
> | Stream TTL 6 h (3) | **Changed**: 1 h TTL (CDN URLs expire upstream) |
> | `videoId == "*"` deletes playlist (4) | Re-implemented in `Database.deletePlaylistTrack` |
> | Concurrent music search (5) | Re-implemented in `YoutubeGateway.searchMusic` (loser cancelled) |
> | iOS-client-first extraction (6) | Re-implemented in `YoutubeGateway` behind a mutex (race fixed) |
> | Ktor CIO downloader (7) | Re-implemented in `NewPipeDownloader` |
> | Cookie wiring (8) | Re-implemented in `YoutubeSession` + `NewPipeDownloader` |
>
> The old `bin/`/`lib/` jars and committed ZIPs were removed from the
> repository; release ZIPs are built from source by CI. This file is kept
> only as historical documentation of the jar era.

The server originally shipped as a compiled jar (`lib/wearsic-server-1.0.0.jar`, Kotlin, no
source available). All features below were added via binary patches. This file
documented every patch so future agents/humans could re-apply them after any
rebuild or extractor update.

## Applied patches (all inside lib/wearsic-server-1.0.0.jar)

| # | Class | Patch | Why |
|---|-------|-------|-----|
| 1 | `ExtractorService` (method `bitrateDistance`) | `sipush 128` → `sipush 70` | Audio quality target 128→70 kbps → picks Opus ~70k, ~50% smaller files |
| 2 | `ExtractorService$resolveAudioStream$$inlined$compareBy$1` + `$streamTarget$2` | constant pool Utf8 `"audio/mp4"` → `"audio/webm"` (+1 byte growth) | Prefer WebM so Opus is actually selected instead of low-grade AAC |
| 3 | `ExtractorService$streamTarget$2` | CONSTANT_Long entry `900000` → `21600000` | Stream URL cache TTL 15 min → 6 h = instant replays |
| 4 | `Database` (method `deletePlaylistTrack`) | Full method body replaced (ASM, COMPUTE_FRAMES): `tid == "*"` now deletes the whole playlist row (FK cascades tracks) | Playlist deletion from the watch — the jar had no delete route/method |
| 5 | `ExtractorService` (method `searchMusic`) | `searchMusic` sequential fallback → concurrent `coroutineScope { async(IO){filtered} async(IO){unfiltered} }` via `SearchMusicHelper2` delegate (ASM `PatchFix2b`, `CompletableFuture` pool) | Fixes 2× search latency when `music_songs` filter fails/empty |
| 6 | `ExtractorService$streamTarget$2` (method `invokeSuspend`) | Swap `resolveAudioStream(id,false)` → `true` first, `true` → `false` fallback + log `Default … iOS` → `iOS … default` (ASM `PatchFix3Tree2`) | Default client almost always fails (bot challenge); iOS-first saves one full extraction on every play |
| 7 | `NewPipeDownloader` (method `execute`) | `HttpURLConnection` (15000/30000, no keep-alive) → Ktor CIO pooled `HttpClient` (`HttpTimeout 10/15s`, `maxConnections=10`, `keepAlive 5s`, pipelining) | No HTTP/2/keep-alive caused fresh TCP+TLS handshake on every extraction |
| 8 | `YoutubeSession` / `NewPipeDownloader` | Verified already wired: `WEARSIC_YOUTUBE_COOKIE` env → `YoutubeSession.cookie` → `Cookie` header in `execute()` | Auth root cause — cookie was not missing, just bottlenecked |

## Current application status (verified 2026-09-01)

- **wearsic-server-termux-FIXED.zip** — patches 1–8 applied (canonical distributable, incl. G1GC launcher).
- **wearsic-server/lib/wearsic-server-1.0.0.jar** — patches 1–8 applied (now in sync; previously patch 4 only).
- Fix 1 was already correct — no jar change, just verification.

Verify a jar's state:

```bash
unzip -p <jar> com/wearsic/server/Database.class | strings | grep -c "DELETE FROM playlists WHERE id"
# 1 = patch 4 present, 0 = missing
unzip -p <jar> com/wearsic/server/ExtractorService.class | strings | grep -c "SearchMusicHelper2"
# 1 = patch 5 present
javap -classpath <jar> -c 'com.wearsic.server.ExtractorService$streamTarget$2' | grep -c "retrying with default"
# 1 = patch 6 present
javap -classpath <jar> -p com.wearsic.server.NewPipeDownloader | grep -c "HttpClient"
# 1 = patch 7 present (vs 0 before)
```

Patch #2 note: growing a Utf8 entry shifts bytes but class files reference
strings by index, so it is safe. Verify with javap afterwards.

Patch #4 tool: `PatchDeletePlaylist.java` in this folder (compile against
ASM 9.x: `javac -cp asm-9.9.jar PatchDeletePlaylist.java`). Re-run on a fresh
Database.class if you rebuild.

Patches 5-7 tools: `PatchFix2b.java` (searchMusic delegate), `PatchFix3Tree2.java` + `PatchFix3b.java` (streamTarget swap), `NewPipeDownloaderFixed.kt` (Ktor CIO, compiled via `kotlinc -classpath lib/*`). See `/tmp` decompiled sources for CFR reference.

## Non-jar components shipped in wearsic-server-termux-FIXED.zip

- `run-termux.sh` — auto-heal supervisor: restarts server on crash, /health
  poll every 30 s with force-restart after ~90 s unhealthy, termux-wake-lock,
  log rotation (wearsic-server.log), **G1GC `Xms64m Xmx512m MaxGCPause 150ms` + `-Dhttp.keepAlive -Dhttp.maxConnections=10`** (was SerialGC 32m/256m).
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
