package com.glassous.aime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.ChatRepository
import com.glassous.aime.data.Conversation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
    
    fun sendMessage(content: String) {
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
                
                repository.sendMessage(conversationId, content)
                _inputText.value = ""
            } catch (e: Exception) {
                // Handle error - could show a snackbar or error message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
    }
    
    fun createNewConversation() {
        viewModelScope.launch {
            // Check if there's already an empty conversation
            val currentConversations = conversations.value
            val emptyConversation = currentConversations.find { it.messageCount == 0 }
            
            if (emptyConversation != null) {
                // Use existing empty conversation
                _currentConversationId.value = emptyConversation.id
            } else {
                // Create new conversation only if no empty one exists
                val newConversation = repository.createNewConversation()
                _currentConversationId.value = newConversation.id
            }
        }
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
}

class ChatViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        throw IllegalArgumentException("ChatViewModelFactory requires Application context")
    }
}