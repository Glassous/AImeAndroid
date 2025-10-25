package com.glassous.aime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
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

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean = false,
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
                    Text(
                        text = "输入消息...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = true,
                shape = inputShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            
            AnimatedVisibility(
                visible = inputText.isNotBlank(),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
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