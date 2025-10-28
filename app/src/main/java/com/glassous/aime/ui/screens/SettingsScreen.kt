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
import com.glassous.aime.R
import com.glassous.aime.ui.theme.ThemeViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import com.glassous.aime.ui.viewmodel.CloudSyncViewModelFactory
import com.glassous.aime.data.preferences.OssPreferences
import kotlinx.coroutines.launch
import com.glassous.aime.data.preferences.AutoSyncPreferences

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import com.glassous.aime.ui.components.FontSizeSettingDialog
import com.glassous.aime.ui.components.MinimalModeConfigDialog
import com.glassous.aime.ui.components.TransparencySettingDialog
import com.glassous.aime.data.model.MinimalModeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToOssConfig: () -> Unit,
    themeViewModel: ThemeViewModel
) {
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val minimalMode by themeViewModel.minimalMode.collectAsState()
    // 新增：回复气泡开关状态
    val replyBubbleEnabled by themeViewModel.replyBubbleEnabled.collectAsState()
    val chatFontSize by themeViewModel.chatFontSize.collectAsState()
    val chatUiOverlayAlpha by themeViewModel.chatUiOverlayAlpha.collectAsState()
    
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showTransparencyDialog by remember { mutableStateOf(false) }
    var showMinimalModeConfigDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dataSyncViewModel: DataSyncViewModel = viewModel(factory = DataSyncViewModelFactory(context.applicationContext as android.app.Application))
    val cloudSyncViewModel: CloudSyncViewModel = viewModel(factory = CloudSyncViewModelFactory(context.applicationContext as android.app.Application))
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val autoSyncPreferences = remember { AutoSyncPreferences(context) }
    var autoSyncEnabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        autoSyncPreferences.autoSyncEnabled.collect { autoSyncEnabled = it }
    }

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

    val cloudDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            cloudSyncViewModel.downloadToUri(uri) { ok, msg ->
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


                    // 新增：启用回复气泡开关
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
                    
                    // 新增：聊天字体大小设置
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

                    // 新增：聊天页面顶部栏透明度设置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTransparencyDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "修改聊天页面顶部栏透明度",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${(chatUiOverlayAlpha * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

            // 云端同步卡片
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
                        text = "云端同步",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "阿里云 OSS 配置与云端上传/下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (autoSyncEnabled != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("自动同步")
                            Switch(
                                checked = autoSyncEnabled == true,
                                onCheckedChange = { enabled ->
                                    autoSyncEnabled = enabled
                                    scope.launch { autoSyncPreferences.setAutoSyncEnabled(enabled) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 阿里云OSS配置独占一行
                    Button(
                        onClick = onNavigateToOssConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("配置阿里云 OSS")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 从云端下载和上传到云端两个按钮占一行
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                cloudSyncViewModel.downloadAndImport { ok, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("从云端导入")
                        }
                        Button(
                            onClick = {
                                cloudSyncViewModel.uploadBackup { ok, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("上传到云端")
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
            onDismiss = { showTransparencyDialog = false },
            onConfirm = { newAlpha ->
                themeViewModel.setChatUiOverlayAlpha(newAlpha)
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