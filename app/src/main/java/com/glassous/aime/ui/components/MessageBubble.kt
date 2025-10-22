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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import com.glassous.aime.data.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale
import android.text.method.LinkMovementMethod
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin

// 供全局提供/获取弹窗时的背景模糊状态
val LocalDialogBlurState = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onShowDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val blurState = LocalDialogBlurState.current

    // 弹窗显示时启用背景模糊
    LaunchedEffect(showDialog) {
        blurState.value = showDialog
    }

    val markwon = remember(context) { 
        Markwon.builder(context)
            .usePlugin(JLatexMathPlugin.create(44f)) // LaTeX数学公式支持，字体大小44f
            .usePlugin(TablePlugin.create(context)) // 表格支持
            .build() 
    }

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
                .testTag("bubble-${message.id}")
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
                val textColor = when {
                    message.isError -> MaterialTheme.colorScheme.onErrorContainer
                    message.isFromUser -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value

                AndroidView(
                    factory = {
                        TextView(it).apply {
                            setTextColor(textColor.toArgb())
                            // 确保链接可点击
                            movementMethod = LinkMovementMethod.getInstance()
                            // 与Material主题大致匹配的字号
                            textSize = textSizeSp
                            // 链接颜色与文本一致，保证深色气泡中可读
                            setLinkTextColor(textColor.toArgb())
                            // 允许但不使用原生文本选择，避免吞掉父级长按
                            setTextIsSelectable(false)
                            // 在 TextView 层接管长按，统一触发气泡弹窗
                            isLongClickable = true
                            setOnLongClickListener {
                                showDialog = true
                                true
                            }
                        }
                    },
                    update = { tv ->
                        // 保证重组后仍有最新的长按监听
                        tv.setOnLongClickListener {
                            showDialog = true
                            true
                        }
                        markwon.setMarkdown(tv, message.content)
                    }
                )
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
}