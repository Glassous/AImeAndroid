package com.glassous.aime.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPopup(
    url: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

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
            // Wait for the animation duration (slightly longer to be safe)
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
        // Overlay box to center the content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = scaleOut(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // 90% width
                        .fillMaxHeight(0.6f), // 60% height
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp), // Reduce padding
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Removed "网页预览" text
                            Spacer(modifier = Modifier.weight(1f))
                            
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Handle exception
                                    }
                                },
                                modifier = Modifier.size(32.dp) // Reduce button size
                            ) {
                                Icon(
                                    Icons.Default.OpenInBrowser, 
                                    contentDescription = "Open in Browser",
                                    modifier = Modifier.size(20.dp) // Reduce icon size
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { dismiss() },
                                modifier = Modifier.size(32.dp) // Reduce button size
                            ) {
                                Icon(
                                    Icons.Default.Close, 
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp) // Reduce icon size
                                )
                            }
                        }
                        
                        // WebView
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.setSupportMultipleWindows(false) // Force open in same window
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val requestUrl = request?.url?.toString() ?: return false
                                            if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                                // Force load in this WebView
                                                return false
                                            }
                                            // Handle other schemes like mailto, tel, intent
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            return true
                                        }
                                        
                                        // Deprecated override for older devices compatibility
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            if (url == null) return false
                                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                                return false
                                            }
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            return true
                                        }
                                    }
                                    webChromeClient = WebChromeClient()
                                    loadUrl(url)
                                }
                            },
                            update = { webView ->
                                if (webView.url != url && webView.originalUrl != url) {
                                    webView.loadUrl(url)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
