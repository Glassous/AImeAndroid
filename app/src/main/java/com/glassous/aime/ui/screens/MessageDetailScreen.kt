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
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    onNavigateBack: () -> Unit,
    cloudSyncViewModel: CloudSyncViewModel
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val repository = application.repository
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<ChatMessage?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(16f) }

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
                    
                    // 触发自动上传
                    cloudSyncViewModel.uploadBackup { success, uploadMessage ->
                        // 这里可以添加成功/失败的处理逻辑
                        // 例如显示Toast或Snackbar
                    }
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
    }
}