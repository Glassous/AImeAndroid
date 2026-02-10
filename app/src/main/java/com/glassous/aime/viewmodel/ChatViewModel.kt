package com.glassous.aime.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as AIMeApplication).repository
    
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
}

class ChatViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        throw IllegalArgumentException("ChatViewModelFactory requires Application context")
    }
}