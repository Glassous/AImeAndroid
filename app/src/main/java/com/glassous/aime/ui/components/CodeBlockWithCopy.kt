package com.glassous.aime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CodeBlockWithCopy(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    onPreview: (() -> Unit)? = null,
    onPreviewSource: (() -> Unit)? = null,
    useCardStyle: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(3000) // 3秒后切换回复制按钮
            showCopiedFeedback = false
        }
    }

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

    val isPreviewable = isHtmlCode || isJsxCode

    // 根据useCardStyle和isHtmlCode决定显示样式
    if (useCardStyle && isPreviewable) {
        // 卡片样式：仅显示HTML/JSX代码提示和预览/源码按钮
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // 修改为单行布局：左侧标题，右侧按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp), // 稍微减小一点内边距以适应一行
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：HTML/JSX代码提示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false) // 允许左侧压缩，但不强制占位
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = if (isJsxCode) "JSX代码" else "HTML代码",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isJsxCode) "JSX 代码" else "HTML 代码",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                // 右侧：按钮组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // 预览按钮
                    if (onPreview != null) {
                        FilledTonalButton(
                            onClick = onPreview,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp) // 稍微调小高度
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = if (isJsxCode) "预览JSX" else "预览HTML",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "预览", fontSize = 13.sp)
                        }
                    }

                    // 源码按钮
                    if (onPreviewSource != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onPreviewSource,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = "查看源码",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "源码", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    } else {
        // 传统样式：显示完整代码块
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 代码内容
            Text(
                text = code,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
                    .padding(top = 8.dp, end = if (isPreviewable && onPreview != null) 72.dp else 32.dp), // 为按钮留出空间
                fontFamily = FontFamily.Monospace,
                fontSize = textSizeSp.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            // 右上角按钮区域
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // 预览按钮（仅当是HTML/JSX代码且提供了预览回调时显示）
                if (isPreviewable && onPreview != null) {
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Preview,
                            contentDescription = if (isJsxCode) "预览JSX" else "预览HTML",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 复制按钮/对号切换
                IconButton(
                    onClick = {
                        if (!showCopiedFeedback) {
                            clipboardManager.setText(AnnotatedString(code))
                            showCopiedFeedback = true
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (showCopiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = if (showCopiedFeedback) "已复制" else "复制代码",
                        tint = if (showCopiedFeedback)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}