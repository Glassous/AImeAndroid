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
    val hideScrollToBottomButton: Boolean = true,
    val hideInputBorder: Boolean = true,
    val hideSendButtonBackground: Boolean = true,
    // 新增：隐藏顶部栏工具图标
    val hideToolIcon: Boolean = false,
    // 新增配置项
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
            id = "tool_icon",
            name = "工具图标",
            description = "顶部栏的工具图标",
            icon = Icons.Filled.Build,
            isEnabled = config.hideToolIcon
        ),
        MinimalModeItem(
            id = "welcome_text",
            name = "欢迎文字",
            description = "聊天界面为空时的问候语",
            icon = Icons.Filled.Chat,
            isEnabled = config.hideWelcomeText
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
