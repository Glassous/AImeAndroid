package com.glassous.aime.sync

import android.util.Log
import com.glassous.aime.AIMeApplication
import androidx.room.InvalidationTracker
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Global auto-sync observer that watches Room table changes and triggers
 * Alibaba Cloud OSS upload when auto-sync is enabled.
 */
class AutoSyncObserver(private val app: AIMeApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    private val observer = object : InvalidationTracker.Observer(
        arrayOf("chat_messages", "conversations", "model_groups", "models")
    ) {
        override fun onInvalidated(tables: Set<String>) {
            // Debounce frequent writes to avoid excessive uploads
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1500)
                tryAutoSync()
            }
        }
    }

    fun start() {
        app.database.invalidationTracker.addObserver(observer)
        Log.i("AutoSync", "Observer started")
    }

    fun stop() {
        app.database.invalidationTracker.removeObserver(observer)
        debounceJob?.cancel()
        scope.cancel()
        Log.i("AutoSync", "Observer stopped")
    }

    private suspend fun tryAutoSync() {
        val enabled = app.ossPreferences.autoSyncEnabled.first()
        if (!enabled) return

        val regionId = app.ossPreferences.regionId.first()
        val endpoint = app.ossPreferences.endpoint.first()
        val bucket = app.ossPreferences.bucket.first()
        val ak = app.ossPreferences.accessKeyId.first()
        val sk = app.ossPreferences.accessKeySecret.first()

        val incomplete = listOf(regionId, endpoint, bucket, ak, sk).any { it.isNullOrBlank() }
        if (incomplete) {
            Log.w("AutoSync", "OSS config incomplete; skip auto sync")
            return
        }

        // Use existing ViewModel logic to perform backup upload
        val vm = CloudSyncViewModel(app)
        vm.uploadBackup { ok, msg ->
            Log.i("AutoSync", "Auto upload result=$ok, $msg")
        }
    }
}