package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.s3DataStore: DataStore<Preferences> by preferencesDataStore(name = "s3_preferences")

class S3Preferences(private val context: Context) {

    companion object {
        private val S3_ENABLED = booleanPreferencesKey("s3_enabled")
        private val S3_ENDPOINT = stringPreferencesKey("s3_endpoint")
        private val S3_REGION = stringPreferencesKey("s3_region")
        private val S3_ACCESS_KEY = stringPreferencesKey("s3_access_key")
        private val S3_SECRET_KEY = stringPreferencesKey("s3_secret_key")
        private val S3_BUCKET_NAME = stringPreferencesKey("s3_bucket_name")
        private val S3_FORCE_PATH_STYLE = booleanPreferencesKey("s3_force_path_style")
    }

    val s3Enabled: Flow<Boolean> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_ENABLED] ?: false
        }

    val s3ForcePathStyle: Flow<Boolean> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_FORCE_PATH_STYLE] ?: false
        }

    val s3Endpoint: Flow<String> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_ENDPOINT] ?: ""
        }

    val s3Region: Flow<String> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_REGION] ?: ""
        }

    val s3AccessKey: Flow<String> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_ACCESS_KEY] ?: ""
        }

    val s3SecretKey: Flow<String> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_SECRET_KEY] ?: ""
        }

    val s3BucketName: Flow<String> = context.s3DataStore.data
        .map { preferences ->
            preferences[S3_BUCKET_NAME] ?: ""
        }

    suspend fun setS3Enabled(enabled: Boolean) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_ENABLED] = enabled
        }
    }

    suspend fun setS3Endpoint(endpoint: String) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_ENDPOINT] = endpoint
        }
    }

    suspend fun setS3Region(region: String) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_REGION] = region
        }
    }

    suspend fun setS3AccessKey(accessKey: String) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_ACCESS_KEY] = accessKey
        }
    }

    suspend fun setS3SecretKey(secretKey: String) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_SECRET_KEY] = secretKey
        }
    }

    suspend fun setS3BucketName(bucketName: String) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_BUCKET_NAME] = bucketName
        }
    }

    suspend fun setS3ForcePathStyle(forcePathStyle: Boolean) {
        context.s3DataStore.edit { preferences ->
            preferences[S3_FORCE_PATH_STYLE] = forcePathStyle
        }
    }
}
