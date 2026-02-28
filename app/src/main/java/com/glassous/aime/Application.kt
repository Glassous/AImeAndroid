package com.glassous.aime

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.glassous.aime.data.ChatDatabase
import com.glassous.aime.data.ChatRepository
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences

 
 


import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.util.DebugLogger

class AIMeApplication : Application(), ImageLoaderFactory {
    
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

    // Tool preferences instance
    val toolPreferences by lazy { com.glassous.aime.data.preferences.ToolPreferences(this) }

    

    val contextPreferences by lazy { com.glassous.aime.data.preferences.ContextPreferences(this) }
    val updatePreferences by lazy { com.glassous.aime.data.preferences.UpdatePreferences(this) }
    val privacyPreferences by lazy { com.glassous.aime.data.preferences.PrivacyPreferences(this) }

    // Repository instance
    val repository by lazy { 
        ChatRepository(
            context = this,
            chatDao = database.chatDao(),
            modelConfigRepository = modelConfigRepository,
            modelPreferences = modelPreferences,
            contextPreferences = contextPreferences,
            toolPreferences = toolPreferences
        )
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
