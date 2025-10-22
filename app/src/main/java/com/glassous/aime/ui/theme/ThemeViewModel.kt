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
    
    init {
        viewModelScope.launch {
            themePreferences.selectedTheme.collect { theme ->
                _selectedTheme.value = theme
            }
        }
    }
    
    fun setTheme(theme: String) {
        viewModelScope.launch {
            themePreferences.setTheme(theme)
        }
    }
}