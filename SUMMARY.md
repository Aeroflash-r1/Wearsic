# Wearsic — Final Summary

## Known Rough Edges & Unverified Assumptions

### 1. Ambient Mode (`LocalAmbientModeManager`)
- **Status:** Resolved. `rememberAmbientModeManager()` is now called at the top of `WearAppNavigation()` in `MainActivity.kt`, so the `LocalAmbientModeManager` composition local is properly set up. The `PlayerScreen` uses a safe-call (`?.`) as a fallback.
- The dimmed ambient state should now trigger correctly on actual ambient transitions on a real device.

### 2. Rotary Input (crown/bezel scrolling)
- **Status:** Improved. `LaunchedEffect(tracks)` now requests focus whenever the track list changes, so focus returns to the `ScalingLazyColumn` after each new search or result update. The column also has initial focus on first launch. Minor temporary focus loss to buttons is acceptable — the next search re-grants focus.

### 3. Playback Service Self-Stop
- **Status:** A 500ms debounced observer stops the service when `playbackState` becomes `Idle`. This is intended to prevent phantom foreground service.
- Caveat: `autoAdvanceNext()` in `PlaybackManager` now does *not* emit `Idle` before starting the next track (the `STATE_ENDED` race condition was fixed by removing the `Idle` assignment from the listener). The `Idle` state is set only by `autoAdvanceNext()` when no next track exists, or by `skipToNext()` when at end of queue. This should be correct.
- The service no longer calls `player.release()` in `onDestroy()` (the player is owned by `PlaybackManager`). If the service is killed and restarted, a new `MediaSession` wraps the same player instance.

### 4. Dynamic Backend URL
- **Status:** Resolved. `SearchViewModel` now accepts a `repositoryProvider: () -> WearsicRepository` lambda instead of a cached instance. Every `search()` call invokes the provider, which reads the current `app.repository`. Changing the URL in Settings and saving immediately updates the repository; the next search uses the new URL with no navigation or recomposition required.

### 5. No TODO Comments Found
- There are no `// TODO:`, `// FIXME:`, or `// HACK:` comments anywhere in the codebase (searched all `.kt`, `.kts`, `.xml`, `.yml` files).

### 6. Test Coverage
- There are no unit or instrumentation tests. The project is build-verified but not test-covered.

### 7. Launcher Icon
- The adaptive icon uses a solid round background (`#ABC7FF`) with a white "W" vector. Only `mipmap-anydpi-v26` is provided. Since `minSdk = 26`, legacy PNG mipmaps are not needed.

### 8. Foreground Service & Wake Locks
- `ExoPlayer` is built with `C.WAKE_MODE_LOCAL` — wake lock is only held locally and released internally by ExoPlayer when playback pauses/stops.
- Position polling (250ms) reads local ExoPlayer state only — no network calls. The only network call during playback is `checkPreFetch()` which fires once per track when near the end. No polling loops exist outside playback.

### 9. GitHub Actions Artifact
- Renamed to `wearsic-debug-apk-${{ github.run_number }}-${{ github.run_attempt }}` to prevent overwrites.

## Build Status
`./gradlew assembleDebug` — **BUILD SUCCESSFUL** (37 actionable tasks, zero errors, zero warnings from project code)
