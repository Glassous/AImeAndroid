package com.glassous.aime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
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
    textSizeSp: Float = 14f
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedFeedback by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(3000) // 3秒后切换回复制按钮
            showCopiedFeedback = false
        }
    }

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
                .padding(top = 8.dp, end = 32.dp), // 为右上角按钮留出空间
            fontFamily = FontFamily.Monospace,
            fontSize = textSizeSp.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        
        // 复制按钮/对号切换
        IconButton(
            onClick = {
                if (!showCopiedFeedback) {
                    clipboardManager.setText(AnnotatedString(code))
                    showCopiedFeedback = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
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