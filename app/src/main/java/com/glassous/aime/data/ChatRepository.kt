package com.glassous.aime.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.AutoSyncPreferences
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val modelPreferences: ModelPreferences,
    private val autoSyncPreferences: AutoSyncPreferences,
    private val cloudSyncViewModel: CloudSyncViewModel,
    private val openAiService: OpenAiService = OpenAiService()
) {
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return chatDao.getAllConversations()
    }

    suspend fun sendMessage(conversationId: Long, message: String): Result<ChatMessage> {
        return try {
            // Save user message
            val userMessage = ChatMessage(
                conversationId = conversationId,
                content = message,
                isFromUser = true,
                timestamp = Date()
            )
            chatDao.insertMessage(userMessage)

            // Update conversation (user side)
            updateConversationAfterMessage(conversationId, message)

            // Resolve selected model config
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "请先选择模型（设置→模型配置）",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
            if (model == null) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "所选模型不存在或已删除，请重新选择。",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("Selected model not found"))
            }
            val group = modelConfigRepository.getGroupById(model.groupId)
            if (group == null) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("Model group not found"))
            }

            // Build conversation context for OpenAI standard
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val messages = history
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }
                .toMutableList()
            messages.add(OpenAiChatMessage(role = "user", content = message))

            // Insert assistant placeholder for streaming
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "",
                isFromUser = false,
                timestamp = Date()
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L // 限制更新频率为每300ms一次，减少频繁重组引发的抖动
            
            try {
                // Switch blocking network streaming to IO dispatcher to avoid main-thread networking
                val finalText = withContext(Dispatchers.IO) {
                    openAiService.streamChatCompletions(
                        baseUrl = group.baseUrl,
                        apiKey = group.apiKey,
                        model = model.modelName,
                        messages = messages
                    ) { delta ->
                        aggregated.append(delta)
                        val currentTime = System.currentTimeMillis()
                        
                        // 节流更新：只有当距离上次更新超过指定间隔时才更新数据库
                        if (currentTime - lastUpdateTime >= updateInterval) {
                            val updated = assistantMessage.copy(content = aggregated.toString())
                            chatDao.updateMessage(updated)
                            lastUpdateTime = currentTime
                        }
                    }
                }

                // 最终一次写入完整文本
                val finalUpdated = assistantMessage.copy(content = aggregated.toString())
                chatDao.updateMessage(finalUpdated)

                // Update conversation (assistant side)
                updateConversationAfterMessage(conversationId, message)

                Result.success(finalUpdated)
            } catch (e: Exception) {
                // On error, update assistant message with error content
                val errorUpdated = assistantMessage.copy(
                    content = "生成失败：${e.message}",
                    isError = true
                )
                chatDao.updateMessage(errorUpdated)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regenerateFromAssistant(conversationId: Long, assistantMessageId: Long): Result<Unit> {
        return try {
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == assistantMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (target.isFromUser) return Result.failure(IllegalArgumentException("Cannot regenerate user message"))

            // find previous user message
            var prevUserIndex = -1
            for (i in targetIndex - 1 downTo 0) {
                if (history[i].isFromUser && !history[i].isError) {
                    prevUserIndex = i
                    break
                }
            }
            if (prevUserIndex == -1) {
                // 更新目标消息为错误提示
                chatDao.updateMessage(
                    target.copy(
                        content = "无法重新生成：缺少前置用户消息。",
                        isError = true
                    )
                )
                return Result.failure(IllegalStateException("No preceding user message"))
            }

            // 删除目标消息后的所有消息
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 清空目标消息，作为新的流式输出占位
            chatDao.updateMessage(target.copy(content = ""))

            // 解析模型配置
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                chatDao.updateMessage(
                    target.copy(content = "请先选择模型（设置→模型配置）", isError = true)
                )
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
                ?: run {
                    chatDao.updateMessage(target.copy(content = "所选模型不存在或已删除，请重新选择。", isError = true))
                    return Result.failure(IllegalStateException("Selected model not found"))
                }
            val group = modelConfigRepository.getGroupById(model.groupId)
                ?: run {
                    chatDao.updateMessage(target.copy(content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。", isError = true))
                    return Result.failure(IllegalStateException("Model group not found"))
                }

            // 构造到前置用户消息为止的上下文
            val contextMessages = history.take(prevUserIndex + 1)
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L

            withContext(Dispatchers.IO) {
                openAiService.streamChatCompletions(
                    baseUrl = group.baseUrl,
                    apiKey = group.apiKey,
                    model = model.modelName,
                    messages = contextMessages
                ) { delta ->
                    aggregated.append(delta)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= updateInterval) {
                        val updated = target.copy(content = aggregated.toString())
                        chatDao.updateMessage(updated)
                        lastUpdateTime = currentTime
                    }
                }
            }

            // 最终写入完整文本
            chatDao.updateMessage(target.copy(content = aggregated.toString()))
            refreshConversationMetadata(conversationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewConversation(): Conversation {
        val conversation = Conversation(
            title = "新对话",
            lastMessage = "",
            lastMessageTime = Date(),
            messageCount = 0
        )
        val conversationId = chatDao.insertConversation(conversation)
        return conversation.copy(id = conversationId)
    }

    suspend fun updateConversationTitle(conversationId: Long, newTitle: String, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val updatedConversation = conversation.copy(title = newTitle)
            chatDao.updateConversation(updatedConversation)
            
            // 如果启用了自动同步，则自动上传
            if (autoSyncPreferences.autoSyncEnabled.first()) {
                cloudSyncViewModel.uploadBackup { success, message ->
                    // 通知UI更新同步状态
                    onSyncResult?.invoke(success, message)
                }
            }
        }
    }

    private suspend fun updateConversationAfterMessage(conversationId: Long, message: String) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val messageCount = chatDao.getMessageCount(conversationId)
            val updatedConversation = conversation.copy(
                title = if (conversation.title == "新对话" && messageCount > 0) {
                    message.take(20) + if (message.length > 20) "..." else ""
                } else conversation.title,
                lastMessage = message,
                lastMessageTime = Date(),
                messageCount = messageCount
            )
            chatDao.updateConversation(updatedConversation)
        }
    }

    private suspend fun refreshConversationMetadata(conversationId: Long) {
        val conversation = chatDao.getConversation(conversationId) ?: return
        val messageCount = chatDao.getMessageCount(conversationId)
        val lastMsg = chatDao.getLastMessage(conversationId)
        val updated = conversation.copy(
            lastMessage = lastMsg?.content ?: "",
            lastMessageTime = Date(),
            messageCount = messageCount
        )
        chatDao.updateConversation(updated)
    }

    suspend fun hasValidMessages(conversationId: Long): Boolean {
        return chatDao.getMessageCount(conversationId) > 0
    }

    suspend fun deleteConversation(conversationId: Long, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            chatDao.deleteMessagesForConversation(conversationId)
            chatDao.deleteConversation(conversation)
            
            // 如果启用了自动同步，则自动上传
        if (autoSyncPreferences.autoSyncEnabled.first()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                // 通知UI更新同步状态
                onSyncResult?.invoke(success, message)
            }
        }
        }
    }

    // Added: fetch single message by id
    suspend fun getMessageById(id: Long): ChatMessage? {
        return chatDao.getMessageById(id)
    }

    // Added: update message content
    suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(message)
    }

    // Added: edit user message and resend from original position
    suspend fun editUserMessageAndResend(conversationId: Long, userMessageId: Long, newContent: String, onSyncResult: ((Boolean, String) -> Unit)? = null): Result<Unit> {
        return try {
            // 获取完整历史
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == userMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (!target.isFromUser) return Result.failure(IllegalArgumentException("Only user message can be edited"))

            val trimmed = newContent.trim()
            val updatedUser = target.copy(content = trimmed)
            chatDao.updateMessage(updatedUser)

            // 删除其后的所有消息（保证重新生成上下文正确）
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 解析模型配置
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "请先选择模型（设置→模型配置）",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
                ?: run {
                    val errorMessage = ChatMessage(
                        conversationId = conversationId,
                        content = "所选模型不存在或已删除，请重新选择。",
                        isFromUser = false,
                        timestamp = Date(),
                        isError = true
                    )
                    chatDao.insertMessage(errorMessage)
                    return Result.failure(IllegalStateException("Selected model not found"))
                }
            val group = modelConfigRepository.getGroupById(model.groupId)
                ?: run {
                    val errorMessage = ChatMessage(
                        conversationId = conversationId,
                        content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。",
                        isFromUser = false,
                        timestamp = Date(),
                        isError = true
                    )
                    chatDao.insertMessage(errorMessage)
                    return Result.failure(IllegalStateException("Model group not found"))
                }

            // 构造到该用户消息为止的上下文（包含编辑后的用户消息）
            val contextMessages = history.take(targetIndex) // 不含旧用户消息
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }
                .toMutableList()
            contextMessages.add(OpenAiChatMessage(role = "user", content = trimmed))

            // 插入新的助手消息占位以进行流式写入
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "",
                isFromUser = false,
                timestamp = Date()
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L

            withContext(Dispatchers.IO) {
                openAiService.streamChatCompletions(
                    baseUrl = group.baseUrl,
                    apiKey = group.apiKey,
                    model = model.modelName,
                    messages = contextMessages
                ) { delta ->
                    aggregated.append(delta)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= updateInterval) {
                        val updated = assistantMessage.copy(content = aggregated.toString())
                        chatDao.updateMessage(updated)
                        lastUpdateTime = currentTime
                    }
                }
            }

            // 最终写入完整文本并刷新会话元数据
            chatDao.updateMessage(assistantMessage.copy(content = aggregated.toString()))
            refreshConversationMetadata(conversationId)
            
            // 如果启用了自动同步，则自动上传
            if (autoSyncPreferences.autoSyncEnabled.first()) {
                cloudSyncViewModel.uploadBackup { success, message ->
                    // 通知UI更新同步状态
                    onSyncResult?.invoke(success, message)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}