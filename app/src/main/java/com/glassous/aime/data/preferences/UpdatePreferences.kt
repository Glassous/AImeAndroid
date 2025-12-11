package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "update_preferences")

class UpdatePreferences(private val context: Context) {
    companion object {
        private val AUTO_CHECK_UPDATE_ENABLED = booleanPreferencesKey("auto_check_update_enabled")
    }

    val autoCheckUpdateEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_CHECK_UPDATE_ENABLED] ?: true
        }

    suspend fun setAutoCheckUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CHECK_UPDATE_ENABLED] = enabled
        }
    }
}
