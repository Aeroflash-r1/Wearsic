package com.wearsic.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wearsic_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        private const val DEFAULT_BACKEND_URL = "http://localhost:8080"
    }

    val backendUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BACKEND_URL_KEY] ?: DEFAULT_BACKEND_URL
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKEND_URL_KEY] = url
        }
    }
}
