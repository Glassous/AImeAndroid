package com.glassous.aime.data.model

/**
 * 备份数据结构（使用Long表示时间戳，避免Date的序列化差异）
 */
data class BackupData(
    val version: Int,
    val exportedAt: Long,
    val modelGroups: List<ModelGroup>,
    val models: List<Model>,
    val selectedModelId: String?,
    val conversations: List<BackupConversation>,
    val userProfile: UserProfile? = null
)

data class BackupConversation(
    val title: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val messageCount: Int,
    val messages: List<BackupMessage>
)

data class BackupMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isError: Boolean
)