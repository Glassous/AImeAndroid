package com.glassous.aime.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.sp
import com.glassous.aime.data.ChatMessage

@Composable
fun MarkdownCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    onPreview: (() -> Unit)? = null,
    onPreviewSource: (() -> Unit)? = null,
    useCardStyle: Boolean = false,
    isShareMode: Boolean = false
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val edgeZoneWidth = 24.dp // Width of the edge zone where scroll is disabled to allow drawer gesture
    
    // Logic from CodeBlockWithCopy to detect languages
    // 检测是否为HTML代码块
    val isHtmlCode = remember(code, language) {
        val lowerCaseLang = language?.lowercase()
        val hasHtmlLanguage = lowerCaseLang == "html" || lowerCaseLang == "htm"
        val hasHtmlTags = code.contains(Regex("<!DOCTYPE html>|</?html>|</?body>|</?head>", RegexOption.IGNORE_CASE))
        hasHtmlLanguage || hasHtmlTags
    }

    // 检测是否为JSX代码块
    val isJsxCode = remember(code, language) {
        val lowerCaseLang = language?.lowercase()
        val hasJsxLanguage = lowerCaseLang == "jsx" || lowerCaseLang == "tsx"
        val hasJsxContent = code.contains("import React") || code.contains("from 'react'") || code.contains("from \"react\"")
        hasJsxLanguage || ((lowerCaseLang == "js" || lowerCaseLang == "javascript") && hasJsxContent)
    }

    // 检测是否为Vue代码块
    val isVueCode = remember(code, language) {
        val lowerCaseLang = language?.lowercase()
        val hasVueLanguage = lowerCaseLang == "vue"
        // 简单的 SFC 检测：包含 template 且 (包含 script 或 style 或 setup)
        val hasVueContent = code.contains("<template>") && (code.contains("<script") || code.contains("<style"))
        hasVueLanguage || hasVueContent
    }

    // 检测是否为网页分析卡片 (通过 HTML 注释标记)
    val isWebAnalysisCard = remember(code) {
        code.contains("<!-- type: web_analysis")
    }

    // 提取网页标题
    val webAnalysisTitle = remember(code) {
        if (isWebAnalysisCard) {
            val titleMarker = "web_title:"
            val start = code.indexOf(titleMarker)
            if (start != -1) {
                val end = code.indexOf("-->", start)
                if (end != -1) {
                    code.substring(start + titleMarker.length, end).trim()
                } else null
            } else null
        } else null
    }

    val isPreviewable = isHtmlCode || isJsxCode || isVueCode

    // If it's a special preview card (Summary Card), keep the old style
    if (useCardStyle && isPreviewable && !isShareMode) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (!isWebAnalysisCard) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = when {
                                isVueCode -> "Vue代码"
                                isJsxCode -> "JSX代码"
                                else -> "HTML代码"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = when {
                            isWebAnalysisCard -> if (!webAnalysisTitle.isNullOrBlank()) webAnalysisTitle else "网页标题"
                            isVueCode -> "Vue 代码"
                            isJsxCode -> "JSX 代码"
                            else -> "HTML 代码"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = if (isWebAnalysisCard) 2 else 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // Right: Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onPreview != null) {
                        FilledTonalButton(
                            onClick = onPreview,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "预览",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "预览", fontSize = 13.sp)
                        }
                    }

                    if (onPreviewSource != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onPreviewSource,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isWebAnalysisCard) Icons.Filled.Description else Icons.Filled.Code,
                                contentDescription = "查看内容",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isWebAnalysisCard) "内容" else "源码", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    } else {
        // New Table-like Code Card
        var isExpanded by remember { mutableStateOf(false) }
        var showShareSheet by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        
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
            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (!isShareMode) {
                            it.clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                isExpanded = !isExpanded
                            }
                        } else {
                            it
                        }
                    }
            ) {
                // If isShareMode, we might want to wrap text instead of scrolling
                // But for now let's keep it scrollable or consistent
                // To "auto adjust ratio", we can disable horizontal scroll in share mode
                // so it captures the visible part? Or maybe use softWrap?
                // Using softWrap=true and horizontalScroll=null for share mode ensures all code is visible vertically
                // if it wraps. But code usually looks bad when wrapped.
                // However, user requirement 6 says "Auto adjust ratio".
                
                val scrollState = rememberScrollState()
                
                // State to control horizontal scroll enabled status based on touch position
                var isScrollEnabled by remember { mutableStateOf(true) }
                
                // Use SyntaxHighlighter for code highlighting
                val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val highlightedCode = remember(code, language, isDarkTheme) {
                    SyntaxHighlighter.highlight(code, language, isDarkTheme)
                }
                
                Text(
                    text = highlightedCode,
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
                                                val isAtStart = scrollState.value == 0
                                                
                                                // If at start, disable scroll initially (assume potential drawer gesture)
                                                // If at edges, also disable scroll
                                                if ((isAtStart && x < edgeZoneWidthPx) || x > width - edgeZoneWidthPx) {
                                                    isScrollEnabled = false
                                                } else {
                                                    isScrollEnabled = true
                                                }
                                            }
                                            
                                            // If scroll is disabled but we are at start, check if user is dragging LEFT (scrolling content)
                                            if (!isScrollEnabled && scrollState.value == 0) {
                                                val drag = event.changes.firstOrNull()?.positionChange()
                                                if (drag != null && drag.x < 0) {
                                                    isScrollEnabled = true
                                                }
                                            }
                                        }
                                    }
                                }
                                .horizontalScroll(scrollState, enabled = isScrollEnabled)
                            } else {
                                // In share mode, we want the text to expand horizontally without wrapping
                                // To force it to expand beyond screen width, we must use a modifier that allows it.
                                // horizontalScroll without state/enabled allows it to layout at full width
                                // but requires unbounded constraints from parent or modifier.
                                // wrapContentWidth(unbounded = true) is the key.
                                it.wrapContentWidth(align = Alignment.Start, unbounded = true)
                            }
                        }
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = textSizeSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    softWrap = false // Disable soft wrap for both modes to keep code structure
                )
            }
            
            // Bottom Bar
            // In share mode: always visible, only language
            // In normal mode: visible when expanded, all buttons
            val showBottomBar = isShareMode || isExpanded
            
            AnimatedVisibility(
                visible = showBottomBar,
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
                    // Left: Language
                    Text(
                        text = language ?: "Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    // Right: Buttons (Only show if NOT in share mode)
                    if (!isShareMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Preview Button removed as requested for non-HTML blocks
                            // (HTML blocks use the separate card style above)
                            
                            // Share Button
                            IconButton(
                                onClick = { showShareSheet = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Code",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Copy Button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Code",
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
            val dummyMessage = remember(code) {
                ChatMessage(
                    conversationId = 0,
                    content = "```$language\n$code\n```", // Wrap in markdown code block for renderer
                    isFromUser = false,
                    modelDisplayName = "Code Share"
                )
            }
            LongImagePreviewBottomSheet(
                messages = listOf(dummyMessage),
                onDismiss = { showShareSheet = false },
                showLinkButton = false
            )
        }
    }
}
