package com.glassous.aime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.SupabaseAuthService
import com.glassous.aime.data.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AIMeApplication
    private val service = SupabaseAuthService()

    val isLoggedIn: Flow<Boolean> = app.authPreferences.accessToken.map { !it.isNullOrBlank() }
    val email: Flow<String?> = app.authPreferences.email

    fun register(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, session, msg) = service.signUp(email, password)
            if (ok && session != null) {
                app.authPreferences.setSession(
                    session.sessionToken,
                    null,
                    session.userId,
                    session.email ?: email
                )
                onResult(true, msg)
            } else {
                onResult(false, msg)
            }
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, session, msg) = service.login(email, password)
            if (ok && session != null) {
                app.authPreferences.setSession(
                    session.sessionToken,
                    null,
                    session.userId,
                    session.email ?: email
                )
                onResult(true, msg)
            } else {
                onResult(false, msg)
            }
        }
    }

    fun logout(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            app.authPreferences.setSession(null, null, null, null)
            onResult(true, "已退出登录")
        }
    }

    fun recover(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = service.recover(email)
            onResult(ok, msg)
        }
    }

    fun resetPassword(email: String, newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = service.resetPassword(email, newPassword)
            onResult(ok, msg)
        }
    }

    fun clearLocalData(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chatDao = app.database.chatDao()
                val modelDao = app.database.modelConfigDao()
                chatDao.deleteAllMessages()
                chatDao.deleteAllConversations()
                modelDao.deleteAllModels()
                modelDao.deleteAllGroups()
                app.modelPreferences.setSelectedModelId(null)
                onResult(true, "已清除本地数据")
            } catch (e: Exception) {
                onResult(false, "清除失败：${e.message}")
            }
        }
    }
}

class AuthViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
