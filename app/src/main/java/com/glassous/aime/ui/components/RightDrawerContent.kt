package com.glassous.aime.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RightDrawerContent(
    messages: List<ChatMessage>,
    selectedTool: Tool? = null,
    toolCallInProgress: Boolean = false,
    currentToolType: ToolType? = null,
    onModelClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onToolClick: () -> Unit,
    onAnchorClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp)
        ) {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                QuickActionItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    text = "模型切换",
                    onClick = onModelClick
                )
                QuickActionItem(
                    icon = Icons.Default.AttachFile,
                    text = "附件上传",
                    onClick = onAttachmentClick
                )
                QuickActionItem(
                    icon = Icons.Default.Build,
                    text = "工具调用",
                    onClick = onToolClick
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
                    // Only show non-empty messages
                    if (message.content.isNotBlank()) {
                        DirectoryItem(
                            message = message,
                            onClick = { onAnchorClick(index) }
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectoryItem(
    message: ChatMessage,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (message.isFromUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp),
                tint = if (message.isFromUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.content.take(50).replace("\n", " "),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
