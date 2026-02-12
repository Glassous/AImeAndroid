package com.glassous.aime.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(
    mermaidCode: String,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    
    // Scale factor to reduce chart size
    val contentScale = 0.6f

    // State for dynamic height and loading status
    var webViewHeight by remember { mutableStateOf(200.dp) }
    var isLoading by remember { mutableStateOf(true) }

    // JavaScript Interface to receive callbacks from WebView
    val jsInterface = remember {
        object {
            @JavascriptInterface
            fun onRenderFinished(height: Float) {
                // height is in CSS pixels.
                // Since initial-scale is set to contentScale, the visual height in DPs is height * contentScale.
                // We add some padding to ensure no clipping.
                val heightDp = (height * contentScale + 40).dp
                if (heightDp > 0.dp) {
                    webViewHeight = heightDp
                    isLoading = false
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
                });
            </script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: $bgColorHex;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
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
                        // Enable caching for offline support
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                    setBackgroundColor(0) // Transparent background
                    addJavascriptInterface(jsInterface, "Android")
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("https://cdnjs.cloudflare.com/", htmlContent, "text/html", "UTF-8", null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(webViewHeight)
                .alpha(if (isLoading) 0f else 1f) // Hide WebView while loading
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    }
}
