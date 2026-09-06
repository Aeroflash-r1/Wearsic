package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WearsicDownloadRepository
import com.example.data.WearsicRecentRepository
import com.example.data.db.WearsicDatabase
import com.example.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * REGRESSION — "Recent song plays the WRONG audio".
 *
 * Recents are keyed by the stable recording identity (a real YouTube videoId
 * since the 1.5 YTM migration), NEVER by title/artist. Two different
 * recordings can legitimately share a title + artist and must stay separate
 * rows that each resolve to their OWN stream/local file.
 *
 * The identity chain under test for every scenario:
 *
 *     Recent item id (videoId)
 *         = MediaItem/track id  (entity.toDomainTrack().id)
 *         = stream URL segment  (mediaUri ends in /api/stream/{videoId})
 *         = local file id, when one exists
 *
 * If this test fails, Recent identity was rebuilt from display metadata
 * (title/artist) instead of the exact stored id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentPlaybackIdentityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: WearsicDatabase
    private lateinit var recents: WearsicRecentRepository
    private lateinit var downloads: WearsicDownloadRepository

    /** Same title AND same artist on purpose — the two recordings must stay distinct. */
    private fun recording(id: String) = Track(
        id = id,
        title = "Test Song",
        artist = "Same Artist",
        album = "Album",
        durationMs = 180_000L,
        mediaUri = "https://server.example/api/stream/$id",
    )

    /** Exactly how playback picks a source: local file for the id first, else
     *  the row's own stream URL. NEVER a title search. */
    private suspend fun resolveForPlayback(recentRow: Track): Track =
        downloads.getDownloadedTrack(recentRow.id) ?: recentRow

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WearsicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recents = WearsicRecentRepository(context, recentTrackDao = db.recentTrackDao())
        downloads = WearsicDownloadRepository(context, downloadDao = db.downloadDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recentList(): List<Track> = runBlocking { recents.recentTracksFlow.first() }

    // A + B + F: same title/artist, different videoIds -> distinct recordings.
    @Test
    fun `same-title recordings AAA and BBB stay distinct and resolve their own ids`() = runBlocking {
        recents.recordPlayed(recording("AAA"))
        recents.recordPlayed(recording("BBB"))

        val rows = recentList()
        assertEquals("two recordings, two rows: " + rows.map { it.id }, 2, rows.size)

        val a = rows.first { it.id == "AAA" }
        val b = rows.first { it.id == "BBB" }
        // Click A -> resolves stream segment A, never B's.
        assertEquals("AAA", a.id)
        assertEquals("AAA", a.mediaUri.substringAfterLast('/'))
        assertEquals("AAA", (resolveForPlayback(a) as Track).id)
        // Click B -> resolves stream segment B, never A's.
        assertEquals("BBB", b.id)
        assertEquals("BBB", b.mediaUri.substringAfterLast('/'))
        assertEquals("BBB", (resolveForPlayback(b) as Track).id)
    }

    // C: AAA -> BBB -> AAA replay keeps exact ids through every play.
    @Test
    fun `replaying AAA after BBB always replays recording AAA`() = runBlocking {
        recents.recordPlayed(recording("AAA"))
        recents.recordPlayed(recording("BBB"))
        recents.recordPlayed(recording("AAA")) // AAA again — the buggy code erased it here

        val rows = recentList()
        assertEquals("both recordings remain after replay: " + rows.map { it.id }, 2, rows.size)

        // Top of Recents is AAA again, still pointing at AAA's stream.
        assertEquals("AAA", rows[0].id)
        assertEquals("https://server.example/api/stream/AAA", rows[0].mediaUri)
        assertEquals("AAA", rows[0].mediaUri.substringAfterLast('/'))
        // BBB's row survived untouched.
        assertEquals("BBB", rows[1].id)
        assertEquals("BBB", rows[1].mediaUri.substringAfterLast('/'))
    }

    // D: local AUTO file for AAA -> play AAA's exact local file.
    @Test
    fun `local AUTO file for AAA is played for AAA and never for BBB`() = runBlocking {
        recents.recordPlayed(recording("AAA"))
        recents.recordPlayed(recording("BBB"))
        seedCompletedFile("AAA", autoCached = true)

        val aRow = recentList().first { it.id == "AAA" }
        val resolvedA = resolveForPlayback(aRow)
        // One local file, owned by AAA's id.
        assertEquals("AAA", resolvedA.id)
        assertTrue("plays the local AUTO file", resolvedA.mediaUri.startsWith("/"))
        assertEquals("AAA.m4a", resolvedA.mediaUri.substringAfterLast('/'))
        assertTrue(File(resolvedA.mediaUri).isFile)

        // BBB is unaffected by AAA's local file: still resolves BBB's stream.
        val bRow = recentList().first { it.id == "BBB" }
        val resolvedB = resolveForPlayback(bRow)
        assertEquals("BBB", resolvedB.id)
        assertEquals("https://server.example/api/stream/BBB", resolvedB.mediaUri)
    }

    // E: local MANUAL file for BBB -> play BBB's exact local file.
    @Test
    fun `local MANUAL file for BBB is played for BBB and never for AAA`() = runBlocking {
        recents.recordPlayed(recording("AAA"))
        recents.recordPlayed(recording("BBB"))
        seedCompletedFile("BBB", autoCached = false)

        val bRow = recentList().first { it.id == "BBB" }
        val resolvedB = resolveForPlayback(bRow)
        assertEquals("BBB", resolvedB.id)
        assertTrue("plays the local MANUAL file", resolvedB.mediaUri.startsWith("/"))
        assertEquals("BBB.m4a", resolvedB.mediaUri.substringAfterLast('/'))
        assertTrue(File(resolvedB.mediaUri).isFile)

        val aRow = recentList().first { it.id == "AAA" }
        val resolvedA = resolveForPlayback(aRow)
        assertEquals("AAA", resolvedA.id)
        assertEquals("https://server.example/api/stream/AAA", resolvedA.mediaUri)
    }

    // G: identity survives an app restart (fresh repository over the same store).
    @Test
    fun `exact ids survive restart and still resolve their own recordings`() = runBlocking {
        recents.recordPlayed(recording("AAA"))
        recents.recordPlayed(recording("BBB"))

        // "Restart": brand-new repository instances over the same database.
        val recentsAfterRestart = WearsicRecentRepository(context, recentTrackDao = db.recentTrackDao())
        val downloadsAfterRestart = WearsicDownloadRepository(context, downloadDao = db.downloadDao())
        val rows = recentsAfterRestart.recentTracksFlow.first()
        assertEquals(2, rows.size)

        val a = rows.first { it.id == "AAA" }
        val b = rows.first { it.id == "BBB" }
        assertEquals("AAA", a.mediaUri.substringAfterLast('/'))
        assertEquals("BBB", b.mediaUri.substringAfterLast('/'))
        // Clicking after restart resolves the exact ids (no title search).
        assertEquals("AAA", (downloadsAfterRestart.getDownloadedTrack(a.id) ?: a).id)
        assertEquals("BBB", (downloadsAfterRestart.getDownloadedTrack(b.id) ?: b).id)
    }

    private fun seedCompletedFile(id: String, autoCached: Boolean) {
        val dir = File(context.filesDir, "wearsic_downloads").apply { mkdirs() }
        val file = File(dir, "$id.m4a")
        file.writeBytes(ByteArray(1024) { 7 })
        runBlocking {
            downloads.recordQueued(recording(id), file.absolutePath, autoCached)
            downloads.markCompleted(id, file.length(), autoCached)
        }
    }
}
