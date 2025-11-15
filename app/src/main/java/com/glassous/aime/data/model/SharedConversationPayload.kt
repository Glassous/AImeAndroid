package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

data class SharedConversationPayload(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("title") val title: String,
    @SerializedName("messages") val messages: List<SharedMessage>
)

data class SharedMessage(
    @SerializedName("content") val content: String,
    @SerializedName("isFromUser") val isFromUser: Boolean,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("isError") val isError: Boolean? = null
)