package com.glassous.aime

import android.app.Application
import com.glassous.aime.data.ChatDatabase
import com.glassous.aime.data.ChatRepository
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences

class AIMeApplication : Application() {
    
    // Database instance
    val database by lazy { ChatDatabase.getDatabase(this) }
    
    // Model config repository instance
    val modelConfigRepository by lazy {
        ModelConfigRepository(database.modelConfigDao())
    }

    // Model preferences instance
    val modelPreferences by lazy { ModelPreferences(this) }

    // Repository instance
    val repository by lazy { 
        ChatRepository(
            chatDao = database.chatDao(),
            modelConfigRepository = modelConfigRepository,
            modelPreferences = modelPreferences
        )
    }
}