package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.contextDataStore: DataStore<Preferences> by preferencesDataStore(name = "context_preferences")

class ContextPreferences(private val context: Context) {
    companion object {
        private val MAX_CONTEXT_MESSAGES = intPreferencesKey("max_context_messages")
        private const val DEFAULT_LIMIT = 5 // 5 条为默认
    }

    // 约定：值 <= 0 表示无限
    val maxContextMessages: Flow<Int> = context.contextDataStore.data
        .map { prefs ->
            prefs[MAX_CONTEXT_MESSAGES] ?: DEFAULT_LIMIT
        }

    suspend fun setMaxContextMessages(limit: Int) {
        context.contextDataStore.edit { prefs ->
            prefs[MAX_CONTEXT_MESSAGES] = limit
        }
    }
}