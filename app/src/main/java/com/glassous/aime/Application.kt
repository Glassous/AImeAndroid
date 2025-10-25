package com.glassous.aime

import android.app.Application
import com.glassous.aime.data.ChatDatabase
import com.glassous.aime.data.ChatRepository
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.OssPreferences
import com.glassous.aime.data.preferences.AutoSyncPreferences
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel


class AIMeApplication : Application() {
    
    // Database instance
    val database by lazy { ChatDatabase.getDatabase(this) }
    
    // Model config repository instance
    val modelConfigRepository by lazy {
        ModelConfigRepository(
            modelConfigDao = database.modelConfigDao(),
            autoSyncPreferences = autoSyncPreferences,
            cloudSyncViewModel = cloudSyncViewModel
        )
    }

    // Model preferences instance
    val modelPreferences by lazy { ModelPreferences(this) }

    // OSS preferences instance
    val ossPreferences by lazy { OssPreferences(this) }

    // Auto sync preferences instance
    val autoSyncPreferences by lazy { AutoSyncPreferences(this) }

    // Cloud sync view model instance
    val cloudSyncViewModel by lazy { CloudSyncViewModel(this) }

    // Repository instance
    val repository by lazy { 
        ChatRepository(
            chatDao = database.chatDao(),
            modelConfigRepository = modelConfigRepository,
            modelPreferences = modelPreferences,
            autoSyncPreferences = autoSyncPreferences,
            cloudSyncViewModel = cloudSyncViewModel
        )
    }

    override fun onCreate() {
        super.onCreate()

    }
}