package com.glassous.aime.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Date = Date(),
    val isError: Boolean = false,
    val modelDisplayName: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null,
    val errorDetails: String? = null,
    val imagePaths: List<String> = emptyList(),
    val metadata: String? = null // For extra data like aspect ratio
)

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val lastMessage: String = "",
    val lastMessageTime: Date = Date(),
    val messageCount: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)
