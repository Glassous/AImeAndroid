package com.glassous.aime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.repository.FetchStatus
import com.glassous.aime.data.repository.ModelConfigRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ModelConfigViewModel(
    private val repository: ModelConfigRepository
) : ViewModel() {
    
    // Fetch Status
    private val _fetchStatus = MutableStateFlow<FetchStatus>(FetchStatus.Idle)
    val fetchStatus = _fetchStatus.asStateFlow()
    
    // 所有分组
    val groups = repository.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // UI状态
    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState = _uiState.asStateFlow()
    
    // 创建新分组
    fun createGroup(name: String, baseUrl: String, apiKey: String, providerUrl: String?) {
        viewModelScope.launch {
            try {
                repository.createGroup(name, baseUrl, apiKey, providerUrl)
                _uiState.value = _uiState.value.copy(
                    showCreateGroupDialog = false,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }
    
    // 更新分组
    fun updateGroup(groupId: String, name: String, baseUrl: String, apiKey: String, providerUrl: String?) {
        viewModelScope.launch {
            try {
                val updatedGroup = ModelGroup(
                    id = groupId,
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    providerUrl = providerUrl
                )
                repository.updateGroup(updatedGroup)
                _uiState.value = _uiState.value.copy(
                    showEditGroupDialog = false,
                    selectedGroup = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    // 更新模型
    fun updateModel(modelId: String, groupId: String, name: String, modelName: String, remark: String?) {
        viewModelScope.launch {
            try {
                val updatedModel = Model(
                    id = modelId,
                    groupId = groupId,
                    name = name,
                    modelName = modelName,
                    remark = remark
                )
                repository.updateModel(updatedModel)
                _uiState.value = _uiState.value.copy(
                    showEditModelDialog = false,
                    selectedModel = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    // 删除分组
    fun deleteGroup(group: ModelGroup) {
        viewModelScope.launch {
            try {
                repository.deleteGroup(group)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    // 添加模型到分组
    fun addModelToGroup(groupId: String, name: String, modelName: String, remark: String?) {
        viewModelScope.launch {
            try {
                repository.addModelToGroup(groupId, name, modelName, remark)
                _uiState.value = _uiState.value.copy(
                    showAddModelDialog = false,
                    selectedGroupId = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    // 删除模型
    fun deleteModel(model: Model) {
        viewModelScope.launch {
            try {
                repository.deleteModel(model)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    // 获取指定分组的模型
    fun getModelsByGroupId(groupId: String): Flow<List<Model>> = 
        repository.getModelsByGroupId(groupId)
    
    // UI状态管理
    fun showCreateGroupDialog() {
        _uiState.value = _uiState.value.copy(showCreateGroupDialog = true)
    }
    
    fun hideCreateGroupDialog() {
        _uiState.value = _uiState.value.copy(showCreateGroupDialog = false)
    }
    
    fun showAddModelDialog(groupId: String) {
        _uiState.value = _uiState.value.copy(
            showAddModelDialog = true,
            selectedGroupId = groupId
        )
    }
    
    fun hideAddModelDialog() {
        _uiState.value = _uiState.value.copy(
            showAddModelDialog = false,
            selectedGroupId = null
        )
    }
    
    fun showEditGroupDialog(group: ModelGroup) {
        _uiState.value = _uiState.value.copy(
            showEditGroupDialog = true,
            selectedGroup = group
        )
    }
    
    fun hideEditGroupDialog() {
        _uiState.value = _uiState.value.copy(
            showEditGroupDialog = false,
            selectedGroup = null
        )
    }
    
    fun showEditModelDialog(model: Model) {
        _uiState.value = _uiState.value.copy(
            showEditModelDialog = true,
            selectedModel = model
        )
    }
    
    fun hideEditModelDialog() {
        _uiState.value = _uiState.value.copy(
            showEditModelDialog = false,
            selectedModel = null
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // 显示恢复预设确认对话框
    fun showResetConfirmDialog() {
        _uiState.value = _uiState.value.copy(showResetConfirmDialog = true)
    }

    // 隐藏恢复预设确认对话框
    fun hideResetConfirmDialog() {
        _uiState.value = _uiState.value.copy(showResetConfirmDialog = false)
    }

    // 从云端获取最新模型
    fun fetchLatestModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showResetConfirmDialog = false)
            repository.fetchAndMergeModels()
                .collect { status ->
                    _fetchStatus.value = status
                }
        }
    }
    
    // 关闭获取进度弹窗
    fun closeFetchDialog() {
        _fetchStatus.value = FetchStatus.Idle
    }
}

// UI状态数据类
data class ModelConfigUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateGroupDialog: Boolean = false,
    val showAddModelDialog: Boolean = false,
    val showEditGroupDialog: Boolean = false,
    val showEditModelDialog: Boolean = false,
    val showResetConfirmDialog: Boolean = false,
    val selectedGroupId: String? = null,
    val selectedGroup: ModelGroup? = null,
    val selectedModel: Model? = null
)

// ViewModelFactory
class ModelConfigViewModelFactory(
    private val repository: ModelConfigRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelConfigViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}