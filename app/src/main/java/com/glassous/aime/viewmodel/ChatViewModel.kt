package com.glassous.aime.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import com.glassous.aime.data.repository.SupabaseShareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.glassous.aime.BuildConfig

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as AIMeApplication).repository
    private val chatDao = (application as AIMeApplication).database.chatDao()
    
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    val conversations: StateFlow<List<Conversation>> = repository.getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val currentMessages: StateFlow<List<ChatMessage>> = _currentConversationId
        .flatMapLatest { conversationId ->
            if (conversationId != null) {
                repository.getMessagesForConversation(conversationId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    fun shareConversation(
        title: String,
        model: String,
        messages: List<ChatMessage>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isSharing.value) return
        
        viewModelScope.launch {
            _isSharing.value = true
            try {
                val url = com.glassous.aime.data.repository.SupabaseShareRepository.uploadConversation(title, model, messages)
                onSuccess(url)
            } catch (e: Exception) {
                onError(e.message ?: "分享失败")
            } finally {
                _isSharing.value = false
            }
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    private val _toolCallInProgress = MutableStateFlow(false)
    val toolCallInProgress: StateFlow<Boolean> = _toolCallInProgress.asStateFlow()

    private val _currentToolType = MutableStateFlow<ToolType?>(null)
    val currentToolType: StateFlow<ToolType?> = _currentToolType.asStateFlow()

    fun sendMessage(content: String, selectedTool: Tool? = null, isAutoMode: Boolean = false) {
        // Prevent sending empty messages
        if (content.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversationId = _currentConversationId.value ?: run {
                    // Check for existing empty conversation first
                    val currentConversations = conversations.value
                    val emptyConversation = currentConversations.find { it.messageCount == 0 }
                    
                    if (emptyConversation != null) {
                        _currentConversationId.value = emptyConversation.id
                        emptyConversation.id
                    } else {
                        // Create new conversation if none exists
                        val newConversation = repository.createNewConversation()
                        _currentConversationId.value = newConversation.id
                        newConversation.id
                    }
                }
                
                _inputText.value = ""
                
                repository.sendMessage(
                    conversationId,
                    content,
                    selectedTool,
                    isAutoMode,
                    onToolCallStart = { type ->
                        _currentToolType.value = type
                        _toolCallInProgress.value = true
                    },
                    onToolCallEnd = {
                        _toolCallInProgress.value = false
                        _currentToolType.value = null
                    }
                )
                
                launch {
                    repository.tryAutoGenerateTitle(conversationId)
                }
            } catch (e: Exception) {
                // Handle error - could show a snackbar or error message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun regenerateFromAssistant(messageId: Long, selectedTool: Tool? = null, isAutoMode: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val msg = repository.getMessageById(messageId)
                if (msg != null && !msg.isFromUser) {
                    repository.regenerateFromAssistant(
                        msg.conversationId,
                        msg.id,
                        selectedTool,
                        isAutoMode,
                        onToolCallStart = { type ->
                            _currentToolType.value = type
                            _toolCallInProgress.value = true
                        },
                        onToolCallEnd = {
                            _toolCallInProgress.value = false
                            _currentToolType.value = null
                        }
                    )
                }
            } catch (_: Exception) {
                // swallow for now; UI can display error via message insertion
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resendMessage(
        conversationId: Long,
        userMessageId: Long,
        newContent: String,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false
    ) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                     repository.editUserMessageAndResend(
                        conversationId = conversationId,
                        userMessageId = userMessageId,
                        newContent = newContent,
                        selectedTool = selectedTool,
                        isAutoMode = isAutoMode,
                        onToolCallStart = { type ->
                            _toolCallInProgress.value = true
                            _currentToolType.value = type
                        },
                        onToolCallEnd = {
                            _toolCallInProgress.value = false
                            _currentToolType.value = null
                        }
                    )
                }
            } catch (_: Exception) {
                // swallow for now; UI可通过错误消息提示
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryFailedMessage(
        conversationId: Long,
        failedMessageId: Long,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false
    ) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.retryFailedMessage(
                        conversationId = conversationId,
                        failedMessageId = failedMessageId,
                        selectedTool = selectedTool,
                        isAutoMode = isAutoMode,
                        onToolCallStart = { type ->
                            _toolCallInProgress.value = true
                            _currentToolType.value = type
                        },
                        onToolCallEnd = {
                            _toolCallInProgress.value = false
                            _currentToolType.value = null
                        }
                    )
                }
            } catch (_: Exception) {
                // swallow for now; UI可通过错误消息提示
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
    }
    
    fun createNewConversation() {
        // 初始化页面状态，类似重新进入应用
        // 清除当前对话选择，回到初始状态
        _currentConversationId.value = null
        // 清空输入框
        _inputText.value = ""
    }
    
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            // If we deleted the current conversation, clear the selection
            if (_currentConversationId.value == conversationId) {
                _currentConversationId.value = null
            }
        }
    }

    fun updateConversationTitle(conversationId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(conversationId, newTitle)
        }
    }

    fun generateConversationTitle(conversationId: Long, onTitleGenerated: (String) -> Unit, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.generateConversationTitle(conversationId, onTitleGenerated)
            } catch (e: Exception) {
                // 如果生成失败，返回默认标题
                onTitleGenerated("新对话")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun isSharedConversationUrl(input: String): Boolean {
        // 简单判断是否包含 BuildConfig.SHARE_BASE_URL 且包含 UUID
        // UUID regex: 8-4-4-4-12 hex digits
        val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex(RegexOption.IGNORE_CASE)
        val baseUrl = BuildConfig.SHARE_BASE_URL.trimEnd('/')
        return input.contains(baseUrl, ignoreCase = true) && uuidRegex.containsMatchIn(input)
    }

    fun importSharedConversation(input: String, onResult: (Boolean, String) -> Unit) {
        if (_isLoading.value || _isImporting.value) return
        _isLoading.value = true
        _isImporting.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Extract UUID
                val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex(RegexOption.IGNORE_CASE)
                val match = uuidRegex.find(input)
                val uuid = match?.value
                
                if (uuid == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "无效的链接或ID")
                    }
                    return@launch
                }

                // 2. Fetch data
                val data = SupabaseShareRepository.getSharedConversation(uuid)

                // 3. Create Conversation
                val now = java.util.Date()
                val newConv = Conversation(
                    title = data.title,
                    lastMessage = data.messages.lastOrNull()?.content ?: "",
                    lastMessageTime = now,
                    messageCount = data.messages.size
                )
                val convId = chatDao.insertConversation(newConv)

                // 4. Create Messages
                data.messages.forEachIndexed { index, msgDto ->
                    val msgTime = java.util.Date(now.time + index * 100)
                    val chatMsg = ChatMessage(
                        conversationId = convId,
                        content = msgDto.content,
                        isFromUser = msgDto.role == "user",
                        timestamp = msgTime,
                        modelDisplayName = if (msgDto.role != "user") data.model else null
                    )
                    chatDao.insertMessage(chatMsg)
                }

                withContext(Dispatchers.Main) {
                    _currentConversationId.value = convId
                    onResult(true, "导入成功：${data.title}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "导入失败: ${e.message}")
                }
            } finally {
                _isLoading.value = false
                _isImporting.value = false
            }
        }
    }
}

class ChatViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        throw IllegalArgumentException("ChatViewModelFactory requires Application context")
    }
}