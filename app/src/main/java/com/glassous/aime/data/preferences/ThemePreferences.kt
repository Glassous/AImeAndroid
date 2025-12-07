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

        // 现有字段
        private val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
        private val MINIMAL_MODE_CONFIG = stringPreferencesKey("minimal_mode_config")
        private val REPLY_BUBBLE_ENABLED = booleanPreferencesKey("reply_bubble_enabled")
        private val CHAT_FONT_SIZE = floatPreferencesKey("chat_font_size")
        private val CHAT_UI_OVERLAY_ALPHA = floatPreferencesKey("chat_ui_overlay_alpha")
        private val TOPBAR_HAMBURGER_ALPHA = floatPreferencesKey("topbar_hamburger_alpha")
        private val TOPBAR_MODEL_TEXT_ALPHA = floatPreferencesKey("topbar_model_text_alpha")
        private val CHAT_INPUT_INNER_ALPHA = floatPreferencesKey("chat_input_inner_alpha")
        private val MINIMAL_MODE_FULLSCREEN = booleanPreferencesKey("minimal_mode_fullscreen")
        private val CHAT_FULLSCREEN = booleanPreferencesKey("chat_fullscreen")
        private val HIDE_IMPORT_SHARED_BUTTON = booleanPreferencesKey("hide_import_shared_button")
        private val THEME_ADVANCED_EXPANDED = booleanPreferencesKey("theme_advanced_expanded")

        // --- 新增：黑白主题开关 ---
        private val MONOCHROME_THEME = booleanPreferencesKey("monochrome_theme")
    }

    val selectedTheme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: THEME_SYSTEM
        }

    // --- 新增：获取黑白主题状态 (默认为 false) ---
    val monochromeTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MONOCHROME_THEME] ?: false
        }

    // 现有获取方法...
    val minimalMode: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[MINIMAL_MODE] ?: false }

    val replyBubbleEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[REPLY_BUBBLE_ENABLED] ?: true }

    val chatFontSize: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CHAT_FONT_SIZE] ?: 16f }

    val chatUiOverlayAlpha: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CHAT_UI_OVERLAY_ALPHA] ?: 0.75f }

    val topBarHamburgerAlpha: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[TOPBAR_HAMBURGER_ALPHA] ?: 0.5f }

    val topBarModelTextAlpha: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[TOPBAR_MODEL_TEXT_ALPHA] ?: 0.5f }

    val chatInputInnerAlpha: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CHAT_INPUT_INNER_ALPHA] ?: 0.9f }

    val minimalModeFullscreen: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[MINIMAL_MODE_FULLSCREEN] ?: false }

    val chatFullscreen: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[CHAT_FULLSCREEN] ?: false }

    val hideImportSharedButton: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[HIDE_IMPORT_SHARED_BUTTON] ?: false }

    val themeAdvancedExpanded: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[THEME_ADVANCED_EXPANDED] ?: false }

    val minimalModeConfig: Flow<MinimalModeConfig> = context.dataStore.data
        .map { preferences ->
            val configJson = preferences[MINIMAL_MODE_CONFIG]
            if (configJson != null) {
                try {
                    Json.decodeFromString<MinimalModeConfig>(configJson)
                } catch (e: Exception) {
                    MinimalModeConfig()
                }
            } else {
                MinimalModeConfig()
            }
        }

    // 设置方法
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences -> preferences[THEME_KEY] = theme }
    }

    // --- 新增：设置黑白主题 ---
    suspend fun setMonochromeTheme(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[MONOCHROME_THEME] = enabled }
    }

    // 现有设置方法...
    suspend fun setMinimalMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[MINIMAL_MODE] = enabled }
    }

    suspend fun setReplyBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[REPLY_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setChatFontSize(size: Float) {
        context.dataStore.edit { preferences -> preferences[CHAT_FONT_SIZE] = size }
    }

    suspend fun setChatUiOverlayAlpha(alpha: Float) {
        context.dataStore.edit { preferences -> preferences[CHAT_UI_OVERLAY_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setTopBarHamburgerAlpha(alpha: Float) {
        context.dataStore.edit { preferences -> preferences[TOPBAR_HAMBURGER_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setTopBarModelTextAlpha(alpha: Float) {
        context.dataStore.edit { preferences -> preferences[TOPBAR_MODEL_TEXT_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setChatInputInnerAlpha(alpha: Float) {
        context.dataStore.edit { preferences -> preferences[CHAT_INPUT_INNER_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setMinimalModeFullscreen(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[MINIMAL_MODE_FULLSCREEN] = enabled }
    }

    suspend fun setChatFullscreen(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[CHAT_FULLSCREEN] = enabled }
    }

    suspend fun setHideImportSharedButton(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[HIDE_IMPORT_SHARED_BUTTON] = enabled }
    }

    suspend fun setThemeAdvancedExpanded(expanded: Boolean) {
        context.dataStore.edit { preferences -> preferences[THEME_ADVANCED_EXPANDED] = expanded }
    }

    suspend fun setMinimalModeConfig(config: MinimalModeConfig) {
        context.dataStore.edit { preferences -> preferences[MINIMAL_MODE_CONFIG] = Json.encodeToString(config) }
    }
}