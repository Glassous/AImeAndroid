package com.glassous.aime.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.TrendingUp
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
    WEATHER_QUERY(
        displayName = "城市天气查询",
        icon = Icons.Default.WbSunny,
        description = "查询指定城市的未来几天天气与空气质量"
    ),
    STOCK_QUERY(
        displayName = "股票数据查询",
        icon = Icons.Default.TrendingUp,
        description = "查询指定证券代码的历史行情数据（开盘/收盘/振幅等）"
    ),
    GOLD_PRICE(
        displayName = "黄金价格查询",
        icon = Icons.Default.TrendingUp,
        description = "查询银行金条、回收价格与品牌贵金属价格"
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