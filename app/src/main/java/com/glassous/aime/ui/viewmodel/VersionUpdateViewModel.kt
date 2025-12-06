package com.glassous.aime.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.BuildConfig
import com.glassous.aime.data.GitHubReleaseService
import com.glassous.aime.data.model.VersionUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 版本更新状态
 */
sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class Success(val updateInfo: VersionUpdateInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

/**
 * 版本更新ViewModel
 */
class VersionUpdateViewModel(
    private val gitHubService: GitHubReleaseService
) : ViewModel() {
    
    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()
    
    private val repoOwner = "Glassous"
    private val repoName = "AImeAndroid"
    
    /**
     * 获取当前应用版本
     */
    fun getCurrentVersion(): String {
        return "v${BuildConfig.VERSION_NAME}"
    }
    
    /**
     * 检查更新
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            
            try {
                val result = gitHubService.getLatestRelease(repoOwner, repoName)
                
                result.onSuccess { release ->
                    val currentVersion = getCurrentVersion()
                    val latestVersion = release.tagName
                    
                    // 比较版本号
                    val comparison = gitHubService.compareVersions(latestVersion, currentVersion)
                    val hasUpdate = comparison > 0
                    
                    // 查找APK下载链接
                    val downloadUrl = gitHubService.findApkDownloadUrl(release)
                    
                    val updateInfo = VersionUpdateInfo(
                        hasUpdate = hasUpdate,
                        currentVersion = currentVersion,
                        latestVersion = latestVersion,
                        downloadUrl = downloadUrl,
                        releaseNotes = release.body,
                        releaseUrl = release.htmlUrl
                    )
                    
                    _updateCheckState.value = UpdateCheckState.Success(updateInfo)
                }.onFailure { error ->
                    _updateCheckState.value = UpdateCheckState.Error(
                        error.message ?: "检查更新失败"
                    )
                }
            } catch (e: Exception) {
                _updateCheckState.value = UpdateCheckState.Error(
                    e.message ?: "检查更新时发生未知错误"
                )
            }
        }
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _updateCheckState.value = UpdateCheckState.Idle
    }
}

/**
 * VersionUpdateViewModel工厂
 */
class VersionUpdateViewModelFactory(
    private val gitHubService: GitHubReleaseService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VersionUpdateViewModel::class.java)) {
            return VersionUpdateViewModel(gitHubService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}