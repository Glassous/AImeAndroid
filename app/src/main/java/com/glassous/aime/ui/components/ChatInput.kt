package com.glassous.aime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    placeholderText: String? = null, // 新增参数：自定义占位符文本
    showScrollToBottomButton: Boolean = false,
    onScrollToBottomClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.5f,
    // 新增：输入框内部背景透明度（默认与当前实现一致）
    innerAlpha: Float = 0.9f
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
