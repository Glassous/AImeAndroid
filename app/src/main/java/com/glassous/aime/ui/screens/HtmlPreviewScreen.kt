package com.glassous.aime.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin

import androidx.compose.ui.graphics.toArgb

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
    var rotation by remember { mutableStateOf(0f) }

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
    val surfaceColor = MaterialTheme.colorScheme.surface
    val highlightedHtml = remember(htmlCode, isDarkTheme, surfaceColor) {
        val theme = if (isDarkTheme) "atom-one-dark" else "atom-one-light"
        val bgColor = String.format("#%06X", (surfaceColor.toArgb() and 0xFFFFFF))
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
                body { margin: 0; padding: 0; background-color: $bgColor; user-select: text; -webkit-user-select: text; }
                pre { margin: 0; padding: 16px; white-space: pre; overflow-x: auto; user-select: text; -webkit-user-select: text; }
                code { font-family: 'Fira Code', 'Consolas', monospace; font-size: 14px; line-height: 1.5; background-color: transparent !important; user-select: text; -webkit-user-select: text; }
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
        val hasHtml = Regex("<\\s*html", RegexOption.IGNORE_CASE).containsMatchIn(htmlCode)

        // JSX 检测逻辑
        val isJsx = !hasHtml && (
            htmlCode.contains("import React") ||
            htmlCode.contains("from 'react'") ||
            htmlCode.contains("from \"react\"") ||
            htmlCode.contains("export default") ||
            htmlCode.contains("className=") ||
            Regex("<[A-Z]").containsMatchIn(htmlCode)
        )

        val content = if (hasHtml) {
            htmlCode
        } else if (isJsx) {
            // JSX 预览模板
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://cdn.jsdelivr.net/npm/react@18.2.0/umd/react.production.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/react-dom@18.2.0/umd/react-dom.production.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/@babel/standalone@7.23.5/babel.min.js"></script>
                <style>
                    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                    #root { padding: 16px; height: 100vh; box-sizing: border-box; }
                </style>
            </head>
            <body>
                <div id="root"></div>
                <script>
                    window.exports = {};
                    window.module = { exports: window.exports };
                    window.require = (name) => {
                        if (name === 'react') return window.React;
                        if (name === 'react-dom') return window.ReactDOM;
                        return {};
                    };
                </script>
                <script type="text/babel" data-presets="react,env">
                    ${htmlCode.replace("</script>", "<\\/script>")}

                    // 尝试渲染默认导出
                    setTimeout(() => {
                        const App = module.exports.default || window.exports.default;
                        const rootElement = document.getElementById('root');
                        if (App && rootElement && rootElement.innerHTML === "") {
                            try {
                                const root = ReactDOM.createRoot(rootElement);
                                root.render(React.createElement(App));
                            } catch (e) {
                                console.error("Auto-render failed:", e);
                            }
                        }
                    }, 100);
                </script>
            </body>
            </html>
            """.trimIndent()
        } else {
            """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
                <style>
                    html,body{height:100%}
                    body{margin:0;padding:0}
                </style>
            </head>
            <body>
            $htmlCode
            </body>
            </html>
            """.trimIndent()
        }
        webViewRef?.loadDataWithBaseURL("https://aime.local/", content, "text/html", "UTF-8", null)
    }

    // 当切换到预览模式时加载HTML内容
    LaunchedEffect(localIsSourceMode, webViewRef, htmlCode) {
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
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = "旋转")
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
                            isLongClickable = true
                            setBackgroundColor(0x00000000)
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                            }
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                            }
                            sourceWebViewRef = this
                        }
                    },
                    update = { view ->
                        view.loadDataWithBaseURL(null, highlightedHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            rotationZ = rotation,
                            transformOrigin = TransformOrigin.Center
                        )
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
                            settings.loadsImagesAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.supportZoom()
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                            }
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                            }

                            webViewRef = this
                        }
                    },
                    update = {
                        // 移除自动加载，改为通过LaunchedEffect加载
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            rotationZ = rotation,
                            transformOrigin = TransformOrigin.Center
                        )
                )
            }
        }
    }
}
