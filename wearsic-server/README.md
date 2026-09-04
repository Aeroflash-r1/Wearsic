# Wearsic Server

Standalone Ktor + NewPipe Extractor backend for the Wearsic Wear OS app. This project is intentionally separate from the Android app and can run on an old Android phone through Termux.

## Requirements

- Java 17
- Termux packages: `pkg install openjdk-17 git`
- A Cloudflare Tunnel pointed at the server port

## Build and run

The Kotlin source in this folder is the **canonical implementation** (registered as the `:wearsic-server` module in the root Gradle build). There is no prebuilt jar anymore — release ZIPs are generated from source by CI.

### From the Git repository (source build)

```bash
./gradlew :wearsic-server:installDist
cd wearsic-server
PORT=8080 WEARSIC_DB_PATH="$PWD/wearsic.db" ./run-termux.sh
```

`run-termux.sh` finds the fresh source build at `build/install/wearsic-server/bin/wearsic-server` automatically. See `SETUP.md` for details.

### From the ready-made Termux ZIP (`wearsic-server-termux-v<version>.zip`)

1. Install Termux packages: `pkg install openjdk-17 unzip`
2. Allow storage access once: `termux-setup-storage` (then restart Termux if asked)
3. Copy the ZIP from Downloads and extract it:
   ```bash
   cp ~/storage/downloads/wearsic-server-termux.zip ~/
   cd ~
   unzip wearsic-server-termux.zip
   ```
4. Start the server:
   ```bash
   cd ~/wearsic-server
   chmod +x run-termux.sh
   ./run-termux.sh
   ```

The ZIP is self-contained: `run-termux.sh` sits next to `bin/` and `lib/` and launches the server with a tuned heap. Your favorites/playlists are stored in `wearsic.db` next to the script — keep a copy of an old `wearsic.db` if you want to carry data over.

`run-termux.sh` uses G1GC and a 512 MB heap by default. Override `JAVA_OPTS` when the phone has more or less memory. If `ffmpeg` is missing, the launcher attempts `pkg install -y ffmpeg` and **exits non-zero on failure** (the server cannot transcode Opus/WebM-only songs without it).

## Environment

- `PORT` — defaults to `8080`
- `WEARSIC_DB_PATH` — defaults to `wearsic.db`
- `WEARSIC_API_KEY` — optional. If set, every `/api/*` request must include `X-Wearsic-Key` (constant-time comparison); `/health` remains public. When unset the server is OPEN — fine on a private LAN/Tailscale, never on a public tunnel. A warning is printed at startup when open.
- `WEARSIC_YOUTUBE_COOKIE` — optional browser cookie string fallback. Required when YouTube returns `Sign in to confirm that you're not a bot` for the server IP. Keep it private and export it only at runtime. The watch app can also push a cookie at runtime (see below). The cookie is never logged and the API never returns it — only `{"hasCookie":true|false}`.

## API

Public:

- `GET /health`

Authenticated when `WEARSIC_API_KEY` is set:

- `GET /api/search?q=` — maximum 10 results
- `GET /api/suggestions?q=` — maximum 5 suggestions
- `GET /api/related/{videoId}` — maximum 10 results
- `GET /api/stream/{videoId}` — proxied audio with Range forwarding; prefers M4A/AAC near 128 kbps
- `GET|POST|DELETE /api/favorites[/{videoId}]`
- `GET|POST /api/playlists`
- `GET /api/playlists/{id}`
- `POST|DELETE /api/playlists/{id}/tracks[/{videoId}]`
- `GET /api/playlist?url=` — maximum 10 tracks
- `GET /api/channel?url=` — maximum 10 tracks from the first channel tab
- `GET /api/config/youtube-cookie` — returns `{"hasCookie": true|false}`
- `POST /api/config/youtube-cookie` — body `{"cookie": "SID=...; HSID=..."}`; saves the cookie in SQLite and applies it to every YouTube request immediately. Send `{"cookie":""}` to clear it.

The server caches search results and resolved stream targets in small bounded in-memory caches (stream targets expire after 1 hour — CDN URLs expire upstream). SQLite uses WAL mode with `synchronous=NORMAL` for good performance on a phone.

## Errors and rate limiting

Every error is JSON: `{"error": "<message>"}` with a meaningful status — `400` invalid request/malformed body, `401` missing or wrong API key, `404` unknown route/video, `502` upstream CDN failure, `503` rate-limited or missing ffmpeg for transcode-needing songs, `500` unexpected (message is generic; details go to the log).

`GET /api/stream/{id}` is rate-limited per client (API key, else IP): sustained 30 requests/minute with short bursts above that allowed. This protects an open server from becoming a free YouTube proxy; normal playback (a few streams per minute) is unaffected.

Stream extraction is resilient:

- The **iOS Innertube client is tried first** (it is the one that actually works against YouTube's bot wall), falling back to the default client. Because the client choice is a process-global NewPipeExtractor setting, all extractions are serialized behind a mutex — correctness over concurrency, at no practical cost for a personal server.
- NewPipe failures map to clean JSON errors instead of empty 500 responses: `404` when a video is unavailable, `503` for bot/ReCaptcha challenges (with a hint to configure the YouTube cookie), and `502` for other extraction failures.

## Cloudflare Tunnel

Keep the tunnel URL out of source code. In the watch app Settings screen, enter the public HTTPS URL, for example:

```text
https://your-tunnel.trycloudflare.com
```

If you set `WEARSIC_API_KEY`, the Android client must also be extended to send `X-Wearsic-Key` on all `/api` calls.
