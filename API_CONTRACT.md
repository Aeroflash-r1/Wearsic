# Wearsic — Server API Contract (v1, as implemented)

This document describes the REST API that the Wearsic Wear OS client actually
speaks. It matches the shipped Ktor + NewPipe Extractor server
(`wearsic-server/`, `wearsic-server-termux-FIXED.zip`) and the client parser in
`app/src/main/java/com/example/network/WearsicHttpApiClient.kt`.

> Note: earlier revisions of this file described a `/api/v1/...` namespace with
> `tracks[]` arrays. That contract was never implemented; the real surface is
> below. Do not "fix" the client to match old docs — update the docs instead.

---

## Design Philosophy

1. **Lightweight watch client** — extraction, metadata resolving, artwork and
   transcoding all happen server-side.
2. **No versioned namespace (yet)** — endpoints live under `/api/...`. Any
   breaking change should introduce `/api/v2/...` alongside.
3. **Personal use** — optional shared-secret auth; expected on a trusted
   network or tunnel.
4. **Streaming formats** — MP3/AAC/M4A/WebM(Opus) audio with HTTP Range
   request support.

---

## Authentication

- Set `WEARSIC_API_KEY` in the server environment to require a key.
- The watch sends it as an HTTP header on every `/api/*` request:

```
X-Wearsic-Key: <key>
```

- `/health` stays public. Empty/unset env var = open server.

---

## Endpoints

### 1. Health

- **GET** `/health`
- **Response** `200 OK`:

```json
{ "status": "ok", "version": "1.0.0", "serverName": "Wearsic Engine" }
```

Client tolerates missing fields (`status` defaults to `"ok"` when HTTP 200).

### 2. Music Search

- **GET** `/api/search?q=<query>`
- **Response** `200 OK`:

```json
{
  "results": [
    {
      "videoId": "3DsD83sJ",
      "title": "Weather with You",
      "uploader": "Crowded House",
      "durationMs": 240000,
      "thumbnailUrl": "https://i.ytimg.com/vi/.../default.jpg"
    }
  ]
}
```

- Max ~10 results (server-side limit).
- The client derives the stream URL itself:
  `{server}/api/stream/{videoId}`.
- Thumbnails are typically `w60-h60`; the client rewrites ytimg/googleusercontent
  URLs to `w544-h544` for crisp artwork.

### 3. Audio Stream

- **GET** `/api/stream/{videoId}`
- Headers: standard `Range: bytes=start-end` supported (forwarded upstream).
- Response: `200 OK` full body or `206 Partial Content`.
- Content-Type: `audio/mp4`, `audio/webm` (Opus), or `audio/mpeg`.

### 4. Suggestions

- **GET** `/api/suggestions?q=<prefix>`
- **Response**: `{ "suggestions": ["...", "..."] }` (max ~5).

### 5. Related / Radio

- **GET** `/api/related/{videoId}`
- **Response**: `{ "results": [TrackDto...] }` (max ~10).
- Client filters out items >10 min (full-album mixes).

### 6. Albums

- **GET** `/api/search/albums?q=<query>`
- **Response**: array of album objects:

```json
[ { "id": "<playlist-url>", "name": "...", "uploader": "...", "trackCount": 10, "thumbnailUrl": "..." } ]
```

Note: album `id` is a full playlist URL, not a bare id.

### 7. Playlist by URL

- **GET** `/api/playlist?url=<playlist-or-album-url>`
- **Response**: `{ "id": "...", "name": "...", "tracks": [TrackDto...] }`
- Max ~10 tracks.

### 8. Favorites

- **GET** `/api/favorites` → array of TrackDto.
- **POST** `/api/favorites` with TrackDto JSON body (see below).
- **DELETE** `/api/favorites/{videoId}`.

### 9. Playlists

- **GET** `/api/playlists` → array: `[ { "id", "name", "trackCount", "thumbnailUrl" } ]`
- **POST** `/api/playlists` with `{ "name": "..." }` → PlaylistDto.
- **GET** `/api/playlists/{id}` → `{ "id", "name", "tracks": [TrackDto...] }`
- **POST** `/api/playlists/{id}/tracks` with TrackDto JSON body.
- **DELETE** `/api/playlists/{id}/tracks/{videoId}`
  - Special case (bytecode patch #4): `videoId == "*"` deletes the entire
    playlist (FK cascade). This is how the app's "remove playlist" works.

---

## TrackDto Object

Used in search results, favorites, playlists bodies:

| Field | Type | Required | Notes |
| :--- | :--- | :--- | :--- |
| `videoId` | String | Yes | Unique id; also used to build stream URL client-side. |
| `title` | String | Yes | |
| `uploader` | String | Yes | Displayed as artist. |
| `durationMs` | Long | No | Milliseconds; `0` if unknown. |
| `thumbnailUrl` | String | No | Small thumbnail OK; client upgrades resolution. |

The client POSTs exactly these five fields as JSON.

## Compatibility Rules

1. The client parses responses defensively (`optString`/`optLong`), so servers
   may append new fields freely.
2. Never rename or remove `videoId` or the `/api/stream/{videoId}` shape within
   this generation of clients; introduce `/api/v2/...` for breaking changes.
3. Artwork: prefer serving pre-downscaled images (~150px) to protect watch
   bandwidth; the client will upscale ytimg URLs itself when needed.
