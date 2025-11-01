package com.glassous.aime.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import com.glassous.aime.data.ChatMessage

// 供全局提供/获取弹窗时的背景模糊状态
val LocalDialogBlurState = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onShowDetails: (Long) -> Unit,
    onRegenerate: ((Long) -> Unit)? = null,
    onEditUserMessage: ((Long, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    // 新增：控制 AI 回复是否以气泡展示
    replyBubbleEnabled: Boolean = true,
    // 新增：聊天字体大小
    chatFontSize: Float = 16f,
    // 新增：是否正在流式输出（用于打字机效果）
    isStreaming: Boolean = false,
    // 新增：是否启用打字机效果
    enableTypewriterEffect: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val blurState = LocalDialogBlurState.current

    // 弹窗显示时启用背景模糊（任一弹窗打开都模糊），关闭时立即禁用
    LaunchedEffect(showDialog, showEditDialog) {
        blurState.value = showDialog || showEditDialog
    }

    // 确保组件销毁时重置模糊状态
    DisposableEffect(Unit) {
        onDispose {
            blurState.value = false
        }
    }

    val useBubble = message.isFromUser || replyBubbleEnabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (useBubble) {
            // 维持原有气泡样式，实现自适应宽度
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(max = 300.dp)
                    .animateContentSize()
                    .testTag("bubble-${message.id}"),
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
                    val textColor = when {
                        message.isError -> MaterialTheme.colorScheme.onErrorContainer
                        message.isFromUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val textSizeSp = chatFontSize

                    // 识别特殊两段格式，使用可折叠框渲染
                    val hasTwoPartReply = !message.isFromUser && (
                        message.content.contains("【前置回复】") ||
                        message.content.contains("【第一次回复】") ||
                        message.content.contains("【正式回复】")
                    )

                    if (hasTwoPartReply) {
                        ExpandableReplyBox(
                            content = message.content,
                            textColor = textColor,
                            textSizeSp = textSizeSp,
                            isStreaming = isStreaming,
                            onLongClick = { showDialog = true }
                        )
                    } else {
                        // 根据是否为AI回复且启用打字机效果来选择渲染组件
                        if (!message.isFromUser && enableTypewriterEffect) {
                            TypewriterMarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { showDialog = true },
                                isStreaming = isStreaming,
                                typingDelayMs = 30L
                            )
                        } else {
                            MarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { showDialog = true }
                            )
                        }
                    }
                }
            }
        } else {
            // 新增：AI 回复直接在页面背景渲染（无气泡）
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .testTag("bubble-${message.id}")
            ) {
                val textColor = MaterialTheme.colorScheme.onSurface
                val textSizeSp = chatFontSize
                // 为了与截图风格更接近，增加左右留白与分段
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                ) {
                    // 识别特殊两段格式，使用可折叠框渲染
                    val hasTwoPartReply = !message.isFromUser && (
                        message.content.contains("【前置回复】") ||
                        message.content.contains("【第一次回复】") ||
                        message.content.contains("【正式回复】")
                    )

                    if (hasTwoPartReply) {
                        ExpandableReplyBox(
                            content = message.content,
                            textColor = textColor,
                            textSizeSp = textSizeSp,
                            isStreaming = isStreaming,
                            onLongClick = { showDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // 根据是否为AI回复且启用打字机效果来选择渲染组件
                        if (!message.isFromUser && enableTypewriterEffect) {
                            TypewriterMarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { showDialog = true },
                                isStreaming = isStreaming,
                                typingDelayMs = 30L
                            )
                        } else {
                            MarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { showDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        BasicAlertDialog(
            onDismissRequest = {
                showDialog = false
            }
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            showDialog = false
                        }
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制全文")
                    }

                    if (!message.isFromUser) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDialog = false
                                onRegenerate?.invoke(message.id)
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重新生成")
                        }
                    }

                    if (message.isFromUser) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                // 关闭长按弹窗并打开编辑弹窗
                                showDialog = false
                                editText = message.content
                                showEditDialog = true
                            }
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("编辑消息")
                        }
                    }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
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

    if (showEditDialog) {
        BasicAlertDialog(
            onDismissRequest = { showEditDialog = false }
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("编辑消息") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val newText = editText.trim()
                                if (newText.isNotBlank()) {
                                    onEditUserMessage?.invoke(message.id, newText)
                                    showEditDialog = false
                                }
                            }
                        ) {
                            Text("更新")
                        }
                    }
                }
            }
        }
    }
}