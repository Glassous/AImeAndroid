package com.glassous.aime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RightDrawerContent(
    conversationTitle: String,
    messages: List<ChatMessage>,
    selectedTool: Tool? = null,
    toolCallInProgress: Boolean = false,
    currentToolType: ToolType? = null,
    onModelClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onToolClick: () -> Unit,
    onAnchorClick: (Int) -> Unit,
    onShowDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerTonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp)
        ) {
            // Conversation Title
            Text(
                text = if (conversationTitle.isBlank()) "新对话" else conversationTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, bottom = 24.dp)
            )

            // Header: Selected Tool Display
            if (toolCallInProgress || selectedTool != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (toolCallInProgress) (currentToolType ?: ToolType.WEB_SEARCH).icon else selectedTool!!.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (toolCallInProgress) "正在使用: ${(currentToolType ?: ToolType.WEB_SEARCH).displayName}" else "已选工具: ${selectedTool!!.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Top Section: Quick Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                QuickActionItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    text = "模型切换",
                    onClick = onModelClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    icon = Icons.Default.AttachFile,
                    text = "附件上传",
                    onClick = onAttachmentClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    icon = Icons.Default.Build,
                    text = "工具调用",
                    onClick = onToolClick,
                    modifier = Modifier.weight(1f)
                )
            }

            val bottomPadding =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp + bottomPadding)
            ) {
                itemsIndexed(messages) { index, message ->
                    // Show message if it has content OR attachments
                    if (message.content.isNotBlank() || message.imagePaths.isNotEmpty()) {
                        DirectoryItem(
                            message = message,
                            onClick = { onAnchorClick(index) },
                            onShowDetails = { onShowDetails(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectoryItem(
    message: ChatMessage,
    onClick: () -> Unit,
    onShowDetails: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (message.isFromUser) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (!expanded) onClick() },
                onLongClick = { expanded = !expanded },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Column {
            // Expanded content (Top part: Info card)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = false } // Click info card to collapse
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Timestamp
                            Text(
                                text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Model name (only for assistant messages)
                            if (!message.isFromUser && !message.modelDisplayName.isNullOrBlank()) {
                                Text(
                                    text = message.modelDisplayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        // New Row for Copy and Detail buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onShowDetails,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Show details",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "详情",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))

                            TextButton(
                                onClick = { 
                                    clipboardManager.setText(AnnotatedString(message.content))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy content",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "复制内容",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Expanded content (Full message content overlay)
            Box {
                if (!expanded) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content.take(50).replace("\n", " "),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        if (message.imagePaths.isNotEmpty()) {
                            if (message.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                message.imagePaths.take(4).forEach { path ->
                                    AttachmentPreview(path)
                                }
                                if (message.imagePaths.size > 4) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${message.imagePaths.size - 4}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                     // Expanded Full Content Card
                     Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh // Slightly distinct background
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp) // Limit height
                            .verticalScroll(rememberScrollState())
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Consume click events to prevent collapse/navigation */ }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                             if (message.content.isNotBlank()) {
                                MarkdownRenderer(
                                    markdown = message.content,
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    textSizeSp = 14f,
                                    onLongClick = {}, // Disable long click in preview
                                    modifier = Modifier.fillMaxWidth(),
                                    enableTables = true,
                                    enableCodeBlocks = true,
                                    enableLatex = true
                                )
                            }
                            
                            if (message.imagePaths.isNotEmpty()) {
                                if (message.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                // Show all attachments in expanded mode
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    message.imagePaths.forEach { path ->
                                        AttachmentPreview(path)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(path: String) {
    val isImage = path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) || 
                  path.endsWith(".png", true) || path.endsWith(".webp", true) ||
                  path.startsWith("content://") // Assume content URIs might be images for now
    val isVideo = path.endsWith(".mp4", true) || path.endsWith(".mkv", true) || path.endsWith(".webm", true)
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isImage || isVideo) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        } else {
            val icon = when {
                path.endsWith(".pdf", true) -> Icons.Default.Description
                path.endsWith(".txt", true) -> Icons.Default.Article
                path.contains("audio", true) -> Icons.Default.AudioFile
                else -> Icons.Default.InsertDriveFile
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
