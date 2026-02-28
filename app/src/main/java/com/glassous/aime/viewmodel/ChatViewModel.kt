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

    private val _attachedImages = MutableStateFlow<List<String>>(emptyList())
    val attachedImages: StateFlow<List<String>> = _attachedImages.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow("1:1")
    val selectedAspectRatio: StateFlow<String> = _selectedAspectRatio.asStateFlow()

    fun updateAspectRatio(ratio: String) {
        _selectedAspectRatio.value = ratio
    }
    
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
                val url = com.glassous.aime.data.repository.SupabaseShareRepository.uploadConversation(
                    getApplication(), 
                    title, 
                    model, 
                    messages
                )
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

    fun addAttachment(uri: android.net.Uri, context: android.content.Context, type: String = "image") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get original filename
                var originalName = when (type) {
                    "video" -> "video.mp4"
                    "audio" -> "audio.m4a"
                    "pdf" -> "document.pdf"
                    "text" -> "file.txt"
                    else -> "image.jpg"
                }
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) {
                        originalName = cursor.getString(nameIndex)
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val imagesDir = java.io.File(context.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                
                // Create a unique file name
                val extension = when (type) {
                    "video" -> ".mp4"
                    "audio" -> ".m4a"
                    "pdf" -> ".pdf"
                    "text" -> {
                        val ext = originalName.substringAfterLast(".", "")
                        if (ext.isNotEmpty()) ".$ext" else ".txt"
                    }
                    else -> ".jpg"
                }
                val prefix = when (type) {
                    "video" -> "vid_"
                    "audio" -> "aud_"
                    "pdf" -> "pdf_${originalName}_" // Include original name in the saved path
                    "text" -> "txt_${originalName}_" // Include original name in the saved path
                    else -> "img_"
                }
                // Sanitize prefix to avoid illegal characters in path (allow Unicode/Chinese)
                val sanitizedPrefix = prefix.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val fileName = "${sanitizedPrefix}${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}$extension"
                val file = java.io.File(imagesDir, fileName)
                
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                val currentList = _attachedImages.value.toMutableList()
                currentList.add(file.absolutePath)
                _attachedImages.value = currentList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addLinkAttachment(url: String, context: android.content.Context) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return

        val type = when {
            // YouTube check
            trimmedUrl.contains("youtube.com/watch", ignoreCase = true) || 
            trimmedUrl.contains("youtu.be/", ignoreCase = true) -> "video_url"
            
            // Image extensions
            trimmedUrl.endsWith(".png", ignoreCase = true) || 
            trimmedUrl.endsWith(".jpg", ignoreCase = true) || 
            trimmedUrl.endsWith(".jpeg", ignoreCase = true) || 
            trimmedUrl.endsWith(".webp", ignoreCase = true) || 
            trimmedUrl.endsWith(".gif", ignoreCase = true) -> "image_url"
            
            // Video extensions
            trimmedUrl.endsWith(".mp4", ignoreCase = true) || 
            trimmedUrl.endsWith(".mpeg", ignoreCase = true) || 
            trimmedUrl.endsWith(".mov", ignoreCase = true) || 
            trimmedUrl.endsWith(".webm", ignoreCase = true) -> "video_url"
            
            // PDF extension
            trimmedUrl.endsWith(".pdf", ignoreCase = true) -> "pdf_url"
            
            else -> null
        }

        if (type != null) {
            val currentList = _attachedImages.value.toMutableList()
            // Use a prefix to distinguish URL attachments from local paths
            currentList.add("url:${type}:${trimmedUrl}")
            _attachedImages.value = currentList
        } else {
            // Show toast or error? For now just ignore as requested "格式不支持则无法加入待定区域"
            android.widget.Toast.makeText(context, "不支持的链接格式", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Deprecated: helper for backward compatibility if needed, but we will update callers
    fun addAttachment(uri: android.net.Uri, context: android.content.Context, isVideo: Boolean) {
        addAttachment(uri, context, if (isVideo) "video" else "image")
    }

    fun removeAttachment(path: String) {
        val currentList = _attachedImages.value.toMutableList()
        if (currentList.remove(path)) {
            _attachedImages.value = currentList
            // Optionally delete the file if it was just a temp attachment?
            // But we might want to keep it if it's already saved in a message?
            // Here we are removing from the *input* attachment list, so we can probably delete it if it's not referenced elsewhere.
            // But for simplicity, we just remove from list. The file remains.
        }
    }
    
    private val _toolCallInProgress = MutableStateFlow(false)
    val toolCallInProgress: StateFlow<Boolean> = _toolCallInProgress.asStateFlow()

    private val _currentToolType = MutableStateFlow<ToolType?>(null)
    val currentToolType: StateFlow<ToolType?> = _currentToolType.asStateFlow()

    fun sendMessage(content: String, selectedTool: Tool? = null) {
        // Prevent sending empty messages unless there are attachments
        if (content.isBlank() && _attachedImages.value.isEmpty()) return
        
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
                
                val imagesToSend = _attachedImages.value.filter { !it.contains("/txt_") && !it.startsWith("url:") }.toList()
                val textFiles = _attachedImages.value.filter { it.contains("/txt_") }
                val urlFiles = _attachedImages.value.filter { it.startsWith("url:") }
                
                var finalContent = content
                textFiles.forEach { path ->
                    try {
                        val file = java.io.File(path)
                        val fileName = file.name
                            .substringAfter("txt_")
                            .substringBeforeLast("_") // Remove UUID
                            .substringBeforeLast("_") // Remove timestamp
                        val textContent = file.readText()
                        finalContent += "\n\n<file name=\"$fileName\">\n$textContent\n</file>"
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                urlFiles.forEachIndexed { index, path ->
                    // url:type:actual_url
                    val urlParts = path.split(":", limit = 3)
                    if (urlParts.size == 3) {
                        val actualUrl = urlParts[2]
                        finalContent += "\n\n<file_url index=\"${index + 1}\" url=\"$actualUrl\" />"
                    }
                }
                
                val currentAspectRatio = _selectedAspectRatio.value
                _inputText.value = ""
                _attachedImages.value = emptyList()
                
                // CRITICAL: Filter out URL attachments from being sent to the AI backend via imagePaths
                // The URL info is already included in finalContent as <file_url> tags
                // And repository.sendMessage will handle the actual content parts
                val localAttachments = imagesToSend
                
                // Add all URL attachments (including non-image ones) to a separate list or handle in repository
                val allAttachmentsForBackend = localAttachments + urlFiles
                
                repository.sendMessage(
                    conversationId,
                    finalContent,
                    allAttachmentsForBackend,
                    selectedTool,
                    aspectRatio = currentAspectRatio,
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

    fun regenerateFromAssistant(messageId: Long, selectedTool: Tool? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val msg = repository.getMessageById(messageId)
                if (msg != null && !msg.isFromUser) {
                    val currentAspectRatio = _selectedAspectRatio.value
                    repository.regenerateFromAssistant(
                        msg.conversationId,
                        msg.id,
                        selectedTool,
                        aspectRatio = currentAspectRatio,
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
        selectedTool: Tool? = null
    ) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val currentAspectRatio = _selectedAspectRatio.value
                withContext(Dispatchers.IO) {
                     repository.editUserMessageAndResend(
                        conversationId = conversationId,
                        userMessageId = userMessageId,
                        newContent = newContent,
                        selectedTool = selectedTool,
                        aspectRatio = currentAspectRatio,
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
        selectedTool: Tool? = null
    ) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val currentAspectRatio = _selectedAspectRatio.value
                withContext(Dispatchers.IO) {
                    repository.retryFailedMessage(
                        conversationId = conversationId,
                        failedMessageId = failedMessageId,
                        selectedTool = selectedTool,
                        aspectRatio = currentAspectRatio,
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