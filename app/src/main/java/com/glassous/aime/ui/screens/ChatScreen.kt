package com.glassous.aime.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.components.ChatInput
import com.glassous.aime.ui.components.MessageBubble
import com.glassous.aime.ui.components.ModelSelectionBottomSheet
import com.glassous.aime.ui.components.ToolSelectionBottomSheet
import com.glassous.aime.ui.components.NavigationDrawer
import com.glassous.aime.ui.components.LocalDialogBlurState
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.ToolSelectionViewModel
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import com.glassous.aime.ui.viewmodel.CloudSyncViewModelFactory
import com.glassous.aime.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar
import com.glassous.aime.data.preferences.OssPreferences
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.preferences.AutoSyncPreferences

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.view.WindowManager
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.data.AutoToolSelector
import com.glassous.aime.data.model.ModelGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit,
    modelSelectionViewModel: ModelSelectionViewModel,
    themeViewModel: ThemeViewModel = viewModel()
) {
    val chatViewModel: ChatViewModel = viewModel()
    val toolSelectionViewModel: ToolSelectionViewModel = viewModel()
    val context = LocalContext.current
    val cloudSyncViewModel: CloudSyncViewModel = viewModel(
        factory = CloudSyncViewModelFactory(context.applicationContext as android.app.Application)
    )

    val conversations by chatViewModel.conversations.collectAsState()
    val currentMessages by chatViewModel.currentMessages.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()

    val modelSelectionUiState by modelSelectionViewModel.uiState.collectAsState()
    val selectedModel by modelSelectionViewModel.selectedModel.collectAsState()
    val selectedModelDisplayName = selectedModel?.name ?: "请先选择模型"
    val groups by modelSelectionViewModel.groups.collectAsState(initial = emptyList())
    val selectedGroup: ModelGroup? = remember(selectedModel, groups) {
        groups.firstOrNull { it.id == selectedModel?.groupId }
    }

    val toolSelectionUiState by toolSelectionViewModel.uiState.collectAsState()
    val selectedTool by toolSelectionViewModel.selectedTool.collectAsState()
    val isAutoSelected by toolSelectionViewModel.isAutoSelected.collectAsState()
    val toolCallInProgress by chatViewModel.toolCallInProgress.collectAsState()
    val currentToolType by chatViewModel.currentToolType.collectAsState()

    // 读取 OSS 配置以控制云端上传/下载按钮显示
    val ossPreferences = remember { OssPreferences(context) }
    val endpoint by ossPreferences.endpoint.collectAsState(initial = null)
    val bucket by ossPreferences.bucket.collectAsState(initial = null)
    val ak by ossPreferences.accessKeyId.collectAsState(initial = null)
    val sk by ossPreferences.accessKeySecret.collectAsState(initial = null)
    val isOssConfigured = !endpoint.isNullOrBlank() && !bucket.isNullOrBlank() && !ak.isNullOrBlank() && !sk.isNullOrBlank()

    val autoSyncPreferences = remember { AutoSyncPreferences(context) }
    var autoSyncEnabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        autoSyncPreferences.autoSyncEnabled.collect { autoSyncEnabled = it }
    }

    // 读取极简模式以控制 UI 可见性
    val themePreferences = remember { ThemePreferences(context) }
    val minimalMode by themePreferences.minimalMode.collectAsState(initial = false)
    // 新增：读取极简模式详细配置
    val minimalModeConfig by themePreferences.minimalModeConfig.collectAsState(initial = com.glassous.aime.data.model.MinimalModeConfig())
    // 新增：读取回复气泡开关
    val replyBubbleEnabled by themePreferences.replyBubbleEnabled.collectAsState(initial = true)
    // 新增：读取聊天字体大小
    val chatFontSize by themePreferences.chatFontSize.collectAsState(initial = 16f)
    // 新增：读取聊天页面UI透明度
    val chatUiOverlayAlpha by themePreferences.chatUiOverlayAlpha.collectAsState(initial = 0.5f)
    val hideImportSharedButton by themePreferences.hideImportSharedButton.collectAsState(initial = false)

    // 新增：读取顶部栏汉堡菜单与模型文字按钮透明度，以及输入框内部透明度
    val topBarHamburgerAlpha by themeViewModel.topBarHamburgerAlpha.collectAsState()
    val topBarModelTextAlpha by themeViewModel.topBarModelTextAlpha.collectAsState()
    val chatInputInnerAlpha by themeViewModel.chatInputInnerAlpha.collectAsState()

    // 新增：读取聊天页面单独全屏显示设置
    val chatFullscreen by themeViewModel.chatFullscreen.collectAsState()
    // 读取全局极简模式全屏设置
    val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()

    // 获取当前Activity和View用于全屏控制
    val view = LocalView.current
    val activity = view.context as? Activity

    // 聊天页面全屏显示控制
    DisposableEffect(chatFullscreen, minimalMode, minimalModeFullscreen) {
        activity?.let { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            
            // 确定最终的全屏状态：聊天页面独立全屏 OR 全局极简模式全屏
            val shouldBeFullscreen = chatFullscreen || (minimalMode && minimalModeFullscreen)
            
            if (shouldBeFullscreen) {
                // 设置全屏模式
                controller?.let { insetsController ->
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
                // 设置窗口标志确保全屏效果
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            } else {
                // 恢复正常模式
                controller?.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
        
        onDispose {
            // 退出聊天页面时，不做任何操作，让MainActivity的全局逻辑接管
            // 这样可以避免与MainActivity的LaunchedEffect产生冲突
        }
    }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var syncSuccessType by remember { mutableStateOf<String?>(null) }
    var syncErrorType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(syncSuccessType) {
        if (syncSuccessType != null) {
            delay(3000)
            syncSuccessType = null
        }
    }

    LaunchedEffect(syncErrorType) {
        if (syncErrorType != null) {
            delay(3000)
            syncErrorType = null
        }
    }

    // 当抽屉打开时，拦截系统返回键，优先关闭抽屉而不是退出到桌面
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    // 背景模糊共享状态（弹窗显示期间启用）
    val dialogBlurState = remember { mutableStateOf(false) }

    // 检查是否需要显示回到底部按钮
    val showScrollToBottomButton by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount

            if (totalItemsCount == 0 || visibleItems.isEmpty()) {
                false
            } else {
                // 检查是否在底部：最后一个可见项是否是列表的最后一项，且完全可见
                val lastVisibleItem = visibleItems.lastOrNull()
                if (lastVisibleItem == null) {
                    false
                } else {
                    val isLastItem = lastVisibleItem.index == totalItemsCount - 1
                    val isFullyVisible = lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
                    !(isLastItem && isFullyVisible)
                }
            }
        }
    }

    // 移除生成期间的自动保持底部机制，仅保留回到底部按钮

    // 进入对话时滚动到最新消息（仅在对话切换时触发）
    LaunchedEffect(currentConversationId) {
        if (currentMessages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(
                index = currentMessages.size - 1,
                scrollOffset = 0
            )
        }
    }

    // 云端获取按钮显示状态
    var showCloudSyncButton by remember { mutableStateOf(false) }

    // 监听消息列表变化，控制云端获取按钮显示
    LaunchedEffect(currentMessages.isEmpty()) {
        if (currentMessages.isEmpty()) {
            showCloudSyncButton = true
            // 10秒后自动隐藏
            delay(10000)
            showCloudSyncButton = false
        } else {
            // 有消息时立即隐藏
            showCloudSyncButton = false
        }
    }

    // 监听输入文本变化，发送消息时隐藏按钮
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank() && showCloudSyncButton) {
            showCloudSyncButton = false
        }
    }

    // 应用启动时（进入主页）自动从云端获取一次（仅当开启自动同步且配置完整）
    var initialAutoDownloadDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoSyncEnabled, isOssConfigured) {
        if (autoSyncEnabled == true && isOssConfigured && !initialAutoDownloadDone) {
            cloudSyncViewModel.downloadAndImport { success, message ->
                scope.launch {
                    if (success) {
                        syncSuccessType = "download"
                        syncErrorType = null
                    } else {
                        syncErrorType = "download"
                        syncSuccessType = null
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
            initialAutoDownloadDone = true
        }
    }

    // 在AI生成结束时自动上传一次（仅在自动同步开启且配置完整时）
    var didStartGeneration by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            didStartGeneration = true
        } else if (didStartGeneration) {
            didStartGeneration = false
            if (autoSyncEnabled == true && isOssConfigured && currentMessages.isNotEmpty()) {
                cloudSyncViewModel.uploadBackup { success, message ->
                    scope.launch {
                        if (success) {
                            syncSuccessType = "upload"
                            syncErrorType = null
                        } else {
                            syncErrorType = "upload"
                            syncSuccessType = null
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalDialogBlurState provides dialogBlurState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                NavigationDrawer(
                    conversations = conversations,
                    currentConversationId = currentConversationId,
                    onConversationSelect = { conversationId ->
                        chatViewModel.selectConversation(conversationId)
                        scope.launch { drawerState.close() }
                    },
                    onNewConversation = {
                        chatViewModel.createNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { conversationId ->
                        chatViewModel.deleteConversation(conversationId) { success, message ->
                            if (success) {
                                syncSuccessType = "upload"
                            } else {
                                syncErrorType = "upload"
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "同步失败: $message",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    },
                    onEditConversationTitle = { conversationId, newTitle ->
                        chatViewModel.updateConversationTitle(conversationId, newTitle) { success, message ->
                            if (success) {
                                syncSuccessType = "upload"
                            } else {
                                syncErrorType = "upload"
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "同步失败: $message",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    },
                    onGenerateShareCode = { convId, onResult ->
                        chatViewModel.generateShareCode(convId) { code ->
                            onResult(code)
                        }
                    },
                    onImportSharedConversation = { code, onResult ->
                        chatViewModel.importSharedConversation(code, { newId ->
                            onResult(newId)
                            if (newId != null) {
                                chatViewModel.selectConversation(newId)
                                scope.launch { drawerState.close() }
                            }
                        }) { success, message ->
                            if (!success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "同步失败: $message",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    },
                    hideImportSharedButton = hideImportSharedButton,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (dialogBlurState.value) {
                            Modifier.blur(8.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 工具图标：自动/实际工具切换；可隐藏；背景始终显示为 50% 透明
                                if (!(minimalMode && minimalModeConfig.hideToolIcon)) {
                                    when {
                                        toolCallInProgress -> {
                                            // 工具调用进行中：显示实际调用工具图标并附加旋转指示器（图形化，无文本）
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                    shape = CircleShape,
                                                    tonalElevation = 0.dp,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = (currentToolType ?: com.glassous.aime.data.model.ToolType.WEB_SEARCH).icon,
                                                        contentDescription = (currentToolType ?: com.glassous.aime.data.model.ToolType.WEB_SEARCH).displayName,
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .padding(6.dp)
                                                    )
                                                }
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                        selectedTool != null -> {
                                            Surface(
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                shape = CircleShape,
                                                tonalElevation = 0.dp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = selectedTool!!.icon,
                                                    contentDescription = selectedTool!!.displayName,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .padding(6.dp)
                                                )
                                            }
                                        }
                                        toolSelectionUiState.isProcessing || isAutoSelected -> {
                                            // 自动模式：使用与 BottomSheet 一致的组合图标（齿轮 + 星星）
                                            Surface(
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                shape = CircleShape,
                                                tonalElevation = 0.dp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                androidx.compose.foundation.layout.Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .padding(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Settings,
                                                        contentDescription = "自动调用",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.align(Alignment.Center)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Filled.Star,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .align(Alignment.TopEnd)
                                                            .rotate(-20f)
                                                    )
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                                
                                // 模型按钮背景与页面背景同步；仅在极简模式且隐藏模型文字时整体透明
                                Surface(
                                    color = if (minimalMode && minimalModeConfig.hideModelSelectionText) {
                                        Color.Transparent
                                    } else {
                                        MaterialTheme.colorScheme.background.copy(alpha = topBarModelTextAlpha)
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.alpha(
                                        if (minimalMode && minimalModeConfig.hideModelSelectionText) 0f else 1f
                                    )
                                ) {
                                    TextButton(
                                        onClick = { modelSelectionViewModel.showBottomSheet() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text(
                                            text = selectedModelDisplayName,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            if (!(minimalMode && minimalModeConfig.hideNavigationMenu)) {
                                // 汉堡菜单背景始终显示为 50% 透明，不再判断
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = topBarHamburgerAlpha),
                                    shape = CircleShape,
                                    tonalElevation = 0.dp
                                ) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "打开导航菜单"
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            // 显示同步成功图标（仅在非极简模式或未隐藏同步状态时显示）
                            if (!(minimalMode && minimalModeConfig.hideSyncStatusIndicator)) {
                                when (syncSuccessType) {
                                    "download" -> {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = "获取成功",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }
                                    "upload" -> {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = "上传成功",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }
                                    else -> {}
                                }

                                // 显示同步失败图标
                                when (syncErrorType) {
                                    "download", "upload" -> {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "同步失败",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background.copy(alpha = chatUiOverlayAlpha),
                            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = chatUiOverlayAlpha),
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    ChatInput(
                        inputText = inputText,
                        onInputChange = chatViewModel::updateInputText,
                        onSendMessage = {
                            chatViewModel.sendMessage(
                                inputText.trim(),
                                selectedTool,
                                isAutoMode = isAutoSelected
                            )
                            // 清空输入框
                            chatViewModel.updateInputText("")
                        },
                        isLoading = isLoading,
                        minimalMode = minimalMode,
                        hideInputBorder = minimalModeConfig.hideInputBorder,
                        hideSendButtonBackground = minimalModeConfig.hideSendButtonBackground,
                        hideInputPlaceholder = minimalModeConfig.hideInputPlaceholder,
                        // 内嵌按钮配置
                        showUploadButton = !(minimalMode && minimalModeConfig.hideCloudUploadButton) && currentMessages.isNotEmpty() && isOssConfigured && autoSyncEnabled != true,
                        showDownloadButton = !(minimalMode && minimalModeConfig.hideCloudDownloadButton) && showCloudSyncButton && isOssConfigured && autoSyncEnabled != true,
                        showScrollToBottomButton = !(minimalMode && minimalModeConfig.hideScrollToBottomButton) && showScrollToBottomButton,
                        onUploadClick = {
                            cloudSyncViewModel.uploadBackup { success, message ->
                                scope.launch {
                                    if (success) {
                                        syncSuccessType = "upload"
                                        syncErrorType = null
                                    } else {
                                        syncErrorType = "upload"
                                        syncSuccessType = null
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        },
                        onDownloadClick = {
                            cloudSyncViewModel.downloadAndImport { success, message ->
                                scope.launch {
                                    if (success) {
                                        syncSuccessType = "download"
                                        syncErrorType = null
                                        showCloudSyncButton = false
                                    } else {
                                        syncErrorType = "download"
                                        syncSuccessType = null
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        },
                        onScrollToBottomClick = {
                            scope.launch {
                                if (currentMessages.isNotEmpty()) {
                                    // 滚动到列表的最底部，包括底部的Spacer
                                    listState.animateScrollToItem(
                                        index = currentMessages.size + 1, // 滚动到底部Spacer项（消息数量+顶部Spacer+底部Spacer）
                                        scrollOffset = 0 // 确保完全滚动到底部
                                    )
                                }
                            }
                        },
                        overlayAlpha = chatUiOverlayAlpha,
                        innerAlpha = chatInputInnerAlpha
                    )
                }
            ) { paddingValues ->
                if (currentMessages.isEmpty()) {
                    // 空态：问候语
                    val greeting = remember {
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val greetings = when {
                            // 清晨 (05:00 - 07:59)
                            hour in 5..7 -> listOf(
                                "早安，又是元气满满的一天",
                                "清晨的第一缕阳光，你好呀",
                                "新的一天，从此刻开始",
                                "早～ 记得吃早餐哦",
                                "黎明破晓，万物复苏"
                            )

                            // 上午 (08:00 - 10:59)
                            hour in 8..10 -> listOf(
                                "上午好，今天也要加油",
                                "精神百倍，投入战斗吧",
                                "愿你灵感涌现，万事顺遂",
                                "今日份的努力已上线",
                                "泡杯咖啡，开始专注时刻"
                            )

                            // 正午 (11:00 - 12:59)
                            hour in 11..12 -> listOf(
                                "午安～ 准备好迎接午餐了吗",
                                "中午好，休息一下大脑",
                                "快到饭点啦，想想吃什么",
                                "日光正盛，稍作小憩",
                                "辛苦了一上午，充个电吧"
                            )

                            // 午后 (13:00 - 15:59)
                            hour in 13..15 -> listOf(
                                "下午好，继续奋斗",
                                "午后的时光，愿你轻松度过",
                                "来点下午茶，提提神",
                                "春困秋乏夏打盹，顶住",
                                "愿你效率up up"
                            )

                            // 傍晚 (16:00 - 17:59)
                            hour in 16..17 -> listOf(
                                "傍晚时分，日落很美",
                                "快下班/放学啦，坚持一下",
                                "今天的任务都完成了吗",
                                "晚霞满天，一天即将落幕",
                                "收拾心情，准备迎接夜晚"
                            )

                            // 黄昏/入夜 (18:00 - 20:59)
                            hour in 18..20 -> listOf(
                                "晚上好！",
                                "夜幕降临，回家路上请注意安全",
                                "晚餐愉快，好好犒劳自己",
                                "华灯初上，享受片刻安宁",
                                "忙碌结束，现在是你的时间"
                            )

                            // 深夜 (21:00 - 22:59)
                            hour in 21..22 -> listOf(
                                "夜深了，早点休息",
                                "月色真美，晚安",
                                "是时候放下手机，进入梦乡了",
                                "愿你今夜好梦",
                                "今天辛苦了，明天再见"
                            )

                            // 子夜/午夜 (23:00 - 01:59)
                            hour == 23 || hour in 0..1 -> listOf(
                                "夜猫子，还在呀",
                                "已经跨过零点咯",
                                "别熬太晚，身体是革命的本钱",
                                "夜阑人静，万物皆眠",
                                "晚安，好梦香甜"
                            )

                            // 凌晨 (02:00 - 04:59)
                            else -> listOf(
                                "凌晨时分，夜色正浓",
                                "还在忙吗？也太拼了",
                                "嘘... 整个城市都在休息",
                                "黎明前的黑暗，请注意休息",
                                "早睡早起... 好像已经晚了"
                            )
                        }
                        greetings.random()
                    }

                    // 使用Box来叠加云端获取按钮
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        if (!(minimalMode && minimalModeConfig.hideWelcomeText)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = greeting,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            ,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 6.dp
                        )
                    ) {
                        // 顶部安全距离Spacer：初始状态保持现有起始位置，滚动后让气泡进入顶部栏区域
                        item {
                            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
                        }
                        items(
                            items = currentMessages,
                            key = { message -> message.id } // 添加稳定的key以优化重组性能
                        ) { message ->
                            // 判断是否为最后一条AI消息且正在流式输出
                            val isLastAiMessage = !message.isFromUser && 
                                currentMessages.lastOrNull { !it.isFromUser }?.id == message.id
                            val isStreamingMessage = isLoading && isLastAiMessage
                            
                            MessageBubble(
                                message = message,
                                onShowDetails = { onNavigateToMessageDetail(message.id) },
                                onRegenerate = {
                                    chatViewModel.regenerateFromAssistant(
                                        it,
                                        selectedTool,
                                        isAutoMode = isAutoSelected
                                    )
                                },
                                onEditUserMessage = { id, text -> 
                                    chatViewModel.editUserMessageAndResend(
                                        id,
                                        text,
                                        selectedTool,
                                        isAutoMode = isAutoSelected
                                    ) { success, message ->
                                        if (success) {
                                            syncSuccessType = "upload"
                                        } else {
                                            syncErrorType = "upload"
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "同步失败: $message",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                },
                                replyBubbleEnabled = replyBubbleEnabled,
                                chatFontSize = chatFontSize,
                                isStreaming = isStreamingMessage,
                                enableTypewriterEffect = true
                            )
                        }
                        // 底部安全距离Spacer：初始状态保持现有底部位置，滚动到底后内容可进入输入栏区域
                        item {
                            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
                        }
                    }
                }
            }
        }

        // 模型选择Bottom Sheet
        if (modelSelectionUiState.showBottomSheet) {
            ModelSelectionBottomSheet(
                viewModel = modelSelectionViewModel,
                onDismiss = { modelSelectionViewModel.hideBottomSheet() },
                onSyncResult = { success, message ->
                    scope.launch {
                        if (success) {
                            syncSuccessType = "upload"
                            syncErrorType = null
                        } else {
                            syncErrorType = "upload"
                            syncSuccessType = null
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                },
                selectedTool = selectedTool,
                onToolSelectionClick = {
                    toolSelectionViewModel.showBottomSheet()
                },
                autoProcessing = toolSelectionUiState.isProcessing,
                autoSelected = isAutoSelected,
                toolCallInProgress = toolCallInProgress,
                currentToolType = currentToolType
            )
        }

        // 工具选择Bottom Sheet
        if (toolSelectionUiState.showBottomSheet) {
            val autoToolSelector = remember(selectedGroup, selectedModel) {
                val group = selectedGroup
                val model = selectedModel
                if (group != null && model != null) {
                    AutoToolSelector(
                        baseUrl = group.baseUrl,
                        apiKey = group.apiKey,
                        modelName = model.modelName
                    )
                } else null
            }
            ToolSelectionBottomSheet(
                viewModel = toolSelectionViewModel,
                onDismiss = { toolSelectionViewModel.hideBottomSheet() },
                autoToolSelector = autoToolSelector,
                onAutoNavigate = { route ->
                    when (route) {
                        "settings" -> onNavigateToSettings()
                        // chat 或未知路由：保持在当前聊天页
                        else -> {}
                    }
                }
            )
        }
    }
}