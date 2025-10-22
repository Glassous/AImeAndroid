package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ossDataStore: DataStore<Preferences> by preferencesDataStore(name = "oss_preferences")

class OssPreferences(private val context: Context) {
    companion object {
        private val REGION_ID = stringPreferencesKey("oss_region_id")
        private val ENDPOINT = stringPreferencesKey("oss_endpoint")
        private val BUCKET = stringPreferencesKey("oss_bucket")
        private val ACCESS_KEY_ID = stringPreferencesKey("oss_access_key_id")
        private val ACCESS_KEY_SECRET = stringPreferencesKey("oss_access_key_secret")

    }

    val regionId: Flow<String?> = context.ossDataStore.data.map { it[REGION_ID] }
    val endpoint: Flow<String?> = context.ossDataStore.data.map { it[ENDPOINT] }
    val bucket: Flow<String?> = context.ossDataStore.data.map { it[BUCKET] }
    val accessKeyId: Flow<String?> = context.ossDataStore.data.map { it[ACCESS_KEY_ID] }
    val accessKeySecret: Flow<String?> = context.ossDataStore.data.map { it[ACCESS_KEY_SECRET] }


    suspend fun setRegionId(value: String?) {
        context.ossDataStore.edit { prefs ->
            if (value == null) prefs.remove(REGION_ID) else prefs[REGION_ID] = value
        }
    }

    suspend fun setEndpoint(value: String?) {
        context.ossDataStore.edit { prefs ->
            if (value == null) prefs.remove(ENDPOINT) else prefs[ENDPOINT] = value
        }
    }

    suspend fun setBucket(value: String?) {
        context.ossDataStore.edit { prefs ->
            if (value == null) prefs.remove(BUCKET) else prefs[BUCKET] = value
        }
    }

    suspend fun setAccessKeyId(value: String?) {
        context.ossDataStore.edit { prefs ->
            if (value == null) prefs.remove(ACCESS_KEY_ID) else prefs[ACCESS_KEY_ID] = value
        }
    }

    suspend fun setAccessKeySecret(value: String?) {
        context.ossDataStore.edit { prefs ->
            if (value == null) prefs.remove(ACCESS_KEY_SECRET) else prefs[ACCESS_KEY_SECRET] = value
        }
    }


}