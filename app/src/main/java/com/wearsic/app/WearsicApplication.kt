package com.wearsic.app

import android.app.Application
import com.wearsic.app.data.api.WearsicApi
import com.wearsic.app.data.repository.WearsicRepository
import com.wearsic.app.playback.PlaybackManager

class WearsicApplication : Application() {

    val repository: WearsicRepository by lazy {
        val api = WearsicApi.create("http://localhost:8080")
        WearsicRepository(api)
    }

    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(this, repository)
    }
}
