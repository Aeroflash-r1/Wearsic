package com.wearsic.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OrchestratorFallbackTest {

    private class FakeMetadataSource : MetadataSource {
        var tracks: List<ITunesTrack> = emptyList()
        val searchCalls: MutableList<String> = mutableListOf()

        override suspend fun searchSongs(query: String, limit: Int): List<ITunesTrack> {
            searchCalls.add(query)
            return tracks
        }

        override suspend fun lookupTrack(trackId: Long): ITunesTrack? =
            tracks.firstOrNull { it.trackId == trackId }

        override fun toTrackDto(track: ITunesTrack): TrackDto = TrackDto(
            videoId = track.surrogateId,
            title = track.trackName ?: "Unknown",
            uploader = track.artistName ?: "Unknown",
            durationMs = track.trackTimeMillis ?: 0L,
            thumbnailUrl = null,
        )
    }

    private fun track(id: Long, name: String = "Song $id") = ITunesTrack(
        trackId = id,
        trackName = name,
        artistName = "Artist $id",
        trackTimeMillis = 200_000L,
        artworkUrl100 = null,
    )

    @Test
    fun `iTunes results are returned directly - YouTube fallback NOT called`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = listOf(track(1), track(2)) }
        val youtube = FakeYoutubeMetadataClient()
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        val results = orchestrator.search("crowded house")

        assertEquals(2, results.size)
        assertEquals("it:1", results[0].videoId, "results must carry surrogate ids")
        assertEquals(0, orchestrator.youtubeFallbackCount, "usable iTunes results must not count as YouTube fallbacks")
        // The only YouTube traffic for this search is the BACKGROUND prefetch
        // (top-6 match + stream warm), which never blocks the response.
        val prefetchSearches = youtube.searchCalls.toList()
        assertTrue(
            prefetchSearches.all { it.startsWith("Artist ") },
            "only prefetch-style artist-title searches expected, got: $prefetchSearches",
        )
    }

    @Test
    fun `empty iTunes results fall back to YouTube search`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = emptyList() }
        val youtube = FakeYoutubeMetadataClient().apply {
            results = listOf(TrackDto(videoId = "yt1", title = "YT Song", uploader = "Chan"))
        }
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        val results = orchestrator.search("obscure song")

        assertEquals(1, results.size)
        assertEquals("yt1", results[0].videoId)
        assertEquals(1, orchestrator.youtubeFallbackCount)
        assertEquals("obscure song", youtube.searchCalls.single())
    }

    /**
     * Verified behavior: the real ITunesService filters unusable entries
     * (null trackName/artistName) BEFORE returning, so entries that are only
     * partially populated never reach the orchestrator. The orchestrator's
     * contract is: ANY non-empty list = usable. This test pins that the
     * filtering responsibility lives in ITunesService (covered by its own
     * contract) and that the orchestrator treats non-empty as usable.
     */
    @Test
    fun `non-empty iTunes response is treated as usable regardless of entry quality`() = runTest {
        val metadata = FakeMetadataSource().apply {
            tracks = listOf(
                ITunesTrack(trackId = 1, trackName = null, artistName = "Artist", trackTimeMillis = 1),
                ITunesTrack(trackId = 2, trackName = "Name", artistName = null, trackTimeMillis = 1),
            )
        }
        val youtube = FakeYoutubeMetadataClient().apply {
            results = listOf(TrackDto(videoId = "yt9", title = "t", uploader = "u"))
        }
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        val results = orchestrator.search("broken metadata")

        assertEquals(2, results.size, "non-empty iTunes list must be returned as-is (no fallback)")
        assertEquals(0, orchestrator.youtubeFallbackCount)
    }

    @Test
    fun `background prefetch resolves streams for top results`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = listOf(track(1), track(2)) }
        val youtube = FakeYoutubeMetadataClient().apply {
            streamTargets["yt-match-1"] = StreamTarget("https://cdn/x.m4a", "audio/mp4")
        }
        val matcher = TrackMatcher(youtube)
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, matcher)

        orchestrator.search("crowded house")

        // Prefetch runs on a real Dispatchers.IO scope outside runTest's
        // virtual clock — yield to the main-test dispatcher and poll with real
        // (wall-clock) sleeps until the background job lands.
        val deadline = System.currentTimeMillis() + 5_000
        var matched = false
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.delay(50)
            }
            if (youtube.searchCalls.any { it.contains("Artist 1 - Song 1") }) {
                matched = true
                break
            }
        }
        assertTrue(
            matched,
            "prefetch must match the top iTunes result via a YouTube search (calls=$youtube.searchCalls)",
        )
    }

    @Test
    fun `surrogate id is resolved via iTunes lookup after restart`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = listOf(track(77)) }
        val youtube = FakeYoutubeMetadataClient().apply {
            results = listOf(
                TrackDto(videoId = "realVideo", title = "Song 77", uploader = "Artist 77", durationMs = 200_000),
            )
        }
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        // Simulates a fresh server receiving a saved "it:77" from the watch.
        val resolved = orchestrator.resolveStreamVideoId("it:77")

        assertEquals("realVideo", resolved)
        assertTrue(metadata.searchCalls.isEmpty(), "lookup path must use iTunes /lookup, not search")
    }

    @Test
    fun `real YouTube ids pass through unchanged`() = runTest {
        val metadata = FakeMetadataSource()
        val youtube = FakeYoutubeMetadataClient()
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        assertEquals("dQw4w9WgXcQ", orchestrator.resolveStreamVideoId("dQw4w9WgXcQ"))
        assertEquals(0, orchestrator.youtubeFallbackCount)
    }

    @Test
    fun `unknown surrogate id resolves to null`() = runTest {
        val metadata = FakeMetadataSource()
        val youtube = FakeYoutubeMetadataClient()
        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube))

        assertEquals(null, orchestrator.resolveStreamVideoId("it:999999"))
    }

    // ---------------- Persistent match store (restart fast-replay) ----------------

    private class InMemoryMatchStore : MetadataSearchOrchestrator.MatchPersistence {
        val map = mutableMapOf<String, String>()
        override fun getMatchedVideoId(surrogateId: String): String? = map[surrogateId]
        override fun putMatchedVideoId(surrogateId: String, videoId: String) {
            map[surrogateId] = videoId
        }
    }

    @Test
    fun `persisted match survives a restart without hitting YouTube again`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = listOf(track(77)) }
        val youtube = FakeYoutubeMetadataClient().apply {
            results = listOf(
                TrackDto(videoId = "realVideo", title = "Song 77", uploader = "Artist 77", durationMs = 200_000),
            )
        }
        val store = InMemoryMatchStore()

        // "Before restart": resolve pays the YouTube match once and persists it.
        val before = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube), store)
        assertEquals("realVideo", before.resolveStreamVideoId("it:77"))
        assertEquals("realVideo", store.map["it:77"])

        // "After restart": fresh orchestrator (empty in-memory caches) but the
        // same persistent store. Saved-song replay must not touch YouTube.
        val youtubeAfterRestart = FakeYoutubeMetadataClient()
        val after = MetadataSearchOrchestrator(metadata, youtubeAfterRestart, TrackMatcher(youtubeAfterRestart), store)
        assertEquals("realVideo", after.resolveStreamVideoId("it:77"))
        assertTrue(
            youtubeAfterRestart.searchCalls.isEmpty(),
            "restart replay must be served from the persisted match, got searches: ${youtubeAfterRestart.searchCalls}",
        )
    }

    @Test
    fun `persistence failure never breaks matching`() = runTest {
        val metadata = FakeMetadataSource().apply { tracks = listOf(track(77)) }
        val youtube = FakeYoutubeMetadataClient().apply {
            results = listOf(
                TrackDto(videoId = "realVideo", title = "Song 77", uploader = "Artist 77", durationMs = 200_000),
            )
        }
        val broken = object : MetadataSearchOrchestrator.MatchPersistence {
            override fun getMatchedVideoId(surrogateId: String): String? =
                throw IllegalStateException("db gone")
            override fun putMatchedVideoId(surrogateId: String, videoId: String) {
                throw IllegalStateException("db gone")
            }
        }

        val orchestrator = MetadataSearchOrchestrator(metadata, youtube, TrackMatcher(youtube), broken)
        assertEquals("realVideo", orchestrator.resolveStreamVideoId("it:77"), "matching must proceed without persistence")
    }
}
