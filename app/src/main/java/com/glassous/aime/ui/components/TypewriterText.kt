package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import java.text.BreakIterator
import java.text.StringCharacterIterator
import kotlin.math.max
import kotlin.math.min

/**
 * 打字机效果的文本组件
 */
@Composable
fun TypewriterText(
    text: String,
    textColor: Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    typingDelayMs: Long = 30L, // 每个字符间的延迟时间
    isStreaming: Boolean = false, // 是否正在流式输出
    onAnimationComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var displayedText by remember { mutableStateOf("") }
    var isAnimating by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }
    var adaptiveDelay by remember { mutableLongStateOf(typingDelayMs) }
    
    // 当文本变化时，决定是否需要动画
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            displayedText = ""
            return@LaunchedEffect
        }
        
        // 如果新文本比当前显示的文本长，且正在流式输出，则使用动画
        if (isStreaming && text.length > displayedText.length) {
            isAnimating = true
            
            val currentTime = System.currentTimeMillis()
            val timeSinceLastUpdate = if (lastUpdateTime > 0) currentTime - lastUpdateTime else 300L
            lastUpdateTime = currentTime
            
            // 计算新增的字符数量
            val newCharCount = text.length - displayedText.length
            
            // 动态调整打字机速度：根据AI回复速率调整
            // AI更新间隔约300ms，我们希望在下次更新前完成当前字符的显示
            adaptiveDelay = if (newCharCount > 0) {
                // 基于时间间隔和字符数量计算合适的延迟
                val targetDelay = (timeSinceLastUpdate * 0.8 / newCharCount).toLong()
                // 限制延迟范围：最小5ms，最大100ms
                max(5L, min(100L, targetDelay))
            } else {
                typingDelayMs
            }
            
            // 使用BreakIterator正确处理字符边界（包括emoji）
            val breakIterator = BreakIterator.getCharacterInstance()
            breakIterator.text = StringCharacterIterator(text)
            
            var currentIndex = displayedText.length
            var nextIndex = breakIterator.first()
            
            // 找到当前显示文本的结束位置
            while (nextIndex != BreakIterator.DONE && nextIndex <= currentIndex) {
                nextIndex = breakIterator.next()
            }
            
            // 从当前位置开始逐字符显示
            while (nextIndex != BreakIterator.DONE && nextIndex <= text.length) {
                displayedText = text.substring(0, nextIndex)
                delay(adaptiveDelay)
                nextIndex = breakIterator.next()
            }
            
            // 确保显示完整文本
            if (displayedText != text) {
                displayedText = text
            }
            
            isAnimating = false
            onAnimationComplete?.invoke()
        } else {
            // 非流式输出或文本变短时，直接显示
            displayedText = text
        }
    }
    
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor.toArgb())
                textSize = textSizeSp
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        },
        update = { textView ->
            textView.text = displayedText
            textView.setTextColor(textColor.toArgb())
            textView.textSize = textSizeSp
        },
        modifier = modifier
    )
}

/**
 * 打字机效果的Markdown渲染器
 */
@Composable
fun TypewriterMarkdownRenderer(
    markdown: String,
    textColor: Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    typingDelayMs: Long = 30L,
    isStreaming: Boolean = false,
    onAnimationComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    var displayedMarkdown by remember { mutableStateOf("") }
    var isAnimating by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }
    var adaptiveDelay by remember { mutableLongStateOf(typingDelayMs) }
    
    // 创建Markwon实例
    val markwon = remember(context) { 
        io.noties.markwon.Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.latex.JLatexMathPlugin.create(44f))
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .build() 
    }
    
    // 动画逻辑
    LaunchedEffect(markdown) {
        if (markdown.isEmpty()) {
            displayedMarkdown = ""
            return@LaunchedEffect
        }
        
        if (isStreaming && markdown.length > displayedMarkdown.length) {
            isAnimating = true
            
            val currentTime = System.currentTimeMillis()
            val timeSinceLastUpdate = if (lastUpdateTime > 0) currentTime - lastUpdateTime else 300L
            lastUpdateTime = currentTime
            
            // 计算新增的字符数量
            val newCharCount = markdown.length - displayedMarkdown.length
            
            // 动态调整打字机速度：根据AI回复速率调整
            adaptiveDelay = if (newCharCount > 0) {
                // 基于时间间隔和字符数量计算合适的延迟
                val targetDelay = (timeSinceLastUpdate * 0.8 / newCharCount).toLong()
                // 限制延迟范围：最小5ms，最大100ms
                max(5L, min(100L, targetDelay))
            } else {
                typingDelayMs
            }
            
            val breakIterator = BreakIterator.getCharacterInstance()
            breakIterator.text = StringCharacterIterator(markdown)
            
            var currentIndex = displayedMarkdown.length
            var nextIndex = breakIterator.first()
            
            // 找到当前显示文本的结束位置
            while (nextIndex != BreakIterator.DONE && nextIndex <= currentIndex) {
                nextIndex = breakIterator.next()
            }
            
            // 逐字符显示
            while (nextIndex != BreakIterator.DONE && nextIndex <= markdown.length) {
                displayedMarkdown = markdown.substring(0, nextIndex)
                delay(adaptiveDelay)
                nextIndex = breakIterator.next()
            }
            
            if (displayedMarkdown != markdown) {
                displayedMarkdown = markdown
            }
            
            isAnimating = false
            onAnimationComplete?.invoke()
        } else {
            displayedMarkdown = markdown
        }
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block.type) {
                BlockType.TEXT -> {
                    // 计算当前块在整个markdown中的位置
                    val blockStartIndex = markdown.indexOf(block.content)
                    val blockEndIndex = blockStartIndex + block.content.length
                    
                    // 计算当前应该显示的部分
                    val displayedContent = if (displayedMarkdown.length > blockStartIndex) {
                        val endIndex = minOf(displayedMarkdown.length, blockEndIndex)
                        block.content.substring(0, endIndex - blockStartIndex)
                    } else {
                        ""
                    }
                    
                    if (displayedContent.isNotEmpty()) {
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    setTextColor(textColor.toArgb())
                                    textSize = textSizeSp
                                    setOnLongClickListener {
                                        onLongClick()
                                        true
                                    }
                                }
                            },
                            update = { textView ->
                                markwon.setMarkdown(textView, displayedContent)
                                textView.setTextColor(textColor.toArgb())
                                textView.textSize = textSizeSp
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                BlockType.CODE_BLOCK -> {
                    // 计算当前代码块在整个markdown中的位置
                    val blockStartIndex = markdown.indexOf(block.content)
                    val blockEndIndex = blockStartIndex + block.content.length
                    
                    // 计算当前应该显示的部分
                    val displayedContent = if (displayedMarkdown.length > blockStartIndex) {
                        val endIndex = minOf(displayedMarkdown.length, blockEndIndex)
                        block.content.substring(0, endIndex - blockStartIndex)
                    } else {
                        ""
                    }
                    
                    if (displayedContent.isNotEmpty()) {
                        CodeBlockWithCopy(
                            code = displayedContent,
                            language = block.language,
                            textSizeSp = textSizeSp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}