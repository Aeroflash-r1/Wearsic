package com.example.di

import android.content.Context
import com.example.data.WearsicDownloadRepository
import com.example.data.WearsicMusicRepository
import com.example.data.WearsicPreferencesRepository
import com.example.data.WearsicRecentRepository
import com.example.network.WearsicApiClient
import com.example.network.WearsicHttpApiClient

class AppContainer(private val context: Context) {

    /**
     * The single preferences instance for the whole app. DataStore is
     * process-singleton per file, but sharing one repository (instead of each
     * consumer constructing its own) keeps one source of truth and one set of
     * flow subscriptions.
     */
    val preferencesRepository: WearsicPreferencesRepository by lazy { WearsicPreferencesRepository(context) }

    private val apiClient: WearsicApiClient by lazy { WearsicHttpApiClient() }

    val musicRepository by lazy { WearsicMusicRepository(context, preferencesRepository, apiClient) }
    val downloadRepository by lazy { WearsicDownloadRepository(context) }
    val recentRepository by lazy { WearsicRecentRepository(context) }
}
