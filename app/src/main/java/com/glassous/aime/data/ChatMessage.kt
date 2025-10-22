package com.glassous.aime.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Date = Date(),
    val isError: Boolean = false
)

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val lastMessage: String = "",
    val lastMessageTime: Date = Date(),
    val messageCount: Int = 0
)