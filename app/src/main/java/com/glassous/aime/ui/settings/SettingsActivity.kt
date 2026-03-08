@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.BuildConfig
import com.glassous.aime.R
import com.glassous.aime.data.GitHubReleaseService
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.ImportSharedConversationDialog
import com.glassous.aime.ui.components.PrivacyPolicyDialog
import com.glassous.aime.ui.components.S3ConfigDialog
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.S3SyncViewModel
import com.glassous.aime.ui.viewmodel.S3SyncViewModelFactory
import com.glassous.aime.ui.viewmodel.SyncStatus
import com.glassous.aime.ui.viewmodel.UpdateCheckState
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModel
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModelFactory
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
                SettingsContent(themeViewModel = themeViewModel)
            }
        }
    }
}

@Composable
fun SettingsContent(
    themeViewModel: ThemeViewModel
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()

    // ViewModels
    val syncViewModel: DataSyncViewModel = viewModel(
        factory = DataSyncViewModelFactory(application)
    )
    val s3SyncViewModel: S3SyncViewModel = viewModel(
        factory = S3SyncViewModelFactory(application)
    )
    val s3SyncStatus by s3SyncViewModel.syncStatus.collectAsState()
    val versionUpdateViewModel: VersionUpdateViewModel = viewModel(
        factory = VersionUpdateViewModelFactory(GitHubReleaseService(), application.updatePreferences)
    )

    // Only needed for hiding the import button
    val hideImportSharedButton by themeViewModel.hideImportSharedButton.collectAsState()

    // Dialog States
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showImportSharedDialog by remember { mutableStateOf(false) }
    
    // S3 Config State
    val s3Enabled by application.s3Preferences.s3Enabled.collectAsState(initial = false)
    val s3Endpoint by application.s3Preferences.s3Endpoint.collectAsState(initial = "")
    val s3AccessKey by application.s3Preferences.s3AccessKey.collectAsState(initial = "")
    val s3SecretKey by application.s3Preferences.s3SecretKey.collectAsState(initial = "")
    val s3BucketName by application.s3Preferences.s3BucketName.collectAsState(initial = "")
    
    val isS3ConfigComplete = remember(s3Endpoint, s3AccessKey, s3SecretKey, s3BucketName) {
        s3Endpoint.isNotBlank() && s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank() && s3BucketName.isNotBlank()
    }
    
    var showS3ConfigDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Version Update States
    val updateCheckState by versionUpdateViewModel.updateCheckState.collectAsState()
    val autoCheckUpdateEnabled by versionUpdateViewModel.autoCheckUpdateEnabled.collectAsState()

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
                // --- 获取分享对话 ---
                if (!hideImportSharedButton) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = "获取分享对话",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "从云端导入他人分享的对话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Button(
                                    onClick = { showImportSharedDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = "获取")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("获取分享对话")
                                }
                            }
                        }
                    }
                }

                // --- 主题设置入口 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "主题设置",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "界面主题、样式、字体大小等",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(context, ThemeSettingsActivity::class.java))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Palette, contentDescription = "主题设置")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("主题设置")
                            }
                        }
                    }
                }

                // --- 配置设置入口 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "配置设置",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "模型、提示词、工具、上下文等",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(context, ConfigSettingsActivity::class.java))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.SettingsApplications, contentDescription = "配置设置")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("配置设置")
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

                // --- 版本更新 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
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
                }

                // --- 关于 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "关于",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPrivacyPolicyDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "隐私政策",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // --- Dialogs ---

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false }
        )
    }

    if (showImportSharedDialog) {
        ImportSharedConversationDialog(
            onDismiss = { showImportSharedDialog = false },
            onImport = { input, callback ->
                syncViewModel.importSharedConversation(input, callback)
            }
        )
    }

    if (showS3ConfigDialog) {
        S3ConfigDialog(
            s3Preferences = application.s3Preferences,
            onDismiss = { showS3ConfigDialog = false }
        )
    }
}
