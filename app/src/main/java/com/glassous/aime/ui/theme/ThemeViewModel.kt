package com.glassous.aime.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.model.MinimalModeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)

    // --- 新增：初始化完成状态 ---
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _selectedTheme = MutableStateFlow(ThemePreferences.THEME_SYSTEM)
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private val _monochromeTheme = MutableStateFlow(false)
    val monochromeTheme: StateFlow<Boolean> = _monochromeTheme.asStateFlow()
    
    private val _htmlCodeBlockCardEnabled = MutableStateFlow(true)
    val htmlCodeBlockCardEnabled: StateFlow<Boolean> = _htmlCodeBlockCardEnabled.asStateFlow()

    private val _minimalMode = MutableStateFlow(false)
    val minimalMode: StateFlow<Boolean> = _minimalMode.asStateFlow()

    private val _replyBubbleEnabled = MutableStateFlow(true)
    val replyBubbleEnabled: StateFlow<Boolean> = _replyBubbleEnabled.asStateFlow()

    private val _chatFontSize = MutableStateFlow(16f)
    val chatFontSize: StateFlow<Float> = _chatFontSize.asStateFlow()

    private val _chatUiOverlayAlpha = MutableStateFlow(0.5f)
    val chatUiOverlayAlpha: StateFlow<Float> = _chatUiOverlayAlpha.asStateFlow()

    private val _topBarHamburgerAlpha = MutableStateFlow(0.5f)
    val topBarHamburgerAlpha: StateFlow<Float> = _topBarHamburgerAlpha.asStateFlow()

    private val _topBarModelTextAlpha = MutableStateFlow(0.5f)
    val topBarModelTextAlpha: StateFlow<Float> = _topBarModelTextAlpha.asStateFlow()

    private val _chatInputInnerAlpha = MutableStateFlow(0.9f)
    val chatInputInnerAlpha: StateFlow<Float> = _chatInputInnerAlpha.asStateFlow()

    private val _minimalModeFullscreen = MutableStateFlow(false)
    val minimalModeFullscreen: StateFlow<Boolean> = _minimalModeFullscreen.asStateFlow()

    private val _chatFullscreen = MutableStateFlow(false)
    val chatFullscreen: StateFlow<Boolean> = _chatFullscreen.asStateFlow()

    private val _hideImportSharedButton = MutableStateFlow(false)
    val hideImportSharedButton: StateFlow<Boolean> = _hideImportSharedButton.asStateFlow()

    private val _themeAdvancedExpanded = MutableStateFlow(false)
    val themeAdvancedExpanded: StateFlow<Boolean> = _themeAdvancedExpanded.asStateFlow()

    private val _minimalModeConfig = MutableStateFlow(MinimalModeConfig())
    val minimalModeConfig: StateFlow<MinimalModeConfig> = _minimalModeConfig.asStateFlow()

    init {
        // --- 修改：优先加载外观相关的关键配置 ---
        // 使用 combine 确保 Theme 和 Monochrome 状态都已从 DataStore 获取到最新值
        viewModelScope.launch {
            combine(
                themePreferences.selectedTheme,
                themePreferences.monochromeTheme,
                themePreferences.themeAdvancedExpanded // 添加主题高级选项展开状态
            ) { theme, mono, expanded ->
                _selectedTheme.value = theme
                _monochromeTheme.value = mono
                _themeAdvancedExpanded.value = expanded // 优先设置展开状态，避免进入页面时的动画
                true // 标记为已加载
            }.collect {
                // 只有当数据真正发射出来后，才设置 ready
                _isReady.value = true
            }
        }

        // 其他非阻塞性的配置可以单独加载
        viewModelScope.launch {
            themePreferences.minimalMode.collect { enabled -> _minimalMode.value = enabled }
        }
        viewModelScope.launch {
            themePreferences.replyBubbleEnabled.collect { enabled -> _replyBubbleEnabled.value = enabled }
        }
        viewModelScope.launch {
            themePreferences.chatFontSize.collect { size -> _chatFontSize.value = size }
        }
        viewModelScope.launch {
            themePreferences.chatUiOverlayAlpha.collect { alpha -> _chatUiOverlayAlpha.value = alpha }
        }
        viewModelScope.launch {
            themePreferences.topBarHamburgerAlpha.collect { alpha -> _topBarHamburgerAlpha.value = alpha }
        }
        viewModelScope.launch {
            themePreferences.topBarModelTextAlpha.collect { alpha -> _topBarModelTextAlpha.value = alpha }
        }
        viewModelScope.launch {
            themePreferences.chatInputInnerAlpha.collect { alpha -> _chatInputInnerAlpha.value = alpha }
        }
        viewModelScope.launch {
            themePreferences.minimalModeFullscreen.collect { enabled -> _minimalModeFullscreen.value = enabled }
        }
        viewModelScope.launch {
            themePreferences.chatFullscreen.collect { enabled -> _chatFullscreen.value = enabled }
        }
        viewModelScope.launch {
            themePreferences.hideImportSharedButton.collect { enabled -> _hideImportSharedButton.value = enabled }
        }
        viewModelScope.launch {
            themePreferences.minimalModeConfig.collect { config -> _minimalModeConfig.value = config }
        }
        viewModelScope.launch {
            themePreferences.htmlCodeBlockCardEnabled.collect { enabled -> _htmlCodeBlockCardEnabled.value = enabled }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            themePreferences.setTheme(theme)
        }
    }

    fun setMonochromeTheme(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setMonochromeTheme(enabled)
        }
    }

    fun toggleTheme() {
        val current = _selectedTheme.value
        val next = when (current) {
            ThemePreferences.THEME_SYSTEM -> ThemePreferences.THEME_LIGHT
            ThemePreferences.THEME_LIGHT -> ThemePreferences.THEME_DARK
            ThemePreferences.THEME_DARK -> ThemePreferences.THEME_SYSTEM
            else -> ThemePreferences.THEME_SYSTEM
        }
        setTheme(next)
    }

    fun setMinimalMode(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setMinimalMode(enabled) }
    }

    fun setReplyBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setReplyBubbleEnabled(enabled) }
    }

    fun setChatFontSize(size: Float) {
        viewModelScope.launch { themePreferences.setChatFontSize(size) }
    }

    fun setMinimalModeConfig(config: MinimalModeConfig) {
        viewModelScope.launch { themePreferences.setMinimalModeConfig(config) }
    }

    fun setChatUiOverlayAlpha(alpha: Float) {
        viewModelScope.launch { themePreferences.setChatUiOverlayAlpha(alpha) }
    }

    fun setTopBarHamburgerAlpha(alpha: Float) {
        viewModelScope.launch { themePreferences.setTopBarHamburgerAlpha(alpha) }
    }

    fun setTopBarModelTextAlpha(alpha: Float) {
        viewModelScope.launch { themePreferences.setTopBarModelTextAlpha(alpha) }
    }

    fun setChatInputInnerAlpha(alpha: Float) {
        viewModelScope.launch { themePreferences.setChatInputInnerAlpha(alpha) }
    }

    fun setMinimalModeFullscreen(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setMinimalModeFullscreen(enabled) }
    }

    fun setChatFullscreen(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setChatFullscreen(enabled) }
    }

    fun setHideImportSharedButton(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setHideImportSharedButton(enabled) }
    }

    fun setThemeAdvancedExpanded(expanded: Boolean) {
        viewModelScope.launch { themePreferences.setThemeAdvancedExpanded(expanded) }
    }
    
    fun setHtmlCodeBlockCardEnabled(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setHtmlCodeBlockCardEnabled(enabled) }
    }
}