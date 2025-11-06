package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.glassous.aime.data.model.UserProfile

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile_preferences")

class UserProfilePreferences(private val context: Context) {
    companion object {
        private val USER_PROFILE_JSON = stringPreferencesKey("user_profile_json")
    }

    private val gson = Gson()

    // 读取用户资料（若不存在则返回空对象）
    val profile: Flow<UserProfile> = context.userProfileDataStore.data
        .map { prefs ->
            val json = prefs[USER_PROFILE_JSON]
            if (json.isNullOrBlank()) UserProfile() else try {
                gson.fromJson(json, UserProfile::class.java)
            } catch (_: Exception) {
                UserProfile()
            }
        }

    // 保存完整用户资料
    suspend fun setUserProfile(profile: UserProfile) {
        context.userProfileDataStore.edit { prefs ->
            prefs[USER_PROFILE_JSON] = gson.toJson(profile)
        }
    }

    // 清空用户资料
    suspend fun clearUserProfile() {
        context.userProfileDataStore.edit { prefs ->
            prefs.remove(USER_PROFILE_JSON)
        }
    }
}