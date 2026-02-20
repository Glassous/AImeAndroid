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
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val ENABLE_DYNAMIC_DATE = booleanPreferencesKey("enable_dynamic_date")
        private val ENABLE_DYNAMIC_TIMESTAMP = booleanPreferencesKey("enable_dynamic_timestamp")
        private val ENABLE_DYNAMIC_LOCATION = booleanPreferencesKey("enable_dynamic_location")
        private val ENABLE_DYNAMIC_DEVICE_MODEL = booleanPreferencesKey("enable_dynamic_device_model")
        private val ENABLE_DYNAMIC_LANGUAGE = booleanPreferencesKey("enable_dynamic_language")
    }

    val selectedModelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_ID]
        }

    val systemPrompt: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SYSTEM_PROMPT] ?: ""
        }

    val enableDynamicDate: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENABLE_DYNAMIC_DATE] ?: false
        }

    val enableDynamicTimestamp: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENABLE_DYNAMIC_TIMESTAMP] ?: false
        }

    val enableDynamicLocation: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENABLE_DYNAMIC_LOCATION] ?: false
        }

    val enableDynamicDeviceModel: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENABLE_DYNAMIC_DEVICE_MODEL] ?: false
        }

    val enableDynamicLanguage: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENABLE_DYNAMIC_LANGUAGE] ?: false
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

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setEnableDynamicDate(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DYNAMIC_DATE] = enabled
        }
    }

    suspend fun setEnableDynamicTimestamp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DYNAMIC_TIMESTAMP] = enabled
        }
    }

    suspend fun setEnableDynamicLocation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DYNAMIC_LOCATION] = enabled
        }
    }

    suspend fun setEnableDynamicDeviceModel(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DYNAMIC_DEVICE_MODEL] = enabled
        }
    }

    suspend fun setEnableDynamicLanguage(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DYNAMIC_LANGUAGE] = enabled
        }
    }
}
