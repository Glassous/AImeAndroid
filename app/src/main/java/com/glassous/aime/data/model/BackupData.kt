package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

/**
 * 备份数据结构（使用Long表示时间戳，避免Date的序列化差异）
 */
data class BackupData(
    @SerializedName("version") val version: Int,
    @SerializedName("exportedAt") val exportedAt: Long,
    @SerializedName("modelGroups") val modelGroups: List<ModelGroup>,
    @SerializedName("models") val models: List<Model>,
    @SerializedName("selectedModelId") val selectedModelId: String?,
    @SerializedName("conversations") val conversations: List<BackupConversation>,
    @SerializedName("apiKeys") val apiKeys: List<ApiKey>? = null
)

data class BackupConversation(
    @SerializedName("title") val title: String,
    @SerializedName("uuid") val uuid: String? = null,
    @SerializedName("lastMessage") val lastMessage: String,
    @SerializedName("lastMessageTime") val lastMessageTime: Long,
    @SerializedName("messageCount") val messageCount: Int,
    @SerializedName("messages") val messages: List<BackupMessage>,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("deletedAt") val deletedAt: Long? = null
)

data class BackupMessage(
    @SerializedName("uuid") val uuid: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("isFromUser") val isFromUser: Boolean,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("isError") val isError: Boolean?,
    @SerializedName("modelDisplayName") val modelDisplayName: String? = null
)

data class ApiKey(
    @SerializedName("platform") val platform: String,
    @SerializedName("apiKey") val apiKey: String
)
