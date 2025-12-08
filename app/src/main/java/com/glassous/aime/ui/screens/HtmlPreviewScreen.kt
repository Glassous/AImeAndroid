package com.glassous.aime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewScreen(
    htmlCode: String,
    onNavigateBack: () -> Unit,
    isSourceMode: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isLoading by remember { mutableStateOf(true) }
    var showCopiedIcon by remember { mutableStateOf(false) }
    var localIsSourceMode by remember { mutableStateOf(isSourceMode) }

    // 复制HTML代码到剪贴板
    fun copyHtmlCode() {
        clipboardManager.setText(AnnotatedString(htmlCode))
        showCopiedIcon = true
    }

    // 3秒后恢复复制图标
    LaunchedEffect(showCopiedIcon) {
        if (showCopiedIcon) {
            kotlinx.coroutines.delay(3000)
            showCopiedIcon = false
        }
    }

    // 刷新WebView
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    fun refreshWebView() {
        webViewRef?.reload()
    }

    // 加载HTML内容
    fun loadHtmlContent() {
        webViewRef?.loadDataWithBaseURL(null, htmlCode, "text/html", "UTF-8", null)
    }

    // 当切换到预览模式时加载HTML内容
    LaunchedEffect(localIsSourceMode) {
        if (!localIsSourceMode) {
            loadHtmlContent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 预览模式按钮
                    IconButton(
                        onClick = { localIsSourceMode = false },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (!localIsSourceMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = "预览模式",
                            tint = if (!localIsSourceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 源码模式按钮
                    IconButton(
                        onClick = { localIsSourceMode = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (localIsSourceMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Filled.Code,
                            contentDescription = "源码模式",
                            tint = if (localIsSourceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 复制按钮
                    IconButton(onClick = { copyHtmlCode() }) {
                        Icon(
                            if (showCopiedIcon) Icons.Filled.Done else Icons.Filled.ContentCopy,
                            contentDescription = if (showCopiedIcon) "已复制" else "复制HTML代码",
                            tint = if (showCopiedIcon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 刷新按钮
                    IconButton(onClick = { refreshWebView() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新预览")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // 禁用自动inset处理，手动添加padding
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 加载状态指示器
                if (isLoading && !localIsSourceMode) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 根据模式显示不同内容
                if (localIsSourceMode) {
                    // 源码模式：显示HTML代码
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // 使用可滚动的文本区域显示源码
                        Text(
                            text = htmlCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp
                                    // 移除底部内边距
                                )
                                .verticalScroll(rememberScrollState())
                        )
                    }
                } else {
                    // 预览模式：显示WebView
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 移除底部内边距
                    ) {
                        AndroidView(
                            factory = {
                                WebView(context).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoading = false
                                        }
                                    }
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.supportZoom()
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    // 允许WebView显示HTML背景颜色
                                    setBackgroundColor(0x00000000) // 设置透明背景
                                    webViewRef = this
                                }
                            },
                            update = {
                                // 移除自动加载，改为通过LaunchedEffect加载
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}