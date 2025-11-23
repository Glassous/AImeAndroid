package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

class SyncPreferences(private val context: Context) {
    companion object {
        private val UPLOAD_HISTORY_ENABLED = booleanPreferencesKey("upload_history_enabled")
        private val UPLOAD_MODEL_CONFIG_ENABLED = booleanPreferencesKey("upload_model_config_enabled")
        private val UPLOAD_SELECTED_MODEL_ENABLED = booleanPreferencesKey("upload_selected_model_enabled")
        private val UPLOAD_API_KEY_ENABLED = booleanPreferencesKey("upload_api_key_enabled")
    }

    val uploadHistoryEnabled: Flow<Boolean> = context.dataStore.data.map { it[UPLOAD_HISTORY_ENABLED] ?: true }
    val uploadModelConfigEnabled: Flow<Boolean> = context.dataStore.data.map { it[UPLOAD_MODEL_CONFIG_ENABLED] ?: true }
    val uploadSelectedModelEnabled: Flow<Boolean> = context.dataStore.data.map { it[UPLOAD_SELECTED_MODEL_ENABLED] ?: true }
    val uploadApiKeyEnabled: Flow<Boolean> = context.dataStore.data.map { it[UPLOAD_API_KEY_ENABLED] ?: false }

    suspend fun setUploadHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[UPLOAD_HISTORY_ENABLED] = enabled }
    }

    suspend fun setUploadModelConfigEnabled(enabled: Boolean) {
        context.dataStore.edit { it[UPLOAD_MODEL_CONFIG_ENABLED] = enabled }
    }
    suspend fun setUploadSelectedModelEnabled(enabled: Boolean) {
        context.dataStore.edit { it[UPLOAD_SELECTED_MODEL_ENABLED] = enabled }
    }

    suspend fun setUploadApiKeyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[UPLOAD_API_KEY_ENABLED] = enabled }
    }
}
