package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException
import java.net.URI

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wearsic_settings")

class WearsicPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_CACHE_LIMIT = intPreferencesKey("cache_limit_mb")
        val KEY_AUTO_CACHE_ENABLED = booleanPreferencesKey("auto_cache_enabled")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_HIDDEN_PLAYLISTS = stringSetPreferencesKey("hidden_playlists")
        val KEY_OFFLINE_LIMIT = intPreferencesKey("offline_song_limit")
        /** No server is preconfigured; users enter their own URL in Settings. */
        const val DEFAULT_SERVER_URL = ""
        const val DEFAULT_CACHE_LIMIT = 32
        const val DEFAULT_OFFLINE_LIMIT = 15
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
        }

    val cacheLimitFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[KEY_CACHE_LIMIT] ?: DEFAULT_CACHE_LIMIT
        }

    suspend fun saveServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
        }
    }

    suspend fun saveCacheLimit(limitMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CACHE_LIMIT] = limitMb
        }
    }

    val autoCacheEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[KEY_AUTO_CACHE_ENABLED] ?: true
        }

    suspend fun setAutoCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_CACHE_ENABLED] = enabled
        }
    }

    val offlineLimitFlow: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[KEY_OFFLINE_LIMIT] ?: DEFAULT_OFFLINE_LIMIT
        }

    suspend fun saveOfflineLimit(limitSongs: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OFFLINE_LIMIT] = limitSongs.coerceIn(5, 200)
        }
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[KEY_API_KEY] ?: ""
        }

    suspend fun getApiKey(): String {
        return context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences -> preferences[KEY_API_KEY] ?: "" }
            .first()
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_API_KEY] = key.trim()
        }
    }

    fun isValidServerUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = URI.create(url.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    val hiddenPlaylistsFlow: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[KEY_HIDDEN_PLAYLISTS] ?: emptySet()
        }

    suspend fun toggleHiddenPlaylist(id: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_HIDDEN_PLAYLISTS] ?: emptySet()
            preferences[KEY_HIDDEN_PLAYLISTS] =
                if (id in current) current - id else current + id
        }
    }
}
