package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

class ThemePreferences(private val context: Context) {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("selected_theme")
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
    }
    
    val selectedTheme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: THEME_SYSTEM
        }
    
    val minimalMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MINIMAL_MODE] ?: false
        }
    
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }
    
    suspend fun setMinimalMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MINIMAL_MODE] = enabled
        }
    }
}