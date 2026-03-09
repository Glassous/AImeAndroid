package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

/**
 * 同步清单，用于快速比对云端和本地数据差异
 */
data class SyncManifest(
    @SerializedName("conversations") val conversations: Map<String, ConversationMeta>,
    @SerializedName("settingsVersion") val settingsVersion: Long = 0,
    @SerializedName("modelsVersion") val modelsVersion: Long = 0,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class ConversationMeta(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("lastMessageTime") val lastMessageTime: Long,
    @SerializedName("messageCount") val messageCount: Int,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("deletedAt") val deletedAt: Long? = null,
    @SerializedName("version") val version: Long = 1L
)
