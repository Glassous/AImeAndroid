@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.glassous.aime.AIMeApplication
import com.glassous.aime.BuildConfig
import com.glassous.aime.R
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.ImagePreviewPopup
import com.glassous.aime.ui.components.S3ConfigDialog
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.S3SyncViewModel
import com.glassous.aime.ui.viewmodel.S3SyncViewModelFactory
import com.glassous.aime.ui.viewmodel.SyncStatus
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.regex.Pattern

class DataManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val monochromeTheme by themeViewModel.monochromeTheme.collectAsState()

            val darkTheme = when (selectedTheme) {
                ThemePreferences.THEME_LIGHT -> false
                ThemePreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            AImeTheme(
                darkTheme = darkTheme,
                isMonochrome = monochromeTheme
            ) {
                DataManagementContent()
            }
        }
    }
}

@Composable
fun CarouselImageItem(
    imageUrl: String,
    s3Endpoint: String,
    onClick: () -> Unit,
    onLoadSuccess: () -> Unit,
    onLoadFailed: (String) -> Unit
) {
    var isThumbnailFailed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var hasSucceeded by remember { mutableStateOf(false) }

    // 直接使用原始 URL，不使用代理
    val finalUrl = remember(imageUrl, isThumbnailFailed) {
        val isHttp = imageUrl.startsWith("http://") || imageUrl.startsWith("https://")
        var targetUrl = imageUrl
        
        // 缩略图处理逻辑
        if (!isThumbnailFailed && isHttp) {
            // 简单判断是否是 OSS/S3 链接，如果是则尝试加参数，否则不加
            // 这里为了保险，可以尝试加上参数，如果失败会回退到原图
            if (targetUrl.contains("?")) "$targetUrl&x-oss-process=image/resize,w_300" 
            else "$targetUrl?x-oss-process=image/resize,w_300"
        } else {
            targetUrl
        }
    }

    // 超时处理：每次 finalUrl 变更（如从缩略图切到原图）时，重新开始 10 秒计时
    LaunchedEffect(finalUrl) {
        if (!hasSucceeded) {
            isLoading = true
            kotlinx.coroutines.delay(10000)
            if (isLoading && !hasSucceeded) {
                // 如果当前 URL 加载超时且仍未成功
                if (!isThumbnailFailed && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                    // 只是缩略图超时，尝试切换到原图
                    isThumbnailFailed = true
                } else {
                    // 原图加载也超时，彻底失败
                    onLoadFailed(imageUrl)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = finalUrl,
            contentDescription = "Extracted Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onState = { state ->
                when (state) {
                    is coil.compose.AsyncImagePainter.State.Loading -> {
                        isLoading = true
                    }
                    is coil.compose.AsyncImagePainter.State.Success -> {
                        isLoading = false
                        hasSucceeded = true
                        onLoadSuccess()
                    }
                    is coil.compose.AsyncImagePainter.State.Error -> {
                        if (!hasSucceeded) {
                            if (!isThumbnailFailed) {
                                // 缩略图加载失败，尝试原图
                                isThumbnailFailed = true
                            } else {
                                // 原图也加载失败
                                isLoading = false
                                onLoadFailed(imageUrl)
                            }
                        }
                    }
                    else -> {}
                }
            }
        )

        if (isLoading && !hasSucceeded) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementContent() {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()

    // S3 Config State
    val s3Enabled by application.s3Preferences.s3Enabled.collectAsState(initial = false)
    val s3Endpoint by application.s3Preferences.s3Endpoint.collectAsState(initial = "")
    val s3AccessKey by application.s3Preferences.s3AccessKey.collectAsState(initial = "")
    val s3SecretKey by application.s3Preferences.s3SecretKey.collectAsState(initial = "")
    val s3BucketName by application.s3Preferences.s3BucketName.collectAsState(initial = "")
    
    // 提取图片逻辑
    val assistantMessages by application.database.chatDao().getAllAssistantMessages().collectAsState(initial = emptyList())
    val extractedImages = remember(assistantMessages, s3Endpoint) {
        val items = mutableListOf<Pair<String, Long>>()
        val imgTagPattern = Pattern.compile("<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val mdImgPattern = Pattern.compile("!\\[.*?\\]\\((.*?)\\)")
        // 匹配 <search>...</search> 或 <search_results>...</search_results> 及其变体
        val searchPattern = Pattern.compile("<search(_results)?>.*?</search(_results)?>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        
        // 用于临时存储已处理过的 URL (String) -> 消息ID (Long)，防止跨消息的重复（如果需要全局去重）
        // 但题目要求的是同一张图片被加载两次（一个成功一个失败），通常是因为 extractedImages 里有重复项
        // 这里使用 Set 来辅助去重
        val processedUrls = mutableSetOf<String>()

        assistantMessages.forEach { msg ->
            // 彻底过滤掉联网搜索部分的内容
            val contentToScan = searchPattern.matcher(msg.content).replaceAll("")
            
            val messageImages = mutableListOf<String>()

            // 1. 从 imagePaths 提取 (通常是生成的图片或附件)
            messageImages.addAll(msg.imagePaths)
            
            // 2. 从处理后的 content 提取 img 标签
            val imgMatcher = imgTagPattern.matcher(contentToScan)
            while (imgMatcher.find()) {
                imgMatcher.group(1)?.let { messageImages.add(it) }
            }
            
            // 3. 从处理后的 content 提取 Markdown 图片
            val mdMatcher = mdImgPattern.matcher(contentToScan)
            while (mdMatcher.find()) {
                mdMatcher.group(1)?.let { messageImages.add(it) }
            }

            // 优化逻辑：如果同一条消息中既有 S3 链接又有本地相对路径，则只保留 S3 链接
            // 判断是否包含 S3 链接
            val hasS3Link = s3Endpoint.isNotBlank() && messageImages.any { it.startsWith(s3Endpoint) }
            
            messageImages.distinct().forEach { url ->
                // 如果该消息已有 S3 链接，则过滤掉所有不带 http/https 前缀的本地路径
                // 或者是 file:// 开头的路径
                val isLocalPath = !url.startsWith("http") && !url.startsWith("https")
                if (hasS3Link && isLocalPath) {
                    // 跳过本地路径，避免重复占位
                } else {
                    // 修复：确保 URL 格式正确，去除可能存在的 Markdown 格式残留或非法字符
                    val cleanUrl = url.trim().replace("url:image_url:", "")
                    if (cleanUrl.isNotBlank() && cleanUrl !in processedUrls) {
                        items.add(cleanUrl to msg.id)
                        processedUrls.add(cleanUrl)
                    }
                }
            }
        }
        items
    }

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    
    // 用于记录加载失败的图片 URL 及对应的消息 ID
    val failedImagesMap = remember { mutableStateMapOf<String, Long>() }
    // 用于记录加载成功的消息 ID (只要消息中有一张图片加载成功，就不能删除该消息)
    val successMessageIds = remember { mutableStateListOf<Long>() }
    
    val displayImages = remember(extractedImages, failedImagesMap.size) {
        extractedImages.filter { it.first !in failedImagesMap.keys }
    }

    // ViewModels
    val syncViewModel: DataSyncViewModel = viewModel(
        factory = DataSyncViewModelFactory(application)
    )
    val s3SyncViewModel: S3SyncViewModel = viewModel(
        factory = S3SyncViewModelFactory(application)
    )
    val s3SyncStatus by s3SyncViewModel.syncStatus.collectAsState()

    val isS3ConfigComplete = remember(s3Endpoint, s3AccessKey, s3SecretKey, s3BucketName) {
        s3Endpoint.isNotBlank() && s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank() && s3BucketName.isNotBlank()
    }
    
    var showS3ConfigDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Data Sync Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            syncViewModel.exportToUri(context, uri) { ok, msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            syncViewModel.importFromUri(context, uri) { ok, msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }
    
    // 失效图片清理逻辑计算
    val idsToDelete = remember(failedImagesMap.size, successMessageIds.size) {
        failedImagesMap.values.distinct().filter { it !in successMessageIds }.toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = {
                    IconButton(onClick = { (context as DataManagementActivity).finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val screenWidth = maxWidth
            val columns = when {
                screenWidth < 600.dp -> 1
                screenWidth < 840.dp -> 2
                else -> 3
            }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalItemSpacing = 16.dp,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- 轮播图 ---
                if (displayImages.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        val carouselState = rememberCarouselState { displayImages.size }
                        Column {
                            Text(
                                text = "最近回复中的图片",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            HorizontalMultiBrowseCarousel(
                                state = carouselState,
                                preferredItemWidth = 160.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) { i: Int ->
                                if (i < displayImages.size) {
                                    val (imageUrl, messageId) = displayImages[i]
                                    CarouselImageItem(
                                        imageUrl = imageUrl,
                                        s3Endpoint = s3Endpoint,
                                        onClick = { previewImageUrl = imageUrl },
                                        onLoadSuccess = {
                                            if (messageId !in successMessageIds) {
                                                successMessageIds.add(messageId)
                                            }
                                        },
                                        onLoadFailed = { failedUrl ->
                                            if (failedUrl !in failedImagesMap) {
                                                failedImagesMap[failedUrl] = messageId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- S3 配置 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "S3 对象存储",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "配置 S3 兼容的对象存储，启用后附件将上传至云端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Row 1: Enable Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "启用 S3 上传", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (!isS3ConfigComplete) "请先完成配置" else if (s3Enabled) "已启用" else "已禁用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (!isS3ConfigComplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = s3Enabled && isS3ConfigComplete,
                                    onCheckedChange = { 
                                        scope.launch { 
                                            application.s3Preferences.setS3Enabled(it) 
                                        }
                                    },
                                    enabled = isS3ConfigComplete
                                )
                            }
                            
                            // S3 Sync Status and Button
                            if (isS3ConfigComplete) {
                                Spacer(modifier = Modifier.height(8.dp))
                                when (val status = s3SyncStatus) {
                                    is SyncStatus.Idle -> {
                                        Button(
                                            onClick = { s3SyncViewModel.sync() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = isS3ConfigComplete
                                        ) {
                                            Icon(Icons.Filled.Sync, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("立即同步")
                                        }
                                    }
                                    is SyncStatus.Syncing -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(status.message, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    is SyncStatus.Success -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text("同步成功", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.width(8.dp))
                                            TextButton(onClick = { s3SyncViewModel.clearStatus() }) {
                                                Text("确定")
                                            }
                                        }
                                    }
                                    is SyncStatus.Error -> {
                                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("同步失败: ${status.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                            Button(onClick = { s3SyncViewModel.sync() }, modifier = Modifier.fillMaxWidth()) {
                                                Text("重试")
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2: Config Button
                            OutlinedButton(
                                onClick = { showS3ConfigDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "配置参数")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("配置参数")
                            }
                        }
                    }
                }

                // --- 数据备份 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "数据备份",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "导入/导出到本地文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { exportLauncher.launch("aime-backup-${System.currentTimeMillis()}.zip") },
                                    modifier = Modifier.weight(1f)
                                ) { Text("导出") }

                                OutlinedButton(
                                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/json")) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("导入") }
                            }
                        }
                    }
                }

                // --- 失效图片清理 ---
                if (failedImagesMap.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "失效图片清理",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "检测到 ${failedImagesMap.size} 张图片加载失败。其中 ${idsToDelete.size} 条消息可清理（已自动排除包含有效图片的消息）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (idsToDelete.isNotEmpty()) {
                                                application.database.chatDao().deleteMessagesByIds(idsToDelete)
                                                // 仅清理已删除消息关联的失败记录，保留那些被保护的失败记录（避免 UI 闪烁）
                                                val keysToRemove = failedImagesMap.filterValues { it in idsToDelete }.keys
                                                keysToRemove.forEach { failedImagesMap.remove(it) }
                                                
                                                snackbarHostState.showSnackbar("已清理 ${idsToDelete.size} 条失效消息")
                                            } else {
                                                snackbarHostState.showSnackbar("无可清理的消息（所有失效图片均属于包含有效图片的消息）")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("立即清理失效记录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showS3ConfigDialog) {
        S3ConfigDialog(
            s3Preferences = application.s3Preferences,
            onDismiss = { showS3ConfigDialog = false }
        )
    }

    if (previewImageUrl != null) {
        ImagePreviewPopup(
            imagePath = previewImageUrl!!,
            onDismissRequest = { previewImageUrl = null }
        )
    }
}
