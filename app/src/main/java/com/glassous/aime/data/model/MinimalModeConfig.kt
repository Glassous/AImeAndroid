package com.glassous.aime.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * 极简模式配置数据类
 */
@Serializable
data class MinimalModeConfig(
    val hideNavigationMenu: Boolean = true,
    val hideWelcomeText: Boolean = true,
    val hideCloudDownloadButton: Boolean = true,
    val hideCloudUploadButton: Boolean = true,
    val hideScrollToBottomButton: Boolean = true,
    val hideInputBorder: Boolean = true,
    val hideSendButtonBackground: Boolean = true,
    // 新增配置项
    val hideSyncStatusIndicator: Boolean = false,  // 隐藏顶部栏右侧同步提示
    val hideModelSelectionText: Boolean = false,   // 隐藏模型选择按钮文字
    val hideInputPlaceholder: Boolean = false      // 隐藏输入框占位符
)

/**
 * UI组件配置项
 */
data class MinimalModeItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isEnabled: Boolean
)

/**
 * 获取所有可配置的极简模式项目
 */
fun getMinimalModeItems(config: MinimalModeConfig): List<MinimalModeItem> {
    return listOf(
        MinimalModeItem(
            id = "navigation_menu",
            name = "菜单按钮",
            description = "顶部导航栏的菜单按钮",
            icon = Icons.Filled.Menu,
            isEnabled = config.hideNavigationMenu
        ),
        MinimalModeItem(
            id = "welcome_text",
            name = "欢迎文字",
            description = "聊天界面为空时的问候语",
            icon = Icons.Filled.Chat,
            isEnabled = config.hideWelcomeText
        ),
        MinimalModeItem(
            id = "cloud_download",
            name = "云端获取",
            description = "从云端下载数据的浮动按钮，此按钮在开启自动同步时也会隐藏",
            icon = Icons.Filled.CloudDownload,
            isEnabled = config.hideCloudDownloadButton
        ),
        MinimalModeItem(
            id = "cloud_upload",
            name = "云端上传",
            description = "上传数据到云端的浮动按钮，此按钮在开启自动同步时也会隐藏",
            icon = Icons.Filled.CloudUpload,
            isEnabled = config.hideCloudUploadButton
        ),
        MinimalModeItem(
            id = "scroll_to_bottom",
            name = "回到底部",
            description = "快速滚动到聊天底部的按钮",
            icon = Icons.Filled.KeyboardArrowDown,
            isEnabled = config.hideScrollToBottomButton
        ),
        MinimalModeItem(
            id = "input_border",
            name = "输入框边框",
            description = "聊天输入框的边框样式",
            icon = Icons.Filled.BorderStyle,
            isEnabled = config.hideInputBorder
        ),
        MinimalModeItem(
            id = "send_button_bg",
            name = "发送按钮背景",
            description = "发送消息按钮的背景样式",
            icon = Icons.Filled.Send,
            isEnabled = config.hideSendButtonBackground
        ),
        // 新增配置项
        MinimalModeItem(
            id = "sync_status",
            name = "同步状态提示",
            description = "顶部栏右侧的同步状态指示器",
            icon = Icons.Filled.Sync,
            isEnabled = config.hideSyncStatusIndicator
        ),
        MinimalModeItem(
            id = "model_selection_text",
            name = "模型选择文字",
            description = "模型选择按钮中显示的模型名称文字",
            icon = Icons.Filled.SmartToy,
            isEnabled = config.hideModelSelectionText
        ),
        MinimalModeItem(
            id = "input_placeholder",
            name = "输入框提示文字",
            description = "输入框中的\"输入消息...\"占位符文字",
            icon = Icons.Filled.TextFields,
            isEnabled = config.hideInputPlaceholder
        )
    )
}