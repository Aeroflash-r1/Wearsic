package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.copyAndClose
import org.slf4j.LoggerFactory

class AudioProxy(
    private val extractor: YoutubeMetadataClient,
    private val client: HttpClient,
    val transcoder: Transcoder = Transcoder(client),
) {

    private val logger = LoggerFactory.getLogger(AudioProxy::class.java)

    suspend fun handle(call: ApplicationCall, videoId: String) {
        val target = try {
            extractor.streamTarget(videoId)
        } catch (e: Exception) {
            // Extraction blew up (most commonly YouTube's bot-wall LOGIN_REQUIRED
            // when no cookie is set). The v1 server answered 503 with an
            // actionable message here — match that contract instead of leaking
            // a 500 + stack trace to the watch.
            logger.warn("Audio extraction failed for {}: {}", videoId, e.message)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    "YouTube requires authentication for audio extraction. Add your YouTube cookie in the app Settings."
                )
            )
            return
        }
        if (target == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("No playable audio stream found for $videoId"))
            return
        }

        val rangeHeader = call.request.header(HttpHeaders.Range)

        // Songs without a native AAC stream (WebM/Opus or Vorbis) are converted
        // to AAC-LC on the server so the watch always gets hardware-decodable
        // audio. When YouTube already offers AAC (audio/mp4 — the common case),
        // pass it through untouched with zero CPU cost.
        if (transcoder.needsTranscode(target.mimeType)) {
            transcoder.handle(call, target.url, rangeHeader)
            return
        }

        try {
            client.prepareGet(target.url) {
                // Always ask upstream for a byte range: YouTube's CDN throttles
                // non-range (200) audio responses and closes them after ~256KB,
                // which made the first play of a song stall after ~15s of audio.
                // Ranged requests stream the whole file at full speed. Forward the
                // client's Range when present, otherwise request from byte 0.
                header(HttpHeaders.Range, rangeHeader ?: "bytes=0-")
            }.execute { upstream ->
                // A 4xx/5xx from the CDN must surface as a real error to the
                // watch — previously the error body was streamed as if it
                // were audio, leaving the player to fail cryptically.
                if (upstream.status.value >= 400) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse("Upstream audio unavailable (HTTP ${upstream.status.value})")
                    )
                    return@execute
                }
                upstream.headers[HttpHeaders.ContentRange]?.let { call.response.header(HttpHeaders.ContentRange, it) }
                call.response.header(HttpHeaders.AcceptRanges, "bytes")

                val status = if (upstream.status == HttpStatusCode.PartialContent) {
                    HttpStatusCode.PartialContent
                } else {
                    HttpStatusCode.OK
                }

                call.respondBytesWriter(contentType = io.ktor.http.ContentType.parse(target.mimeType), status = status) {
                    upstream.bodyAsChannel().copyAndClose(this)
                }
            }
        } catch (e: Exception) {
            // Client disconnected/skipped mid-stream, or upstream failed —
            // not worth a scary 500, this happens routinely on track skips.
            logger.info("Stream proxy ended early for {}: {}", videoId, e.message)
        }
    }
}
