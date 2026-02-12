package com.glassous.aime.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
 
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
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
 
import com.glassous.aime.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.util.Calendar
 
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.settings.SettingsActivity
import android.content.Intent
 

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
    onNavigateToMessageDetail: (Long) -> Unit,
    onNavigateToHtmlPreview: (String) -> Unit,
    onNavigateToHtmlPreviewSource: (String) -> Unit,
    modelSelectionViewModel: ModelSelectionViewModel,
    themeViewModel: ThemeViewModel = viewModel()
) {
    val chatViewModel: ChatViewModel = viewModel()
    val toolSelectionViewModel: ToolSelectionViewModel = viewModel()
    val context = LocalContext.current

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

    // 新增：读取顶部栏汉堡菜单与模型文字按钮透明度，以及输入框内部透明度
    val topBarHamburgerAlpha by themeViewModel.topBarHamburgerAlpha.collectAsState()
    val topBarModelTextAlpha by themeViewModel.topBarModelTextAlpha.collectAsState()
    val chatInputInnerAlpha by themeViewModel.chatInputInnerAlpha.collectAsState()
    // 新增：HTML代码块卡片显示设置
    val htmlCodeBlockCardEnabled by themeViewModel.htmlCodeBlockCardEnabled.collectAsState()

    // 新增：读取聊天页面单独全屏显示设置
    val chatFullscreen by themeViewModel.chatFullscreen.collectAsState()
    // 读取全局极简模式全屏设置
    val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()
    
    val isSharing by chatViewModel.isSharing.collectAsState()

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

    val listState = key(currentConversationId) { rememberLazyListState() }
    // 监听用户拖拽状态
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Track TopAppBar height for auto-scroll logic
    var topBarHeightPx by remember { mutableIntStateOf(0) }

    

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

    // 长图分享预览弹窗状态
    var showLongImageDialog by remember { mutableStateOf(false) }

    // 内容淡入动画状态
    val contentAlpha = remember { Animatable(0f) }

    // 切换对话时触发淡入动画
    LaunchedEffect(currentConversationId) {
        contentAlpha.snapTo(0f)
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    // 进入对话时滚动到最新消息（仅在对话切换时触发）
    LaunchedEffect(currentConversationId) {
        if (currentMessages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.scrollToItem(index = currentMessages.size + 1)
        }
    }

    // 流式输出时的自动滚动逻辑：保持滚动直到发送气泡到达顶部栏下方
    LaunchedEffect(currentMessages.lastOrNull()?.content?.length, isLoading) {
        // 如果用户正在拖拽，或者正在进行非自动滚动的滑动（例如惯性滑动），则不进行自动滚动
        // 注意：animateScrollToItem也会导致isScrollInProgress为true，所以我们主要依靠isDragged来判断用户的主动交互
        if (isDragged) return@LaunchedEffect

        if (isLoading && currentMessages.isNotEmpty()) {
            // User message is the one before the streaming message (last)
            // In LazyColumn, Spacer is index 0.
            // User Message Index = (currentMessages.size - 2) + 1 = currentMessages.size - 1
            // e.g. Size=2 (User, AI). User is index 0 in list. Index 1 in LazyColumn. Size-1 = 1. Correct.
            val userMsgIndex = currentMessages.size - 1 
            
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            val userItem = visibleItems.find { it.index == userMsgIndex }
            
            val shouldScroll = if (userItem != null) {
                // If User message is visible, scroll only if it is below the Top Bar
                // Stop scrolling if it reaches the Top Bar (offset <= height)
                userItem.offset > topBarHeightPx
            } else {
                // If User message is not visible
                val firstVisible = visibleItems.firstOrNull()?.index ?: 0
                // If scrolled off top (firstVisible > userMsgIndex), STOP scrolling
                // If scrolled off bottom (firstVisible <= userMsgIndex), keep scrolling
                firstVisible <= userMsgIndex
            }
            
            if (shouldScroll) {
                listState.animateScrollToItem(
                    index = currentMessages.size + 1, // Scroll to bottom spacer
                    scrollOffset = 0
                )
            }
        }
    }

    

    

    

    CompositionLocalProvider(LocalDialogBlurState provides dialogBlurState) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        chatViewModel.deleteConversation(conversationId)
                    },
                    onEditConversationTitle = { conversationId, newTitle ->
                        chatViewModel.updateConversationTitle(conversationId, newTitle)
                    },
                    onGenerateLongImage = { convId ->
                        chatViewModel.selectConversation(convId)
                        scope.launch {
                            drawerState.close()
                            showLongImageDialog = true
                        }
                    },
                    onNavigateToSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    onGenerateTitle = { conversationId, onTitleGenerated, onComplete ->
                        chatViewModel.generateConversationTitle(conversationId, onTitleGenerated, onComplete)
                    }
                )
            }
        ) {
            Scaffold(
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
                        modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
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
                        actions = {},
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
                        showScrollToBottomButton = !(minimalMode && minimalModeConfig.hideScrollToBottomButton) && showScrollToBottomButton,
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
                        val greetings = when (hour) {
                            // 00:00 - 凌晨0点
                            0 -> listOf(
                                "午夜了，还在忙吗？要注意休息哦",
                                "新的一天已经开始，晚安好梦",
                                "夜深人静，整个世界都在沉睡",
                                "凌晨零点，记得早点睡觉，明天会更好"
                            )
                            // 01:00 - 凌晨1点
                            1 -> listOf(
                                "凌晨一点，正是深度睡眠的好时机",
                                "夜已深，放下手机，好好休息吧",
                                "整个城市都安静了，你也该休息了",
                                "熬夜伤身，早点睡哦"
                            )
                            // 02:00 - 凌晨2点
                            2 -> listOf(
                                "凌晨两点，还在工作吗？太辛苦了",
                                "夜这么深了，赶紧休息吧",
                                "熬夜对身体不好，赶紧去睡觉",
                                "这个点还没睡，是有什么心事吗？"
                            )
                            // 03:00 - 凌晨3点
                            3 -> listOf(
                                "凌晨三点，整个城市都在梦乡",
                                "这么晚还没睡，要注意身体啊",
                                "夜已深，早点休息，明天还有新的挑战",
                                "凌晨时分，安静地享受这片刻宁静"
                            )
                            // 04:00 - 凌晨4点
                            4 -> listOf(
                                "凌晨四点，黎明前的黑暗",
                                "这么早起床？还是熬夜到现在？",
                                "天快亮了，再睡一会儿吧",
                                "凌晨四点，世界还在沉睡，你已经醒来"
                            )
                            // 05:00 - 凌晨5点
                            5 -> listOf(
                                "清晨五点，天空开始泛起鱼肚白",
                                "早起的鸟儿有虫吃，早安",
                                "新的一天开始了，早安",
                                "清晨五点，空气最清新，适合晨练"
                            )
                            // 06:00 - 早上6点
                            6 -> listOf(
                                "早上好！六点了，该起床了",
                                "清晨六点，阳光开始温柔地照进房间",
                                "早安！新的一天，新的开始",
                                "六点了，准备好迎接美好的一天了吗？"
                            )
                            // 07:00 - 早上7点
                            7 -> listOf(
                                "七点了，该吃早餐了",
                                "早安！七点的阳光正好",
                                "早上好！新的一天，加油！",
                                "七点了，准备出门上班/上学了吗？"
                            )
                            // 08:00 - 早上8点
                            8 -> listOf(
                                "八点了，上班/上学高峰期，注意安全",
                                "早上好！八点的城市开始热闹起来",
                                "早安！今天也要好好努力",
                                "八点了，新的一天正式开始"
                            )
                            // 09:00 - 上午9点
                            9 -> listOf(
                                "九点了，工作/学习已经开始了吧？",
                                "上午好！九点的阳光充满活力",
                                "早安！九点了，专注工作/学习的时候到了",
                                "九点了，今天的任务完成了多少？"
                            )
                            // 10:00 - 上午10点
                            10 -> listOf(
                                "十点了，该起来活动活动筋骨了",
                                "上午好！十点的时光正好",
                                "早安！十点了，继续加油",
                                "十点了，喝杯茶，休息一下"
                            )
                            // 11:00 - 上午11点
                            11 -> listOf(
                                "十一点了，距离午餐时间不远了",
                                "上午好！十一点了，工作/学习进展如何？",
                                "早安！十一点了，再坚持一下就可以吃饭了",
                                "十一点了，准备好午餐吃什么了吗？"
                            )
                            // 12:00 - 中午12点
                            12 -> listOf(
                                "中午十二点，该吃午餐了",
                                "午安！十二点了，好好享受午餐",
                                "中午好！十二点，休息一下吧",
                                "十二点了，吃饱喝足，下午才有精力"
                            )
                            // 13:00 - 下午1点
                            13 -> listOf(
                                "下午一点，午休时间，好好休息",
                                "午安！一点了，睡个午觉吧",
                                "下午好！一点了，休息好了吗？",
                                "一点了，午休时间结束，准备下午的工作/学习"
                            )
                            // 14:00 - 下午2点
                            14 -> listOf(
                                "下午两点，下午的工作/学习开始了",
                                "午安！两点了，精神饱满地开始下午的任务",
                                "下午好！两点了，继续努力",
                                "两点了，下午时光正好，加油！"
                            )
                            // 15:00 - 下午3点
                            15 -> listOf(
                                "下午三点，来杯下午茶吧",
                                "午安！三点了，休息一下，喝杯茶",
                                "下午好！三点了，工作/学习还顺利吗？",
                                "三点了，下午茶时间到，放松一下"
                            )
                            // 16:00 - 下午4点
                            16 -> listOf(
                                "下午四点，距离下班/放学不远了",
                                "下午好！四点了，再坚持一下",
                                "下午好！四点了，今天的任务快完成了吧？",
                                "四点了，准备好下班/放学了吗？"
                            )
                            // 17:00 - 下午5点
                            17 -> listOf(
                                "下午五点，下班/放学高峰期，注意安全",
                                "下午好！五点了，一天的工作/学习即将结束",
                                "下午好！五点了，准备回家了吗？",
                                "五点了，今天辛苦了，回家好好休息"
                            )
                            // 18:00 - 晚上6点
                            18 -> listOf(
                                "晚上六点，该吃晚饭了",
                                "晚上好！六点了，好好享受晚餐",
                                "晚上好！六点了，一天的疲惫该缓解一下了",
                                "六点了，晚餐时间到，好好犒劳自己"
                            )
                            // 19:00 - 晚上7点
                            19 -> listOf(
                                "晚上七点，新闻联播开始了",
                                "晚上好！七点了，饭后散散步吧",
                                "晚上好！七点了，今天过得怎么样？",
                                "七点了，晚上的时光开始了"
                            )
                            // 20:00 - 晚上8点
                            20 -> listOf(
                                "晚上八点，看看电视，放松一下",
                                "晚上好！八点了，今晚有什么安排？",
                                "晚上好！八点了，好好享受夜晚时光",
                                "八点了，夜晚的生活正式开始"
                            )
                            // 21:00 - 晚上9点
                            21 -> listOf(
                                "晚上九点，该准备洗漱了",
                                "晚上好！九点了，早点休息哦",
                                "晚上好！九点了，今天早点睡",
                                "九点了，距离睡觉时间不远了"
                            )
                            // 22:00 - 晚上10点
                            22 -> listOf(
                                "晚上十点，该放下手机，准备睡觉了",
                                "晚上好！十点了，早点休息，明天还要早起",
                                "晚上好！十点了，晚安好梦",
                                "十点了，今天辛苦了，好好睡一觉"
                            )
                            // 23:00 - 晚上11点
                            23 -> listOf(
                                "晚上十一点，该睡觉了，熬夜伤身",
                                "晚上好！十一点了，晚安",
                                "十一点了，赶紧睡觉，明天还要工作/学习",
                                "深夜十一点，晚安好梦"
                            )
                            // 默认情况（理论上不会触发，因为hour范围是0-23）
                            else -> listOf(
                                "你好！",
                                "欢迎使用AIme",
                                "有什么可以帮助你的吗？",
                                "很高兴见到你"
                            )
                        }
                        greetings.random()
                    }

                    // 使用Box来叠加云端获取按钮
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(contentAlpha.value)
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
                            .alpha(contentAlpha.value),
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
                                    currentConversationId?.let { convId ->
                                        chatViewModel.resendMessage(
                                            convId,
                                            id,
                                            text,
                                            selectedTool,
                                            isAutoMode = isAutoSelected
                                        )
                                    }
                                },
                                onRetryFailed = { failedMessageId ->
                                    currentConversationId?.let { convId ->
                                        chatViewModel.retryFailedMessage(
                                            convId,
                                            failedMessageId,
                                            selectedTool,
                                            isAutoMode = isAutoSelected
                                        )
                                    }
                                },
                                replyBubbleEnabled = replyBubbleEnabled,
                                chatFontSize = chatFontSize,
                                isStreaming = isStreamingMessage,
                                onHtmlPreview = onNavigateToHtmlPreview,
                                onHtmlPreviewSource = onNavigateToHtmlPreviewSource,
                                useCardStyleForHtmlCode = htmlCodeBlockCardEnabled,
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
                        "settings" -> context.startActivity(Intent(context, SettingsActivity::class.java))
                        // chat 或未知路由：保持在当前聊天页
                        else -> {}
                    }
                }
            )
        }

    // 长图分享预览弹窗
    if (showLongImageDialog) {
        var sharedUrl by remember { mutableStateOf<String?>(null) }
        
        com.glassous.aime.ui.components.LongImagePreviewBottomSheet(
            messages = currentMessages,
            onDismiss = { showLongImageDialog = false },
            chatFontSize = chatFontSize,
            useCardStyleForHtmlCode = htmlCodeBlockCardEnabled,
            replyBubbleEnabled = replyBubbleEnabled,
            isSharing = isSharing,
            sharedUrl = sharedUrl,
            onShareLink = {
                val title = conversations.find { it.id == currentConversationId }?.title ?: "对话分享"
                
                // Aggregate distinct model names from messages
                val distinctModels = currentMessages
                    .filter { !it.isFromUser && !it.modelDisplayName.isNullOrBlank() }
                    .mapNotNull { it.modelDisplayName }
                    .distinct()
                    .joinToString(", ")
                
                // Fallback to selected model if no messages have model info
                val modelsToShare = if (distinctModels.isNotBlank()) distinctModels else selectedModelDisplayName

                chatViewModel.shareConversation(
                    title = title,
                    model = modelsToShare,
                    messages = currentMessages,
                    onSuccess = { url ->
                        sharedUrl = url
                    },
                    onError = { error ->
                        scope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    }
                )
            }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .imePadding()
    )
}
}
}
