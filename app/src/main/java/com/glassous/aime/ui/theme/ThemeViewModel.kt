package com.glassous.aime.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.model.MinimalModeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val themePreferences = ThemePreferences(application)
    
    private val _selectedTheme = MutableStateFlow(ThemePreferences.THEME_SYSTEM)
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private val _minimalMode = MutableStateFlow(false)
    val minimalMode: StateFlow<Boolean> = _minimalMode.asStateFlow()

    // 新增：回复气泡是否启用
    private val _replyBubbleEnabled = MutableStateFlow(true)
    val replyBubbleEnabled: StateFlow<Boolean> = _replyBubbleEnabled.asStateFlow()

    // 新增：聊天字体大小
    private val _chatFontSize = MutableStateFlow(16f)
    val chatFontSize: StateFlow<Float> = _chatFontSize.asStateFlow()

    // 新增：聊天页面UI透明度
    private val _chatUiOverlayAlpha = MutableStateFlow(0.5f)
    val chatUiOverlayAlpha: StateFlow<Float> = _chatUiOverlayAlpha.asStateFlow()

    // 新增：顶部栏汉堡菜单按钮背景透明度
    private val _topBarHamburgerAlpha = MutableStateFlow(0.5f)
    val topBarHamburgerAlpha: StateFlow<Float> = _topBarHamburgerAlpha.asStateFlow()

    // 新增：顶部栏模型文字按钮背景透明度
    private val _topBarModelTextAlpha = MutableStateFlow(0.5f)
    val topBarModelTextAlpha: StateFlow<Float> = _topBarModelTextAlpha.asStateFlow()

    // 新增：输入框内部背景透明度
    private val _chatInputInnerAlpha = MutableStateFlow(0.9f)
    val chatInputInnerAlpha: StateFlow<Float> = _chatInputInnerAlpha.asStateFlow()

    // 新增：极简模式下全屏显示
    private val _minimalModeFullscreen = MutableStateFlow(false)
    val minimalModeFullscreen: StateFlow<Boolean> = _minimalModeFullscreen.asStateFlow()

    // 新增：聊天页面单独全屏显示
    private val _chatFullscreen = MutableStateFlow(false)
    val chatFullscreen: StateFlow<Boolean> = _chatFullscreen.asStateFlow()

    private val _hideImportSharedButton = MutableStateFlow(false)
    val hideImportSharedButton: StateFlow<Boolean> = _hideImportSharedButton.asStateFlow()

    // 新增：极简模式配置
    private val _minimalModeConfig = MutableStateFlow(MinimalModeConfig())
    val minimalModeConfig: StateFlow<MinimalModeConfig> = _minimalModeConfig.asStateFlow()
    
    init {
        viewModelScope.launch {
            themePreferences.selectedTheme.collect { theme ->
                _selectedTheme.value = theme
            }
        }
        viewModelScope.launch {
            themePreferences.minimalMode.collect { enabled ->
                _minimalMode.value = enabled
            }
        }
        // 收集回复气泡开关
        viewModelScope.launch {
            themePreferences.replyBubbleEnabled.collect { enabled ->
                _replyBubbleEnabled.value = enabled
            }
        }
        // 收集聊天字体大小
        viewModelScope.launch {
            themePreferences.chatFontSize.collect { size ->
                _chatFontSize.value = size
            }
        }
        // 收集聊天页面UI透明度
        viewModelScope.launch {
            themePreferences.chatUiOverlayAlpha.collect { alpha ->
                _chatUiOverlayAlpha.value = alpha
            }
        }
        // 收集顶部栏汉堡菜单按钮背景透明度
        viewModelScope.launch {
            themePreferences.topBarHamburgerAlpha.collect { alpha ->
                _topBarHamburgerAlpha.value = alpha
            }
        }
        // 收集顶部栏模型文字按钮背景透明度
        viewModelScope.launch {
            themePreferences.topBarModelTextAlpha.collect { alpha ->
                _topBarModelTextAlpha.value = alpha
            }
        }
        // 收集输入框内部背景透明度
        viewModelScope.launch {
            themePreferences.chatInputInnerAlpha.collect { alpha ->
                _chatInputInnerAlpha.value = alpha
            }
        }
        // 收集极简模式下全屏显示开关
        viewModelScope.launch {
            themePreferences.minimalModeFullscreen.collect { enabled ->
                _minimalModeFullscreen.value = enabled
            }
        }
        // 收集聊天页面单独全屏显示开关
        viewModelScope.launch {
            themePreferences.chatFullscreen.collect { enabled ->
                _chatFullscreen.value = enabled
            }
        }
        viewModelScope.launch {
            themePreferences.hideImportSharedButton.collect { enabled ->
                _hideImportSharedButton.value = enabled
            }
        }
        // 收集极简模式配置
        viewModelScope.launch {
            themePreferences.minimalModeConfig.collect { config ->
                _minimalModeConfig.value = config
            }
        }
    }
    
    fun setTheme(theme: String) {
        viewModelScope.launch {
            themePreferences.setTheme(theme)
        }
    }

    fun setMinimalMode(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setMinimalMode(enabled)
        }
    }

    // 新增：设置回复气泡开关
    fun setReplyBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setReplyBubbleEnabled(enabled)
        }
    }

    // 新增：设置聊天字体大小
    fun setChatFontSize(size: Float) {
        viewModelScope.launch {
            themePreferences.setChatFontSize(size)
        }
    }

    // 新增：设置极简模式配置
    fun setMinimalModeConfig(config: MinimalModeConfig) {
        viewModelScope.launch {
            themePreferences.setMinimalModeConfig(config)
        }
    }

    // 新增：设置聊天页面UI透明度
    fun setChatUiOverlayAlpha(alpha: Float) {
        viewModelScope.launch {
            themePreferences.setChatUiOverlayAlpha(alpha)
        }
    }

    // 新增：设置顶部栏汉堡菜单按钮背景透明度
    fun setTopBarHamburgerAlpha(alpha: Float) {
        viewModelScope.launch {
            themePreferences.setTopBarHamburgerAlpha(alpha)
        }
    }

    // 新增：设置顶部栏模型文字按钮背景透明度
    fun setTopBarModelTextAlpha(alpha: Float) {
        viewModelScope.launch {
            themePreferences.setTopBarModelTextAlpha(alpha)
        }
    }

    // 新增：设置输入框内部背景透明度
    fun setChatInputInnerAlpha(alpha: Float) {
        viewModelScope.launch {
            themePreferences.setChatInputInnerAlpha(alpha)
        }
    }

    // 新增：设置极简模式下全屏显示开关
    fun setMinimalModeFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setMinimalModeFullscreen(enabled)
        }
    }

    // 新增：设置聊天页面单独全屏显示开关
    fun setChatFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setChatFullscreen(enabled)
        }
    }

    fun setHideImportSharedButton(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setHideImportSharedButton(enabled)
        }
    }
}