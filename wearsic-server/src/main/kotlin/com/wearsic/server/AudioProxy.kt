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

class AudioProxy(private val extractor: ExtractorService, private val client: HttpClient) {

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

        try {
            client.prepareGet(target.url) {
                rangeHeader?.let { header(HttpHeaders.Range, it) }
            }.execute { upstream ->
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
