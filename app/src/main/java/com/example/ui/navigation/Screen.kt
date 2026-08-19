package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Player : Screen("player")
    data object Volume : Screen("volume")
    data object Settings : Screen("settings")
    data object Downloads : Screen("downloads")
    data object Playlists : Screen("playlists")
    data object Artists : Screen("artists")
}
