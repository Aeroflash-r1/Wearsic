# Wearsic — Wear OS 6 Music App

**Wearsic** is a lightweight, high-performance, and secure music streaming application engineered specifically for **Wear OS 6** smartwatches, fully optimized for the **Samsung Galaxy Watch7 (44mm)**.

---

## 📜 Architectural Overview

Wearsic adopts a clean, modular Model-View-ViewModel (MVVM) architecture with structured data layers and service boundaries:

```
[ Wear OS Compose Screens ] (Rotary Scroll, M3 Touch Targets)
         │
         ▼
[ WearsicPlayerViewModel ] (Cancellable Coroutine Jobs, StateFlow Engine)
         │
         ▼
[ WearsicPlaybackController ] <══> [ WearsicMediaService ] (MediaSession, ExoPlayer)
         │                                   │
         ▼                                   ▼
[ WearsicDownloadManager ]          [ WearsicPlaybackCacheManager ] (32MB LRU Cache, configurable)
         │                                   │
         ▼                                   ▼
[ WearsicDownloadRepository ]       [ Android Filesystem ]
(Room SQLite, File Storage)        (wearsic_playback_cache / wearsic_downloads)
         │
         ▼
[ WearsicMusicRepository ] <══> [ WearsicHttpApiClient ]
```

### 1. Presentation & Interaction (Jetpack Compose for Wear OS)
- **Rotary Scroll Input**: Uses a dedicated, zero-allocation custom `wearsicRotaryScroll()` modifier leveraging `FocusRequester` and `dispatchRawDelta` to translate physical crown and touch bezel movements directly into list movements and player seeks.
- **Watch-First Touch Targets**: Primary play/pause and transport controls meet the 48dp Wear OS guideline (`WearsicCircularIconButton`, blob pod 84dp); some secondary inline row actions are intentionally smaller (26–38dp) to keep dense lists usable.
- **Material Design 3 (Vibrant Palette)**: Deep black background (`#000000`), dark charcoal surfaces (`#1C1B1F`), and high-contrast Lavender accents (`#D0BCFF`).

### 2. Playback Foundation (AndroidX Media3)
- **Single ExoPlayer Instance**: Instantiated inside the lifecycle of `WearsicMediaService` (extending `MediaSessionService`).
- **Natively Integrated MediaSession**: Exposes artwork, title, artist, play/pause, duration, seeks, and navigation directly to Wear OS system tiles, surfaces, and lock screens. Includes a secure `PendingIntent` for quick back-navigation to the main watch application.
- **Audio Attributes**: Custom music profile (`C.AUDIO_CONTENT_TYPE_MUSIC` & `C.USAGE_MEDIA`) utilizing Android's native audio focus system and noisy-headset behavior (`setHandleAudioBecomingNoisy(true)`).

### 3. Persistent Settings (Jetpack DataStore)
- Backed by Jetpack `DataStore<Preferences>`.
- **Fault Tolerance**: Read flows include `.catch` blocks to gracefully fall back to safe default settings if preference files are corrupted on the filesystem.

### 4. Downloads & Local Cache (Room Database & OKHttp)
- **Room SQLite Store**: Keeps track of track state (`QUEUED`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `CANCELLED`).
- **Isolation**: Downloading stream files are written to `.part` files in `wearsic_downloads` and renamed atomically to `.m4a` upon completion to prevent file truncation. Playback cache uses `wearsic_playback_cache` under cache directories, avoiding conflicts with downloads.
- **Storage Protection**: StatFs check verifies that at least 15MB of storage remains free before beginning any download.
- **One copy per song**: every streamed track is auto-cached (configurable cap, oldest-first eviction; toggle in Settings) and downloading a song purges its streamed copy — a song never exists twice on disk. Explicit downloads are permanent and never auto-evicted.

---

## 🌐 Expected Server API Contract

This client is fully hardened to support any standard Ktor/OkHttp endpoint following the schema below.

### 1. Health Verification
- **Route**: `GET /health`
- **Response Model** (self-healing fields are optional and ignored by older clients):
```json
{
  "status": "ok",
  "version": "1.7.0",
  "serverName": "Wearsic Engine",
  "transcoderAvailable": true,
  "extraction": { "successCount": 42, "failureCount": 1, "failureRatePercent": 2, "consecutiveFailures": 0, "lastError": null },
  "canaryHealthy": null,
  "update": { "status": "idle", "latestKnownVersion": null, "lastCheckAtMillis": 0, "lastError": null, "stagedVersion": null }
}
```

### 2. Music Search
- **Route**: `GET /api/search?q={query}`
- **Response Model** (the client derives stream URLs as `{server}/api/stream/{videoId}`):
```json
{
  "results": [
    {
      "videoId": "track_1",
      "title": "Weather with You",
      "uploader": "Crowded House",
      "durationMs": 240000,
      "thumbnailUrl": "https://i.ytimg.com/vi/.../default.jpg"
    }
  ]
}
```

### 3. Media Stream
- **Route**: `GET /api/stream/{videoId}`
- **Response Stream**: Returns `audio/mp4` (YouTube AAC — the common case), `audio/webm` (Opus), or `audio/aac` (server-transcoded Opus/WebM-only songs) with support for HTTP range requests.

---

## 🔒 Security & Hardening Pass

### 1. URL Sanitation & Scheme Enforcement
- Trim and sanitize Server URLs entered by users.
- Validates that schemes must start with `http://` or `https://` via strict `URI` check to prevent local file descriptor exposure. HTTPS is the expected default configuration for all production requests.

### 2. Duplicate Request Prevention
- Throttles connection testing by locking and skipping execution if `ConnectionTestState.Testing` is active.
- Throttles search queries by canceling previous active search coroutines `searchJob?.cancel()`.
- Throttles progress reporting during downloads (every 10% or 500ms) to reduce watch CPU and UI rendering overhead.
- Ignores duplicate track download requests if a download job for that track ID is already active.

### 3. Clean Error Translation
- Translates raw networking/Media3 exceptions into short, actionable, Wear OS-friendly errors (e.g., "Server connection timed out.", "Host not resolved. Check URL or internet.", "Storage full (<15MB free)").

### 4. Lifecycle & Coroutine Leak Protection
- Releases `MediaController` and cancels the coroutine supervisor scope job inside `WearsicPlaybackController.release()` when screens or ViewModels clear.

---

## 🗺️ Completed Milestones

- [x] **Milestone 1**: Wear OS 6 UI Foundation & Styling
- [x] **Milestone 2**: AndroidX Media3 Audio Playback Engine
- [x] **Milestone 3**: Server/API Client & Persistent Datastore Settings
- [x] **Milestone 4**: Caching & Room Local SQLite Downloads Store
- [x] **Milestone 5**: Native Wear OS Media Integration, Rotary Input & Layout Optimization
- [x] **Milestone 6**: Reliability, Security & Production Hardening
- [x] **Milestone 7**: Final Release & Daily-Use Validation

---

## 📡 API Contract & Server Architecture

The Wearsic watch application is a **lightweight streaming client**. To protect the watch's battery, processor, and cellular data consumption:
- All heavy work — metadata search (YouTube Music-first), YouTube extraction via NewPipeExtractor, and on-the-fly ffmpeg transcoding — happens in the **Wearsic Ktor server** (`wearsic-server/`, the canonical source implementation).
- The watch communicates with the server via the stable HTTP API documented in [API_CONTRACT.md](./API_CONTRACT.md).
- To run the server on a spare Android phone with Termux, follow [TERMUX_SERVER_GUIDE.md](./TERMUX_SERVER_GUIDE.md).

---

## 🛠️ How to Build & Run Tests

### Compile Project
```bash
./gradlew assembleDebug
```

### Run Test Suites
```bash
./gradlew :app:testDebugUnitTest   # app Robolectric tests
./gradlew :wearsic-server:test     # server unit/integration tests
```

---

## 🤖 CI / Releases (GitHub Actions)

`.github/workflows/android.yml` runs on every push/PR:

1. **test** — app Robolectric tests **plus** `:wearsic-server:test` (a broken
   server cannot merge silently while Android tests pass).
2. **build-debug** — unsigned debug APK uploaded as a workflow artifact.
3. **release** *(tag pushes only, `v*`)* — signed release APK **and** a fresh
   source-built server ZIP (`wearsic-server-termux-<tag>.zip`) attached to a
   GitHub Release; the packaged server is booted and its `/health` version is
   verified before publishing.

One-time setup for releases — add these repository **secrets**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 my-upload-key.jks` output |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

Create a keystore locally with:

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then cut a release:

```bash
git tag v1.7.0 && git push origin v1.7.0
```
