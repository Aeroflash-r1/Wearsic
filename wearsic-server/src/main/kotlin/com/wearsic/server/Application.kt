package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val dbPath = System.getenv("WEARSIC_DB_PATH") ?: "wearsic.db"

    val database = Database(dbPath)
    YoutubeSession.init(database)
    val extractor = ExtractorService()
    val proxyClient = HttpClient(CIO)
    val audioProxy = AudioProxy(extractor, proxyClient)
    val apiKey = System.getenv("WEARSIC_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }

    // Detect ffmpeg so songs without native AAC can be converted server-side.
    // Without it those rare songs answer 503 with install guidance instead.
    Transcoder.detect()
    if (!Transcoder.available) {
        System.err.println("WARNING: ffmpeg not found on PATH — songs YouTube only offers in Opus/WebM " +
            "will return 503. Install it in Termux with: pkg install ffmpeg")
    }

    embeddedServer(ServerCIO, port = port, host = "0.0.0.0") {
        module(extractor, database, audioProxy, apiKey)
    }.start(wait = true)
}

fun Application.module(
    extractor: ExtractorService,
    database: Database,
    audioProxy: AudioProxy,
    apiKey: String?,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging) { level = Level.INFO }

    routing {
        get("/health") {
            call.respond(HealthResponse(transcoderAvailable = Transcoder.available))
        }

        route("/api") {
            // Shared-secret auth for everything under /api/* — /health stays public.
            intercept(ApplicationCallPipeline.Plugins) {
                if (apiKey != null) {
                    val provided = call.request.headers["X-Wearsic-Key"]
                    if (provided != apiKey) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid API key"))
                        finish()
                    }
                }
            }

            get("/search") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(SearchResponse(extractor.search(q)))
            }

            get("/suggestions") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(SuggestionsResponse(extractor.suggestions(q)))
            }

            get("/related/{videoId}") {
                val videoId = call.parameters["videoId"]!!
                call.respond(RelatedResponse(extractor.related(videoId)))
            }

            get("/search/albums") {
                val q = call.request.queryParameters["q"].orEmpty()
                call.respond(extractor.searchAlbums(q))
            }

            get("/playlist") {
                val url = call.request.queryParameters["url"]
                if (url.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' query parameter"))
                    return@get
                }
                val result = extractor.playlistByUrl(url)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Could not resolve playlist"))
                } else {
                    call.respond(result)
                }
            }

            get("/stream/{videoId}") {
                audioProxy.handle(call, call.parameters["videoId"]!!)
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
