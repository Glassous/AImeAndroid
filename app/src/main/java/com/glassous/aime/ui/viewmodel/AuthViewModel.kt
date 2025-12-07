package com.glassous.aime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.SupabaseAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AIMeApplication
    private val service = SupabaseAuthService()

    val isLoggedIn: Flow<Boolean> = app.authPreferences.accessToken.map { !it.isNullOrBlank() }
    val email: Flow<String?> = app.authPreferences.email

    // 注册：支持安全问题
    fun register(email: String, password: String, question: String = "", answer: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, session, msg) = service.signUp(email, password, question, answer)
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

    // 兼容旧调用
    fun register(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        register(email, password, "", "", onResult)
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

    // 获取安全问题
    fun fetchSecurityQuestion(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, result) = service.getSecurityQuestion(email)
            onResult(ok, result)
        }
    }

    // 使用安全问题答案重置密码
    fun resetPasswordWithAnswer(email: String, answer: String, newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = service.resetPasswordWithAnswer(email, answer, newPassword)
            onResult(ok, msg)
        }
    }

    // 修复：添加 updateSecurityQuestion 方法
    fun updateSecurityQuestion(password: String, question: String, answer: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentEmail = email.firstOrNull()
            if (currentEmail == null) {
                onResult(false, "未登录")
                return@launch
            }
            val (ok, msg) = service.updateSecurityQuestion(currentEmail, password, question, answer)
            onResult(ok, msg)
        }
    }

    // 兼容旧的 recover 方法
    fun recover(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = service.recover(email)
            onResult(ok, msg)
        }
    }

    // 兼容旧的 resetPassword 方法
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

    // 手动同步数据
    fun manualSync(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (success, message) = app.cloudSyncManager.manualSync()
                onResult(success, message)
            } catch (e: Exception) {
                onResult(false, "同步失败：${e.message}")
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