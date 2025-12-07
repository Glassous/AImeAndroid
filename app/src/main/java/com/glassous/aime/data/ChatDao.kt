package com.glassous.aime.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: Long): Conversation?

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Insert
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: Long): ChatMessage?

    // Added: fetch single message by id
    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): ChatMessage?

    // Added: delete all data methods for import override mode
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    // Added: delete subsequent messages after a timestamp in the same conversation
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId AND timestamp >= :timestamp")
    suspend fun deleteMessagesAfter(conversationId: Long, timestamp: Date)
    
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId AND id = :messageId")
    suspend fun deleteMessageById(conversationId: Long, messageId: Long)
}