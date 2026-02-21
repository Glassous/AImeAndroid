package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.glassous.aime.data.model.ToolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_preferences")

class ToolPreferences(private val context: Context) {

    companion object {
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val WEB_SEARCH_RESULT_COUNT = intPreferencesKey("web_search_result_count")
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
}
