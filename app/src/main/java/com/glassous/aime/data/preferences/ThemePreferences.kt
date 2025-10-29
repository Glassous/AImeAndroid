package com.glassous.aime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.glassous.aime.data.model.MinimalModeConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

class ThemePreferences(private val context: Context) {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("selected_theme")
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
        // 新增：极简模式详细配置
        private val MINIMAL_MODE_CONFIG = stringPreferencesKey("minimal_mode_config")
        // 新增：控制是否启用回复气泡（AI 消息气泡）
        private val REPLY_BUBBLE_ENABLED = booleanPreferencesKey("reply_bubble_enabled")
        // 新增：聊天字体大小设置
        private val CHAT_FONT_SIZE = floatPreferencesKey("chat_font_size")
        // 新增：聊天页面UI透明度设置（顶部栏/状态栏区域/底部输入区），0.0-1.0，默认0.5
        private val CHAT_UI_OVERLAY_ALPHA = floatPreferencesKey("chat_ui_overlay_alpha")
        // 新增：极简模式下全屏显示
        private val MINIMAL_MODE_FULLSCREEN = booleanPreferencesKey("minimal_mode_fullscreen")
        // 新增：聊天页面单独全屏显示
        private val CHAT_FULLSCREEN = booleanPreferencesKey("chat_fullscreen")
    }
    
    val selectedTheme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: THEME_SYSTEM
        }
    
    val minimalMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MINIMAL_MODE] ?: false
        }

    // 新增：回复气泡是否启用（默认启用，以保持现有风格）
    val replyBubbleEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[REPLY_BUBBLE_ENABLED] ?: true
        }

    // 新增：聊天字体大小（默认16sp）
    val chatFontSize: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[CHAT_FONT_SIZE] ?: 16f
        }

    // 新增：聊天页面UI透明度（默认0.75）
    val chatUiOverlayAlpha: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[CHAT_UI_OVERLAY_ALPHA] ?: 0.75f
        }

    // 新增：极简模式下全屏显示开关（默认false）
    val minimalModeFullscreen: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MINIMAL_MODE_FULLSCREEN] ?: false
        }

    // 新增：聊天页面单独全屏显示开关（默认false）
    val chatFullscreen: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[CHAT_FULLSCREEN] ?: false
        }

    // 新增：极简模式详细配置
    val minimalModeConfig: Flow<MinimalModeConfig> = context.dataStore.data
        .map { preferences ->
            val configJson = preferences[MINIMAL_MODE_CONFIG]
            if (configJson != null) {
                try {
                    Json.decodeFromString<MinimalModeConfig>(configJson)
                } catch (e: Exception) {
                    MinimalModeConfig() // 默认配置
                }
            } else {
                MinimalModeConfig() // 默认配置
            }
        }
    
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }
    
    suspend fun setMinimalMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MINIMAL_MODE] = enabled
        }
    }

    // 新增：设置回复气泡开关
    suspend fun setReplyBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REPLY_BUBBLE_ENABLED] = enabled
        }
    }

    // 新增：设置聊天字体大小
    suspend fun setChatFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_FONT_SIZE] = size
        }
    }

    // 新增：设置聊天页面UI透明度
    suspend fun setChatUiOverlayAlpha(alpha: Float) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_UI_OVERLAY_ALPHA] = alpha.coerceIn(0f, 1f)
        }
    }

    // 新增：设置极简模式下全屏显示开关
    suspend fun setMinimalModeFullscreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MINIMAL_MODE_FULLSCREEN] = enabled
        }
    }

    // 新增：设置聊天页面单独全屏显示开关
    suspend fun setChatFullscreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_FULLSCREEN] = enabled
        }
    }

    // 新增：设置极简模式详细配置
    suspend fun setMinimalModeConfig(config: MinimalModeConfig) {
        context.dataStore.edit { preferences ->
            preferences[MINIMAL_MODE_CONFIG] = Json.encodeToString(config)
        }
    }
}