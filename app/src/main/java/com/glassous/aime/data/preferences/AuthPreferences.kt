package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_preferences")

class AuthPreferences(private val context: Context) {
    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val EMAIL = stringPreferencesKey("email")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    val email: Flow<String?> = context.dataStore.data.map { it[EMAIL] }

    suspend fun setSession(accessTokenValue: String?, refreshTokenValue: String?, userIdValue: String?, emailValue: String?) {
        context.dataStore.edit { preferences ->
            if (accessTokenValue == null) preferences.remove(ACCESS_TOKEN) else preferences[ACCESS_TOKEN] = accessTokenValue
            if (refreshTokenValue == null) preferences.remove(REFRESH_TOKEN) else preferences[REFRESH_TOKEN] = refreshTokenValue
            if (userIdValue == null) preferences.remove(USER_ID) else preferences[USER_ID] = userIdValue
            if (emailValue == null) preferences.remove(EMAIL) else preferences[EMAIL] = emailValue
        }
    }
}

