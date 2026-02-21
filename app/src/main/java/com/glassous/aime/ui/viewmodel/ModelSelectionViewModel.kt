package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.BuiltInModels
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences
 
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 模型选择ViewModel
 * 管理Bottom Sheet的状态和选中的模型
 */
class ModelSelectionViewModel(
    private val repository: ModelConfigRepository,
    private val modelPreferences: ModelPreferences
) : ViewModel() {
    
    // 所有分组
    val groups = repository.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // UI状态
    private val _uiState = MutableStateFlow(ModelSelectionUiState())
    val uiState: StateFlow<ModelSelectionUiState> = _uiState.asStateFlow()
    
    // 当前选中的模型
    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

    init {
        viewModelScope.launch {
            modelPreferences.selectedModelId.collect { id ->
                if (id == BuiltInModels.AIME_MODEL_ID) {
                    _selectedModel.value = BuiltInModels.aimeModel
                } else if (id != null) {
                    val model = repository.getModelById(id)
                    _selectedModel.value = model
                    if (model == null) {
                        modelPreferences.setSelectedModelId(null)
                    }
                } else {
                    _selectedModel.value = null
                }
            }
        }
    }
    
    // 显示Bottom Sheet
    fun showBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = true)
    }
    
    // 隐藏Bottom Sheet
    fun hideBottomSheet() {
        _uiState.value = _uiState.value.copy(
            showBottomSheet = false,
            selectedGroup = null
        )
    }
    
    // 选择分组（进入第二级菜单）
    fun selectGroup(group: ModelGroup) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }
    
    // 返回分组列表（从第二级菜单返回第一级）
    fun backToGroups() {
        _uiState.value = _uiState.value.copy(selectedGroup = null)
    }
    
    // 选择模型
    fun selectModel(model: Model, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        _selectedModel.value = model
        viewModelScope.launch { 
            modelPreferences.setSelectedModelId(model.id)
        }
        hideBottomSheet()
    }
    
    // 获取指定分组的模型
    fun getModelsByGroupId(groupId: String) = repository.getModelsByGroupId(groupId)
    
    // 获取当前选中模型的显示名称
    fun getSelectedModelDisplayName(): String {
        return _selectedModel.value?.name ?: "请先选择模型"
    }
}

/**
 * 模型选择UI状态
 */
data class ModelSelectionUiState(
    val showBottomSheet: Boolean = false,
    val selectedGroup: ModelGroup? = null // null表示显示分组列表，非null表示显示该分组下的模型列表
)

// ViewModelFactory
class ModelSelectionViewModelFactory(
    private val repository: ModelConfigRepository,
    private val modelPreferences: ModelPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelSelectionViewModel(repository, modelPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
