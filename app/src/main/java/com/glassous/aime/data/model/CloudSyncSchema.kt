package com.glassous.aime.data.model

/**
 * 云端增量同步文件结构定义
 * - 模型配置文件：保存模型分组、模型列表与选中模型ID
 * - 历史记录索引：保存每条会话的ID、标题与更新时间戳
 * - 历史记录文件：每条会话的完整消息内容
 */

data class ModelConfigBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val modelGroups: List<ModelGroup>,
    val models: List<Model>,
    val selectedModelId: String?
)

data class HistoryIndex(
    val version: Int = 1,
    val generatedAt: Long,
    val items: List<HistoryEntry>
)

data class HistoryEntry(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val lastMessage: String
)

data class HistoryRecord(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<BackupMessage>
)