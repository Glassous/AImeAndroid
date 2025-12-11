package com.glassous.aime.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale

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
    var localIsSourceMode by remember { mutableStateOf(isSourceMode) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 复制HTML代码到剪贴板
    fun copyHtmlCode() {
        clipboardManager.setText(AnnotatedString(htmlCode))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // 保存文件
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html"),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(htmlCode.toByteArray())
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // 刷新WebView
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sourceWebViewRef by remember { mutableStateOf<WebView?>(null) }

    fun refreshWebView() {
        webViewRef?.reload()
        sourceWebViewRef?.reload()
    }

    // 生成高亮HTML
    val isDarkTheme = isSystemInDarkTheme()
    val highlightedHtml = remember(htmlCode, isDarkTheme) {
        val theme = if (isDarkTheme) "atom-one-dark" else "atom-one-light"
        val bgColor = if (isDarkTheme) "#1c1b1f" else "#fffbff" // Match Material3 default background roughly
        val escapedCode = htmlCode.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/$theme.min.css">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            <script>hljs.highlightAll();</script>
            <style>
                body { margin: 0; padding: 0; background-color: $bgColor; }
                pre { margin: 0; padding: 16px; white-space: pre; overflow-x: auto; }
                code { font-family: 'Fira Code', 'Consolas', monospace; font-size: 14px; line-height: 1.5; background-color: transparent !important; }
            </style>
        </head>
        <body>
            <pre><code class="language-html">$escapedCode</code></pre>
        </body>
        </html>
        """.trimIndent()
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

    // 统计信息
    val charCount = remember(htmlCode) { htmlCode.length }
    val lineCount = remember(htmlCode) { htmlCode.lines().size }

    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("代码统计") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("字符数:", style = MaterialTheme.typography.bodyLarge)
                        Text("$charCount", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("行数:", style = MaterialTheme.typography.bodyLarge)
                        Text("$lineCount", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("关闭")
                }
            }
        )
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
                    // 预览/源码 切换按钮
                    IconButton(
                        onClick = { localIsSourceMode = !localIsSourceMode }
                    ) {
                        Icon(
                            if (localIsSourceMode) Icons.Filled.Visibility else Icons.Filled.Code,
                            contentDescription = if (localIsSourceMode) "切换到预览" else "切换到源码"
                        )
                    }

                    // 刷新按钮
                    IconButton(onClick = { refreshWebView() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }

                    // 更多选项
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 复制源码
                            DropdownMenuItem(
                                text = { Text("复制源码") },
                                onClick = {
                                    copyHtmlCode()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }
                            )
                            // 代码统计
                            DropdownMenuItem(
                                text = { Text("代码统计") },
                                onClick = {
                                    showStatsDialog = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Info, null) }
                            )
                            // 下载HTML
                            DropdownMenuItem(
                                text = { Text("下载 HTML") },
                                onClick = {
                                    saveFileLauncher.launch("index.html")
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Download, null) }
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // 禁用自动inset处理，手动添加padding
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 加载状态指示器
            if (isLoading && !localIsSourceMode) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                        .zIndex(1f)
                )
            }

            if (localIsSourceMode) {
                // 源码模式：使用WebView显示高亮代码
                AndroidView(
                    factory = {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            setBackgroundColor(0x00000000)
                            sourceWebViewRef = this
                        }
                    },
                    update = { view ->
                        view.loadDataWithBaseURL(null, highlightedHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 预览模式：显示WebView
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