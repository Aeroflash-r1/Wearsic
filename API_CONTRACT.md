# Wearsic — Ktor Server API Contract Document (v1)

This document specifies the official, versioned REST API contract that any future backend (e.g., Ktor, Node.js, Go) must implement to be fully compatible with the Wearsic Wear OS client.

---

## 🧭 Design Philosophy

1. **Lightweight & Single-Purpose**: The watch is a highly resource-constrained client. The server must handle all heavy music extraction, metadata resolving, artwork resizing, and audio transcoding.
2. **Strict API Versioning**: All endpoints must be prefixed with `/api/v1/` to ensure future server modifications do not break existing watch installations.
3. **No Auth Requirements**: This is a lightweight personal-use application. Direct access within local networks or secure VPN tunnels is expected.
4. **Standard Format Support**: Streaming endpoints should serve audio tracks in standard formats (MP3/AAC/Ogg) and fully support HTTP Range Requests for efficient seeking and caching.

---

## 🎛️ API Endpoints Reference

### 1. Health & Server Status

Used by the watch to confirm server reachability, display connection diagnostic success, and retrieve basic status.

- **Endpoint**: `GET /api/v1/health`
- **Method**: `GET`
- **Headers**: `Accept: application/json`
- **Response**: `200 OK`
- **Response Body**:
```json
{
  "status": "ok",
  "version": "1.0.0",
  "serverName": "Wearsic Ktor Engine"
}
```

---

### 2. Music Search

Searches a query (e.g., YouTube Music, local libraries, or extractors) and returns matching track metadata.

- **Endpoint**: `GET /api/v1/search?q=<query_string>`
- **Method**: `GET`
- **Headers**: `Accept: application/json`
- **Query Parameters**:
  - `q` (string, required): The search query, URL-encoded.
- **Response**: `200 OK`
- **Response Body**:
```json
{
  "query": "Crowded House",
  "tracks": [
    {
      "id": "track_unique_id_1",
      "title": "Weather with You",
      "artist": "Crowded House",
      "album": "Woodface",
      "artworkUrl": "https://wearsic-server.internal/artwork/track_1.jpg",
      "durationMs": 240000,
      "streamUrl": "https://wearsic-server.internal/api/v1/stream/track_unique_id_1"
    }
  ]
}
```

---

## 🎵 Data Models Spec

### Track Metadata Object

Every track returned in the `tracks` array must precisely contain the following fields:

| Field Name | Type | Description | Required | Example |
| :--- | :--- | :--- | :--- | :--- |
| `id` | String | A unique identifier for the track. Used for local database keys and cache lookups. | **Yes** | `"youtube_3DsD83sJ"` |
| `title` | String | The title of the track. Displayed in the Wear OS screen and system media cards. | **Yes** | `"Weather with You"` |
| `artist` | String | The name of the artist/band. Displayed alongside the track title. | **Yes** | `"Crowded House"` |
| `album` | String | The album name. If unavailable, default to `"Single"` or empty. | **Yes** | `"Woodface"` |
| `artworkUrl` | String | The URL of the track cover artwork (minimum 120x120px, JPG/PNG). | No | `"https://internal/art.jpg"` |
| `durationMs` | Long | The total duration of the track in milliseconds. | **Yes** | `240000` |
| `streamUrl` | String | The direct URL to stream or download the raw audio file. | **Yes** | `"https://internal/stream/1"` |

---

### 3. Audio Streaming Endpoint

Streams the actual audio media file to ExoPlayer.

- **Endpoint**: `GET /api/v1/stream/{trackId}`
- **Method**: `GET`
- **Headers**:
  - Expected: `Range: bytes=start-end` (for partial media content buffering/seeking)
- **Response**:
  - Full File: `200 OK`
  - Range Request: `206 Partial Content`
- **Content-Type**: `audio/mpeg` (MP3), `audio/aac` (AAC), or `audio/ogg` (Ogg)

---

## 🔄 Client Compatibility & Evolution Guidelines

1. **New Fields**: Older Wearsic clients parse responses using robust `optString`/`optLong` JSON parsers. Future servers can safely append new JSON fields without breaking compatibility.
2. **Field Deprecation**: Standard fields like `streamUrl` and `id` must never be renamed or deleted in `v1`. If major breaking restructuring is needed, the server must bump the endpoint namespace to `/api/v2/...`.
3. **Artwork Optimization**: To protect the watch's limited bandwidth and low-power battery, servers are highly recommended to downscale, compress, or proxy raw artwork images to tiny circular-friendly dimensions (e.g. 150x150px) before serving them via `artworkUrl`.
