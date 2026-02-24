package com.glassous.aime.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 工具类型枚举
 */
enum class ToolType(
    val displayName: String,
    val icon: ImageVector,
    val description: String
) {
    WEB_SEARCH(
        displayName = "联网搜索",
        icon = Icons.Default.Search,
        description = "通过互联网搜索获取实时信息"
    ),
    WEB_ANALYSIS(
        displayName = "网页分析",
        icon = Icons.Default.Language,
        description = "深入分析指定网页的内容、结构与元数据"
    ),
    MUSIC_SEARCH(
        displayName = "音乐搜索",
        icon = Icons.Default.MusicNote,
        description = "搜索全网音乐并获取播放链接"
    ),
    WEATHER_QUERY(
        displayName = "城市天气查询",
        icon = Icons.Default.WbSunny,
        description = "查询指定城市的未来几天天气与空气质量"
    ),
    BAIDU_TIKU(
        displayName = "百度题库搜索",
        icon = Icons.Default.Search,
        description = "检索题库并返回题干/选项/答案"
    );
    
    companion object {
        /**
         * 获取所有可用的工具
         */
        fun getAllTools(): List<ToolType> = values().toList()
    }
}

/**
 * 工具数据类
 */
data class Tool(
    val type: ToolType,
    val isEnabled: Boolean = true
) {
    val displayName: String get() = type.displayName
    val icon: ImageVector get() = type.icon
    val description: String get() = type.description
}
