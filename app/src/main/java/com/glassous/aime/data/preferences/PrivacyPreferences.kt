package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_preferences")

class PrivacyPreferences(private val context: Context) {
    companion object {
        private val PRIVACY_POLICY_AGREED = booleanPreferencesKey("privacy_policy_agreed")
    }

    val isPrivacyPolicyAgreed: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PRIVACY_POLICY_AGREED] ?: false
        }

    suspend fun setPrivacyPolicyAgreed(agreed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRIVACY_POLICY_AGREED] = agreed
        }
    }
}
