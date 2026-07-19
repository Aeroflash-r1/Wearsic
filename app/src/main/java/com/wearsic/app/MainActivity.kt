package com.wearsic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.wearsic.app.ui.navigation.Routes
import com.wearsic.app.ui.screen.PlayerScreen
import com.wearsic.app.ui.screen.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearsicTheme {
                WearAppNavigation()
            }
        }
    }
}

@Composable
private fun WearAppNavigation() {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.SEARCH,
    ) {
        composable(Routes.SEARCH) {
            SearchScreen(
                onTrackSelected = { track, allTracks ->
                    val app = context.applicationContext as WearsicApplication
                    val startIndex = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                    app.playbackManager.setQueue(allTracks, startIndex)
                    app.playbackManager.play()
                    navController.navigate(Routes.PLAYER)
                },
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
