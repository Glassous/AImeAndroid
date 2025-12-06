package com.glassous.aime.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.BuildConfig
import com.glassous.aime.R
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.ContextLimitSettingDialog
import com.glassous.aime.ui.components.FontSizeSettingDialog
import com.glassous.aime.ui.components.MinimalModeConfigDialog
import com.glassous.aime.ui.components.TransparencySettingDialog
import com.glassous.aime.ui.settings.AuthActivity
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModelFactory
import com.glassous.aime.ui.viewmodel.DataSyncViewModel
import com.glassous.aime.ui.viewmodel.DataSyncViewModelFactory
import com.glassous.aime.ui.viewmodel.ModelConfigViewModel
import com.glassous.aime.ui.viewmodel.ModelConfigViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    themeViewModel: ThemeViewModel
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
    // 修复 1: 传入 repository 而不是 application
    val modelViewModel: ModelConfigViewModel = viewModel(
        factory = ModelConfigViewModelFactory(application.modelConfigRepository)
    )

    // States
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
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
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSecurityQuestionDialog by remember { mutableStateOf(false) } // 新增：安全问题弹窗

    val snackbarHostState = remember { SnackbarHostState() }
    val contextLimit by application.contextPreferences.maxContextMessages.collectAsState(initial = 5)

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

                        // 新增：安全设置入口
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showSecurityQuestionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("设置/修改安全问题")
                        }
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

            // --- 云同步 ---
            if (isLoggedIn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "云同步",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "同步聊天记录和模型配置到云端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 手动同步按钮
                        Button(
                            onClick = {
                                authViewModel.manualSync { success, message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("立即同步")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "同步设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 这里可以添加同步设置开关，但需要先实现SyncPreferences的Flow
                        // 暂时显示一个提示
                        Text(
                            text = "同步设置可在账号管理页面配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { themeViewModel.setThemeAdvancedExpanded(!themeAdvancedExpanded) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "更多主题选项", style = MaterialTheme.typography.titleSmall)
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
                                    checked = hideImportSharedButton,
                                    onCheckedChange = { themeViewModel.setHideImportSharedButton(it) }
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

            // --- 清除数据 ---
            OutlinedButton(
                onClick = { showClearDataDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除本地数据")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- Dialogs ---

    // 修复 2: 补充 FontSizeSettingDialog 缺失参数
    if (showFontSizeDialog) {
        FontSizeSettingDialog(
            currentFontSize = chatFontSize,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { newSize ->
                themeViewModel.setChatFontSize(newSize)
            }
        )
    }

    // 修复 3: 补充 TransparencySettingDialog 缺失参数
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

    // 修复 4: 补充 ContextLimitSettingDialog 缺失参数
    if (showContextLimitDialog) {
        ContextLimitSettingDialog(
            currentLimit = contextLimit,
            onDismiss = { showContextLimitDialog = false },
            onConfirm = { newLimit ->
                scope.launch {
                    application.contextPreferences.setMaxContextMessages(newLimit)
                }
            }
        )
    }

    // 修复 5: 补充 MinimalModeConfigDialog 缺失参数
    if (showMinimalModeConfigDialog) {
        MinimalModeConfigDialog(
            config = minimalModeConfig,
            onDismiss = { showMinimalModeConfigDialog = false },
            onConfigChange = { themeViewModel.setMinimalModeConfig(it) },
            fullscreenEnabled = minimalModeFullscreen,
            onFullscreenChange = { themeViewModel.setMinimalModeFullscreen(it) }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("清除数据") },
            text = { Text("确定要删除所有本地聊天记录和配置吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.clearLocalData { ok, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("取消") }
            }
        )
    }

    // 安全问题设置弹窗
    if (showSecurityQuestionDialog) {
        SecurityQuestionDialog(
            onDismiss = { showSecurityQuestionDialog = false },
            onConfirm = { pwd, q, a ->
                // 修复 6: 调用 AuthViewModel 中新增的方法
                authViewModel.updateSecurityQuestion(pwd, q, a) { ok, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                        if (ok) showSecurityQuestionDialog = false
                    }
                }
            }
        )
    }
}

// 安全问题设置弹窗组件
@Composable
fun SecurityQuestionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置安全问题") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "为了保护您的账号安全，请验证密码并设置新的安全问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("当前登录密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("新安全问题") },
                    placeholder = { Text("例如：我的出生地是？") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("问题答案") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotBlank() && question.isNotBlank() && answer.isNotBlank()) {
                        isSubmitting = true
                        onConfirm(password, question, answer)
                    }
                },
                enabled = !isSubmitting && password.isNotBlank() && question.isNotBlank() && answer.isNotBlank()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}