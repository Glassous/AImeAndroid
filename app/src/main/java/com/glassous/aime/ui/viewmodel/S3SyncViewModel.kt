package com.glassous.aime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class S3SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val s3SyncRepository = (application as AIMeApplication).s3SyncRepository

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus = _syncStatus.asStateFlow()

    fun sync() {
        if (_syncStatus.value is SyncStatus.Syncing) return
        
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Syncing("准备同步...")
            try {
                s3SyncRepository.sync { progress ->
                    _syncStatus.value = SyncStatus.Syncing(progress)
                }
                _syncStatus.value = SyncStatus.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = SyncStatus.Error(e.message ?: "同步失败")
            }
        }
    }
    
    fun clearStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val message: String) : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class S3SyncViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(S3SyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return S3SyncViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
