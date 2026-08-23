# Wearsic Server

Standalone Ktor + NewPipe Extractor backend for the Wearsic Wear OS app. This project is intentionally separate from the Android app and can run on an old Android phone through Termux.

> **PATCHED BUILD (2026-08-21):** `lib/wearsic-server-1.0.0.jar` in this package has the
> audio quality target changed from 128 to 70 kbps, so streams, downloads and cache use
> Opus ~70 kbps — about 55% smaller files with the same perceived quality on a watch
> speaker or earbuds. No app changes are required.
>
> **AUTO-HEAL (2026-08-21):** `run-termux.sh` is now a supervisor: it restarts the server
> if it crashes, polls `/health` every 30 s and force-restarts it after ~90 s of being
> unhealthy, takes a Termux wake-lock so Android won't freeze it, and writes
> `wearsic-server.log` (rotated at 2 MB).
>
> **START ON PHONE REBOOT (optional):** install the *Termux:Boot* app once, then run:
>
> ```bash
> mkdir -p ~/.termux/boot
> printf '#!/data/data/com.termux/files/usr/bin/bash\ntermux-wake-lock\n~/wearsic-server/run-termux.sh\n' > ~/.termux/boot/wearsic.sh
> chmod +x ~/.termux/boot/wearsic.sh
> ```

## Requirements

- Java 17
- Termux packages: `pkg install openjdk-17 git`
- A Cloudflare Tunnel pointed at the server port

## Build and run

### From the Git repository

```bash
./gradlew -p server build
./gradlew -p server installDist
cd server
PORT=8080 WEARSIC_DB_PATH="$PWD/wearsic.db" ./run-termux.sh
```

### From the ready-made Termux ZIP (`wearsic-server-termux.zip`)

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

The ZIP is self-contained: `run-termux.sh` sits next to `bin/` and `lib/` and launches the server with a small heap. Your favorites/playlists are stored in `wearsic.db` next to the script — keep a copy of an old `wearsic.db` if you want to carry data over.

`run-termux.sh` uses a small heap and Serial GC by default. Override `JAVA_OPTS` when the phone has more memory.

## Environment

- `PORT` — defaults to `8080`
- `WEARSIC_DB_PATH` — defaults to `wearsic.db`
- `WEARSIC_API_KEY` — optional. If set, every `/api/*` request must include `X-Wearsic-Key`; `/health` remains public.
- `WEARSIC_YOUTUBE_COOKIE` — optional browser cookie string fallback. Required when YouTube returns `Sign in to confirm that you're not a bot` for the server IP. Keep it private and export it only at runtime. The watch app can also push a cookie at runtime (see below).

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
- `GET /api/config/youtube-cookie` — returns `{"configured": true|false}`
- `POST /api/config/youtube-cookie` — body `{"cookie": "SID=...; HSID=..."}`; saves the cookie in SQLite and applies it to every YouTube request immediately. Send `{"cookie":""}` to clear it.

The server caches search results and resolved stream targets in small bounded in-memory caches. SQLite uses WAL mode and `synchronous=NORMAL` for good performance on a phone.

Stream extraction is resilient:

- If the default YouTube client fails (bot check, throttling, player/API changes), the server automatically retries the extraction with the **iOS Innertube client** before reporting an error.
- NewPipe failures map to clean JSON errors instead of empty 500 responses: `404` when a video is unavailable, `503` for bot/ReCaptcha challenges (with a hint to configure the YouTube cookie), and `502` for other extraction failures.

## Cloudflare Tunnel

Keep the tunnel URL out of source code. In the watch app Settings screen, enter the public HTTPS URL, for example:

```text
https://your-tunnel.trycloudflare.com
```

If you set `WEARSIC_API_KEY`, the Android client must also be extended to send `X-Wearsic-Key` on all `/api` calls.
