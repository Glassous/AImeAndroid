package com.glassous.aime.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request
import com.glassous.aime.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import android.graphics.Bitmap

import com.glassous.aime.data.ProxyBlacklist

import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebViewPopup(
    url: String,
    onDismissRequest: () -> Unit,
    useCloudProxy: Boolean = false
) {
    val context = LocalContext.current
    val okHttpClient = remember { OkHttpClient() }
    var visible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

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
                        
                        // WebView container with loading indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        // Set background to transparent to avoid white flash in dark mode
                                        setBackgroundColor(0)
                                        
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
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isLoading = true
                                                super.onPageStarted(view, url, favicon)
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isLoading = false
                                                super.onPageFinished(view, url)
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: android.webkit.WebResourceError?
                                            ) {
                                                isLoading = false
                                                super.onReceivedError(view, request, error)
                                            }

                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                val resourceUrl = request?.url?.toString() ?: return null
                                                
                                                // 如果没有开启云端代理，或者请求的已经是代理地址本身，则不拦截
                                                if (!useCloudProxy || resourceUrl.startsWith(BuildConfig.CLOUDFLARE_PROXY_URL)) {
                                                    return super.shouldInterceptRequest(view, request)
                                                }
                                                
                                                // 检查是否在黑名单中
                                                if (!ProxyBlacklist.shouldUseProxy(resourceUrl)) {
                                                    return super.shouldInterceptRequest(view, request)
                                                }
                                            
                                                try {
                                                    // 使用 OkHttpClient 通过代理地址去拉取子资源
                                                    val okHttpRequest = Request.Builder()
                                                        .url(BuildConfig.CLOUDFLARE_PROXY_URL) // 请求代理服务器
                                                        .addHeader("x-target-url", resourceUrl) // 告诉代理要抓哪个资源
                                                        .addHeader("User-Agent", request?.requestHeaders?.get("User-Agent") ?: "")
                                                        .build()
                                                        
                                                    val response = okHttpClient.newCall(okHttpRequest).execute()
                                                    
                                                    // 提取正确的 MimeType
                                                    val contentType = response.header("content-type") ?: "text/plain"
                                                    val mimeType = contentType.split(";")[0].trim()
                                                    val encoding = "utf-8"
                                                    
                                                    return WebResourceResponse(
                                                        mimeType,
                                                        encoding,
                                                        response.body?.byteStream()
                                                    )
                                                } catch (e: Exception) {
                                                    return super.shouldInterceptRequest(view, request)
                                                }
                                            }

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
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                progress = newProgress / 100f
                                                super.onProgressChanged(view, newProgress)
                                            }
                                        }
                                        
                                        // Initial load
                                        val loadUrl = if (useCloudProxy && BuildConfig.CLOUDFLARE_PROXY_URL.isNotEmpty() && ProxyBlacklist.shouldUseProxy(url)) {
                                            try {
                                                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                                "${BuildConfig.CLOUDFLARE_PROXY_URL}?url=$encodedUrl"
                                            } catch (e: Exception) {
                                                url
                                            }
                                        } else {
                                            url
                                        }
                                        loadUrl(loadUrl)
                                    }
                                },
                                update = { webView ->
                                    val targetUrl = if (useCloudProxy && BuildConfig.CLOUDFLARE_PROXY_URL.isNotEmpty() && ProxyBlacklist.shouldUseProxy(url)) {
                                        try {
                                            val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                            "${BuildConfig.CLOUDFLARE_PROXY_URL}?url=$encodedUrl"
                                        } catch (e: Exception) {
                                            url
                                        }
                                    } else {
                                        url
                                    }
                                    
                                    if (webView.url != targetUrl && webView.originalUrl != targetUrl) {
                                        webView.loadUrl(targetUrl)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (isLoading) 0f else 1f)
                            )

                            // Expressive Loading Indicator
                            if (isLoading) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.width(120.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
