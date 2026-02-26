package com.glassous.aime.ui.components

import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glassous.aime.data.ChatMessage
import kotlin.math.max
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.CompositionLocalProvider
    import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange

data class TableData(
    val headers: List<String>,
    val rows: List<List<String>>
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkdownTable(
    markdown: String,
    markwon: Markwon,
    textColor: Color,
    textSizeSp: Float,
    modifier: Modifier = Modifier,
    isShareMode: Boolean = false // New parameter to indicate share mode
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    val tableData = remember(markdown) { parseMarkdownTable(markdown) }
    val horizontalScrollState = rememberScrollState()
    
    // State to control horizontal scroll enabled status based on touch position
    var isScrollEnabled by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val edgeZoneWidth = 24.dp // Width of the edge zone where scroll is disabled to allow drawer gesture

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isShareMode) MaterialTheme.colorScheme.surface else Color.Transparent)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Table Content
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .let {
                    if (!isShareMode) {
                        it.clickable(
                            interactionSource = interactionSource,
                            indication = null // No ripple effect
                        ) {
                            isExpanded = !isExpanded
                        }
                    } else {
                        it
                    }
                }
        ) {
            val screenWidth = maxWidth
            
            // In share mode, we want to ensure the table is fully visible and not scrolling
            // So we might need to adjust column widths to fit or allow expansion
            // However, the requirement says "auto adjust ratio to avoid cutoff"
            // If the table is too wide, it will be cut off in screenshot if it's scrollable.
            // So for share mode, we should perhaps force fit or use a different layout strategy.
            // But standard MarkdownTable usually scrolls.
            // If we want it to be fully visible for screenshot (LongImagePreview), it should probably wrap content width?
            // But LongImagePreview usually captures the view as is.
            // If the view is scrollable, it might only capture the visible part.
            // Wait, LongImagePreview might be using a specialized view or capturing the whole scrollable content?
            // If it captures the view, and the view has a fixed width (screen width), then the content to the right is clipped.
            
            // To fix this for sharing:
            // 1. We can try to fit columns into screen width (shrink them).
            // 2. OR we can let the table take as much width as it needs, and rely on the parent container in share mode to be wide enough?
            // Usually, share mode (generating image) renders the component in a View that might not be constrained by screen width if we set it up right.
            // But here, it seems we are constrained by `maxWidth`.
            
            // Let's implement a "Fit to Width" strategy for share mode if possible, 
            // OR simply calculate widths such that they fit.
            
            val minColWidth = if (isShareMode) 50.dp else 150.dp // Relax min width in share mode to allow fitting
            val colCount = if (tableData.headers.isNotEmpty()) tableData.headers.size else 1
            val totalMinWidth = minColWidth * colCount
            
            // If content fits in screen, distribute width equally. Otherwise use min width.
            val columnWidth = if (totalMinWidth < screenWidth || isShareMode) {
                 // In share mode, try to squeeze into available width if reasonable
                 // Or just divide equally
                 screenWidth / colCount
            } else {
                minColWidth
            }

            // Disable overscroll effect to prevent stretching and improve boundary handling
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (!isShareMode) {
                                it.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        var edgeZoneWidthPx = 0f
                                        
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val down = event.changes.find { it.changedToDown() }
                                            
                                            if (down != null) {
                                                val x = down.position.x
                                                val width = size.width
                                                edgeZoneWidthPx = with(density) { edgeZoneWidth.toPx() }
                                                val isAtStart = horizontalScrollState.value == 0
                                                
                                                // If at start, disable scroll initially (assume potential drawer gesture)
                                                // If at edges, also disable scroll
                                                // FIX: Only disable if at start AND in edge zone, to allow scrolling from center
                                                if ((isAtStart && x < edgeZoneWidthPx) || x > width - edgeZoneWidthPx) {
                                                    isScrollEnabled = false
                                                } else {
                                                    isScrollEnabled = true
                                                }
                                            }
                                            
                                            // If scroll is disabled but we are at start, check if user is dragging LEFT (scrolling table)
                                            if (!isScrollEnabled && horizontalScrollState.value == 0) {
                                                val drag = event.changes.firstOrNull()?.positionChange()
                                                if (drag != null && drag.x < 0) {
                                                    isScrollEnabled = true
                                                }
                                            }
                                        }
                                    }
                                }
                                .horizontalScroll(horizontalScrollState, enabled = isScrollEnabled)
                            } else {
                                // In share mode, disable horizontal scroll to force layout to fit (or at least not be scrollable container)
                                // Actually if we want to show everything, we should probably wrap content?
                                // If we wrap content width, the parent needs to support it.
                                // If we are constrained to screen width, we MUST shrink content.
                                it
                            }
                        }
                ) {
                    // Headers
                    if (tableData.headers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .height(IntrinsicSize.Min) // 使分割线高度一致
                        ) {
                            tableData.headers.forEachIndexed { index, header ->
                                Cell(
                                    text = header,
                                    isHeader = true,
                                    textColor = textColor,
                                    textSizeSp = textSizeSp,
                                    markwon = markwon,
                                    modifier = Modifier.width(columnWidth), // Use calculated width
                                    onClick = { if (!isShareMode) isExpanded = !isExpanded }
                                )
                                if (index < tableData.headers.size - 1) {
                                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
    
                    // Rows
                    tableData.rows.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier
                                .background(
                                    if (rowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .height(IntrinsicSize.Min)
                        ) {
                            // 补齐列数不足的情况
                            val cells = if (row.size < tableData.headers.size) {
                                row + List(tableData.headers.size - row.size) { "" }
                            } else {
                                row
                            }
                            
                            cells.forEachIndexed { colIndex, cell ->
                                if (colIndex < tableData.headers.size) { // 只显示表头对应的列
                                    Cell(
                                        text = cell,
                                        isHeader = false,
                                        textColor = textColor,
                                        textSizeSp = textSizeSp,
                                        markwon = markwon,
                                        modifier = Modifier.width(columnWidth), // Use calculated width
                                        onClick = { if (!isShareMode) isExpanded = !isExpanded }
                                    )
                                    if (colIndex < tableData.headers.size - 1) {
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                        if (rowIndex < tableData.rows.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // Expanded Toolbar (Only show if not in share mode)
        if (!isShareMode) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Label
                    Text(
                        text = "表格",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    // Right: Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Share Button
                        IconButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Table",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Copy Button
                        IconButton(
                            onClick = {
                                val csv = parseMarkdownTableToCsv(markdown)
                                clipboardManager.setText(AnnotatedString(csv))
                                Toast.makeText(context, "表格已复制为 CSV", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy CSV",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Download Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val csv = parseMarkdownTableToCsv(markdown)
                                    saveCsvToFile(context, csv)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download CSV",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareSheet) {
        val dummyMessage = remember(markdown) {
            ChatMessage(
                conversationId = 0,
                content = markdown,
                isFromUser = false,
                modelDisplayName = "Table Share"
            )
        }
        LongImagePreviewBottomSheet(
            messages = listOf(dummyMessage),
            onDismiss = { showShareSheet = false },
            showLinkButton = false
        )
    }
}

@Composable
private fun Cell(
    text: String,
    isHeader: Boolean,
    textColor: Color,
    textSizeSp: Float,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(textColor.toArgb())
                    textSize = textSizeSp
                    setOnClickListener { onClick() }
                }
            },
            update = { tv ->
                tv.setTextColor(textColor.toArgb())
                tv.textSize = textSizeSp
                tv.setOnClickListener { onClick() }
                
                // Use markwon to render markdown/latex
                // Preprocess text to ensure consistent behavior with main renderer (e.g. latex $ -> $$, sup/sub)
                val processedText = preprocessText(text, true)
                markwon.setMarkdown(tv, processedText)
                
                // Enforce header bold styling
                if (isHeader) {
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
        )
    }
}

@Composable
private fun VerticalDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(color)
    )
}

@Composable
private fun HorizontalDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

private fun parseMarkdownTable(markdown: String): TableData {
    val lines = markdown.trim().split("\n")
    if (lines.isEmpty()) return TableData(emptyList(), emptyList())

    // 1. Headers
    val headerLine = lines[0]
    val headers = parseRow(headerLine)

    // 2. Rows
    // Skip separator line (usually index 1)
    val rows = lines.drop(1)
        .filter { !isSeparatorLine(it) }
        .map { parseRow(it) }

    return TableData(headers, rows)
}

private fun parseRow(line: String): List<String> {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return emptyList()

    val cells = mutableListOf<String>()
    var currentCell = StringBuilder()
    var i = 0
    
    // Skip leading pipe if present
    if (i < trimmed.length && trimmed[i] == '|') {
        i++
    }

    var isEscaped = false
    var inCode = false
    var inMath = false // $...$
    
    while (i < trimmed.length) {
        val c = trimmed[i]
        
        if (isEscaped) {
            currentCell.append(c)
            isEscaped = false
        } else {
            when (c) {
                '\\' -> {
                    isEscaped = true
                    currentCell.append(c)
                }
                '`' -> {
                    inCode = !inCode
                    currentCell.append(c)
                }
                '$' -> {
                    inMath = !inMath
                    currentCell.append(c)
                }
                '|' -> {
                    if (!inCode && !inMath) {
                        cells.add(currentCell.toString().trim())
                        currentCell.clear()
                    } else {
                        currentCell.append(c)
                    }
                }
                else -> {
                    currentCell.append(c)
                }
            }
        }
        i++
    }
    
    // If the last character processed was NOT a separator (meaning we have content), add it.
    // If the loop ended after processing a separator |, currentCell is empty.
    // However, we only want to add currentCell if it contains content OR if we are not at the "expected" end of a row (trailing pipe).
    // But since we can't easily distinguish trailing pipe from "pipe then empty cell", we follow standard markdown table rules:
    // Trailing pipe is ignored.
    
    if (currentCell.isNotEmpty()) {
         cells.add(currentCell.toString().trim())
    }
    
    return cells
}

private fun isSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    // 简单的分隔行检测：只包含 | - : 空格
    return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' } && trimmed.contains("-")
}

private fun cleanMarkdown(text: String): String {
    var result = text
    // Remove bold/italic: **text** -> text, *text* -> text, __text__ -> text, _text_ -> text
    // Using Regex to replace common markdown syntax
    val patterns = listOf(
        Regex("\\*\\*(.*?)\\*\\*"), // Bold **
        Regex("\\*(.*?)\\*"),       // Italic *
        Regex("__(.*?)__"),         // Bold __
        Regex("_(.*?)_"),           // Italic _
        Regex("`(.*?)`"),           // Code `
        Regex("~~(.*?)~~")          // Strikethrough ~~
    )
    
    patterns.forEach { pattern ->
        result = result.replace(pattern, "$1")
    }
    
    // Links [text](url) -> text
    result = result.replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
    
    return result
}

private fun parseMarkdownTableToCsv(markdown: String): String {
    val lines = markdown.trim().split("\n")
    if (lines.size < 2) return markdown

    val csvBuilder = StringBuilder()
    
    // Filter out separator line (usually 2nd line, contains --- or :---:)
    // The previous regex was not covering all cases like ":---" or ":---:" without pipes
    // Also, handle lines that might not start/end with pipes in raw markdown
    val contentLines = lines.filter { line ->
        val trimmed = line.trim()
        // Check if line contains only characters used in separators: |, -, :, space
        val isSeparator = trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' } && trimmed.contains("-")
        !isSeparator
    }

    for (line in contentLines) {
        val trimmed = line.trim()
        
        // Skip empty lines
        if (trimmed.isEmpty()) continue
        
        // Handle pipe-based tables (most common)
        val segments = if (trimmed.contains("|")) {
            parseRow(trimmed)
        } else if (trimmed.contains(",")) {
            // Fallback for CSV-like lines
            trimmed.split(",")
        } else {
             // Fallback for space-separated or single column
             listOf(trimmed)
        }
        
        val csvRow = segments.joinToString(",") { cell ->
            // Clean markdown first
            var cleanedCell = cleanMarkdown(cell.trim())
            
            // Remove common table alignment markers if they leaked in (e.g. :---)
            if (cleanedCell.matches(Regex("^[:\\-]{3,}$"))) {
                 cleanedCell = ""
            }
            
            // Escape quotes and wrap in quotes if necessary
            if (cleanedCell.contains(",") || cleanedCell.contains("\"") || cleanedCell.contains("\n")) {
                "\"${cleanedCell.replace("\"", "\"\"")}\""
            } else {
                cleanedCell
            }
        }
        csvBuilder.append(csvRow).append("\n")
    }
    return csvBuilder.toString().trim()
}

private suspend fun saveCsvToFile(context: android.content.Context, content: String) {
    withContext(Dispatchers.IO) {
        try {
            val fileName = "table_${System.currentTimeMillis()}.csv"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            FileWriter(file).use { it.write(content) }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已保存到下载目录: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
