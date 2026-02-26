package com.glassous.aime.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.glassous.aime.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.glassous.aime.ui.components.MessageBubble

import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongImagePreviewBottomSheet(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    chatFontSize: Float = 16f,
    useCardStyleForHtmlCode: Boolean = false,
    replyBubbleEnabled: Boolean = true,
    isSharing: Boolean = false,
    sharedUrl: String? = null,
    onShareLink: () -> Unit = {},
    showLinkButton: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LongImagePreviewContent(
            messages = messages,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
            chatFontSize = chatFontSize,
            useCardStyleForHtmlCode = useCardStyleForHtmlCode,
            replyBubbleEnabled = replyBubbleEnabled,
            isSharing = isSharing,
            sharedUrl = sharedUrl,
            onShareLink = onShareLink,
            showLinkButton = showLinkButton,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f),
            isSideSheet = false
        )
    }
}

@Composable
fun LongImagePreviewContent(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    chatFontSize: Float,
    useCardStyleForHtmlCode: Boolean,
    replyBubbleEnabled: Boolean,
    isSharing: Boolean,
    sharedUrl: String?,
    onShareLink: () -> Unit,
    showLinkButton: Boolean,
    modifier: Modifier = Modifier,
    isSideSheet: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    
    // Copy feedback state
    var showSuccessFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    // Auto-copy when URL is generated
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            clipboardManager.setText(AnnotatedString(sharedUrl))
            showSuccessFeedback = true
        }
    }
    
    // Timer to reset feedback state
    LaunchedEffect(showSuccessFeedback) {
        if (showSuccessFeedback) {
            delay(3000)
            showSuccessFeedback = false
        }
    }
    
    // We use a reference to the ComposeView that we will capture
    var captureView by remember { mutableStateOf<View?>(null) }
    
    Column(
        modifier = modifier
    ) {
        // Image Preview Area
        Box(
            modifier = Modifier
                .weight(1f, fill = false) // Use weight with fill=false to allow shrinking but expanding up to available space
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                AndroidView(
                    factory = { ctx ->
                        ComposeView(ctx).apply {
                            setContent {
                                MaterialTheme(
                                     colorScheme = MaterialTheme.colorScheme,
                                     typography = MaterialTheme.typography
                                ) {
                                    Surface(color = MaterialTheme.colorScheme.surface) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            messages.forEach { msg ->
                                                MessageBubble(
                                                    message = msg,
                                                    onShowDetails = {},
                                                    replyBubbleEnabled = true, // 强制展开深度思考区域
                                                    chatFontSize = chatFontSize,
                                                    useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                                                    forceExpandReply = true, // 添加参数强制展开
                                                    isShareMode = true // New parameter
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            
                                            // Extract models used in conversation (in order)
                                            val modelsUsed = messages
                                                .filter { !it.isFromUser && it.modelDisplayName != null }
                                                .mapNotNull { it.modelDisplayName }
                                                .distinct()
                                            
                                            // Footer
                                            Column(
                                                modifier = Modifier
                                                    .padding(top = 16.dp)
                                                    .align(Alignment.CenterHorizontally),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                if (modelsUsed.isNotEmpty()) {
                                                    Text(
                                                        text = modelsUsed.joinToString(", "), 
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray,
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = buildAnnotatedString {
                                                        withStyle(style = SpanStyle(fontFamily = FontFamily.Default)) {
                                                            append("Generated by ")
                                                        }
                                                        withStyle(style = SpanStyle(fontFamily = FontFamily.Cursive)) {
                                                            append("AIme")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        captureView = view
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Floating Drag Handle
            if (!isSideSheet) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .size(width = 32.dp, height = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape
                ) {}
            }
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = if (isSideSheet) Arrangement.End else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Button (Left) - Hide if Side Sheet
            if (!isSideSheet) {
                Surface(
                    onClick = {
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Action Buttons (Right)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Copy Link Button
                if (showLinkButton) {
                    Surface(
                        onClick = {
                            if (sharedUrl != null) {
                                clipboardManager.setText(AnnotatedString(sharedUrl))
                                showSuccessFeedback = true
                            } else {
                                onShareLink()
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .height(48.dp)
                            .animateContentSize()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = if (sharedUrl != null) 16.dp else 0.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center, 
                                modifier = Modifier.size(48.dp)
                            ) {
                                if (isSharing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (showSuccessFeedback) Icons.Default.Check else Icons.Default.Link,
                                        contentDescription = "复制链接",
                                        tint = MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                            }
                            
                            if (sharedUrl != null && !isSharing) {
                                Text(
                                    text = if (showSuccessFeedback) "链接已复制" else "重新复制",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Save Button
                Surface(
                    onClick = {
                        if (!isGenerating && captureView != null) {
                            isGenerating = true
                            scope.launch {
                                val bitmap = captureBitmapFromView(captureView!!)
                                if (bitmap != null) {
                                    saveBitmapToGallery(context, bitmap)
                                } else {
                                    Toast.makeText(context, "生成图片失败", Toast.LENGTH_SHORT).show()
                                }
                                isGenerating = false
                            }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                     Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                         if (isGenerating) {
                             CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                         } else {
                             Icon(
                                 imageVector = Icons.Default.Download,
                                 contentDescription = "保存",
                                 tint = MaterialTheme.colorScheme.onPrimaryContainer
                             )
                         }
                    }
                }

                // Share Button
                Surface(
                    onClick = {
                        if (!isGenerating && captureView != null) {
                            isGenerating = true
                            scope.launch {
                                val bitmap = captureBitmapFromView(captureView!!)
                                if (bitmap != null) {
                                    shareBitmap(context, bitmap)
                                } else {
                                    Toast.makeText(context, "生成图片失败", Toast.LENGTH_SHORT).show()
                                }
                                isGenerating = false
                            }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

suspend fun captureBitmapFromView(view: View): Bitmap? = withContext(Dispatchers.Main) {
    try {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        
        val width = view.measuredWidth
        val height = view.measuredHeight
        
        if (width <= 0 || height <= 0) return@withContext null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        
        val oldLeft = view.left
        val oldTop = view.top
        val oldRight = view.right
        val oldBottom = view.bottom
        
        view.layout(0, 0, width, height)
        view.draw(canvas)
        
        view.layout(oldLeft, oldTop, oldRight, oldBottom)
        return@withContext bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "AIme_Share_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIme")
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } else {
                 withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "images")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            // Use a unique name or overwrite? Overwriting is fine for temp share, prevents cache bloat.
            // But if user shares multiple times concurrently (unlikely), unique is safer.
            // Let's use "share_preview.png" and overwrite to keep cache clean.
            val file = File(cachePath, "share_preview.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "分享预览"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
