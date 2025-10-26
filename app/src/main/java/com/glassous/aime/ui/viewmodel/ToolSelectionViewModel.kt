package com.glassous.aime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        hideBottomSheet()
    }
    
    /**
     * 清除工具选择
     */
    fun clearToolSelection() {
        _selectedTool.value = null
    }
    
    /**
     * 获取当前选中工具的显示名称
     */
    fun getSelectedToolDisplayName(): String? {
        return _selectedTool.value?.displayName
    }
}

/**
 * 工具选择UI状态
 */
data class ToolSelectionUiState(
    val showBottomSheet: Boolean = false
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