package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.glassous.aime.data.model.ToolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_preferences")

class ToolPreferences(private val context: Context) {

    companion object {
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val WEB_SEARCH_RESULT_COUNT = intPreferencesKey("web_search_result_count")
        val WEB_SEARCH_ENGINE = stringPreferencesKey("web_search_engine")
        val TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
        val TAVILY_USE_PROXY = booleanPreferencesKey("tavily_use_proxy")
        val MUSIC_SEARCH_SOURCE = stringPreferencesKey("music_search_source")
    }

    // Get all visible tool names
    val visibleTools: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            ToolType.values().filter { tool ->
                val key = booleanPreferencesKey("tool_visibility_${tool.name}")
                preferences[key] ?: true // Default to true
            }.map { it.name }.toSet()
        }

    val webSearchEnabled: Flow<Boolean> = getToolVisibility("WEB_SEARCH")

    val webSearchResultCount: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[WEB_SEARCH_RESULT_COUNT] ?: 6
        }

    val webSearchEngine: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WEB_SEARCH_ENGINE] ?: "pear"
        }

    val tavilyApiKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[TAVILY_API_KEY] ?: ""
        }

    val tavilyUseProxy: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TAVILY_USE_PROXY] ?: false
        }

    val musicSearchSource: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[MUSIC_SEARCH_SOURCE] ?: "wy"
        }

    fun getToolVisibility(toolName: String): Flow<Boolean> {
        val key = booleanPreferencesKey("tool_visibility_$toolName")
        return context.dataStore.data
            .map { preferences ->
                preferences[key] ?: true // Default to true (visible)
            }
    }

    suspend fun setToolVisibility(toolName: String, visible: Boolean) {
        val key = booleanPreferencesKey("tool_visibility_$toolName")
        context.dataStore.edit { preferences ->
            preferences[key] = visible
        }
    }

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        // Legacy: map to visibility of WEB_SEARCH
        setToolVisibility("WEB_SEARCH", enabled)
    }

    suspend fun setWebSearchResultCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[WEB_SEARCH_RESULT_COUNT] = count
        }
    }

    suspend fun setWebSearchEngine(engine: String) {
        context.dataStore.edit { preferences ->
            preferences[WEB_SEARCH_ENGINE] = engine
        }
    }

    suspend fun setTavilyApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[TAVILY_API_KEY] = apiKey
        }
    }

    suspend fun setTavilyUseProxy(useProxy: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TAVILY_USE_PROXY] = useProxy
        }
    }

    suspend fun setMusicSearchSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[MUSIC_SEARCH_SOURCE] = source
        }
    }
}
