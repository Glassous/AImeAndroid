package com.glassous.aime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.glassous.aime.R
import com.glassous.aime.ui.theme.ThemeViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
 
import kotlinx.coroutines.launch
 
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import com.glassous.aime.ui.components.FontSizeSettingDialog
import com.glassous.aime.ui.components.MinimalModeConfigDialog
import com.glassous.aime.ui.components.TransparencySettingDialog
import com.glassous.aime.ui.components.ContextLimitSettingDialog
import com.glassous.aime.data.model.MinimalModeConfig
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToUserSettings: () -> Unit,
    themeViewModel: ThemeViewModel
) {
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val minimalMode by themeViewModel.minimalMode.collectAsState()
    // 新增：回复气泡开关状态
    val replyBubbleEnabled by themeViewModel.replyBubbleEnabled.collectAsState()
    val chatFontSize by themeViewModel.chatFontSize.collectAsState()
    val chatUiOverlayAlpha by themeViewModel.chatUiOverlayAlpha.collectAsState()
    val topBarHamburgerAlpha by themeViewModel.topBarHamburgerAlpha.collectAsState()
    val topBarModelTextAlpha by themeViewModel.topBarModelTextAlpha.collectAsState()
    val chatInputInnerAlpha by themeViewModel.chatInputInnerAlpha.collectAsState()
    val hideImportSharedButton by themeViewModel.hideImportSharedButton.collectAsState()
    val themeAdvancedExpanded by themeViewModel.themeAdvancedExpanded.collectAsState()
    
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showTransparencyDialog by remember { mutableStateOf(false) }
    var showMinimalModeConfigDialog by remember { mutableStateOf(false) }
    var showContextLimitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as AIMeApplication
    val contextLimit by app.contextPreferences.maxContextMessages.collectAsState(initial = 5)
    val dataSyncViewModel: DataSyncViewModel = viewModel(factory = DataSyncViewModelFactory(context.applicationContext as android.app.Application))
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            dataSyncViewModel.exportToUri(context, uri) { ok, msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            dataSyncViewModel.importFromUri(context, uri) { ok, msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    



    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主题设置卡片（移到上方）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "主题设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            "system" to "跟随系统",
                            "light" to "浅色",
                            "dark" to "深色"
                        )
                        
                        themeOptions.forEach { (value, label) ->
                            val isSelected = selectedTheme == value
                            
                            FilterChip(
                                onClick = { themeViewModel.setTheme(value) },
                                label = { 
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium
                                    ) 
                                },
                                selected = isSelected,
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { themeViewModel.setTheme(value) },
                                        role = Role.RadioButton
                                    ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { themeViewModel.setThemeAdvancedExpanded(!themeAdvancedExpanded) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "更多主题选项",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Icon(
                            imageVector = if (themeAdvancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(
                        visible = themeAdvancedExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMinimalModeConfigDialog = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "极简模式",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "点击配置隐藏的界面元素",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = minimalMode,
                                    onCheckedChange = { themeViewModel.setMinimalMode(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "启用回复气泡",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Switch(
                                    checked = replyBubbleEnabled,
                                    onCheckedChange = { themeViewModel.setReplyBubbleEnabled(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "是否显示“获取分享对话”按钮",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Switch(
                                    checked = hideImportSharedButton,
                                    onCheckedChange = { themeViewModel.setHideImportSharedButton(it) }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFontSizeDialog = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "修改聊天字体大小",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${chatFontSize.toInt()}sp",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTransparencyDialog = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "修改聊天页面组件透明度",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }

                }
            }
            // 模型配置卡片（移到下方）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "模型配置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "配置 OpenAI 兼容的 API 服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onNavigateToModelConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "模型设置")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("模型设置")
                    }
                }
            }

            // 用户设置卡片（位于模型配置与最大上下文限制之间）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "用户设置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "编辑昵称、城市、语言、年龄等个人资料",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onNavigateToUserSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "用户设置")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开用户设置")
                    }
                }
            }

            // 最大上下文限制卡片（位于模型配置下、云端同步上方）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "最大上下文限制",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "同一对话仅向AI发送最近N条消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showContextLimitDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "当前限制",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if ((contextLimit) <= 0) "无限" else "${contextLimit}条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showContextLimitDialog = true }) {
                            Text("设置")
                        }
                    }
                }
            }

            

            // 本地同步卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "本地同步",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "导入/导出到本地文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("aime-backup-${System.currentTimeMillis()}.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出到本地")
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("从本地导入")
                        }
                    }
                }
            }

            // 应用信息卡片（位于页面最底部）
            val uriHandler = LocalUriHandler.current
            var checkingUpdate by remember { mutableStateOf(false) }
            var latestTag by remember { mutableStateOf<String?>(null) }
            var latestApkUrl by remember { mutableStateOf<String?>(null) }
            var updateStatus by remember { mutableStateOf<String?>(null) }

            fun getCurrentVersionName(ctx: android.content.Context): String {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ctx.packageManager.getPackageInfo(
                            ctx.packageName,
                            PackageManager.PackageInfoFlags.of(0)
                        ).versionName ?: "unknown"
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
                    }
                } catch (e: Exception) {
                    "unknown"
                }
            }

            val appVersionName = remember { getCurrentVersionName(context) }

            fun normalizeVersion(v: String): List<Int> {
                val clean = v.trim().removePrefix("v")
                val parts = clean.split('.')
                return listOf(
                    parts.getOrNull(0)?.toIntOrNull() ?: 0,
                    parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    parts.getOrNull(2)?.toIntOrNull() ?: 0
                )
            }

            fun isNewerVersion(latest: String, current: String): Boolean {
                val l = normalizeVersion(latest)
                val c = normalizeVersion(current)
                return when {
                    l[0] != c[0] -> l[0] > c[0]
                    l[1] != c[1] -> l[1] > c[1]
                    else -> l[2] > c[2]
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "应用信息",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 应用名称
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "应用名称", style = MaterialTheme.typography.titleSmall)
                        Text(text = "AIme", style = MaterialTheme.typography.bodyMedium)
                    }

                    // 版本号（通过 BuildConfig 读取）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "版本号", style = MaterialTheme.typography.titleSmall)
                        Text(text = "v${appVersionName}", style = MaterialTheme.typography.bodyMedium)
                    }

                    // Github 仓库链接
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Github 仓库", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = {
                            uriHandler.openUri("https://github.com/Glassous/AImeAndroid")
                        }) {
                            Text("打开仓库")
                        }
                    }

                    // 版本更新检测
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "版本更新检测",
                            style = MaterialTheme.typography.titleSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (checkingUpdate) return@Button
                                    checkingUpdate = true
                                    updateStatus = null
                                    latestApkUrl = null
                                    latestTag = null
                                    scope.launch {
                                        val apiUrl = "https://api.github.com/repos/Glassous/AImeAndroid/releases/latest"
                                        try {
                                            val jsonStr = withContext(Dispatchers.IO) {
                                                URL(apiUrl).readText()
                                            }
                                            val obj = JSONObject(jsonStr)
                                            val tag = obj.optString("tag_name")
                                            latestTag = tag

                                            // 寻找符合命名规则的APK资源：AIme-vx.x.z.apk
                                            val assets = obj.optJSONArray("assets")
                                            var apkUrl: String? = null
                                            if (assets != null) {
                                                for (i in 0 until assets.length()) {
                                                    val a = assets.optJSONObject(i)
                                                    val name = a?.optString("name") ?: ""
                                                    val url = a?.optString("browser_download_url")
                                                    if (name.endsWith(".apk")) {
                                                        // 优先匹配 AIme-v<tag>.apk
                                                        if (tag.isNotBlank() && name == "AIme-${tag}.apk") {
                                                            apkUrl = url
                                                            break
                                                        } else if (apkUrl == null) {
                                                            apkUrl = url // 兜底取第一个apk
                                                        }
                                                    }
                                                }
                                            }
                                            latestApkUrl = apkUrl

                                            val current = appVersionName
                                            val newer = tag.isNotBlank() && isNewerVersion(tag, current)
                                            updateStatus = if (newer) {
                                                "发现新版本 ${tag}"
                                            } else {
                                                "当前已是最新版本"
                                            }
                                        } catch (e: Exception) {
                                            updateStatus = "检查失败：" + (e.message ?: "未知错误")
                                        } finally {
                                            checkingUpdate = false
                                        }
                                    }
                                },
                                enabled = !checkingUpdate
                            ) {
                                Text(if (checkingUpdate) "检测中..." else "检查更新")
                            }

                            // 打开 Release 页面（若已获取到 tag 则指向对应版本）
                            OutlinedButton(onClick = {
                                val url = latestTag?.let { "https://github.com/Glassous/AImeAndroid/releases/tag/${it}" }
                                    ?: "https://github.com/Glassous/AImeAndroid/releases/tag/v2.1.0"
                                uriHandler.openUri(url)
                            }) {
                                Text("打开Release")
                            }

                            // 下载更新（当检测到APK时出现）
                            if (!checkingUpdate && latestApkUrl != null) {
                                Button(onClick = { uriHandler.openUri(latestApkUrl!!) }) {
                                    Text("下载更新")
                                }
                            }
                        }

                        if (updateStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateStatus!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 字体大小设置弹窗
    if (showFontSizeDialog) {
        FontSizeSettingDialog(
            currentFontSize = chatFontSize,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { newSize ->
                themeViewModel.setChatFontSize(newSize)
            }
        )
    }

    // 透明度设置弹窗
    if (showTransparencyDialog) {
        TransparencySettingDialog(
            currentAlpha = chatUiOverlayAlpha,
            onAlphaChange = { newAlpha ->
                themeViewModel.setChatUiOverlayAlpha(newAlpha)
            },
            currentHamburgerAlpha = topBarHamburgerAlpha,
            onHamburgerAlphaChange = { newAlpha ->
                themeViewModel.setTopBarHamburgerAlpha(newAlpha)
            },
            currentModelTextAlpha = topBarModelTextAlpha,
            onModelTextAlphaChange = { newAlpha ->
                themeViewModel.setTopBarModelTextAlpha(newAlpha)
            },
            currentInputInnerAlpha = chatInputInnerAlpha,
            onInputInnerAlphaChange = { newAlpha ->
                themeViewModel.setChatInputInnerAlpha(newAlpha)
            },
            onDismiss = { showTransparencyDialog = false },
            onConfirm = { showTransparencyDialog = false }
        )
    }
    
    // 最大上下文限制设置弹窗
    if (showContextLimitDialog) {
        ContextLimitSettingDialog(
            currentLimit = contextLimit,
            onDismiss = { showContextLimitDialog = false },
            onConfirm = { newLimit ->
                scope.launch {
                    app.contextPreferences.setMaxContextMessages(newLimit)
                }
            }
        )
    }

    // 极简模式配置弹窗
    if (showMinimalModeConfigDialog) {
        val minimalModeConfig by themeViewModel.minimalModeConfig.collectAsState()
        val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()
        MinimalModeConfigDialog(
            config = minimalModeConfig,
            onDismiss = { showMinimalModeConfigDialog = false },
            onConfigChange = { newConfig ->
                themeViewModel.setMinimalModeConfig(newConfig)
            },
            fullscreenEnabled = minimalModeFullscreen,
            onFullscreenChange = { themeViewModel.setMinimalModeFullscreen(it) }
        )
    }
}
