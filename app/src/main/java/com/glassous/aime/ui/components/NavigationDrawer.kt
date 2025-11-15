package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.Conversation
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onConversationSelect: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onEditConversationTitle: (Long, String) -> Unit,
    onGenerateShareCode: (Long, (String?) -> Unit) -> Unit,
    onImportSharedConversation: (String, (Long?) -> Unit) -> Unit,
    hideImportSharedButton: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "AIme",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Cursive
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // New Conversation Button
            FilledTonalButton(
                onClick = onNewConversation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建对话",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建对话")
            }

            var showImportDialog by remember { mutableStateOf(false) }
            var importCode by remember { mutableStateOf("") }
            val context = LocalContext.current
            val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
                    importCode = code
                }
            }

            if (!hideImportSharedButton) {
                OutlinedButton(
                    onClick = { showImportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "获取分享的对话",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("获取分享的对话")
                }
            }

            // Conversations List
            if (conversations.isNotEmpty()) {
                Text(
                    text = "对话历史",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            isSelected = conversation.id == currentConversationId,
                            onSelect = { onConversationSelect(conversation.id) },
                            onDelete = { onDeleteConversation(conversation.id) },
                            onEditTitle = { conversationId, newTitle ->
                                onEditConversationTitle(conversationId, newTitle)
                            },
                            onShare = { convId, onCode ->
                                onGenerateShareCode(convId) { code ->
                                    onCode(code)
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无对话记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Settings Button
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置")
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("获取分享的对话") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = importCode,
                                onValueChange = { importCode = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("粘贴分享码") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { importJsonLauncher.launch(arrayOf("application/json")) }) {
                                    Text("从JSON文件获取")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onImportSharedConversation(importCode) { _ ->
                                    showImportDialog = false
                                    importCode = ""
                                }
                            }
                        ) {
                            Text("插入")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEditTitle: (Long, String) -> Unit,
    onShare: (Long, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    var isExpanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf(conversation.title) }
    // 新增：删除确认弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareCode by remember { mutableStateOf<String?>(null) }
    var isLongShare by remember { mutableStateOf(false) }
    var shareJson by remember { mutableStateOf<String>("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(shareJson.toByteArray(Charsets.UTF_8))
            }
            showShareDialog = false
        }
    }
    
    Card(
        onClick = { 
            if (!isExpanded && !isEditing) {
                onSelect()
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editingTitle,
                            onValueChange = { editingTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 删除回答与时间戳展示，仅保留标题
                    // (移除原先的 lastMessage 与 lastMessageTime 文本块)

                }

                if (isEditing) {
                    Row {
                        IconButton(
                            onClick = {
                                onEditTitle(conversation.id, editingTitle)
                                isEditing = false
                                isExpanded = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "确认编辑",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                editingTitle = conversation.title
                                isEditing = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = { 
                            isExpanded = !isExpanded
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // 展开的编辑和删除选项（加入平滑动画）
            AnimatedVisibility(
                visible = isExpanded && !isEditing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isEditing = true
                            isExpanded = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    OutlinedButton(
                        onClick = {
                            showDeleteConfirm = true
                            isExpanded = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onShare(conversation.id) { code ->
                                shareCode = code
                                isLongShare = (code?.length ?: 0) > 3000
                                if (isLongShare) {
                                    if (code != null) {
                                        try {
                                            shareJson = String(java.util.Base64.getUrlDecoder().decode(code), Charsets.UTF_8)
                                        } catch (_: Exception) {
                                            shareJson = String(java.util.Base64.getDecoder().decode(code), Charsets.UTF_8)
                                        }
                                    } else {
                                        shareJson = ""
                                    }
                                }
                                showShareDialog = true
                                isExpanded = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // 删除确认弹窗
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("确认删除对话") },
                    text = { Text("删除后不可恢复，确定要删除该对话吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }

            if (showShareDialog) {
                AlertDialog(
                    onDismissRequest = { showShareDialog = false },
                    title = { Text("分享对话") },
                    text = {
                        Column {
                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = if (isLongShare) "分享码较长，请导出为JSON文件" else (shareCode ?: "正在生成分享码…"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isLongShare) {
                                TextButton(
                                    onClick = {
                                        shareCode?.let { code ->
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                                            showShareDialog = false
                                        }
                                    }
                                ) { Text("复制分享码") }
                                TextButton(
                                    onClick = {
                                        shareCode?.let { code ->
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, code)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享对话"))
                                            showShareDialog = false
                                        }
                                    }
                                ) { Text("系统分享") }
                            } else {
                                TextButton(
                                    onClick = {
                                        exportLauncher.launch("shared-conversation-${System.currentTimeMillis()}.json")
                                    }
                                ) { Text("导出JSON") }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showShareDialog = false }) { Text("关闭") }
                    }
                )
            }
        }
    }
}