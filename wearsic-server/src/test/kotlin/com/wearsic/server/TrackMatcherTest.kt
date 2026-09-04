package com.wearsic.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMatcherTest {

    private val fakeYoutube = FakeYoutubeMetadataClient()
    private val matcher = TrackMatcher(fakeYoutube)

    private suspend fun best(): String? = matcher.match(
        artist = "Crowded House",
        title = "Weather with You",
        durationMs = 225_000L,
    )

    private fun candidate(
        id: String,
        title: String,
        uploader: String = "SomeChannel",
        durationSec: Long = 225,
    ) = TrackDto(
        videoId = id,
        title = title,
        uploader = uploader,
        durationMs = durationSec * 1000,
        thumbnailUrl = null,
    )

    @Test
    fun `prefers duration-exact Artist-Topic upload`() = runTest {
        fakeYoutube.results = listOf(
            candidate("cover1", "Weather with You (Cover)", durationSec = 240),
            candidate("topic1", "Weather with You", uploader = "Crowded House - Topic", durationSec = 225),
            candidate("random1", "Whatever", durationSec = 100),
        )
        assertEquals("topic1", best())
    }

    @Test
    fun `duration within 2 seconds scores highest`() = runTest {
        fakeYoutube.results = listOf(
            candidate("far", "Weather with You", durationSec = 260),
            candidate("exact", "Weather with You official audio", durationSec = 226),
        )
        assertEquals("exact", best())
    }

    @Test
    fun `duration within 5 seconds still beats a far-off match`() = runTest {
        fakeYoutube.results = listOf(
            candidate("far", "Weather with You", durationSec = 300),
            candidate("near", "Weather with You", durationSec = 229),
        )
        assertEquals("near", best())
    }

    @Test
    fun `large duration difference is penalized`() = runTest {
        fakeYoutube.results = listOf(
            candidate("extended", "Weather with You (Extended Mix)", durationSec = 500),
            candidate("normal", "Weather with You", durationSec = 226),
        )
        assertEquals("normal", best())
    }

    /**
     * Verified scoring semantics: "remix"/"cover" only cost −20 while the
     * duration tier spread is up to 70 (50 vs −20) and uploader/artist/title
     * matches add up to +55 — so a duration-exact remix legitimately beats a
     * duration-far clean title, and a penalty-free but weak-duration pool can
     * leave every candidate below MIN_CONFIDENCE (= fallback to top result).
     * These tests pin that real behavior; change the matcher deliberately or
     * not at all.
     */
    @Test
    fun `duration-exact remix beats duration-far clean title`() = runTest {
        fakeYoutube.results = listOf(
            candidate("remix", "Weather with You (Remix)", durationSec = 225),
            candidate("plain", "Weather with You", durationSec = 300),
        )
        assertEquals("remix", best())
    }

    @Test
    fun `cover beats coverless far-duration candidate`() = runTest {
        fakeYoutube.results = listOf(
            candidate("cover", "Weather with You Cover by Someone", durationSec = 225),
            candidate("plain", "Something completely unrelated", durationSec = 500),
        )
        assertEquals("cover", best())
    }

    @Test
    fun `weak pool falls back to the top search result`() = runTest {
        // Nobody matches artist/title and durations are far off: no candidate
        // clears MIN_CONFIDENCE, so the matcher returns the raw top result to
        // keep playback alive instead of blocking the song.
        fakeYoutube.results = listOf(
            candidate("weak1", "Totally unrelated thing", durationSec = 42),
            candidate("weak2", "Also unrelated", durationSec = 43),
        )
        val got = best()
        assertNotNull(got)
        assertTrue(got == "weak1" || got == "weak2")
    }

    @Test
    fun `karaoke version loses to duration-close official upload`() = runTest {
        fakeYoutube.results = listOf(
            candidate("karaoke", "Weather with You (Karaoke Version)", durationSec = 225),
            candidate("plain", "Weather with You", uploader = "Crowded House - Topic", durationSec = 226),
        )
        assertEquals("plain", best())
    }

    @Test
    fun `penalty skipped when the real track itself is a remix`() = runTest {
        // The real title contains "Remix": candidate remixes must not be
        // penalized relative to each other — the duration-closest wins.
        fakeYoutube.results = listOf(
            candidate("officialRemix", "Song (Remix)", durationSec = 200),
            candidate("wrongRemix", "Song (Other Remix)", durationSec = 260),
        )
        val got = matcher.match("Artist", "Song (Remix)", 200_000L)
        assertEquals("officialRemix", got)
    }

    @Test
    fun `uploader containing the artist name scores higher`() = runTest {
        fakeYoutube.results = listOf(
            candidate("official", "Weather with You", uploader = "CrowdedHouseOfficial", durationSec = 226),
            candidate("fan", "Weather with You", uploader = "RandomFanUploads", durationSec = 226),
        )
        assertEquals("official", best())
    }

    @Test
    fun `no candidates at all yields null`() = runTest {
        fakeYoutube.results = emptyList()
        assertNull(best())
    }

    @Test
    fun `zero durations are ignored in scoring rather than exploding`() = runTest {
        fakeYoutube.results = listOf(
            TrackDto(videoId = "nodur", title = "Weather with You", uploader = "Crowded House", durationMs = 0),
        )
        val got = matcher.match("Crowded House", "Weather with You", durationMs = 0L)
        assertEquals("nodur", got)
    }

    @Test
    fun `searches youtube with artist-title query`() = runTest {
        best()
        assertEquals("Crowded House - Weather with You", fakeYoutube.lastQuery)
    }

    // ---------------- Accuracy improvements (v1.4.3) ----------------

    /**
     * Regression: the old fallback returned the RAW TOP search hit when no
     * candidate cleared MIN_CONFIDENCE — often a vlog/mix sharing one word.
     * Now a candidate with >=50% title-word overlap must win the fallback.
     */
    @Test
    fun `weak pool falls back to title-overlap candidate not the raw top hit`() = runTest {
        fakeYoutube.results = listOf(
            candidate("vlog", "Random vlog thing", durationSec = 400),
            candidate("live", "Weather With You (Live)", durationSec = 400),
        )
        assertEquals("live", best(), "fallback must prefer title resemblance over search rank")
    }

    @Test
    fun `partial title overlap earns proportional credit`() = runTest {
        // Distant durations (so duration scoring is equal) — the 3-of-3-words
        // candidate must beat the unrelated one via title resemblance.
        fakeYoutube.results = listOf(
            candidate("unrelated", "Completely different thing", durationSec = 400),
            candidate("partial", "Weather With You (Live Version)", durationSec = 400),
        )
        assertEquals("partial", best())
    }

    @Test
    fun `artist in uploader beats artist only in title`() = runTest {
        fakeYoutube.results = listOf(
            // Cover-style upload: artist only inside the video title.
            candidate("coverish", "Weather with You by Crowded House", uploader = "FanUploads", durationSec = 226),
            // Genuine channel carrying the artist name (spaced, like real
            // channel names "Crowded House - Topic" / "Crowded House VEVO").
            candidate("channel", "Weather with You", uploader = "Crowded House VEVO", durationSec = 226),
        )
        assertEquals("channel", best())
    }
}

/** Minimal in-memory [YoutubeMetadataClient] for matcher/orchestrator tests. */
class FakeYoutubeMetadataClient : YoutubeMetadataClient {
    var results: List<TrackDto> = emptyList()
    var streamTargets: MutableMap<String, StreamTarget> = mutableMapOf()
    var lastQuery: String? = null
    val searchCalls: MutableList<String> = mutableListOf()

    override suspend fun search(query: String): List<TrackDto> {
        lastQuery = query
        searchCalls.add(query)
        return results
    }

    override suspend fun streamTarget(videoId: String): StreamTarget? =
        streamTargets[videoId]

    override suspend fun suggestions(prefix: String): List<String> = emptyList()

    override suspend fun related(videoId: String): List<TrackDto> = emptyList()

    override suspend fun searchAlbums(query: String): List<AlbumDto> = emptyList()

    override suspend fun playlistByUrl(url: String): PlaylistTracksResponse? = null
}
