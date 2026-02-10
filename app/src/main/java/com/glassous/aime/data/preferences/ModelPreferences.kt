package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "model_preferences")

class ModelPreferences(private val context: Context) {

    companion object {
        private val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        private val TITLE_GENERATION_MODEL_ID = stringPreferencesKey("title_generation_model_id")
    }

    val selectedModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_ID]
        }

    val titleGenerationModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TITLE_GENERATION_MODEL_ID]
        }

    suspend fun setSelectedModelId(modelId: String?) {
        context.dataStore.edit { preferences ->
            val current = preferences[SELECTED_MODEL_ID]
            if (modelId == null) {
                if (current != null) preferences.remove(SELECTED_MODEL_ID)
            } else {
                if (current != modelId) preferences[SELECTED_MODEL_ID] = modelId
            }
        }
    }

    suspend fun setTitleGenerationModelId(modelId: String?) {
        context.dataStore.edit { preferences ->
            if (modelId == null) {
                preferences.remove(TITLE_GENERATION_MODEL_ID)
            } else {
                preferences[TITLE_GENERATION_MODEL_ID] = modelId
            }
        }
    }
}
