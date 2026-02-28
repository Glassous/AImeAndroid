package com.glassous.aime.ui.components

import android.content.Context
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import java.util.regex.Pattern

data class MarkdownBlock(
    val type: BlockType,
    val content: String,
    val language: String? = null,
    val musicList: List<MusicInfo>? = null,
    val fileName: String? = null,
    val index: Int? = null,
    val url: String? = null
)

enum class BlockType {
    TEXT, CODE_BLOCK, MERMAID, TABLE, MUSIC, FILE_CARD, FILE_URL_CARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownFileCard(
    fileName: String,
    content: String,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Text File",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showDialog) {
        androidx.compose.material3.BasicAlertDialog(
            onDismissRequest = { showDialog = false }
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownFileUrlCard(
    index: Int,
    url: String,
    textColor: androidx.compose.ui.graphics.Color,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onUrlPreview: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isImage = url.lowercase().let { 
        it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".webp") || it.endsWith(".gif") 
    }
    val isVideo = url.lowercase().let {
        it.endsWith(".mp4") || it.endsWith(".mpeg") || it.endsWith(".mov") || it.endsWith(".webm")
    }
    val isYouTube = url.contains("youtube.com/watch", ignoreCase = true) || url.contains("youtu.be/", ignoreCase = true)
    val isPdf = url.lowercase().endsWith(".pdf")

    Card(
        onClick = {
            when {
                isImage -> onImageClick?.invoke(url)
                isVideo -> onVideoClick?.invoke(url)
                isYouTube -> onUrlPreview?.invoke(url)
                isPdf -> { /* Do nothing for PDF as requested */ }
                else -> onUrlPreview?.invoke(url)
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Icon(
                imageVector = when {
                    isImage -> Icons.Default.Image
                    isVideo -> Icons.Default.PlayCircleOutline
                    isYouTube -> Icons.Default.PlayCircleOutline
                    isPdf -> Icons.Default.PictureAsPdf
                    else -> Icons.Default.Link
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    textColor: androidx.compose.ui.graphics.Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableTables: Boolean = true,
    enableCodeBlocks: Boolean = true,
    enableLatex: Boolean = true,
    onHtmlPreview: ((String) -> Unit)? = null,
    onHtmlPreviewSource: ((String) -> Unit)? = null,
    useCardStyleForHtmlCode: Boolean = false,
    isStreaming: Boolean = false,
    onCitationClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onUrlPreview: ((String) -> Unit)? = null,
    isShareMode: Boolean = false // New parameter
) {
    // 【修改开始】对 <think> 标签进行转义，防止被作为 HTML 标签隐藏
    val displayMarkdown = remember(markdown) {
        markdown
            .replace("<think>", "&lt;think&gt;")
            .replace("</think>", "&lt;/think&gt;")
            .replace("<search>", "&lt;search&gt;")
            .replace("</search>", "&lt;search&gt;")
    }
    // 【修改结束】

    val context = LocalContext.current
    // 使用处理过的 displayMarkdown 进行解析
    val blocks = remember(displayMarkdown, enableCodeBlocks) { 
        if (enableCodeBlocks) {
            parseMarkdownBlocks(displayMarkdown) 
        } else {
            listOf(MarkdownBlock(BlockType.TEXT, displayMarkdown))
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // 创建不包含代码块处理的Markwon实例，用于渲染普通文本
    val markwon = remember(context, enableTables, enableLatex, textSizeSp, colorScheme, density) {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val textPx = textSizeSp * scaledDensity

        val builder = Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(HtmlPlugin.create())
        
        if (enableLatex) {
            builder.usePlugin(
                JLatexMathPlugin.create(
                    textPx,
                    textPx,
                    object : JLatexMathPlugin.BuilderConfigure {
                        override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                            builder.inlinesEnabled(true)
                        }
                    }
                )
            )
        }

        if (enableTables) {
            val tableTheme = TableTheme.Builder()
                .tableBorderColor(colorScheme.outlineVariant.toArgb())
                .tableBorderWidth(with(density) { 1.dp.toPx() }.toInt())
                .tableCellPadding(with(density) { 8.dp.toPx() }.toInt())
                .tableHeaderRowBackgroundColor(colorScheme.surfaceVariant.copy(alpha = 0.5f).toArgb())
                .tableEvenRowBackgroundColor(colorScheme.surfaceVariant.copy(alpha = 0.1f).toArgb())
                .tableOddRowBackgroundColor(android.graphics.Color.TRANSPARENT)
                .build()
            builder.usePlugin(TablePlugin.create(tableTheme))
        }
        builder.build()
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block.type) {
                BlockType.TEXT -> {
                    if (block.content.trim().isNotEmpty()) {
                        AndroidView(
                            factory = {
                                TextView(it).apply {
                                    setTextColor(textColor.toArgb())
                                    textSize = textSizeSp
                                    setLinkTextColor(textColor.toArgb())
                                    setTextIsSelectable(false)
                                    isLongClickable = true
                                    // 确保链接可点击
                                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                    setOnLongClickListener {
                                        onLongClick()
                                        true
                                    }
                                }
                            },
                            update = { tv ->
                                tv.setTextColor(textColor.toArgb())
                                tv.textSize = textSizeSp
                                tv.setLinkTextColor(textColor.toArgb())
                                tv.setOnLongClickListener {
                                    onLongClick()
                                    true
                                }
                                val textToRender = preprocessText(block.content, enableLatex)
                                var spanned: CharSequence = markwon.toMarkdown(textToRender)
                                
                                    // 处理引用标签 ^n
                                if (spanned is Spanned) {
                                    val builder = SpannableStringBuilder(spanned)
                                    val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
                                    var hasModifications = false
                                    
                                    for (span in urlSpans) {
                                        val url = span.url
                                        val start = builder.getSpanStart(span)
                                        val end = builder.getSpanEnd(span)
                                        val flags = builder.getSpanFlags(span)
                                        
                                        if (url.startsWith("citation:")) {
                                            val id = url.removePrefix("citation:")
                                            builder.removeSpan(span)
                                            
                                            // 添加点击事件
                                            builder.setSpan(object : ClickableSpan() {
                                                override fun onClick(widget: View) {
                                                    onCitationClick?.invoke(id)
                                                }
                                                override fun updateDrawState(ds: TextPaint) {
                                                    ds.isUnderlineText = false
                                                    ds.color = textColor.toArgb()
                                                }
                                            }, start, end, flags)
                                            
                                            // 添加外观样式（小卡片）
                                            builder.setSpan(CitationAppearanceSpan(id, textColor.toArgb(), textSizeSp), start, end, flags)
                                            hasModifications = true
                                        } else if (onLinkClick != null) {
                                            // 处理普通链接点击
                                            builder.removeSpan(span)
                                            
                                            builder.setSpan(object : ClickableSpan() {
                                                override fun onClick(widget: View) {
                                                    onLinkClick.invoke(url)
                                                }
                                                override fun updateDrawState(ds: TextPaint) {
                                                    ds.isUnderlineText = true
                                                    ds.color = textColor.toArgb()
                                                }
                                            }, start, end, flags)
                                            hasModifications = true
                                        }
                                    }
                                    
                                    if (hasModifications) {
                                        spanned = builder
                                    }
                                }
                                
                                // 处理流式输出的淡入动画
                                if (isStreaming) {
                                    val state = (tv.tag as? TextFadeState) ?: TextFadeState().also { tv.tag = it }
                                    val currentLength = spanned.length
                                    
                                    // 如果文本变短（可能是重新生成或编辑），更新状态而不是重置
                                    if (currentLength < state.lastLength) {
                                        state.lastLength = currentLength
                                        // 移除所有起始位置超出当前长度的动画块
                                        state.fadingChunks.removeAll { (start, _) -> start >= currentLength }
                                    }
                                    
                                    // 检测新增文本块
                                    if (currentLength > state.lastLength) {
                                        state.fadingChunks.add(state.lastLength to System.currentTimeMillis())
                                        state.lastLength = currentLength
                                    }
                                    
                                    if (state.fadingChunks.isNotEmpty()) {
                                        val builder = SpannableStringBuilder(spanned)
                                        val now = System.currentTimeMillis()
                                        val duration = 500L // 动画持续时间 500ms
                                        
                                        // 清理已完成的动画
                                        state.fadingChunks.removeAll { (_, time) -> (now - time) >= duration }
                                        
                                        // 应用淡入 Span
                                        val sortedChunks = state.fadingChunks.sortedBy { it.first }
                                        for (i in sortedChunks.indices) {
                                            val (start, time) = sortedChunks[i]
                                            val end = if (i + 1 < sortedChunks.size) sortedChunks[i+1].first else builder.length
                                            
                                            if (start < end) {
                                                builder.setSpan(
                                                    TimeBasedFadeSpan(time, duration),
                                                    start,
                                                    end,
                                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                                )
                                            }
                                        }
                                        
                                        tv.text = builder
                                        
                                        // 触发持续重绘
                                        if (state.fadingChunks.isNotEmpty()) {
                                            state.animator?.let { tv.removeCallbacks(it) }
                                            state.animator = object : Runnable {
                                                override fun run() {
                                                    val currentTime = System.currentTimeMillis()
                                                    if (state.fadingChunks.isNotEmpty()) {
                                                        tv.invalidate()
                                                        // 只有当还有未完成的动画时才继续 post
                                                        if (state.fadingChunks.any { (_, time) -> (currentTime - time) < duration }) {
                                                            tv.postOnAnimation(this)
                                                        } else {
                                                            // 所有动画已完成，清理状态
                                                            state.fadingChunks.clear()
                                                        }
                                                    }
                                                }
                                            }
                                            tv.postOnAnimation(state.animator)
                                        }
                                    } else {
                                        tv.text = spanned
                                        state.lastLength = currentLength
                                    }
                                } else {
                                    // 非流式状态，直接显示
                                    if (spanned is Spanned) {
                                        markwon.setParsedMarkdown(tv, spanned)
                                    } else {
                                        tv.text = spanned
                                    }
                                    // 重置状态以防下次变为流式
                                    val state = (tv.tag as? TextFadeState) ?: TextFadeState().also { tv.tag = it }
                                    state.lastLength = spanned.length
                                    state.fadingChunks.clear()
                                }
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }
                BlockType.MERMAID -> {
                    MarkdownMermaid(
                        mermaidCode = block.content,
                        modifier = Modifier.padding(vertical = 8.dp),
                        isShareMode = isShareMode
                    )
                }
                BlockType.CODE_BLOCK -> {
                    MarkdownCodeBlock(
                        code = block.content,
                        language = block.language,
                        textSizeSp = textSizeSp,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(vertical = 4.dp),
                        onPreview = if (onHtmlPreview != null) {
                            { onHtmlPreview(block.content) }
                        } else {
                            null
                        },
                        onPreviewSource = if (onHtmlPreviewSource != null) {
                            { onHtmlPreviewSource(block.content) }
                        } else {
                            null
                        },
                        useCardStyle = useCardStyleForHtmlCode,
                        isShareMode = isShareMode
                    )
                }
                BlockType.TABLE -> {
                    MarkdownTable(
                        markdown = block.content,
                        markwon = markwon,
                        textColor = textColor,
                        textSizeSp = textSizeSp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        isShareMode = isShareMode
                    )
                }
                BlockType.MUSIC -> {
                    block.musicList?.let {
                        MusicList(
                            musicList = it,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                BlockType.FILE_CARD -> {
                    MarkdownFileCard(
                        fileName = block.fileName ?: "file.txt",
                        content = block.content,
                        textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                BlockType.FILE_URL_CARD -> {
                    MarkdownFileUrlCard(
                        index = block.index ?: 0,
                        url = block.url ?: "",
                        textColor = textColor,
                        onImageClick = { url ->
                            // Map URL to path-like format for preview if needed
                            // Or handle directly. In ChatScreen, onImageClick handles paths.
                            onImageClick?.invoke(url)
                        },
                        onVideoClick = { url ->
                            onVideoClick?.invoke(url)
                        },
                        onUrlPreview = onUrlPreview,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    // Updated pattern to include <music> and <file> tags
    // Group 1: Code block (full match)
    // Group 2: Code block language
    // Group 3: Code block content
    // Group 4: Music block (full match)
    // Group 5: Music block content
    // Group 6: File block (full match)
    // Group 7: File name
    // Group 8: File content
    // Group 9: File URL block (full match)
    // Group 10: Index
    // Group 11: URL
    val combinedPattern = Pattern.compile("(```(\\w+)?\\n([\\s\\S]*?)```)|(<music>([\\s\\S]*?)</music>)|(<file name=\"(.*?)\">([\\s\\S]*?)</file>)|(<file_url index=\"(.*?)\" url=\"(.*?)\" />)", Pattern.MULTILINE)
    val matcher = combinedPattern.matcher(markdown)

    var lastEnd = 0

    while (matcher.find()) {
        val start = matcher.start()
        
        // 1. Process text between last match and current match
        if (start > lastEnd) {
            val textContent = markdown.substring(lastEnd, start)
            val intermediateBlocks = parseTables(textContent)
            
            // Check if we should merge with previous Music block
            val isMusicMatch = matcher.group(4) != null
            val isPreviousMusic = blocks.lastOrNull()?.type == BlockType.MUSIC
            val isIntermediateWhitespace = intermediateBlocks.all { it.type == BlockType.TEXT && it.content.isBlank() }
            
            if (isMusicMatch && isPreviousMusic && isIntermediateWhitespace) {
                // Skip adding intermediate blocks (whitespace) to allow merging
            } else {
                blocks.addAll(intermediateBlocks)
            }
        }

        // 2. Process current match
        when {
            matcher.group(1) != null -> {
                // It's a Code Block
                val language = matcher.group(2)
                val code = matcher.group(3) ?: ""
                
                if (language.equals("mermaid", ignoreCase = true)) {
                    blocks.add(MarkdownBlock(BlockType.MERMAID, code, language))
                } else {
                    blocks.add(MarkdownBlock(BlockType.CODE_BLOCK, code, language))
                }
            }
            matcher.group(4) != null -> {
                // It's a Music Block
                val musicContent = matcher.group(5) ?: ""
                val musicInfo = parseMusicContent(musicContent)
                
                if (musicInfo != null) {
                    val lastBlock = blocks.lastOrNull()
                    if (lastBlock != null && lastBlock.type == BlockType.MUSIC) {
                        // Merge with previous music block
                        val newList = (lastBlock.musicList ?: emptyList()) + musicInfo
                        blocks[blocks.lastIndex] = lastBlock.copy(musicList = newList)
                    } else {
                        // New music block
                        blocks.add(MarkdownBlock(BlockType.MUSIC, musicContent, null, listOf(musicInfo)))
                    }
                }
            }
            matcher.group(6) != null -> {
                // It's a File Block
                val fileName = matcher.group(7) ?: "file.txt"
                val fileContent = matcher.group(8) ?: ""
                blocks.add(MarkdownBlock(BlockType.FILE_CARD, fileContent, fileName = fileName))
            }
            matcher.group(9) != null -> {
                // It's a File URL Block
                val index = matcher.group(10)?.toIntOrNull() ?: 0
                val url = matcher.group(11) ?: ""
                blocks.add(MarkdownBlock(BlockType.FILE_URL_CARD, "", index = index, url = url))
            }
        }
        
        lastEnd = matcher.end()
    }

    // Add remaining text
    if (lastEnd < markdown.length) {
        val textContent = markdown.substring(lastEnd)
        // We don't merge music blocks across the end of the string, so just add text
        blocks.addAll(parseTables(textContent))
    }

    // Fallback if no matches found
    if (blocks.isEmpty() && markdown.isNotEmpty()) {
        blocks.addAll(parseTables(markdown))
    }

    return blocks
}

private fun parseTables(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.split("\n")
    val currentText = StringBuilder()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Check if this line is a separator line (e.g., "|---|---|")
        if (isTableSeparator(line)) {
            // Check if previous line exists and is a header (contains '|')
            val headerCandidateIndex = i - 1
            if (headerCandidateIndex >= 0) {
                val headerCandidate = lines[headerCandidateIndex]
                if (headerCandidate.contains("|")) {
                    // Found a table start!
                    
                    // 1. Flush accumulated text (excluding the header line which is part of the table)
                    // The header line was appended to currentText in the previous iteration
                    // We need to remove it from currentText
                    val textContent = currentText.toString()
                    val headerWithNewline = headerCandidate + "\n"
                    
                    // Remove the last occurrence of header line
                    val textBeforeHeader = if (textContent.endsWith(headerWithNewline)) {
                        textContent.substring(0, textContent.length - headerWithNewline.length)
                    } else if (textContent.endsWith(headerCandidate)) { // case where no newline at end
                         textContent.substring(0, textContent.length - headerCandidate.length)
                    } else {
                        // Fallback, shouldn't happen with correct logic
                        textContent
                    }

                    if (textBeforeHeader.isNotBlank()) {
                        blocks.add(MarkdownBlock(BlockType.TEXT, textBeforeHeader))
                    }
                    currentText.clear() 

                    // 2. Start collecting table content
                    val tableBuilder = StringBuilder()
                    tableBuilder.append(headerCandidate).append("\n")
                    tableBuilder.append(line).append("\n")

                    // 3. Consume subsequent lines that look like table rows
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j]
                        // A table row must contain a pipe '|'
                        if (nextLine.trim().isNotEmpty() && nextLine.contains("|")) {
                            tableBuilder.append(nextLine).append("\n")
                            j++
                        } else {
                            break
                        }
                    }
                    
                    blocks.add(MarkdownBlock(BlockType.TABLE, tableBuilder.toString()))
                    i = j // Advance main index to the line after the table
                    continue
                }
            }
        }

        currentText.append(line).append("\n")
        i++
    }

    if (currentText.isNotEmpty()) {
        blocks.add(MarkdownBlock(BlockType.TEXT, currentText.toString()))
    }

    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    // Must contain |, -, and optionally : and spaces. No other chars.
    // Also must contain at least 3 dashes usually, but let's be lenient: contain '-'
    // Standard markdown table separator line: |---|---| or --- | ---
    // It must contain at least one hyphen and one pipe (usually)
    // Markwon expects GFM tables.
    
    // Check for valid characters
    val validChars = setOf('|', '-', ':', ' ')
    if (trimmed.any { it !in validChars }) return false
    
    // Must have at least one pipe and one dash
    if (!trimmed.contains("|")) return false
    if (!trimmed.contains("-")) return false
    
    return true
}

fun preprocessText(input: String, enableLatex: Boolean): String {
    val sb = StringBuilder()
    var i = 0
    var inInlineCode = false
    var inMath = false
    var inSup = false
    var inSub = false
    
    while (i < input.length) {
        val c = input[i]
        val prev = if (i > 0) input[i - 1] else '\u0000'
        
        // Handle inline code backticks
        if (c == '`' && prev != '\\') {
            inInlineCode = !inInlineCode
            sb.append(c)
            i++
            continue
        }

        if (!inInlineCode) {
            // Handle citation (ref:n) or (ref:n, m, ...)
            if (c == '(' && prev != '\\') {
                if (i + 5 < input.length && input.substring(i + 1, i + 5) == "ref:") {
                    var j = i + 5
                    val startNum = j
                    // Allow digits, commas, spaces, and "ref:"
                    while (j < input.length && input[j] != ')') {
                        j++
                    }
                    if (j > startNum && j < input.length && input[j] == ')') {
                        val content = input.substring(startNum, j)
                        // Handle both "1, 2, 3" and "1, ref:2, ref:3" formats
                        // Remove "ref:" prefixes and split by comma/space
                        val cleanedContent = content.replace("ref:", "").replace(",", " ")
                        val numbers = cleanedContent.split(" ").map { it.trim() }.filter { it.isNotEmpty() && it.all { char -> char.isDigit() } }
                        
                        // Only process if we found valid numbers and the content was mostly valid
                        // (To avoid matching "(ref: see figure 1)")
                        // Check if reconstruction matches original (ignoring spaces/commas/ref:)
                        val reconstructedLength = numbers.sumOf { it.length }
                        val originalDigitCount = content.count { it.isDigit() }
                        
                        if (numbers.isNotEmpty() && reconstructedLength == originalDigitCount) {
                            numbers.forEach { num ->
                                sb.append("[$num](citation:$num)")
                            }
                            i = j + 1
                            continue
                        }
                    }
                }
            }

            if (enableLatex) {
                // Handle LaTeX delimiters \[ \] \( \)
                if (c == '\\' && i + 1 < input.length) {
                    val next = input[i + 1]
                    if (next == '[' || next == ']' || next == '(' || next == ')') {
                        if (!inMath) {
                            sb.append("$$")
                            inMath = true
                        } else {
                            sb.append("$$")
                            inMath = false
                        }
                        i += 2
                        continue
                    }
                }

                // Handle single dollar $
                if (c == '$' && prev != '\\') {
                    val next = if (i + 1 < input.length) input[i + 1] else '\u0000'
                    val prevChar = prev
                    // Avoid matching $$ (already handled by Markwon or previous iteration)
                    if (next == '$' || prevChar == '$') {
                        // Let Markwon handle $$ (or it's the second $ of $$ we just processed)
                        // If we see $$ here, we just append it and update state if needed?
                        // Actually, Markwon expects $$ for display math.
                        // We should probably track $$ toggle too.
                        // The original code was a bit loose.
                        // Let's assume $$ toggles math.
                        if (next == '$') {
                            // Found $$, skip next
                            sb.append("$$")
                            inMath = !inMath
                            i += 2
                            continue
                        } else if (prevChar == '$') {
                            // Should have been handled by previous iteration
                            sb.append(c)
                            i++
                            continue
                        }
                    }
                    
                    // Convert single $ to $$
                    if (!inMath) {
                        sb.append("$$")
                        inMath = true
                    } else {
                        sb.append("$$")
                        inMath = false
                    }
                    i++
                    continue
                }
            }

            // Sup/Sub handling (only if not in math)
            if (!inMath) {
                 if (c == '^' && prev != '\\') {
                     if (inSup) {
                         sb.append("</sup>")
                         inSup = false
                     } else {
                         sb.append("<sup>")
                         inSup = true
                     }
                     i++
                     continue
                 }
                 if (c == '~' && prev != '\\') {
                     if (inSub) {
                         sb.append("</sub>")
                         inSub = false
                     } else {
                         sb.append("<sub>")
                         inSub = true
                     }
                     i++
                     continue
                 }
            }
        }
        
        sb.append(c)
        i++
    }
    return sb.toString()
}

class CitationAppearanceSpan(
    private val id: String,
    private val textColor: Int,
    private val textSizeSp: Float
) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val originalTextSize = paint.textSize
        val smallTextSize = originalTextSize * 0.65f
        paint.textSize = smallTextSize
        val width = paint.measureText(id)
        paint.textSize = originalTextSize
        
        val paddingX = smallTextSize * 0.6f
        val cardWidth = width + paddingX * 2
        return (cardWidth + 8).toInt() // Add margin
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val originalTextSize = paint.textSize
        val originalColor = paint.color
        val originalStyle = paint.style

        // Set smaller font for citation
        val smallTextSize = originalTextSize * 0.65f
        paint.textSize = smallTextSize
        
        val textWidth = paint.measureText(id)
        val fontMetrics = paint.fontMetrics
        val textHeight = -fontMetrics.ascent + fontMetrics.descent
        
        val paddingX = smallTextSize * 0.6f
        val paddingY = smallTextSize * 0.1f
        
        val cardWidth = textWidth + paddingX * 2
        val cardHeight = textHeight + paddingY * 2
        
        // Calculate position (Superscript style)
        // Restore original size to get main text ascent
        paint.textSize = originalTextSize
        val originalAscent = paint.ascent()
        paint.textSize = smallTextSize
        
        // Align top of card roughly with top of main text
        // y is baseline. originalAscent is negative.
        // Top of main text is y + originalAscent.
        val rectTop = y + originalAscent * 0.85f // Move slightly down from very top
        
        val rect = RectF(
            x + 4f,
            rectTop,
            x + 4f + cardWidth,
            rectTop + cardHeight
        )
        
        // Draw background
        paint.color = textColor 
        paint.alpha = 20 // Light background
        paint.style = Paint.Style.FILL
        // Corner radius relative to size
        val cornerRadius = smallTextSize * 0.3f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Draw text
        paint.color = textColor
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val textX = rect.centerX() - textWidth / 2
        val textBaselineY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
        
        canvas.drawText(id, textX, textBaselineY, paint)
        
        // Restore paint
        paint.textSize = originalTextSize
        paint.color = originalColor
        paint.style = originalStyle
    }
}
