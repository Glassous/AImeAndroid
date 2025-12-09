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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var isPcMode by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

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

    // 监听 PC 模式变化
    LaunchedEffect(isPcMode) {
        if (!localIsSourceMode) {
            webViewRef?.settings?.apply {
                if (isPcMode) {
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    useWideViewPort = true
                    loadWithOverviewMode = true
                } else {
                    userAgentString = null
                    useWideViewPort = false
                    loadWithOverviewMode = false
                }
            }
            loadHtmlContent()
        }
    }

    // HTML 高亮逻辑
    val highlightedCode = remember(htmlCode) {
        buildAnnotatedString {
            val rawCode = htmlCode
            append(rawCode)
            
            // 简单的正则高亮
            val tagPattern = Regex("</?[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
            val attrPattern = Regex("\\s[a-zA-Z0-9-]+=", RegexOption.IGNORE_CASE)
            val stringPattern = Regex("\"[^\"]*\"|'[^']*'")
            val commentPattern = Regex("<!--[\\s\\S]*?-->")

            // 注释 (灰色)
            commentPattern.findAll(rawCode).forEach {
                addStyle(SpanStyle(color = Color(0xFF7F848E)), it.range.first, it.range.last + 1)
            }

            // 标签 (粉红色)
            tagPattern.findAll(rawCode).forEach {
                // 确保不在注释内 (简单判断，若需精确需完整解析)
                // 这里简单覆盖，注意顺序
                addStyle(SpanStyle(color = Color(0xFFE06C75)), it.range.first, it.range.last + 1)
            }
            
            // 标签结束符 > (粉红色)
            Regex(">").findAll(rawCode).forEach {
                addStyle(SpanStyle(color = Color(0xFFE06C75)), it.range.first, it.range.last + 1)
            }

            // 属性名 (橙色)
            attrPattern.findAll(rawCode).forEach {
                addStyle(SpanStyle(color = Color(0xFFD19A66)), it.range.first, it.range.last + 1)
            }

            // 字符串/属性值 (绿色)
            stringPattern.findAll(rawCode).forEach {
                addStyle(SpanStyle(color = Color(0xFF98C379)), it.range.first, it.range.last + 1)
            }
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
                    // 统计信息按钮 (仅在源码模式显示)
                    if (localIsSourceMode) {
                        IconButton(onClick = { showStatsDialog = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "统计信息")
                        }
                    }
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
                    // PC模式按钮
                    IconButton(
                        onClick = { isPcMode = !isPcMode },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPcMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Filled.Computer,
                            contentDescription = "PC模式",
                            tint = if (isPcMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                            text = highlightedCode,
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

                                    // 初始化 PC 模式设置
                                    if (isPcMode) {
                                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                    }

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