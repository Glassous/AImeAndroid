package com.glassous.aime.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.preferences.ThemePreferences
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
}