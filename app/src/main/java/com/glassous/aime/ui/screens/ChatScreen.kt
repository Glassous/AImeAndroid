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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.components.ChatInput
import com.glassous.aime.ui.components.MessageBubble
import com.glassous.aime.ui.components.ModelSelectionBottomSheet
import com.glassous.aime.ui.components.NavigationDrawer
import com.glassous.aime.ui.components.LocalDialogBlurState
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import com.glassous.aime.ui.viewmodel.CloudSyncViewModelFactory
import com.glassous.aime.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar
import com.glassous.aime.data.preferences.OssPreferences
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.preferences.AutoSyncPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit,
    modelSelectionViewModel: ModelSelectionViewModel
) {
    val chatViewModel: ChatViewModel = viewModel()
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
    // 新增：读取回复气泡开关
    val replyBubbleEnabled by themePreferences.replyBubbleEnabled.collectAsState(initial = true)
    // 新增：读取聊天字体大小
    val chatFontSize by themePreferences.chatFontSize.collectAsState(initial = 16f)

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
                val lastVisibleItemIndex = visibleItems.last().index
                // 如果最后一个可见项不是列表的最后一项（包括Spacer），则显示按钮
                lastVisibleItemIndex < totalItemsCount - 1
            }
        }
    }

    // 当生成进行、且当前视图已在底部时，保持锚定到底部以避免因内容增长产生跳动
    LaunchedEffect(isLoading, currentMessages, showScrollToBottomButton) {
        if (isLoading && !showScrollToBottomButton && currentMessages.isNotEmpty()) {
            listState.scrollToItem(index = currentMessages.size, scrollOffset = 0)
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
                topBar = {
                    TopAppBar(
                        title = {
                            // 将模型名称改为可点击的按钮
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
                        },
                        navigationIcon = {
                            if (!minimalMode) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "打开导航菜单"
                                    )
                                }
                            }
                        },
                        actions = {
                            // 显示同步成功图标
                            when (syncSuccessType) {
                                "download" -> {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = "获取成功",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                "upload" -> {
                                    Icon(
                                        imageVector = Icons.Filled.CloudUpload,
                                        contentDescription = "上传成功",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(end = 8.dp)
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
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                else -> {}
                            }
                        }
                    )
                },
                bottomBar = {
                    ChatInput(
                        inputText = inputText,
                        onInputChange = chatViewModel::updateInputText,
                        onSendMessage = {
                            chatViewModel.sendMessage(inputText.trim())
                            // 清空输入框
                            chatViewModel.updateInputText("")
                        },
                        isLoading = isLoading
                    )
                }
            ) { paddingValues ->
                if (currentMessages.isEmpty()) {
                    // 空态：问候语
                    val greeting = remember {
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                        // =========== 开始替换新的文案 ============
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
                        // =========== 结束替换 ============
                        greetings.random()
                    }

                    // 使用Box来叠加云端获取按钮
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (!minimalMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = greeting,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 云端获取按钮
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !minimalMode && showCloudSyncButton && isOssConfigured && autoSyncEnabled != true,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
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
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(0.7f),
                                containerColor = MaterialTheme.colorScheme.primary,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "从云端获取数据",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                } else {
                    // 消息列表容器，使用Box来叠加回到底部按钮
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // 消息列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = currentMessages,
                                key = { message -> message.id } // 添加稳定的key以优化重组性能
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    onShowDetails = { onNavigateToMessageDetail(message.id) },
                                    onRegenerate = { chatViewModel.regenerateFromAssistant(it) },
                                    onEditUserMessage = { id, text -> 
                                        chatViewModel.editUserMessageAndResend(id, text) { success, message ->
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
                                    chatFontSize = chatFontSize
                                )
                            }
                            // 底部额外间距
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }

                        // 云端上传按钮 - 仅在消息列表不为空时显示，位置在回到底部按钮上方
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !minimalMode && currentMessages.isNotEmpty() && isOssConfigured && autoSyncEnabled != true,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 16.dp,
                                    bottom = if (showScrollToBottomButton) 80.dp else 16.dp
                                )
                        ) {
                            FloatingActionButton(
                                onClick = {
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
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(0.7f),
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = "上传数据到云端",
                                    tint = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }

                        // 回到底部按钮
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !minimalMode && showScrollToBottomButton,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (currentMessages.isNotEmpty()) {
                                            // 滚动到列表的最底部，包括底部的Spacer
                                            listState.animateScrollToItem(
                                                index = currentMessages.size, // 滚动到Spacer项
                                                scrollOffset = 0 // 确保完全滚动到底部
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(0.7f), // 降低不透明度
                                containerColor = MaterialTheme.colorScheme.primary,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "回到底部",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
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
                }
            )
        }
    }
}