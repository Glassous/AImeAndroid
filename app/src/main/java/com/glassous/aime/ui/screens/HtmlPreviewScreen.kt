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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

import android.webkit.JavascriptInterface
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

data class ConsoleLog(
    val type: LogType,
    val message: String,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
)

enum class LogType {
    LOG, ERROR, WARN, INFO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewScreen(
    htmlCode: String,
    onNavigateBack: () -> Unit,
    isSourceMode: Boolean,
    isRestricted: Boolean = false, // 新增参数：受限模式（如网页分析）
    previewUrl: String? = null // 新增参数：如果存在则直接加载该URL
) {
    HtmlPreviewContent(
        htmlCode = htmlCode,
        onClose = onNavigateBack,
        initialIsSourceMode = isSourceMode,
        isRestricted = isRestricted,
        previewUrl = previewUrl
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewContent(
    htmlCode: String,
    onClose: () -> Unit,
    initialIsSourceMode: Boolean,
    isRestricted: Boolean = false,
    previewUrl: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isLoading by remember { mutableStateOf(true) }
    // 使用 LaunchedEffect 监听 initialIsSourceMode 的变化，确保外部模式切换能生效
    var localIsSourceMode by remember { mutableStateOf(initialIsSourceMode) }
    LaunchedEffect(initialIsSourceMode, htmlCode) {
        localIsSourceMode = initialIsSourceMode
    }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(0f) }
    
    // 控制台相关状态
    var showConsoleBottomSheet by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<ConsoleLog>() }
    
    // JavaScript接口
    class ConsoleInterface {
        @JavascriptInterface
        fun log(message: String) {
            consoleLogs.add(ConsoleLog(LogType.LOG, message))
        }

        @JavascriptInterface
        fun error(message: String) {
            consoleLogs.add(ConsoleLog(LogType.ERROR, message))
            // 当发生错误时自动弹出控制台（可选，暂时不自动弹出以免打扰）
            // showConsoleBottomSheet = true 
        }

        @JavascriptInterface
        fun warn(message: String) {
            consoleLogs.add(ConsoleLog(LogType.WARN, message))
        }

        @JavascriptInterface
        fun info(message: String) {
            consoleLogs.add(ConsoleLog(LogType.INFO, message))
        }
    }
    
    val consoleInterface = remember { ConsoleInterface() }

    // 复制HTML代码到剪贴板
    fun copyHtmlCode() {
        clipboardManager.setText(AnnotatedString(htmlCode))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    // 复制控制台日志
    fun copyConsoleLogs() {
        val logsText = consoleLogs.joinToString("\n") { "[${it.timestamp}] [${it.type}] ${it.message}" }
        clipboardManager.setText(AnnotatedString(logsText))
        Toast.makeText(context, "控制台日志已复制", Toast.LENGTH_SHORT).show()
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
        consoleLogs.clear() // 刷新时清空日志
        webViewRef?.reload()
        sourceWebViewRef?.reload()
    }

    // 在浏览器打开
    fun openInBrowser() {
        try {
            val fileName = "preview_${System.currentTimeMillis()}.html"
            val file = File(context.cacheDir, fileName)
            file.writeText(htmlCode)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/html")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    // 注入控制台捕获脚本
    val consoleInjectionScript = """
        <script>
        (function() {
            var oldLog = console.log;
            var oldError = console.error;
            var oldWarn = console.warn;
            var oldInfo = console.info;

            function formatArgs(args) {
                return Array.from(args).map(arg => {
                    if (typeof arg === 'object') {
                        try {
                            return JSON.stringify(arg);
                        } catch(e) {
                            return String(arg);
                        }
                    }
                    return String(arg);
                }).join(' ');
            }

            console.log = function() {
                oldLog.apply(console, arguments);
                if (window.AndroidConsole) {
                    window.AndroidConsole.log(formatArgs(arguments));
                }
            };
            console.error = function() {
                oldError.apply(console, arguments);
                if (window.AndroidConsole) {
                    window.AndroidConsole.error(formatArgs(arguments));
                }
            };
            console.warn = function() {
                oldWarn.apply(console, arguments);
                if (window.AndroidConsole) {
                    window.AndroidConsole.warn(formatArgs(arguments));
                }
            };
            console.info = function() {
                oldInfo.apply(console, arguments);
                if (window.AndroidConsole) {
                    window.AndroidConsole.info(formatArgs(arguments));
                }
            };
            
            window.onerror = function(message, source, lineno, colno, error) {
                if (window.AndroidConsole) {
                    window.AndroidConsole.error("Uncaught Error: " + message + " at " + source + ":" + lineno + ":" + colno);
                }
            };
            
            window.addEventListener('unhandledrejection', function(event) {
                if (window.AndroidConsole) {
                    window.AndroidConsole.error("Unhandled Rejection: " + event.reason);
                }
            });
        })();
        </script>
    """.trimIndent()

    // 加载HTML内容
    fun loadHtmlContent() {
        if (previewUrl != null && !localIsSourceMode) {
            webViewRef?.loadUrl(previewUrl)
            return
        }

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

        // Vue 检测逻辑
        val isVue = !hasHtml && !isJsx && (
            htmlCode.contains("<template>") ||
            (htmlCode.contains("export default") && htmlCode.contains("defineComponent")) ||
            htmlCode.contains("<script setup>")
        )

        var content = if (hasHtml) {
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
        } else if (isVue) {
            // Vue 预览模板
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://cdn.jsdelivr.net/npm/vue@3/dist/vue.global.prod.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/vue3-sfc-loader/dist/vue3-sfc-loader.js"></script>
                <style>
                    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                    #app { padding: 16px; min-height: 100vh; box-sizing: border-box; }
                </style>
            </head>
            <body>
                <div id="app"></div>
                
                <!-- 隐藏的 Script 标签存储源码，避免转义问题 -->
                <script type="text/plain" id="vue-content">
${htmlCode.replace("</script>", "<\\/script>")}
                </script>

                <script>
                    const options = {
                        moduleCache: {
                            vue: Vue
                        },
                        async getFile(url) {
                            if (url === '/component.vue') {
                                return document.getElementById('vue-content').textContent;
                            }
                        },
                        addStyle(textContent) {
                            const style = document.createElement('style');
                            style.textContent = textContent;
                            const ref = document.head.getElementsByTagName('style')[0] || null;
                            document.head.insertBefore(style, ref);
                        },
                    }
                    
                    const { loadModule } = window['vue3-sfc-loader'];
                    
                    const app = Vue.createApp(Vue.defineAsyncComponent(() => loadModule('/component.vue', options)));
                    app.mount('#app');
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
                    body{margin:0;padding:8px;white-space:pre-wrap;font-family:sans-serif}
                </style>
            </head>
            <body>
            $htmlCode
            </body>
            </html>
            """.trimIndent()
        }
        
        // 注入控制台脚本到 <head> 中
        if (content.contains("<head>", ignoreCase = true)) {
            content = content.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$consoleInjectionScript")
        } else {
            content = "$consoleInjectionScript\n$content"
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
                    IconButton(onClick = onClose) {
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

                    // 控制台按钮
                    if (!localIsSourceMode && !isRestricted) {
                        IconButton(onClick = { showConsoleBottomSheet = true }) {
                            Icon(Icons.Filled.BugReport, contentDescription = "控制台")
                        }
                    }

                    // 刷新按钮
                    if (!isRestricted) {
                        IconButton(onClick = { refreshWebView() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                    if (!isRestricted) {
                        IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                            Icon(Icons.Filled.RotateRight, contentDescription = "旋转")
                        }
                    }

                    // 更多选项
                    if (!isRestricted) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                            }
                            DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 在浏览器打开
                            DropdownMenuItem(
                                text = { Text("在浏览器打开") },
                                onClick = {
                                    openInBrowser()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) }
                            )
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

                            addJavascriptInterface(consoleInterface, "AndroidConsole")

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

    if (showConsoleBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 500.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左上角复制按钮
                    IconButton(onClick = { copyConsoleLogs() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制日志")
                    }
                    Text("控制台", style = MaterialTheme.typography.titleLarge)
                    // 右侧清空按钮
                    IconButton(onClick = { consoleLogs.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空日志")
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(consoleLogs) { log ->
                        val color = when (log.type) {
                            LogType.ERROR -> MaterialTheme.colorScheme.error
                            LogType.WARN -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = "[${log.timestamp}] [${log.type}] ${log.message}",
                            color = color,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    if (consoleLogs.isEmpty()) {
                        item {
                            Text(
                                "暂无日志",
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
