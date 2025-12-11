package com.glassous.aime.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassous.aime.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LongImagePreviewDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    chatFontSize: Float = 16f,
    useCardStyleForHtmlCode: Boolean = false,
    replyBubbleEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    
    // We use a reference to the ComposeView that we will capture
    var captureView by remember { mutableStateOf<View?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    Text("生成长图预览", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            if (!isGenerating && captureView != null) {
                                isGenerating = true
                                scope.launch {
                                    saveBitmapToGallery(context, captureView!!)
                                    isGenerating = false
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isGenerating
                    ) {
                        Text(if (isGenerating) "保存中..." else "保存到相册")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
                                                // Header
                                                Text(
                                                    "AIme", 
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontFamily = FontFamily.Cursive
                                                    ),
                                                    modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
                                                )
                                                
                                                messages.forEach { msg ->
                                                    MessageBubble(
                                                        message = msg,
                                                        onShowDetails = {},
                                                        replyBubbleEnabled = replyBubbleEnabled,
                                                        chatFontSize = chatFontSize,
                                                        useCardStyleForHtmlCode = useCardStyleForHtmlCode
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                                
                                                // Footer
                                                Text(
                                                    "Generated by AIme", 
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray,
                                                    modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally)
                                                )
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
            }
        }
    }
}

suspend fun saveBitmapToGallery(context: Context, view: View) {
    withContext(Dispatchers.IO) {
        try {
            // Measure the view with unspecified height to get full height
            // Since it's in a verticalScroll, it might already be measured fully?
            // But to be safe and ensure we capture full content even if not fully scrolled:
            
            val widthSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
            
            val width = view.measuredWidth
            val height = view.measuredHeight
            
            if (width <= 0 || height <= 0) {
                 withContext(Dispatchers.Main) {
                    Toast.makeText(context, "生成失败：内容为空", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // Create Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Draw white background
            canvas.drawColor(AndroidColor.WHITE)
            
            // Layout to full size
            val oldLeft = view.left
            val oldTop = view.top
            val oldRight = view.right
            val oldBottom = view.bottom
            
            view.layout(0, 0, width, height)
            view.draw(canvas)
            
            // Restore layout (though we are dismissing anyway)
            view.layout(oldLeft, oldTop, oldRight, oldBottom)

            // Save to MediaStore
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
