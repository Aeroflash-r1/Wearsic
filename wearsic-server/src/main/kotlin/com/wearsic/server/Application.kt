package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.security.MessageDigest

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val dbPath = System.getenv("WEARSIC_DB_PATH") ?: "wearsic.db"

    val database = Database(dbPath)
    YoutubeSession.init(database)
    // v1.4.4: wipe matches persisted by older, version-blind matcher builds
    // so every song re-matches with the fixed logic once.
    database.clearStaleMatchesOnce("matcher_version_wipe", "1.4.4")
    val gateway = YoutubeGateway()
    val proxyClient = HttpClient(CIO)

    // Detect ffmpeg so songs without native AAC can be converted server-side.
    // Without it those rare songs answer 503 with install guidance instead.
    val transcoder = Transcoder(proxyClient)
    if (!transcoder.available) {
        System.err.println("WARNING: ffmpeg not found on PATH — songs YouTube only offers in Opus/WebM " +
            "will return 503. Install it in Termux with: pkg install ffmpeg")
    }

    val audioProxy = AudioProxy(gateway, proxyClient, transcoder)
    val apiKey = System.getenv("WEARSIC_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }

    // /api/search is iTunes-first (fast metadata, no YouTube round trip in the
    // request path); the orchestrator matches results to real YouTube videos
    // and pre-resolves their streams in the background so taps are instant.
    val iTunes = ITunesService()
    val matcher = TrackMatcher(gateway)
    // Persist surrogate -> videoId matches so saved songs replay instantly
    // after a restart instead of re-running the YouTube match path.
    val searchOrchestrator = MetadataSearchOrchestrator(
        iTunes, gateway, matcher,
        persistentMatches = object : MetadataSearchOrchestrator.MatchPersistence {
            override fun getMatchedVideoId(surrogateId: String): String? =
                database.getMatchedVideoId(surrogateId)
            override fun putMatchedVideoId(surrogateId: String, videoId: String) =
                database.putMatchedVideoId(surrogateId, videoId)
        },
    )

    if (apiKey == null) {
        System.err.println(
            "WARNING: WEARSIC_API_KEY is not set — the server is OPEN to anyone who can reach it. " +
                "Fine for private LAN/Tailscale use; REQUIRED for any public/tunnel deployment."
        )
    }

    embeddedServer(ServerCIO, port = port, host = "0.0.0.0") {
        module(gateway, database, audioProxy, apiKey, searchOrchestrator)
    }.start(wait = true)
}

fun Application.module(
    gateway: YoutubeMetadataClient,
    database: Database,
    audioProxy: AudioProxy,
    apiKey: String?,
    searchOrchestrator: MetadataSearchOrchestrator,
    transcoder: Transcoder = audioProxy.transcoder,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging) { level = Level.INFO }

    // Consistent JSON error surface for every failure mode — malformed JSON
    // bodies, unknown routes, and unexpected exceptions all respond with the
    // project's ErrorResponse DTO instead of an empty 500.
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Let the auth interceptor's responses pass through untouched.
            if (call.response.status() == HttpStatusCode.Unauthorized) {
                return@exception
            }
            val (status, message) = when (cause) {
                // Thrown by call.receive<>() on malformed/missing bodies or
                // undecodable JSON (cause chain carries the details; the
                // client only ever sees the generic message).
                is BadRequestException, is SerializationException,
                is IllegalArgumentException,
                -> mapRequestError(cause)
                else -> {
                    call.application.log.error("Unhandled exception on ${call.request.uri}", cause)
                    HttpStatusCode.InternalServerError to "Internal server error"
                }
            }
            call.respond(status, ErrorResponse(message))
        }
        // The catch-all exception handler above already turns unhandled
        // NotFound responses into JSON where possible; this status handler
        // covers framework-level 404s. If ContentNegotiation cannot express
        // the response the raw body is an acceptable, non-sensitive fallback.
        status(HttpStatusCode.NotFound) { call, _ ->
            if (call.response.status() == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No such endpoint: ${call.request.uri}"))
            }
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(transcoderAvailable = transcoder.available))
        }

        route("/api") {
            // Shared-secret auth for everything under /api/* — /health stays public.
            // MessageDigest.isEqual is a constant-time comparison so response
            // timing cannot leak the key byte-by-byte.
            intercept(ApplicationCallPipeline.Plugins) {
                if (apiKey != null) {
                    val provided = call.request.headers["X-Wearsic-Key"]
                    val providedBytes = provided?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                    val ok = MessageDigest.isEqual(providedBytes, apiKey.toByteArray(Charsets.UTF_8))
                    if (!ok) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid API key"))
                        finish()
                    }
                }
            }

            // Expensive endpoints (extraction + CDN traffic + possible ffmpeg
            // CPU) get a lightweight per-client rate limit. This mainly
            // protects the OPEN-server configuration (no API key) from
            // becoming a free YouTube proxy; with auth enabled the shared
            // secret is the primary control and this is defense in depth.
            // Limits are documented in README.md ("Rate limiting").
            val streamLimiter = RateLimiter(
                permitsPerMinute = 30.0,
                burstCapacity = 12,
            )

            get("/stream/{videoId}") {
                val requestedId = call.parameters["videoId"]!!
                val clientKey = call.request.headers["X-Wearsic-Key"]
                    ?: call.request.host()
                    ?: "unknown"
                if (!streamLimiter.tryAcquire(clientKey)) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Too many stream requests; slow down and retry shortly")
                    )
                    return@get
                }
                // Resolves a surrogate iTunes id ("it:12345") to a real
                // YouTube videoId — usually already cached from the background
                // prefetch kicked off at search time, or recovered from iTunes
                // by id when the server restarted since. Real YouTube ids
                // (related/playlist results) pass through unchanged.
                val realVideoId = searchOrchestrator.resolveStreamVideoId(requestedId)
                if (realVideoId == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Could not match '$requestedId' to a playable source")
                    )
                    return@get
                }
                audioProxy.handle(call, realVideoId)
            }

            get("/search") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(SearchResponse(searchOrchestrator.search(q)))
            }

            get("/suggestions") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(SuggestionsResponse(gateway.suggestions(q)))
            }

            get("/related/{videoId}") {
                val requestedId = call.parameters["videoId"]!!
                // RADIO: the app asks for songs related to the CURRENT track,
                // which may be a surrogate iTunes id ("it:12345") when it came
                // from search. Resolve it to the real YouTube video first so
                // NewPipeExtractor always gets a real id.
                val realVideoId = searchOrchestrator.resolveStreamVideoId(requestedId)
                if (realVideoId == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Could not match '$requestedId' to a playable source"))
                    return@get
                }
                call.respond(RelatedResponse(gateway.related(realVideoId)))
            }

            get("/search/albums") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(gateway.searchAlbums(q))
            }

            get("/playlist") {
                val url = call.request.queryParameters["url"]
                if (url.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' query parameter"))
                    return@get
                }
                val result = gateway.playlistByUrl(url)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Could not resolve playlist"))
                } else {
                    call.respond(result)
                }
            }

            // ---- Favorites ----
            get("/favorites") {
                call.respond(database.listFavorites())
            }
            post("/favorites") {
                val track = call.receive<TrackDto>()
                database.addFavorite(track)
                call.respond(HttpStatusCode.Created, track)
            }
            delete("/favorites/{videoId}") {
                database.removeFavorite(call.parameters["videoId"]!!)
                call.respond(HttpStatusCode.NoContent)
            }

            // ---- Playlists ----
            get("/playlists") {
                call.respond(database.listPlaylists())
            }
            post("/playlists") {
                val req = call.receive<CreatePlaylistRequest>()
                call.respond(HttpStatusCode.Created, database.createPlaylist(req.name))
            }
            get("/playlists/{id}") {
                val result = database.getPlaylistTracks(call.parameters["id"]!!)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Playlist not found"))
                } else {
                    call.respond(result)
                }
            }
            post("/playlists/{id}/tracks") {
                val track = call.receive<TrackDto>()
                database.addTrackToPlaylist(call.parameters["id"]!!, track)
                call.respond(HttpStatusCode.Created, track)
            }
            delete("/playlists/{id}/tracks/{videoId}") {
                database.deletePlaylistTrack(call.parameters["id"]!!, call.parameters["videoId"]!!)
                call.respond(HttpStatusCode.NoContent)
            }

            // ---- YouTube cookie runtime config (bot-challenge workaround) ----
            get("/config/youtube-cookie") {
                call.respond(YoutubeCookieStatus(hasCookie = YoutubeSession.hasCookie()))
            }
            post("/config/youtube-cookie") {
                val req = call.receive<YoutubeCookieRequest>()
                YoutubeSession.cookie = req.cookie
                call.respond(YoutubeCookieStatus(hasCookie = YoutubeSession.hasCookie()))
            }
        }
    }
}

/** Maps request-parsing failures to the (400, message) pair. */
private fun mapRequestError(cause: Throwable?): Pair<HttpStatusCode, String> = when (cause) {
    null -> HttpStatusCode.BadRequest to "Invalid request"
    is SerializationException -> HttpStatusCode.BadRequest to "Malformed JSON body"
    is BadRequestException -> HttpStatusCode.BadRequest to "Invalid request"
    is IllegalArgumentException -> HttpStatusCode.BadRequest to "Invalid request"
    else -> HttpStatusCode.BadRequest to "Invalid request"
}
