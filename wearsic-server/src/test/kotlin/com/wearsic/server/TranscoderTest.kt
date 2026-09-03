package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get as routeGet
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the server-side AAC transcode pipeline with a FAKE ffmpeg that
 * simply copies stdin → stdout (a pass-through "transcode"). This validates
 * the process plumbing, the CDN→ffmpeg stdin pump, byte-offset skipping (used
 * by ExoPlayer seeks and the app's download resume) and response metadata —
 * without needing a real ffmpeg or YouTube access in CI.
 */
class TranscoderTest {

    /** 1 MB of deterministic bytes standing in for a source audio stream. */
    private val sourceBytes: ByteArray = ByteArray(1024 * 1024) { (it % 251).toByte() }

    private fun fakeFfmpeg(): File {
        val script = File.createTempFile("fake-ffmpeg", ".sh").apply {
            setExecutable(true)
            // Escaped string (not a raw template) so \$1 reaches bash intact.
            writeText(
                "#!/bin/bash\n" +
                    "if [ \"\$1\" = \"-version\" ]; then exit 0; fi\n" +
                    "exec cat\n"
            )
        }
        return script
    }

    /** A tiny local HTTP server standing in for the YouTube CDN. */
    private suspend fun startUpstream(): ApplicationEngine {
        val engine = embeddedServer(ServerCIO, port = 0) {
            routing {
                routeGet("/source") {
                    val range = call.request.headers[HttpHeaders.Range]
                    val start = if (range != null) {
                        Regex("bytes=(\\d+)-").find(range)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    } else {
                        0L
                    }
                    call.respondBytesWriter(
                        contentType = ContentType.parse("audio/webm"),
                        status = if (start > 0) HttpStatusCode.PartialContent else HttpStatusCode.OK
                    ) {
                        if (start > 0) {
                            call.response.header(HttpHeaders.ContentRange, "bytes $start-*")
                        }
                        writeFully(sourceBytes, start.toInt(), sourceBytes.size - start.toInt())
                    }
                }
            }
        }
        engine.start(wait = false)
        return engine
    }

    @Test
    fun `webm stream is routed through ffmpeg and bytes are served`() = runBlocking {
        val fake = fakeFfmpeg()
        Transcoder.detect(fake.absolutePath)
        assertTrue(Transcoder.available, "fake ffmpeg must be detected")

        // Real client used by the Transcoder to fetch from the upstream server.
        val transcoderClient = HttpClient(CIO)
        val transcoder = Transcoder(transcoderClient, ffmpegBinary = fake.absolutePath)

        val upstream = startUpstream()
        val port = upstream.resolvedConnectors().first().port
        val upstreamUrl = "http://127.0.0.1:$port/source"

        try {
            testApplication {
                application {
                    routing {
                        routeGet("/stream") {
                            transcoder.handle(call, upstreamUrl, call.request.headers[HttpHeaders.Range])
                        }
                    }
                }

                // No range → full stream, 200, AAC content type. (`client` here
                // is testApplication's in-process test client — no real sockets.)
                val full = client.get("/stream")
                assertEquals(HttpStatusCode.OK, full.status)
                assertEquals("audio/aac", full.headers[HttpHeaders.ContentType])
                val fullBody = full.body<ByteArray>()
                assertEquals(sourceBytes.size, fullBody.size)
                assertTrue(fullBody.contentEquals(sourceBytes), "full transcode body must equal source")

                // Byte offset → 206, Content-Range, body starts at the offset
                // (ExoPlayer seeks / download resume on transcoded songs).
                val partial = client.get("/stream") {
                    header(HttpHeaders.Range, "bytes=5000-")
                }
                assertEquals(HttpStatusCode.PartialContent, partial.status)
                assertEquals("bytes 5000-*", partial.headers[HttpHeaders.ContentRange])
                val partialBody = partial.body<ByteArray>()
                assertEquals(sourceBytes.size - 5000, partialBody.size)
                assertTrue(
                    partialBody.contentEquals(sourceBytes.copyOfRange(5000, sourceBytes.size)),
                    "partial body must start at the requested byte offset"
                )
            }
        } finally {
            upstream.stop(100, 500)
            transcoderClient.close()
        }
    }

    @Test
    fun `missing ffmpeg yields actionable 503`() = runBlocking {
        // Point at a binary that does not exist → detection fails.
        Transcoder.detect("/nonexistent/ffmpeg")
        assertTrue(!Transcoder.available)

        val transcoderClient = HttpClient(CIO)
        val transcoder = Transcoder(transcoderClient, ffmpegBinary = "/nonexistent/ffmpeg")

        try {
            testApplication {
                application {
                    // Mirror the real server so ErrorResponse serializes to JSON.
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
                    }
                    routing {
                        routeGet("/stream") {
                            transcoder.handle(call, "http://127.0.0.1:1/unused", call.request.headers[HttpHeaders.Range])
                        }
                    }
                }
                val resp = client.get("/stream")
                assertEquals(HttpStatusCode.ServiceUnavailable, resp.status)
                val body = resp.bodyAsText()
                assertTrue(body.contains("ffmpeg"), "503 must tell the user to install ffmpeg: $body")
            }
        } finally {
            transcoderClient.close()
        }
    }
}