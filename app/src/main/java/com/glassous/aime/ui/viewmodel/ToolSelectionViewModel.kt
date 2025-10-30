package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.glassous.aime.data.AutoToolSelector

/**
 * 工具选择ViewModel
 */
class ToolSelectionViewModel : ViewModel() {
    
    // 所有可用工具
    private val _availableTools = MutableStateFlow(
        ToolType.getAllTools().map { Tool(it) }
    )
    val availableTools: StateFlow<List<Tool>> = _availableTools.asStateFlow()
    
    // 当前选中的工具
    private val _selectedTool = MutableStateFlow<Tool?>(null)
    val selectedTool: StateFlow<Tool?> = _selectedTool.asStateFlow()

    // 是否处于自动模式（显示自动图标，调用时切换为实际工具）
    private val _isAutoSelected = MutableStateFlow(false)
    val isAutoSelected: StateFlow<Boolean> = _isAutoSelected.asStateFlow()
    
    // UI状态
    private val _uiState = MutableStateFlow(ToolSelectionUiState())
    val uiState: StateFlow<ToolSelectionUiState> = _uiState.asStateFlow()
    
    /**
     * 显示工具选择底部弹窗
     */
    fun showBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = true)
    }
    
    /**
     * 隐藏工具选择底部弹窗
     */
    fun hideBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = false)
    }
    
    /**
     * 选择工具
     */
    fun selectTool(tool: Tool?) {
        _selectedTool.value = tool
        _isAutoSelected.value = false
        hideBottomSheet()
    }
    
    /**
     * 清除工具选择
     */
    fun clearToolSelection() {
        _selectedTool.value = null
        _isAutoSelected.value = false
    }
    
    /**
     * 获取当前选中工具的显示名称
     */
    fun getSelectedToolDisplayName(): String? {
        return _selectedTool.value?.displayName
    }

    /**
     * 触发自动工具选择并返回路由
     */
    fun autoSelectTool(
        selector: AutoToolSelector,
        onResult: (selected: Tool?, route: String?) -> Unit
    ) {
        // 防重入：同一时间只能有一次自动选择
        if (_uiState.value.isProcessing) return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            try {
                val tools = _availableTools.value
                val result = selector.selectTool(tools)
                val selected = tools.find { it.displayName == result.toolName }
                _selectedTool.value = selected
                onResult(selected, result.route)
            } catch (e: Exception) {
                // 失败时不改变当前选择，返回空，并保持在当前页面
                onResult(null, null)
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                hideBottomSheet()
            }
        }
    }

    /**
     * 启用自动模式（顶部栏与Bottom Sheet显示自动图标）
     */
    fun enableAutoMode() {
        _isAutoSelected.value = true
        _selectedTool.value = null
        hideBottomSheet()
    }
    
    /**
     * 关闭自动模式
     */
    fun disableAutoMode() {
        _isAutoSelected.value = false
    }
}

/**
 * 工具选择UI状态
 */
data class ToolSelectionUiState(
    val showBottomSheet: Boolean = false,
    val isProcessing: Boolean = false
)

/**
 * ToolSelectionViewModel工厂类
 */
class ToolSelectionViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ToolSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ToolSelectionViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}