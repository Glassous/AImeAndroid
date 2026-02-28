package com.glassous.aime.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.glassous.aime.data.ChatMessage
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Description
import com.glassous.aime.ui.utils.AudioPlayerManager

// 供全局提供/获取弹窗时的背景模糊状态
val LocalDialogBlurState = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onShowDetails: (Long) -> Unit,
    onRegenerate: ((Long) -> Unit)? = null,
    onEditUserMessage: ((Long, String) -> Unit)? = null,
    onRetryFailed: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    // 新增：控制 AI 回复是否以气泡展示
    replyBubbleEnabled: Boolean = true,
    // 新增：聊天字体大小
    chatFontSize: Float = 16f,
    // 新增：是否正在流式输出（用于打字机效果）
    isStreaming: Boolean = false,
    // 新增：是否启用打字机效果
    enableTypewriterEffect: Boolean = true,
    // 新增：HTML 预览回调
    onHtmlPreview: ((String) -> Unit)? = null,
    // 新增：HTML 源码预览回调
    onHtmlPreviewSource: ((String) -> Unit)? = null,
    // 新增：是否使用卡片样式显示 HTML 代码块
    useCardStyleForHtmlCode: Boolean = false,
    // 新增：强制展开深度思考区域
    forceExpandReply: Boolean = false,
    // 新增：链接点击回调
    onLinkClick: ((String) -> Unit)? = null,
    // 新增：搜索结果回调
    onShowSearchResults: ((List<SearchResult>) -> Unit)? = null,
    // 新增：图片点击回调
    onImageClick: ((String) -> Unit)? = null,
    // 新增：是否处于分享模式
    isShareMode: Boolean = false
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

    val useBubble = message.isFromUser || replyBubbleEnabled || message.isError

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (useBubble) {
            BoxWithConstraints {
                val maxBubbleWidth = maxWidth
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .widthIn(max = if (message.isFromUser) 300.dp else maxBubbleWidth)
                        .testTag("bubble-${message.id}"),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                    ),
                    color = when {
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        message.isFromUser -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        if (message.imagePaths.isNotEmpty() || (isStreaming && message.metadata?.startsWith("aspect_ratio:") == true)) {
                            MessageImages(message, isStreaming, onImageClick, isShareMode)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val textColor = when {
                            message.isError -> MaterialTheme.colorScheme.onErrorContainer
                            message.isFromUser -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val textSizeSp = chatFontSize

                        // 【关键修改】增加了对 <think> 标签和 Blockquote Reasoning 格式的检测
                        val hasTwoPartReply = !message.isFromUser && (
                                message.content.contains("【前置回复】") ||
                                        message.content.contains("【第一次回复】") ||
                                        message.content.contains("<think>") ||
                                        message.content.contains("<search>") ||
                                        (message.content.trim().startsWith(">") && message.content.contains("Thought for", ignoreCase = true))
                                )

                        if (hasTwoPartReply) {
                            ExpandableReplyBox(
                            content = message.content,
                            textColor = textColor,
                            textSizeSp = textSizeSp,
                            isStreaming = isStreaming,
                            onLongClick = { if (!isShareMode) showDialog = true },
                            onHtmlPreview = onHtmlPreview,
                            onHtmlPreviewSource = onHtmlPreviewSource,
                            useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                            forceExpanded = forceExpandReply,
                            enableTypewriterEffect = enableTypewriterEffect,
                            onLinkClick = onLinkClick,
                            isShareMode = isShareMode
                        )
                        } else {
                            StreamingMarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { if (!isShareMode) showDialog = true },
                                isStreaming = isStreaming,
                                onHtmlPreview = onHtmlPreview,
                                onHtmlPreviewSource = onHtmlPreviewSource,
                                useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                                enableTypewriterEffect = enableTypewriterEffect,
                                onLinkClick = onLinkClick,
                                isShareMode = isShareMode
                            )
                        }

                        // 如果是错误消息且不是用户消息，显示重新发送按钮
                        if (message.isError && !message.isFromUser) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onRetryFailed?.invoke(message.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重新发送")
                            }
                        }
                    }
                }
            }
        } else {
            // 新增：AI 回复直接在页面背景渲染（无气泡）
            BoxWithConstraints {
                val maxBubbleWidth = maxWidth
                Column(
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .testTag("bubble-${message.id}")
                ) {
                    // Display images for non-bubble mode too
                    if (message.imagePaths.isNotEmpty() || (isStreaming && message.metadata?.startsWith("aspect_ratio:") == true)) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            MessageImages(message, isStreaming, onImageClick, isShareMode)
                        }
                    }

                    val textColor = MaterialTheme.colorScheme.onSurface
                    val textSizeSp = chatFontSize
                    // 为了与截图风格更接近，增加左右留白与分段
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                    ) {
                        // 【关键修改】同样增加了对 <think> 标签和 Blockquote Reasoning 格式的检测
                        val hasTwoPartReply = !message.isFromUser && (
                                message.content.contains("【前置回复】") ||
                                        message.content.contains("【第一次回复】") ||
                                        message.content.contains("<think>") ||
                                        message.content.contains("<search>") ||
                                        (message.content.trim().startsWith(">") && message.content.contains("Thought for", ignoreCase = true))
                                )

                        if (hasTwoPartReply) {
                            ExpandableReplyBox(
                                content = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                isStreaming = isStreaming,
                                onLongClick = { if (!isShareMode) showDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                onHtmlPreview = onHtmlPreview,
                                onHtmlPreviewSource = onHtmlPreviewSource,
                                useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                                forceExpanded = forceExpandReply,
                                enableTypewriterEffect = enableTypewriterEffect,
                                onLinkClick = onLinkClick,
                                onShowSearchResults = onShowSearchResults,
                                isShareMode = isShareMode
                            )
                        } else {
                            // 根据是否为AI回复且启用打字机效果来选择渲染组件
                            StreamingMarkdownRenderer(
                                markdown = message.content,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                onLongClick = { if (!isShareMode) showDialog = true },
                                isStreaming = isStreaming,
                                onHtmlPreview = onHtmlPreview,
                                onHtmlPreviewSource = onHtmlPreviewSource,
                                useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                                enableTypewriterEffect = enableTypewriterEffect,
                                onLinkClick = onLinkClick,
                                isShareMode = isShareMode
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageImages(
    message: ChatMessage,
    isStreaming: Boolean,
    onImageClick: ((String) -> Unit)? = null,
    isShareMode: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePaths = message.imagePaths
    val isImageGen = message.metadata?.startsWith("aspect_ratio:") == true
    val aspectRatioStr = message.metadata?.substringAfter("aspect_ratio:", "1:1") ?: "1:1"
    val aspectRatio = when (aspectRatioStr) {
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        "4:3" -> 4f / 3f
        "3:4" -> 3f / 4f
        else -> 1f
    }

    if (imagePaths.isEmpty() && isStreaming && isImageGen) {
        // Show loading placeholder with aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }

    if (imagePaths.isEmpty()) return
    
    val audioPaths = imagePaths.filter { 
        it.endsWith(".m4a", ignoreCase = true) || 
        it.endsWith(".mp3", ignoreCase = true) || 
        it.endsWith(".wav", ignoreCase = true) 
    }
    val pdfPaths = imagePaths.filter { it.endsWith(".pdf", ignoreCase = true) }
    val visualPaths = imagePaths - audioPaths.toSet() - pdfPaths.toSet()

    // Render Audio Files
    if (audioPaths.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            audioPaths.forEach { path ->
                MessageAudioCard(
                    path = path,
                    onPlay = { onImageClick?.invoke(path) }
                )
            }
        }
        if (pdfPaths.isNotEmpty() || visualPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Render PDF Files
    if (pdfPaths.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pdfPaths.forEach { path ->
                MessageFileCard(
                    path = path,
                    onClick = { 
                        // PDF Click: Try to open via intent, don't show image preview
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(path)
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Toast or fallback
                        }
                    }
                )
            }
        }
        if (visualPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (visualPaths.isEmpty()) return
    
    val imageCount = visualPaths.size
    
    // Only use MessageImageCard for AI messages (replies)
    val useImageCard = !message.isFromUser && !message.isError && !isStreaming
    
    if (imageCount == 1) {
        if (useImageCard) {
            MessageImageCard(
                imagePath = visualPaths.first(),
                isShareMode = isShareMode,
                message = message,
                onImageClick = onImageClick
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                AsyncImage(
                    model = if (visualPaths.first().endsWith(".mp4", ignoreCase = true)) {
                        coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(visualPaths.first())
                            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                            .build()
                    } else {
                        visualPaths.first()
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .wrapContentWidth()
                        .heightIn(max = 400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick?.invoke(visualPaths.first()) },
                    contentScale = ContentScale.Inside
                )
                
                if (visualPaths.first().endsWith(".mp4", ignoreCase = true)) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Play Video",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                            .padding(8.dp)
                    )
                }
            }
        }
    } else {
        // For multiple images, if it's from AI, maybe use a list of cards or keep the grid
        if (useImageCard) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visualPaths.forEach { path ->
                    MessageImageCard(
                        imagePath = path,
                        isShareMode = isShareMode,
                        message = message,
                        onImageClick = onImageClick
                    )
                }
            }
        } else {
             Row(
                 modifier = Modifier.fillMaxWidth(), 
                 horizontalArrangement = Arrangement.Start
             ) {
                  val leftImages = visualPaths.filterIndexed { index, _ -> index % 2 == 0 }
                  val rightImages = visualPaths.filterIndexed { index, _ -> index % 2 != 0 }
                  
                  Column(
                      modifier = Modifier.weight(1f, fill = false).widthIn(max = 150.dp), 
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                      leftImages.forEach { path ->
                          AsyncImage(
                              model = if (path.endsWith(".mp4", ignoreCase = true)) {
                                  coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                      .data(path)
                                      .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                      .build()
                              } else {
                                  path
                              },
                              contentDescription = null,
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .clip(RoundedCornerShape(8.dp))
                                  .clickable { onImageClick?.invoke(path) },
                              contentScale = ContentScale.FillWidth
                          )
                      }
                  }
                  Spacer(modifier = Modifier.width(4.dp))
                  Column(
                      modifier = Modifier.weight(1f, fill = false).widthIn(max = 150.dp), 
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                      rightImages.forEach { path ->
                          AsyncImage(
                              model = if (path.endsWith(".mp4", ignoreCase = true)) {
                                  coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                      .data(path)
                                      .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                      .build()
                              } else {
                                  path
                              },
                              contentDescription = null,
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .clip(RoundedCornerShape(8.dp))
                                  .clickable { onImageClick?.invoke(path) },
                              contentScale = ContentScale.FillWidth
                          )
                      }
                  }
             }
        }
    }
}

@Composable
fun MessageAudioCard(
    path: String,
    onPlay: (String) -> Unit
) {
    val currentPath by AudioPlayerManager.currentPath.collectAsState()
    val isPlaying by AudioPlayerManager.isPlaying.collectAsState()
    val progress by AudioPlayerManager.progress.collectAsState()
    val duration by AudioPlayerManager.duration.collectAsState()
    
    val isCurrent = currentPath == path
    val showPause = isCurrent && isPlaying
    val currentProgress = if (isCurrent) progress else 0f

    Card(
        modifier = Modifier
            .width(200.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { AudioPlayerManager.play(path) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (showPause) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp) // Touch target
                    .pointerInput(isCurrent, duration) {
                        detectTapGestures { offset ->
                            if (isCurrent && duration > 0) {
                                val relative = offset.x / size.width
                                val position = (relative * duration).toLong()
                                AudioPlayerManager.seekTo(position)
                            } else if (!isCurrent) {
                                AudioPlayerManager.play(path)
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun MessageFileCard(
    path: String,
    onClick: (String) -> Unit
) {
    val fileName = remember(path) {
        val rawName = java.io.File(path).name
        when {
            rawName.startsWith("pdf_") -> {
                // pdf_[originalName]_[timestamp]_[uuid].pdf
                rawName.removePrefix("pdf_")
                    .substringBeforeLast("_") // Remove UUID
                    .substringBeforeLast("_") // Remove timestamp
            }
            rawName.startsWith("txt_") -> {
                // txt_[originalName]_[timestamp]_[uuid].txt
                rawName.removePrefix("txt_")
                    .substringBeforeLast("_") // Remove UUID
                    .substringBeforeLast("_") // Remove timestamp
            }
            else -> rawName
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(path) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPdf = path.endsWith(".pdf", ignoreCase = true)
            Icon(
                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                contentDescription = if (isPdf) "PDF Document" else "Text Document",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
