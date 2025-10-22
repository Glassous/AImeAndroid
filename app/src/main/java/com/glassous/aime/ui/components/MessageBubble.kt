package com.glassous.aime.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onShowDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        // Message bubble
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showDialog = true }
                ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 16.dp
            ),
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                message.isFromUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        message.isError -> MaterialTheme.colorScheme.onErrorContainer
                        message.isFromUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }

    if (showDialog) {
        BasicAlertDialog(
            onDismissRequest = { showDialog = false }
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            showDialog = false
                        }
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制全文")
                    }
                    TextButton(
                        onClick = {
                            showDialog = false
                            onShowDetails(message.id)
                        }
                    ) {
                        Icon(Icons.Outlined.Article, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("查看详情")
                    }
                }
            }
        }
    }
}