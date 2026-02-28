package com.glassous.aime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleOutline

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean = false,
    minimalMode: Boolean = false,
    hideInputBorder: Boolean = false,
    hideSendButtonBackground: Boolean = false,
    hideInputPlaceholder: Boolean = false, // 新增参数：隐藏输入框占位符
    placeholderText: String? = null, // 新增参数：自定义占位符文本
    showScrollToBottomButton: Boolean = false,
    onScrollToBottomClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.5f,
    // 新增：输入框内部背景透明度（默认与当前实现一致）
    innerAlpha: Float = 0.9f,
    // 新增：附件预览
    attachedImages: List<String> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    // 固定发送按钮高度为输入框初始高度（硬编码）
    val buttonSize = 56.dp
    val inputShape = RoundedCornerShape(24.dp)
    // 根据极简模式与隐藏占位符设置，控制输入框内部背景透明度
    val inputContainerAlpha = if (minimalMode && hideInputPlaceholder && inputText.isBlank()) 0f else innerAlpha.coerceIn(0f, 1f)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = overlayAlpha.coerceIn(0f, 1f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 附件预览区域
            AnimatedVisibility(
                visible = attachedImages.isNotEmpty(),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachedImages) { path ->
                        Box(
                            modifier = Modifier.size(72.dp)
                        ) {
                            val isUrl = path.startsWith("url:")
                            val urlType = if (isUrl) path.split(":")[1] else ""
                            val actualPath = if (isUrl) path.substringAfterLast(":") else path
                            
                            val isVideo = actualPath.endsWith(".mp4", ignoreCase = true) || urlType == "video_url"
                            val isAudio = actualPath.endsWith(".m4a", ignoreCase = true) || actualPath.endsWith(".mp3", ignoreCase = true) || actualPath.endsWith(".wav", ignoreCase = true)
                            val isPdf = actualPath.endsWith(".pdf", ignoreCase = true) || urlType == "pdf_url"
                            val isText = actualPath.contains("/txt_", ignoreCase = true)
                            
                            if (isAudio || isPdf || isText || isUrl) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable { 
                                            if (!isPdf && !isText && !isUrl) onImageClick(path) 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isPdf -> Icons.Default.PictureAsPdf
                                            isAudio -> Icons.Default.AudioFile
                                            isUrl && urlType == "image_url" -> Icons.Default.Image
                                            isUrl && urlType == "video_url" -> Icons.Default.PlayCircleOutline
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = when {
                                            isPdf -> "PDF"
                                            isAudio -> "Audio"
                                            isUrl -> "URL"
                                            else -> "Text"
                                        },
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    
                                    val label = when {
                                        isPdf -> "PDF"
                                        isText -> {
                                            val ext = actualPath.substringAfterLast(".", "").uppercase()
                                            if (ext.isNotEmpty() && ext != "TXT") ext else "TXT"
                                        }
                                        isUrl -> "URL"
                                        else -> null
                                    }
                                    if (label != null) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = if (isVideo) {
                                        coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                            .data(path)
                                            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                            .build()
                                    } else {
                                        path
                                    },
                                    contentDescription = "预览图片",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(path) },
                                    contentScale = ContentScale.Crop
                                )
                                
                                if (isVideo) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(4.dp)
                                    )
                                }
                            }
                            
                            // 删除按钮
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(16.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .clickable { onRemoveAttachment(path) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 120.dp)
                    .animateContentSize(),
                placeholder = {
                    // 仅在开启极简模式且配置为隐藏时才隐藏占位符
                    if (!(minimalMode && hideInputPlaceholder)) {
                        Text(
                            text = placeholderText ?: "输入消息...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = true,
                shape = inputShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (minimalMode && hideInputBorder) Color.Transparent else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (minimalMode && hideInputBorder) Color.Transparent else MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = inputContainerAlpha),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = inputContainerAlpha)
                ),
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showScrollToBottomButton) {
                            IconButton(
                                onClick = onScrollToBottomClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "回到底部",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
            
            AnimatedVisibility(
                visible = inputText.isNotBlank(),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                if (minimalMode && hideSendButtonBackground) {
                    // 极简模式或隐藏发送按钮背景：只显示图标，无背景
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage()
                                focusManager.clearFocus()
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        if (isLoading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    // 正常模式：带背景的按钮
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage()
                                focusManager.clearFocus()
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.size(buttonSize),
                        shape = inputShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送"
                            )
                    }
                }
            }
        }
    }
}
}
}
