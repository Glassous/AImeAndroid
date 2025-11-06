package com.glassous.aime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AIMeApplication

    val profile: Flow<UserProfile> = app.userProfilePreferences.profile

    fun saveProfile(updated: UserProfile, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.userProfilePreferences.setUserProfile(updated)
                val autoSyncEnabled = app.autoSyncPreferences.autoSyncEnabled.first() ?: false
                if (autoSyncEnabled) {
                    app.cloudSyncViewModel.uploadUserProfileOnly { ok, msg ->
                        onResult(ok, if (ok) "已保存并云端同步" else "已保存；云端同步失败：$msg")
                    }
                } else {
                    onResult(true, "已保存")
                }
            } catch (e: Exception) {
                onResult(false, "保存失败：${e.message}")
            }
        }
    }

    fun clearProfile(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.userProfilePreferences.clearUserProfile()
                val autoSyncEnabled = app.autoSyncPreferences.autoSyncEnabled.first() ?: false
                if (autoSyncEnabled) {
                    app.cloudSyncViewModel.uploadUserProfileOnly { ok, msg ->
                        onResult(ok, if (ok) "已清空并云端同步" else "已清空；云端同步失败：$msg")
                    }
                } else {
                    onResult(true, "已清空")
                }
            } catch (e: Exception) {
                onResult(false, "清空失败：${e.message}")
            }
        }
    }
}

class UserProfileViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserProfileViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}