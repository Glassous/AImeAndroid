@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.BuildConfig
import com.glassous.aime.R
import com.glassous.aime.data.GitHubReleaseService
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.ContextLimitSettingDialog
import com.glassous.aime.ui.components.FontSizeSettingDialog
import com.glassous.aime.ui.components.MinimalModeConfigDialog
import com.glassous.aime.ui.components.TransparencySettingDialog
import com.glassous.aime.ui.screens.ModelConfigActivity
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModelFactory
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.ModelConfigViewModel
import com.glassous.aime.ui.viewmodel.ModelConfigViewModelFactory
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModel
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModelFactory
import com.glassous.aime.ui.viewmodel.UpdateCheckState
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
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
                SettingsContent(
                    themeViewModel = themeViewModel,
                    onNavigateToModelConfig = { 
                        val intent = Intent(this@SettingsActivity, ModelConfigActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsContent(
    themeViewModel: ThemeViewModel,
    onNavigateToModelConfig: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()

    // ViewModels
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(application)
    )
    val syncViewModel: DataSyncViewModel = viewModel(
        factory = DataSyncViewModelFactory(application)
    )
    val versionUpdateViewModel: VersionUpdateViewModel = viewModel(
        factory = VersionUpdateViewModelFactory(GitHubReleaseService(), application.updatePreferences)
    )

    // 主题相关状态
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val monochromeTheme by themeViewModel.monochromeTheme.collectAsState()
    val htmlCodeBlockCardEnabled by themeViewModel.htmlCodeBlockCardEnabled.collectAsState()
    val minimalMode by themeViewModel.minimalMode.collectAsState()
    val replyBubbleEnabled by themeViewModel.replyBubbleEnabled.collectAsState()
    val chatFontSize by themeViewModel.chatFontSize.collectAsState()
    val chatUiOverlayAlpha by themeViewModel.chatUiOverlayAlpha.collectAsState()
    val topBarHamburgerAlpha by themeViewModel.topBarHamburgerAlpha.collectAsState()
    val topBarModelTextAlpha by themeViewModel.topBarModelTextAlpha.collectAsState()
    val chatInputInnerAlpha by themeViewModel.chatInputInnerAlpha.collectAsState()
    val hideImportSharedButton by themeViewModel.hideImportSharedButton.collectAsState()
    val themeAdvancedExpanded by themeViewModel.themeAdvancedExpanded.collectAsState()
    val minimalModeConfig by themeViewModel.minimalModeConfig.collectAsState()
    val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)
    val userEmail by authViewModel.email.collectAsState(initial = null)

    // Dialog States
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showTransparencyDialog by remember { mutableStateOf(false) }
    var showMinimalModeConfigDialog by remember { mutableStateOf(false) }
    var showContextLimitDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val contextLimit by application.contextPreferences.maxContextMessages.collectAsState(initial = 5)

    // Version Update States
    val updateCheckState by versionUpdateViewModel.updateCheckState.collectAsState()
    val autoCheckUpdateEnabled by versionUpdateViewModel.autoCheckUpdateEnabled.collectAsState()

    // Data Sync Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { (context as SettingsActivity).finish() }) {
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
            // --- 账号部分 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "用户账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isLoggedIn) ("已登录：" + (userEmail ?: "")) else "未登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoggedIn) {
                        Button(
                            onClick = {
                                val intent = Intent(context, AuthActivity::class.java)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("打开账号管理") }
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(context, AuthActivity::class.java)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("登录 / 注册") }
                    }
                }
            }

            // --- 主题设置 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "主题设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            ThemePreferences.THEME_SYSTEM to "跟随系统",
                            ThemePreferences.THEME_LIGHT to "浅色",
                            ThemePreferences.THEME_DARK to "深色"
                        )

                        themeOptions.forEach { (value, label) ->
                            val isSelected = selectedTheme == value
                            FilterChip(
                                onClick = { themeViewModel.setTheme(value) },
                                label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                                selected = isSelected,
                                modifier = Modifier.weight(1f).selectable(
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 主题样式选择 ---
                    Text(text = "主题样式", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val styleOptions = listOf(
                            false to "Material You",
                            true to "经典"
                        )

                        styleOptions.forEach { (value, label) ->
                            val isSelected = monochromeTheme == value
                            FilterChip(
                                onClick = { themeViewModel.setMonochromeTheme(value) },
                                label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                                selected = isSelected,
                                modifier = Modifier.weight(1f).selectable(
                                    selected = isSelected,
                                    onClick = { themeViewModel.setMonochromeTheme(value) },
                                    role = Role.RadioButton
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- HTML代码块卡片显示开关 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "HTML代码块卡片显示", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "将HTML代码块显示为卡片样式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = htmlCodeBlockCardEnabled,
                            onCheckedChange = { themeViewModel.setHtmlCodeBlockCardEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    // 极简模式开关
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showMinimalModeConfigDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "极简模式", style = MaterialTheme.typography.titleSmall)
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
                    // 回复气泡开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "启用回复气泡", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = replyBubbleEnabled,
                            onCheckedChange = { themeViewModel.setReplyBubbleEnabled(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 分享按钮开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "显示“获取分享对话”按钮", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = !hideImportSharedButton,
                            onCheckedChange = { themeViewModel.setHideImportSharedButton(!it) }
                        )
                    }
                    // 字体设置入口
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showFontSizeDialog = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "修改聊天字体大小", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${chatFontSize.toInt()}sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 透明度设置入口
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTransparencyDialog = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "修改聊天页面组件透明度", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // --- 模型配置 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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

            // --- 上下文限制 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                        modifier = Modifier.fillMaxWidth().clickable { showContextLimitDialog = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "当前限制", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if ((contextLimit) <= 0) "无限" else "${contextLimit}条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showContextLimitDialog = true }) { Text("设置") }
                    }
                }
            }

            // --- 本地同步 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("aime-backup-${System.currentTimeMillis()}.json") },
                            modifier = Modifier.weight(1f)
                        ) { Text("导出") }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("导入") }
                    }
                }
            }

            // --- 版本更新 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "版本信息",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 自动检查更新开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动检查更新",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "启动应用时自动检查新版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCheckUpdateEnabled,
                            onCheckedChange = { versionUpdateViewModel.setAutoCheckUpdateEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = updateCheckState) {
                        is UpdateCheckState.Idle -> {
                            Button(
                                onClick = { versionUpdateViewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "检查更新")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("检查更新")
                            }
                        }

                        is UpdateCheckState.Checking -> {
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("检查中...")
                            }
                        }

                        is UpdateCheckState.Success -> {
                            val updateInfo = state.updateInfo

                            if (updateInfo.hasUpdate) {
                                // 有新版本
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "发现新版本",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${updateInfo.latestVersion}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (updateInfo.releaseNotes?.isNotBlank() == true) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "更新说明:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = updateInfo.releaseNotes.take(150) + if (updateInfo.releaseNotes.length > 150) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (updateInfo.downloadUrl != null) {
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("下载更新")
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                updateInfo.releaseUrl?.let { url ->
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("查看详情")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = { versionUpdateViewModel.resetState() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("关闭")
                                    }
                                }
                            } else {
                                // 已是最新版本
                                Column {
                                    Text(
                                        text = "当前已是最新版本",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = { versionUpdateViewModel.resetState() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("确定")
                                    }
                                }
                            }
                        }

                        is UpdateCheckState.Error -> {
                            Column {
                                Text(
                                    text = "检查更新失败",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { versionUpdateViewModel.checkForUpdates() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("重试")
                                    }

                                    OutlinedButton(
                                        onClick = { versionUpdateViewModel.resetState() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("关闭")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- Dialogs ---

    if (showFontSizeDialog) {
        FontSizeSettingDialog(
            currentFontSize = chatFontSize,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { themeViewModel.setChatFontSize(it) }
        )
    }

    if (showTransparencyDialog) {
        TransparencySettingDialog(
            currentAlpha = chatUiOverlayAlpha,
            onAlphaChange = { themeViewModel.setChatUiOverlayAlpha(it) },
            currentHamburgerAlpha = topBarHamburgerAlpha,
            onHamburgerAlphaChange = { themeViewModel.setTopBarHamburgerAlpha(it) },
            currentModelTextAlpha = topBarModelTextAlpha,
            onModelTextAlphaChange = { themeViewModel.setTopBarModelTextAlpha(it) },
            currentInputInnerAlpha = chatInputInnerAlpha,
            onInputInnerAlphaChange = { themeViewModel.setChatInputInnerAlpha(it) },
            onDismiss = { showTransparencyDialog = false },
            onConfirm = { showTransparencyDialog = false }
        )
    }

    if (showContextLimitDialog) {
        ContextLimitSettingDialog(
            currentLimit = contextLimit,
            onDismiss = { showContextLimitDialog = false },
            onConfirm = {
                scope.launch {
                    application.contextPreferences.setMaxContextMessages(it)
                }
            }
        )
    }

    if (showMinimalModeConfigDialog) {
        MinimalModeConfigDialog(
            config = minimalModeConfig,
            onDismiss = { showMinimalModeConfigDialog = false },
            onConfigChange = { themeViewModel.setMinimalModeConfig(it) },
            fullscreenEnabled = minimalModeFullscreen,
            onFullscreenChange = { themeViewModel.setMinimalModeFullscreen(it) }
        )
    }
}