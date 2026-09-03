package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * On-the-fly Opus/Vorbis -> AAC-LC converter for songs YouTube doesn't offer
 * in AAC (rare — most videos ship itag 140 AAC-LC, which is passed through
 * untouched with zero CPU cost). The watch's SoC decodes AAC in hardware, so
 * converting the rare WebM-only song on the server keeps EVERY stream
 * hardware-decodable and battery-cheap on the watch.
 *
 * The transcode runs only when the resolved stream's MIME type is not
 * audio/mp4. It pipes the YouTube CDN bytes into an ffmpeg process and streams
 * the ADTS AAC output straight to the client — no temp files, no download
 * latency beyond the encoder's initial buffer.
 */
class Transcoder(
    private val client: HttpClient,
    private val ffmpegBinary: String = "ffmpeg",
) {

    private val logger = LoggerFactory.getLogger(Transcoder::class.java)

    companion object {
        /** True when an `ffmpeg` binary was found at startup. */
        @Volatile
        var available: Boolean = false
            private set

        /** Cap concurrent transcodes so a handful of simultaneous plays can't
         *  peg the phone's CPU (transcoding is the only CPU-heavy path). */
        private val slots = Semaphore(2)

        fun detect(binary: String = "ffmpeg") {
            available = runCatching {
                val p = ProcessBuilder(binary, "-version")
                    .redirectErrorStream(true)
                    .start()
                val exited = p.waitFor(5, TimeUnit.SECONDS)
                p.destroy()
                exited && p.exitValue() == 0
            }.getOrDefault(false)
        }
    }

    /** WebM/Ogg (Opus/Vorbis) needs conversion; MP4 (AAC-LC) is already ideal. */
    fun needsTranscode(mimeType: String): Boolean =
        !mimeType.contains("mp4", ignoreCase = true)

    suspend fun handle(call: ApplicationCall, sourceUrl: String, rangeHeader: String?) {
        if (!available) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("This song needs server-side AAC conversion, but ffmpeg isn't installed. In Termux run: pkg install ffmpeg")
            )
            return
        }

        // Streaming conversion honors byte offsets by discarding that much
        // output (ADTS bytes are continuous), so ExoPlayer seeks and the app's
        // download-resume both stay correct even on transcoded songs.
        val startByte = parseRangeStart(rangeHeader)

        slots.withPermit {
            val process = ProcessBuilder(
                ffmpegBinary, "-hide_banner", "-loglevel", "error",
                "-i", "pipe:0",
                "-vn", "-c:a", "aac", "-b:a", "128k", "-f", "adts", "pipe:1"
            ).start()
            val stdin: OutputStream = process.outputStream
            val stdout: InputStream = process.inputStream

            try {
                client.prepareGet(sourceUrl) {
                    // Always ranged upstream: YouTube's CDN throttles and closes
                    // non-range audio responses around 256KB (same fix as the
                    // passthrough proxy).
                    header(HttpHeaders.Range, "bytes=0-")
                }.execute { upstream ->
                    if (upstream.status.value >= 400) {
                        call.respond(
                            HttpStatusCode.BadGateway,
                            ErrorResponse("Upstream audio unavailable (HTTP ${upstream.status.value})")
                        )
                        return@execute
                    }
                    val upstreamChannel = upstream.bodyAsChannel()

                    val status = if (startByte > 0) {
                        HttpStatusCode.PartialContent
                    } else {
                        HttpStatusCode.OK
                    }
                    if (startByte > 0) {
                        call.response.header(HttpHeaders.ContentRange, "bytes $startByte-*")
                    }
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")

                    call.respondBytesWriter(
                        contentType = ContentType.parse("audio/aac"),
                        status = status
                    ) {
                        coroutineScope {
                            // Pump CDN bytes into ffmpeg's stdin.
                            val pump = launch(Dispatchers.IO) {
                                try {
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = upstreamChannel.readAvailable(buf)
                                        if (n < 0) break
                                        stdin.write(buf, 0, n)
                                    }
                                } catch (e: Exception) {
                                    // Upstream died; ffmpeg sees EOF and flushes what it has.
                                } finally {
                                    runCatching { stdin.close() }
                                }
                            }

                            // Read ffmpeg stdout, discard `startByte` bytes, stream the rest.
                            val buf = ByteArray(64 * 1024)
                            var skipped = 0L
                            while (skipped < startByte) {
                                val n = withContext(Dispatchers.IO) { stdout.read(buf) }
                                if (n < 0) break
                                skipped += n
                                if (skipped > startByte) {
                                    // Chunk straddles the offset: keep the tail.
                                    val tail = buf.copyOfRange(
                                        (n - (skipped - startByte)).toInt(),
                                        n
                                    )
                                    writeFully(tail)
                                }
                            }
                            while (true) {
                                val n = withContext(Dispatchers.IO) { stdout.read(buf) }
                                if (n < 0) break
                                writeFully(buf, 0, n)
                            }
                            pump.join()
                        }
                    }
                }
            } catch (e: Exception) {
                // Client disconnected/skipped, or ffmpeg failed — routine on skips.
                logger.info("Transcode proxy ended early: {}", e.message)
            } finally {
                runCatching { process.destroy() }
            }
        }
    }

    /** Parses "bytes=N-..." → N (0 when absent/malformed). Suffix ranges like
     *  "bytes=-500" are treated as 0 (serve from the start). */
    private fun parseRangeStart(rangeHeader: String?): Long {
        if (rangeHeader.isNullOrBlank()) return 0L
        val m = Regex("bytes=(\\d+)-").find(rangeHeader) ?: return 0L
        return m.groupValues[1].toLongOrNull() ?: 0L
    }
}