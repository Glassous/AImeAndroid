package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.autoSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

class AutoSyncPreferences(private val context: Context) {
    companion object {
        private val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    }

    val autoSyncEnabled: Flow<Boolean> = context.autoSyncDataStore.data
        .map { prefs ->
            prefs[AUTO_SYNC_ENABLED] ?: false
        }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.autoSyncDataStore.edit { prefs ->
            prefs[AUTO_SYNC_ENABLED] = enabled
        }
    }
    
    suspend fun isAutoSyncEnabled(): Boolean {
        return autoSyncEnabled.first()
    }
}