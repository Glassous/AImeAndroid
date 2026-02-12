package com.glassous.aime.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class SharedConversationRequest(
    val title: String,
    val model: String,
    val messages: List<SharedMessageDto>
)

@Serializable
data class SharedConversationResponse(
    val id: String
)

@Serializable
data class SharedConversationRow(
    val id: String,
    val title: String,
    val model: String? = null,
    val messages: List<SharedMessageDto>
)
