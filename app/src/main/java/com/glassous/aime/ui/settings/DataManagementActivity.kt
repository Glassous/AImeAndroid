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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.R
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.S3ConfigDialog
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.S3SyncViewModel
import com.glassous.aime.ui.viewmodel.S3SyncViewModelFactory
import com.glassous.aime.ui.viewmodel.SyncStatus
import kotlinx.coroutines.launch

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
fun DataManagementContent() {
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
            }
        }
    }

    if (showS3ConfigDialog) {
        S3ConfigDialog(
            s3Preferences = application.s3Preferences,
            onDismiss = { showS3ConfigDialog = false }
        )
    }
}
