package com.glassous.aime.ui.navigation

import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType

/**
 * 工具到路由的映射关系
 */
object ToolRouteMapper {
    /**
     * 根据工具返回目标路由字符串
     */
    fun routeFor(tool: Tool?): String {
        return when (tool?.type) {
            ToolType.WEB_SEARCH -> "chat"
            else -> "chat"
        }
    }
}