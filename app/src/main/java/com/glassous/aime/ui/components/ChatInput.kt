package com.glassous.aime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator

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
    // 新增参数：内嵌按钮
    showUploadButton: Boolean = false,
    showDownloadButton: Boolean = false,
    showScrollToBottomButton: Boolean = false,
    onUploadClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onScrollToBottomClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    // 固定发送按钮高度为输入框初始高度（硬编码）
    val buttonSize = 56.dp
    val inputShape = RoundedCornerShape(24.dp)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
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
                    if (!hideInputPlaceholder) {
                        Text(
                            text = "输入消息...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = true,
                shape = inputShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (minimalMode && hideInputBorder) Color.Transparent else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (minimalMode && hideInputBorder) Color.Transparent else MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    // 内嵌按钮行
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 上传按钮
                        if (showUploadButton) {
                            IconButton(
                                onClick = onUploadClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = "上传",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // 下载按钮
                        if (showDownloadButton) {
                            IconButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "下载",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // 回到底部按钮
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
                        colors = IconButtonDefaults.filledIconButtonColors()
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