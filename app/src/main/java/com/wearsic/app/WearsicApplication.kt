package com.wearsic.app

import android.app.Application
import com.wearsic.app.data.api.WearsicApi
import com.wearsic.app.data.local.SettingsDataStore
import com.wearsic.app.data.repository.WearsicRepository
import com.wearsic.app.playback.PlaybackManager

class WearsicApplication : Application() {

    val settingsDataStore by lazy { SettingsDataStore(this) }

    var repository: WearsicRepository = createRepository("http://localhost:8080")
        private set

    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(this, repository)
    }

    private fun createRepository(baseUrl: String): WearsicRepository {
        val api = WearsicApi.create(baseUrl)
        return WearsicRepository(api)
    }

    fun updateRepository(baseUrl: String) {
        repository = createRepository(baseUrl)
        playbackManager.updateRepository(repository)
    }
}
