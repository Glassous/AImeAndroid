package com.glassous.aime

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.glassous.aime.data.ChatDatabase
import com.glassous.aime.data.ChatRepository
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences
 
import com.glassous.aime.data.preferences.UserProfilePreferences
 


class AIMeApplication : Application() {
    
    // Database instance
    val database by lazy { ChatDatabase.getDatabase(this) }
    
    // Model config repository instance
    val modelConfigRepository by lazy {
        ModelConfigRepository(
            modelConfigDao = database.modelConfigDao()
        )
    }

    // Model preferences instance
    val modelPreferences by lazy { ModelPreferences(this) }

    

    // User profile preferences instance
    val userProfilePreferences by lazy { UserProfilePreferences(this) }

    // Context limit preferences instance
    val contextPreferences by lazy { com.glassous.aime.data.preferences.ContextPreferences(this) }

    

    // Repository instance
    val repository by lazy { 
        ChatRepository(
            chatDao = database.chatDao(),
            modelConfigRepository = modelConfigRepository,
            modelPreferences = modelPreferences,
            contextPreferences = contextPreferences,
            userProfilePreferences = userProfilePreferences
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 预设分组与模型的初始化（后台）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                modelConfigRepository.seedDefaultPresets()
            } catch (_: Exception) {
                // 忽略预设插入失败，不影响应用启动
            }
        }
    }
}
