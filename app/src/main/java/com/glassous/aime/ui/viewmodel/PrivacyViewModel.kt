package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.preferences.PrivacyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PrivacyUiState {
    object Loading : PrivacyUiState()
    object Agreed : PrivacyUiState()
    object NotAgreed : PrivacyUiState()
}

class PrivacyViewModel(
    private val privacyPreferences: PrivacyPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrivacyUiState>(PrivacyUiState.Loading)
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            privacyPreferences.isPrivacyPolicyAgreed.collect { agreed ->
                _uiState.value = if (agreed) PrivacyUiState.Agreed else PrivacyUiState.NotAgreed
            }
        }
    }

    fun setPrivacyPolicyAgreed(agreed: Boolean) {
        viewModelScope.launch {
            privacyPreferences.setPrivacyPolicyAgreed(agreed)
            // State will update via flow collection
        }
    }
}

class PrivacyViewModelFactory(
    private val privacyPreferences: PrivacyPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrivacyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PrivacyViewModel(privacyPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
