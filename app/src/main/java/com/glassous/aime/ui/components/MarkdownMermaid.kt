package com.glassous.aime.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.glassous.aime.data.ChatMessage
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownMermaid(
    mermaidCode: String,
    modifier: Modifier = Modifier,
    isShareMode: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    // Scale factor to reduce chart size
    val contentScale = 0.6f

    // State for dynamic height and loading status
    var webViewHeight by remember { mutableStateOf(200.dp) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Interaction states
    var isExpanded by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Size constraints logic
    // val configuration = LocalConfiguration.current
    // val screenHeight = configuration.screenHeightDp.dp
    // val maxAllowedHeight = screenHeight * 0.6f

    // JavaScript Interface to receive callbacks from WebView
    val jsInterface = remember {
        object {
            @JavascriptInterface
            fun onRenderFinished(height: Float) {
                // height is in CSS pixels.
                val heightDp = (height * contentScale + 20).dp
                if (heightDp > 0.dp) {
                    webViewHeight = heightDp
                    isLoading = false
                }
            }

            @JavascriptInterface
            fun onClick() {
                if (!isShareMode) {
                    isExpanded = !isExpanded
                }
            }
        }
    }

    val htmlContent = remember(mermaidCode, isDarkTheme) {
        val bgColorHex = String.format("#%06X", (backgroundColor.toArgb() and 0xFFFFFF))
        
        // High contrast monochrome dark theme configuration
        val themeConfig = if (isDarkTheme) {
            """
            theme: 'base',
            themeVariables: {
                darkMode: true,
                background: '$bgColorHex',
                primaryColor: '#1e1e1e',
                primaryTextColor: '#ffffff',
                primaryBorderColor: '#ffffff',
                lineColor: '#ffffff',
                secondaryColor: '#1e1e1e',
                tertiaryColor: '#1e1e1e',
                noteBkgColor: '#1e1e1e',
                noteTextColor: '#ffffff',
                noteBorderColor: '#ffffff'
            }
            """
        } else {
            """
            theme: 'default',
            themeVariables: {
                darkMode: false,
                background: '$bgColorHex'
            }
            """
        }

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=$contentScale, maximum-scale=3.0, user-scalable=yes">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/mermaid/10.9.0/mermaid.min.js"></script>
            <script>
                mermaid.initialize({
                    startOnLoad: false,
                    securityLevel: 'loose',
                    $themeConfig
                });

                document.addEventListener('DOMContentLoaded', function() {
                    mermaid.run({
                        querySelector: '.mermaid'
                    }).then(() => {
                        // Wait a brief moment for layout to settle
                        setTimeout(() => {
                            const container = document.querySelector('.mermaid');
                            const height = container.getBoundingClientRect().height;
                            Android.onRenderFinished(height);
                        }, 100);
                    });

                    // Add click listener to body/container to toggle expansion
                    document.body.onclick = function() {
                        Android.onClick();
                    };
                });
            </script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: $bgColorHex;
                    display: flex;
                    justify-content: center;
                    /* align-items: center; */
                    /* min-height: 100vh; */
                    overflow: hidden; /* Prevent body scroll bars */
                }
                .mermaid {
                    width: 100%;
                    text-align: center;
                    display: flex;
                    justify-content: center;
                }
                /* Ensure SVG scales correctly */
                svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
            </style>
        </head>
        <body>
            <div class="mermaid">
                $mermaidCode
            </div>
        </body>
        </html>
        """.trimIndent()
    }

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // We keep the clickable here as a fallback, and for the area outside WebView if any
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
            val containerWidth = maxWidth
            
            // In Share Mode, if width is infinite (due to LongImagePreviewBottomSheet logic), 
            // we must constrain it to screen width to ensure WebView renders correctly.
            // WebView does not support infinite width measure specs.
            val webViewModifier = if (isShareMode && containerWidth == androidx.compose.ui.unit.Dp.Infinity) {
                Modifier.width(LocalConfiguration.current.screenWidthDp.dp)
            } else {
                Modifier.fillMaxWidth()
            }
            
            // Revert to original height logic to avoid truncation
            val finalHeight = webViewHeight

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        setBackgroundColor(0) // Transparent background
                        addJavascriptInterface(jsInterface, "Android")
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL("https://cdnjs.cloudflare.com/", htmlContent, "text/html", "UTF-8", null)
                    
                    if (isShareMode) {
                        webView.setOnTouchListener { _, _ -> true } // Disable interaction in share mode
                    } else {
                        webView.setOnTouchListener(null)
                    }
                },
                modifier = webViewModifier
                    .height(finalHeight)
                    .alpha(if (isLoading) 0f else 1f)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
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
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button
                    IconButton(
                        onClick = { showShareSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Chart",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Copy Button
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(mermaidCode))
                            Toast.makeText(context, "Mermaid 代码已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Mermaid Code",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showShareSheet) {
        val dummyMessage = remember(mermaidCode) {
            ChatMessage(
                conversationId = 0,
                content = "```mermaid\n$mermaidCode\n```", 
                isFromUser = false,
                modelDisplayName = "Chart Share"
            )
        }
        LongImagePreviewBottomSheet(
            messages = listOf(dummyMessage),
            onDismiss = { showShareSheet = false },
            showLinkButton = false
        )
    }
}
