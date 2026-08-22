package com.example.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Player : Screen("player")
    data object Volume : Screen("volume")
    data object Settings : Screen("settings")
    data object Downloads : Screen("downloads")
    data object Queue : Screen("queue")
    data object Playlists : Screen("playlists")
    data object Favorites : Screen("favorites")
    data object PlaylistDetail : Screen("playlist/{id}/{name}") {
        fun createRoute(id: String, name: String): String = "playlist/$id/${Uri.encode(name)}"
    }
    data object Artists : Screen("artists")
}
