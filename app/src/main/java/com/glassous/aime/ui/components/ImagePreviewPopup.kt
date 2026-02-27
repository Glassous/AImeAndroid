package com.glassous.aime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.glassous.aime.ui.utils.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewPopup(
    imagePath: String,
    onDismissRequest: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Start enter animation
    LaunchedEffect(Unit) {
        visible = true
    }

    // Function to handle dismissal with animation
    fun dismiss() {
        visible = false
    }

    // Wait for exit animation to finish before calling onDismissRequest
    LaunchedEffect(visible) {
        if (!visible) {
            delay(300)
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dismiss() }, // Click outside to close without ripple
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = scaleOut(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Surface(
                    modifier = Modifier
                        .padding(24.dp)
                        .wrapContentSize()
                        .widthIn(max = 600.dp) // Limit maximum width on tablets
                        .clickable(enabled = false) { }, // Prevent clicks on the image card from closing
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .wrapContentSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        ImageUtils.saveImageToGallery(context, imagePath)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { dismiss() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Image Container
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .wrapContentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imagePath,
                                contentDescription = "Image Preview",
                                modifier = Modifier
                                    .fillMaxWidth(if (imagePath.isNotEmpty()) 1f else 0.9f) // Allow it to expand but not exceed parent
                                    .heightIn(max = 600.dp) // Constrain height
                                    .clip(RoundedCornerShape(12.dp)), // Apply rounded corners to image
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
