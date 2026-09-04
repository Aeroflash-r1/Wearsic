package com.wearsic.server

/**
 * Abstraction over the music-metadata source used by /api/search. Exists so
 * the search orchestration can be unit-tested without network access.
 */
interface MetadataSource {
    /** Search iTunes for songs matching [query]. Empty when nothing usable. */
    suspend fun searchSongs(query: String, limit: Int = 10): List<ITunesTrack>

    /** Recover one track's metadata by its iTunes numeric id (surrogate recovery). */
    suspend fun lookupTrack(trackId: Long): ITunesTrack?

    /** Convert an iTunes track to the wire DTO (videoId = surrogate "it:<id>"). */
    fun toTrackDto(track: ITunesTrack): TrackDto
}

/**
 * Abstraction over the YouTube extraction layer (NewPipeExtractor). Exists so
 * TrackMatcher, AudioProxy, the routes and tests can work against canned
 * results without any network access.
 */
interface YoutubeMetadataClient {
    /** YouTube search with the same dedup/caching semantics as production. */
    suspend fun search(query: String): List<TrackDto>

    /** Resolve a playable stream target for [videoId] (URL + MIME type). */
    suspend fun streamTarget(videoId: String): StreamTarget?

    /** Autocomplete suggestions for a prefix. */
    suspend fun suggestions(prefix: String): List<String>

    /** Songs related to [videoId] (radio), filtered to <=10-minute tracks. */
    suspend fun related(videoId: String): List<TrackDto>

    /** Album search (returns playlist URLs as ids, per the API contract). */
    suspend fun searchAlbums(query: String): List<AlbumDto>

    /** Tracks of a playlist/album URL (max 10, per the API contract). */
    suspend fun playlistByUrl(url: String): PlaylistTracksResponse?
}
