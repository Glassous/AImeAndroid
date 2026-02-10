package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "model_preferences")

class ModelPreferences(private val context: Context) {

    companion object {
        private val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        private val TITLE_GENERATION_MODEL_ID = stringPreferencesKey("title_generation_model_id")
        private val TITLE_GENERATION_CONTEXT_STRATEGY = intPreferencesKey("title_generation_context_strategy")
        private val TITLE_GENERATION_CONTEXT_N = intPreferencesKey("title_generation_context_n")
        private val TITLE_GENERATION_AUTO_GENERATE = booleanPreferencesKey("title_generation_auto_generate")
    }

    val selectedModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_ID]
        }

    val titleGenerationModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TITLE_GENERATION_MODEL_ID]
        }

    val titleGenerationContextStrategy: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[TITLE_GENERATION_CONTEXT_STRATEGY] ?: 0
        }

    val titleGenerationContextN: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[TITLE_GENERATION_CONTEXT_N] ?: 20
        }

    val titleGenerationAutoGenerate: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TITLE_GENERATION_AUTO_GENERATE] ?: false
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

    suspend fun setTitleGenerationContextStrategy(strategy: Int) {
        context.dataStore.edit { preferences ->
            preferences[TITLE_GENERATION_CONTEXT_STRATEGY] = strategy
        }
    }

    suspend fun setTitleGenerationContextN(n: Int) {
        context.dataStore.edit { preferences ->
            preferences[TITLE_GENERATION_CONTEXT_N] = n
        }
    }

    suspend fun setTitleGenerationAutoGenerate(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TITLE_GENERATION_AUTO_GENERATE] = enabled
        }
    }
}
