package com.glassous.aime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.ui.utils.ImageUtils
import kotlinx.coroutines.launch

@Composable
fun MessageImageCard(
    imagePath: String,
    modifier: Modifier = Modifier,
    isShareMode: Boolean = false,
    message: ChatMessage? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    // Reset copy state after 5 seconds
    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(5000)
            isCopied = false
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Assuming parent padding is around 32.dp (16 start + 16 end) in LongImagePreviewDialog
    val minWidth = if (isShareMode) (screenWidth - 32.dp) else 0.dp

    Column(
        modifier = modifier
            .let { 
                if (isShareMode) {
                    it.width(IntrinsicSize.Max).widthIn(min = minWidth)
                } else {
                    it.wrapContentWidth()
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isShareMode) MaterialTheme.colorScheme.surface else Color.Transparent)
    ) {
        // Image Content
        Box(
            modifier = Modifier
                .wrapContentWidth()
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
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier
                    .wrapContentWidth()
                    .heightIn(max = if (isShareMode) 1000.dp else 400.dp),
                contentScale = ContentScale.Fit
            )
        }

        // Bottom Bar (Only show if not in share mode and expanded)
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
                        text = "图片",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    // Right: Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Download Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    ImageUtils.saveImageToGallery(context, imagePath)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save to Gallery",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Share Button
                        IconButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Image",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Copy Button (Copy Path)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(imagePath))
                                isCopied = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (isCopied) "Copied" else "Copy Path",
                                tint = if (isCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareSheet && message != null) {
        LongImagePreviewBottomSheet(
            messages = listOf(message),
            onDismiss = { showShareSheet = false },
            showLinkButton = false,
            isSingleItemShare = true
        )
    }
}
