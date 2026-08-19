package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        const val DEFAULT_SERVER_URL = ""
        const val DEFAULT_CACHE_LIMIT = 128
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
}
