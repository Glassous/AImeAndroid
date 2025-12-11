package com.glassous.aime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
 
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val repository = application.repository
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<ChatMessage?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(16f) }
    var showStatsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messageId) {
        message = repository.getMessageById(messageId)
        message?.let { editedContent = it.content }
    }

    fun saveMessage() {
        message?.let { msg ->
            scope.launch {
                try {
                    val updatedMessage = msg.copy(content = editedContent.trim())
                    repository.updateMessage(updatedMessage)
                    message = updatedMessage
                    isEditing = false
                    
                    
                } catch (e: Exception) {
                    // 处理保存失败的情况
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 字体大小控制
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "A",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 12f..24f,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            text = "A",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showStatsDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "信息")
                    }
                    
                    // 编辑按钮组
                    if (isEditing) {
                        // 取消按钮
                        IconButton(
                            onClick = {
                                isEditing = false
                                message?.let { editedContent = it.content }
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "取消")
                        }
                        // 保存按钮
                        IconButton(onClick = { saveMessage() }) {
                            Icon(Icons.Filled.Check, contentDescription = "保存")
                        }
                    } else {
                        // 编辑按钮
                        IconButton(
                            onClick = { isEditing = true }
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            if (message == null) {
                Text(
                    text = "未找到消息或已删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = fontSize.sp
                )
            } else {
                if (isEditing) {
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = fontSize.sp),
                        placeholder = { Text("输入消息内容...") }
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = message!!.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSize.sp
                        )
                    }
                }
            }
        }

        if (showStatsDialog) {
            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                confirmButton = {
                    TextButton(onClick = { showStatsDialog = false }) { Text("关闭") }
                },
                title = { Text("信息") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val contentText = message?.content ?: ""
                        val timestamp = message?.timestamp
                        if (timestamp != null) {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            Text(text = "时间：${dateFormat.format(timestamp)}")
                        }
                        Text(text = "字数：${contentText.length}")
                        val modelName = message?.modelDisplayName
                        if (!modelName.isNullOrBlank()) {
                            Text(text = "模型：$modelName")
                        }
                        val registry = remember { Encodings.newDefaultEncodingRegistry() }
                        val encodingType = remember(modelName) {
                            val name = modelName?.lowercase() ?: ""
                            when {
                                name.contains("gpt-4o") -> EncodingType.CL100K_BASE
                                name.contains("gpt-4") -> EncodingType.CL100K_BASE
                                name.contains("gpt-3.5") -> EncodingType.CL100K_BASE
                                else -> EncodingType.CL100K_BASE
                            }
                        }
                        val encoding = remember(encodingType) { registry.getEncoding(encodingType) }
                        val conversationId = message?.conversationId
                        val messagesInConversation by remember(conversationId) {
                            if (conversationId != null) repository.getMessagesForConversation(conversationId) else kotlinx.coroutines.flow.flowOf(emptyList())
                        }.collectAsState(initial = emptyList())
                        val maxContextMessages by remember(application) { application.contextPreferences.maxContextMessages }.collectAsState(initial = 5)
                        val currentMsg = message
                        val filtered = messagesInConversation.filter { !it.isError }
                        val idx = filtered.indexOfFirst { it.id == currentMsg?.id }
                        val (inputTokens, outputTokens) = if (idx != -1 && currentMsg != null) {
                            if (currentMsg.isFromUser) {
                                val upto = filtered.subList(0, idx + 1)
                                val limited = if (maxContextMessages <= 0) upto else upto.takeLast(maxContextMessages)
                                val inTokens = limited.sumOf { try { encoding.countTokens(it.content) } catch (_: Exception) { 0 } }
                                val nextAssistant = filtered.drop(idx + 1).firstOrNull { !it.isFromUser }
                                val outTokens = nextAssistant?.let { try { encoding.countTokens(it.content) } catch (_: Exception) { 0 } } ?: 0
                                inTokens to outTokens
                            } else {
                                val before = filtered.subList(0, idx)
                                val lastUserIndex = before.indexOfLast { it.isFromUser }
                                val uptoUser = if (lastUserIndex != -1) filtered.subList(0, lastUserIndex + 1) else emptyList()
                                val limited = if (maxContextMessages <= 0) uptoUser else uptoUser.takeLast(maxContextMessages)
                                val inTokens = limited.sumOf { try { encoding.countTokens(it.content) } catch (_: Exception) { 0 } }
                                val outTokens = try { encoding.countTokens(currentMsg.content) } catch (_: Exception) { 0 }
                                inTokens to outTokens
                            }
                        } else {
                            val inTokens = try { encoding.countTokens(currentMsg?.content ?: "") } catch (_: Exception) { 0 }
                            val outTokens = 0
                            inTokens to outTokens
                        }
                        Text(text = "输入token：$inputTokens")
                        Text(text = "输出token：$outputTokens")
                    }
                }
            )
        }
    }
}
