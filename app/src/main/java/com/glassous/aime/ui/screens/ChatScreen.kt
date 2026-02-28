package com.glassous.aime.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Sync
 
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.glassous.aime.ui.components.WebViewPopup
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.app.Activity
import android.view.WindowManager
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.data.model.ModelGroup

import com.glassous.aime.ui.viewmodel.ToolSelectionViewModelFactory

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.glassous.aime.ui.components.ModelSelectionContent
import com.glassous.aime.ui.components.ToolSelectionContent
import com.glassous.aime.ui.components.SearchResultsBottomSheet
import com.glassous.aime.ui.components.SearchResultsContent
import com.glassous.aime.ui.components.SearchResult
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.DragHandle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
    onNavigateToMessageDetail: (Long) -> Unit,
    onNavigateToHtmlPreview: (String) -> Unit,
    onNavigateToHtmlPreviewSource: (String) -> Unit,
    modelSelectionViewModel: ModelSelectionViewModel,
    themeViewModel: ThemeViewModel = viewModel()
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    
    val chatViewModel: ChatViewModel = viewModel()
    val toolSelectionViewModel: ToolSelectionViewModel = viewModel(
        factory = ToolSelectionViewModelFactory(application.toolPreferences)
    )
    val focusManager = LocalFocusManager.current
    val useCloudProxy by application.modelPreferences.useCloudProxy.collectAsState(initial = false)

    val conversations by chatViewModel.conversations.collectAsState()
    val currentMessages by chatViewModel.currentMessages.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    val isImporting by chatViewModel.isImporting.collectAsState()

    val modelSelectionUiState by modelSelectionViewModel.uiState.collectAsState()
    val selectedModel by modelSelectionViewModel.selectedModel.collectAsState()
    val selectedModelDisplayName = selectedModel?.name ?: "请先选择模型"
    val groups by modelSelectionViewModel.groups.collectAsState(initial = emptyList())
    
    val availableTools by toolSelectionViewModel.availableTools.collectAsState()
    val selectedGroup: ModelGroup? = remember(selectedModel, groups) {
        groups.firstOrNull { it.id == selectedModel?.groupId }
    }

    val toolSelectionUiState by toolSelectionViewModel.uiState.collectAsState()
    val selectedTool by toolSelectionViewModel.selectedTool.collectAsState()
    val toolCallInProgress by chatViewModel.toolCallInProgress.collectAsState()
    val currentToolType by chatViewModel.currentToolType.collectAsState()

    val selectedAspectRatio by chatViewModel.selectedAspectRatio.collectAsState()
    val imageGenModelName by application.toolPreferences.imageGenModelName.collectAsState(initial = "")
    val imageGenModel by application.toolPreferences.imageGenModel.collectAsState(initial = "")
    val openaiImageGenModelName by application.toolPreferences.openaiImageGenModelName.collectAsState(initial = "")
    val openaiImageGenModel by application.toolPreferences.openaiImageGenModel.collectAsState(initial = "")
    val openaiImageGenBaseUrl by application.toolPreferences.openaiImageGenBaseUrl.collectAsState(initial = "")
    
    val isImageGenTool = selectedTool?.type == com.glassous.aime.data.model.ToolType.IMAGE_GENERATION

    val displayModelName = when {
        isImageGenTool -> {
            if (imageGenModelName.isNotBlank()) imageGenModelName else if (imageGenModel.isNotBlank()) imageGenModel 
            else if (openaiImageGenModelName.isNotBlank()) openaiImageGenModelName else if (openaiImageGenModel.isNotBlank()) openaiImageGenModel 
            else "图片生成模型"
        }
        else -> selectedModelDisplayName
    }

    // 附件相关状态
    var showAttachmentSelectionSheet by rememberSaveable { mutableStateOf(false) }
    val attachedImages by chatViewModel.attachedImages.collectAsState()
    
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            chatViewModel.addAttachment(uri, context)
        }
    }

    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            chatViewModel.addAttachment(uri, context, isVideo = true)
        }
    }
    
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            chatViewModel.addAttachment(tempPhotoUri!!, context)
        }
    }

    var tempVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val videoCaptureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && tempVideoUri != null) {
            chatViewModel.addAttachment(tempVideoUri!!, context, isVideo = true)
        }
    }
    
    fun createTempPictureUri(): android.net.Uri {
        val tempFile = java.io.File.createTempFile("camera_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return androidx.core.content.FileProvider.getUriForFile(
            context, 
            "${context.packageName}.fileprovider", 
            tempFile
        )
    }

    fun createTempVideoUri(): android.net.Uri {
        val tempFile = java.io.File.createTempFile("video_", ".mp4", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return androidx.core.content.FileProvider.getUriForFile(
            context, 
            "${context.packageName}.fileprovider", 
            tempFile
        )
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
    
    // 自动解除网页分析工具选择
    val prevIsLoading = remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (prevIsLoading.value && !isLoading) {
            if (selectedTool?.type == com.glassous.aime.data.model.ToolType.WEB_ANALYSIS) {
                toolSelectionViewModel.clearToolSelection()
            }
        }
        prevIsLoading.value = isLoading
    }

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
    val showScrollToBottomButton by remember(listState) {
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
    var sharedUrl by remember { mutableStateOf<String?>(null) }
    
    // Reset sharedUrl when dialog is closed
    LaunchedEffect(showLongImageDialog) {
        if (!showLongImageDialog) {
            sharedUrl = null
        }
    }
    
    val onShareLinkAction: () -> Unit = {
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

    // 内容淡入动画状态
    val contentAlpha = remember { Animatable(0f) }

    // 新增：当前打开的网页链接，用于WebView弹窗
    var currentUrl by remember { mutableStateOf<String?>(null) }
    
    // 新增：当前展示的搜索结果列表
    var currentSearchResults by remember { mutableStateOf<List<SearchResult>?>(null) }

    // 新增：图片预览路径
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var previewVideoPath by remember { mutableStateOf<String?>(null) }

    // HTML预览侧边栏状态（平板模式）
    var showHtmlPreviewSideSheet by remember { mutableStateOf(false) }
    var htmlPreviewSideSheetCode by remember { mutableStateOf("") }
    var htmlPreviewSideSheetIsSourceMode by remember { mutableStateOf(false) }
    var htmlPreviewSideSheetIsRestricted by remember { mutableStateOf(false) }
    var htmlPreviewSideSheetUrl by remember { mutableStateOf<String?>(null) }
    var htmlPreviewSideSheetWidthFraction by remember { mutableStateOf(0.6f) }

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
        if (isDragged) return@LaunchedEffect

        if (isLoading && currentMessages.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

            if (lastVisibleItem == null || totalItemsCount == 0) return@LaunchedEffect

            // 检查是否在底部
            // 策略：只要用户能看到底部 Spacer，或者能看到最后一条消息且距离底部不远，就判定为在底部
            val isAtBottom = if (lastVisibleItem.index == totalItemsCount - 1) {
                // 看到的是 Spacer，肯定在底部
                true
            } else if (lastVisibleItem.index == totalItemsCount - 2) {
                // 看到的是最后一条消息
                // 检查这条消息的底部是否接近视口底部
                // 允许一定的溢出（因为刚生成了新文本，可能刚被顶出去）
                val viewportBottom = layoutInfo.viewportEndOffset
                val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                // 阈值设为 500px (约等于几行文本的高度)，如果超出太多说明用户在往上看
                (itemBottom - viewportBottom) < 200
            } else {
                // 看到的不是最后两条，说明滑上去了
                false
            }

            if (isAtBottom) {
                listState.animateScrollToItem(
                    index = currentMessages.size + 1, // Scroll to bottom spacer
                    scrollOffset = 0
                )
            }
        }
    }

    

    

    

    CompositionLocalProvider(LocalDialogBlurState provides dialogBlurState) {
        BoxWithConstraints {
            val isTablet = maxWidth > 600.dp
            val screenWidth = maxWidth
            
            Box(modifier = Modifier.fillMaxSize()) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = if (isTablet) !showHtmlPreviewSideSheet else true,
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
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxHeight()
                            // 智能宽度调整：
                            // 使用 fillMaxWidth 配合 widthIn 限制最大宽度，
                            // 这样当 Side Sheet 挤压 Box 时，Scaffold 会自动适应剩余空间，
                            // 同时在宽屏下也不会过宽。
                            .fillMaxWidth()
                            .widthIn(max = 1000.dp)
                            .then(
                                if (!isTablet) Modifier.fillMaxWidth() else Modifier
                            )
                            // 在平板模式下增加左右间距
                            .padding(horizontal = if (isTablet) 32.dp else 0.dp)
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
                                                LoadingIndicator(
                                                    modifier = Modifier.size(16.dp)
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
                                        onClick = { 
                                            // 平板模式下，切换侧边栏内容
                                            if (isTablet) {
                                                if (modelSelectionUiState.showBottomSheet) {
                                                    // 如果已经打开了模型选择，再次点击则关闭
                                                    modelSelectionViewModel.hideBottomSheet()
                                                } else {
                                                    // 否则打开模型选择，并关闭其他
                                                    toolSelectionViewModel.hideBottomSheet()
                                                    currentSearchResults = null
                                                    modelSelectionViewModel.showBottomSheet()
                                                }
                                            } else {
                                                modelSelectionViewModel.showBottomSheet() 
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text(
                                            text = displayModelName,
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 比例选择器
                        if (isImageGenTool) {
                            val ratios = listOf("1:1", "16:9", "4:3", "9:16", "3:4")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ratios.forEach { ratio ->
                                    val isSelected = selectedAspectRatio == ratio
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { chatViewModel.updateAspectRatio(ratio) },
                                        label = { Text(ratio) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        border = if (isSelected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                                    )
                                }
                            }
                        }

                        // 网页分析工具提示
                        if (selectedTool?.type == com.glassous.aime.data.model.ToolType.WEB_ANALYSIS) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .align(Alignment.Start)
                            ) {
                                Text(
                                    text = "帮我分析以下网页",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        ChatInput(
                            inputText = inputText,
                            placeholderText = if (selectedTool?.type == com.glassous.aime.data.model.ToolType.WEB_ANALYSIS) "输入网址链接" else null,
                            onInputChange = chatViewModel::updateInputText,
                            onSendMessage = {
                                val trimmedInput = inputText.trim()
                                // 检查是否为分享链接
                                if (chatViewModel.isSharedConversationUrl(trimmedInput)) {
                                    chatViewModel.importSharedConversation(trimmedInput) { success, message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }
                                    chatViewModel.updateInputText("")
                                } else {
                                    chatViewModel.sendMessage(
                                        trimmedInput,
                                        selectedTool
                                    )
                                }
                            },
                            isLoading = isLoading,
                            minimalMode = minimalMode,
                            hideInputBorder = minimalModeConfig.hideInputBorder,
                            hideSendButtonBackground = minimalModeConfig.hideSendButtonBackground,
                            modifier = Modifier.padding(
                                bottom = if (isTablet) 0.dp else 0.dp // WindowInsets handled in ChatInput
                            ),
                            overlayAlpha = themePreferences.chatInputInnerAlpha.collectAsState(initial = 0.9f).value,
                            attachedImages = attachedImages,
                            onRemoveAttachment = { path -> chatViewModel.removeAttachment(path) },
                            onImageClick = { path -> 
                                if (path.endsWith(".mp4", ignoreCase = true)) {
                                    previewVideoPath = path
                                } else {
                                    previewImagePath = path 
                                }
                            },
                            // 内嵌按钮配置
                            showScrollToBottomButton = currentMessages.isNotEmpty() && !(minimalMode && minimalModeConfig.hideScrollToBottomButton) && showScrollToBottomButton,
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
                            innerAlpha = chatInputInnerAlpha
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (!isImporting) {
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
                            .fillMaxHeight()
                            .width(
                                if (isTablet) {
                                    (screenWidth * 0.6f).coerceAtMost(800.dp)
                                } else {
                                    Dp.Unspecified
                                }
                            )
                            .then(if (!isTablet) Modifier.fillMaxWidth() else Modifier)
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
                                        selectedTool
                                    )
                                },
                                onEditUserMessage = { id, text -> 
                                    currentConversationId?.let { convId ->
                                        chatViewModel.resendMessage(
                                            convId,
                                            id,
                                            text,
                                            selectedTool
                                        )
                                    }
                                },
                                onRetryFailed = { failedMessageId ->
                                    currentConversationId?.let { convId ->
                                        chatViewModel.retryFailedMessage(
                                            convId,
                                            failedMessageId,
                                            selectedTool
                                        )
                                    }
                                },
                                onImageClick = { path ->
                                    if (path.endsWith(".mp4", ignoreCase = true)) {
                                        previewVideoPath = path
                                    } else {
                                        previewImagePath = path
                                    }
                                },
                                replyBubbleEnabled = replyBubbleEnabled,
                                chatFontSize = chatFontSize,
                                isStreaming = isStreamingMessage,
                                onHtmlPreview = { code ->
                                    if (isTablet) {
                                        val isWebAnalysis = code.contains("<!-- type: web_analysis")
                                        htmlPreviewSideSheetCode = code
                                        htmlPreviewSideSheetIsRestricted = isWebAnalysis
                                        if (isWebAnalysis) {
                                            val urlRegex = Regex("url:(http[^\\s]+)")
                                            val match = urlRegex.find(code)
                                            htmlPreviewSideSheetUrl = match?.groupValues?.get(1)
                                            htmlPreviewSideSheetIsSourceMode = false
                                        } else {
                                            htmlPreviewSideSheetUrl = null
                                            htmlPreviewSideSheetIsSourceMode = false
                                        }
                                        showHtmlPreviewSideSheet = true
                                        // Close other sheets
                                        modelSelectionViewModel.hideBottomSheet()
                                        toolSelectionViewModel.hideBottomSheet()
                                        currentSearchResults = null
                                    } else {
                                        onNavigateToHtmlPreview(code)
                                    }
                                },
                                onHtmlPreviewSource = { code ->
                                    if (isTablet) {
                                        val isWebAnalysis = code.contains("<!-- type: web_analysis")
                                        htmlPreviewSideSheetCode = code
                                        htmlPreviewSideSheetIsRestricted = isWebAnalysis
                                        htmlPreviewSideSheetUrl = null
                                        if (isWebAnalysis) {
                                             htmlPreviewSideSheetIsSourceMode = false
                                        } else {
                                             htmlPreviewSideSheetIsSourceMode = true
                                        }
                                        showHtmlPreviewSideSheet = true
                                        // Close other sheets
                                        modelSelectionViewModel.hideBottomSheet()
                                        toolSelectionViewModel.hideBottomSheet()
                                        currentSearchResults = null
                                    } else {
                                        onNavigateToHtmlPreviewSource(code)
                                    }
                                },
                                useCardStyleForHtmlCode = htmlCodeBlockCardEnabled,
                                enableTypewriterEffect = true,
                                onLinkClick = { url ->
                                    currentUrl = url
                                },
                                onShowSearchResults = { results ->
                                    // 平板模式下，切换侧边栏内容
                                    if (isTablet) {
                                        modelSelectionViewModel.hideBottomSheet()
                                        toolSelectionViewModel.hideBottomSheet()
                                    }
                                    currentSearchResults = results
                                }
                            )
                        }
                        // 底部安全距离Spacer：初始状态保持现有底部位置，滚动到底后内容可进入输入栏区域
                        item {
                            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
                        }
                    }
                        }
                    }

                // 导入时的加载动画
                if (isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                            .clickable(enabled = false) {}, // 拦截点击
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "正在导入对话...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        }
        
        // Side Sheet
        AnimatedVisibility(
            visible = isTablet && (modelSelectionUiState.showBottomSheet || toolSelectionUiState.showBottomSheet || showAttachmentSelectionSheet || currentSearchResults != null || showLongImageDialog),
            enter = expandHorizontally(expandFrom = Alignment.Start),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
             Surface(
                 modifier = Modifier
                     .width(360.dp)
                     .fillMaxHeight()
                     .windowInsetsPadding(WindowInsets.statusBars), // 避让状态栏
                 color = MaterialTheme.colorScheme.surface,
                 tonalElevation = 1.dp
             ) {
                 Column(modifier = Modifier.fillMaxSize()) {
                     // 顶部按钮栏：返回与关闭
                     Row(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         // 返回按钮：仅在工具选择且未开启搜索结果时显示（返回模型选择）
                         if ((toolSelectionUiState.showBottomSheet || showAttachmentSelectionSheet) && currentSearchResults == null) {
                             IconButton(onClick = {
                                 if (toolSelectionUiState.showBottomSheet) toolSelectionViewModel.hideBottomSheet()
                                 if (showAttachmentSelectionSheet) showAttachmentSelectionSheet = false
                                 modelSelectionViewModel.showBottomSheet()
                             }) {
                                 Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                             }
                         } else {
                             Spacer(modifier = Modifier.size(48.dp)) // 占位保持关闭按钮位置
                         }

                         IconButton(onClick = {
                             modelSelectionViewModel.hideBottomSheet()
                             toolSelectionViewModel.hideBottomSheet()
                             showAttachmentSelectionSheet = false
                             currentSearchResults = null
                             showLongImageDialog = false
                         }) {
                             Icon(Icons.Default.Close, contentDescription = "关闭")
                         }
                     }

                     // 使用 AnimatedContent 实现平滑过渡
                     AnimatedContent(
                         targetState = when {
                             modelSelectionUiState.showBottomSheet -> "model"
                             toolSelectionUiState.showBottomSheet -> "tool"
                             showAttachmentSelectionSheet -> "attachment"
                             currentSearchResults != null -> "search"
                             showLongImageDialog -> "share"
                             else -> "none"
                         },
                         transitionSpec = {
                             if (targetState == "tool" && initialState == "model") {
                                 // Model -> Tool: Slide Left
                                 (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                     slideOutHorizontally { width -> -width } + fadeOut())
                             } else if (targetState == "model" && initialState == "tool") {
                                 // Tool -> Model: Slide Right
                                 (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                     slideOutHorizontally { width -> width } + fadeOut())
                             } else {
                                 // Other: Fade
                                 fadeIn().togetherWith(fadeOut())
                             }
                         },
                         label = "sideSheetContent"
                     ) { targetState ->
                         when (targetState) {
                             "model" -> {
                                 ModelSelectionContent(
                                     viewModel = modelSelectionViewModel,
                                     onDismiss = { modelSelectionViewModel.hideBottomSheet() },
                                     selectedTool = selectedTool,
                                     onToolSelectionClick = {
                                         // 切换到工具选择，隐藏模型选择
                                         modelSelectionViewModel.hideBottomSheet()
                                         toolSelectionViewModel.showBottomSheet()
                                     },
                                     onAttachmentSelectionClick = {
                                         modelSelectionViewModel.hideBottomSheet()
                                         showAttachmentSelectionSheet = true
                                     },
                                     autoProcessing = toolSelectionUiState.isProcessing,
                                     toolCallInProgress = toolCallInProgress,
                                     currentToolType = currentToolType,
                                     showToolSelection = availableTools.isNotEmpty()
                                 )
                             }
                             "tool" -> {
                                 ToolSelectionContent(
                                     viewModel = toolSelectionViewModel,
                                     onDismiss = { toolSelectionViewModel.hideBottomSheet() }
                                 )
                             }
                             "attachment" -> {
                                 com.glassous.aime.ui.components.AttachmentSelectionContent(
                                     onDismiss = { showAttachmentSelectionSheet = false },
                                     onPickImage = { imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                     onTakePhoto = {
                                         try {
                                             val uri = createTempPictureUri()
                                             tempPhotoUri = uri
                                             cameraLauncher.launch(uri)
                                         } catch (e: Exception) {
                                             // Handle error
                                         }
                                     },
                                     onPickVideo = {
                                         videoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly))
                                     },
                                     onTakeVideo = {
                                         try {
                                             val uri = createTempVideoUri()
                                             tempVideoUri = uri
                                             videoCaptureLauncher.launch(uri)
                                         } catch (e: Exception) {
                                             // Handle error
                                         }
                                     }
                                 )
                             }
                             "search" -> {
                                 if (currentSearchResults != null) {
                                     SearchResultsContent(
                                         results = currentSearchResults!!,
                                         onLinkClick = { url ->
                                             currentUrl = url
                                         }
                                     )
                                 }
                             }
                             "share" -> {
                                 com.glassous.aime.ui.components.LongImagePreviewContent(
                                    messages = currentMessages,
                                    onDismiss = { showLongImageDialog = false },
                                    chatFontSize = chatFontSize,
                                    useCardStyleForHtmlCode = htmlCodeBlockCardEnabled,
                                    replyBubbleEnabled = replyBubbleEnabled,
                                    isSharing = isSharing,
                                    sharedUrl = sharedUrl,
                                    onShareLink = onShareLinkAction,
                                    showLinkButton = true,
                                    modifier = Modifier.fillMaxSize(),
                                    isSideSheet = true
                                 )
                             }
                             else -> {
                                 // None
                             }
                         }
                     }
                 }
             }
        }
    // HTML Preview Side Sheet
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = isTablet && showHtmlPreviewSideSheet,
        enter = expandHorizontally(expandFrom = Alignment.Start),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            // Resize Handle
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            // Dragging left (negative) increases side sheet width
                            // deltaFraction = -dragAmount / screenWidth
                            val screenWidthPx = with(density) { screenWidth.toPx() }
                            val newFraction = htmlPreviewSideSheetWidthFraction - (dragAmount / screenWidthPx)
                            htmlPreviewSideSheetWidthFraction = newFraction.coerceIn(0.2f, 0.8f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Visual indicator line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                // Handle icon
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Resize",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .rotate(90f)
                )
            }

            // Side Sheet Content
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(screenWidth * htmlPreviewSideSheetWidthFraction)
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars) // 避让状态栏
            ) {
                HtmlPreviewContent(
                    htmlCode = htmlPreviewSideSheetCode,
                    onClose = { showHtmlPreviewSideSheet = false },
                    initialIsSourceMode = htmlPreviewSideSheetIsSourceMode,
                    isRestricted = htmlPreviewSideSheetIsRestricted,
                    previewUrl = htmlPreviewSideSheetUrl,
                    useCloudProxy = useCloudProxy
                )
            }
        }
    }
    }
    }

        // 模型选择Bottom Sheet
        if (!isTablet && modelSelectionUiState.showBottomSheet) {
            ModelSelectionBottomSheet(
                viewModel = modelSelectionViewModel,
                onDismiss = { modelSelectionViewModel.hideBottomSheet() },
                
                selectedTool = selectedTool,
                onToolSelectionClick = {
                    modelSelectionViewModel.hideBottomSheet()
                    toolSelectionViewModel.showBottomSheet()
                },
                onAttachmentSelectionClick = {
                    modelSelectionViewModel.hideBottomSheet()
                    showAttachmentSelectionSheet = true
                },
                autoProcessing = toolSelectionUiState.isProcessing,
                toolCallInProgress = toolCallInProgress,
                currentToolType = currentToolType,
                showToolSelection = availableTools.isNotEmpty()
            )
        }

        // 附件选择Bottom Sheet
        if (!isTablet && showAttachmentSelectionSheet) {
            com.glassous.aime.ui.components.AttachmentSelectionBottomSheet(
                onDismiss = { showAttachmentSelectionSheet = false },
                onPickImage = { imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTakePhoto = {
                    try {
                        val uri = createTempPictureUri()
                        tempPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        // Handle error
                    }
                },
                onPickVideo = {
                    videoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onTakeVideo = {
                    try {
                        val uri = createTempVideoUri()
                        tempVideoUri = uri
                        videoCaptureLauncher.launch(uri)
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            )
        }

        // 工具选择Bottom Sheet
        if (!isTablet && toolSelectionUiState.showBottomSheet) {
            ToolSelectionBottomSheet(
                viewModel = toolSelectionViewModel,
                onDismiss = { toolSelectionViewModel.hideBottomSheet() }
            )
        }

        // 搜索结果Bottom Sheet
        if (!isTablet && currentSearchResults != null) {
            SearchResultsBottomSheet(
                results = currentSearchResults!!,
                onDismissRequest = { currentSearchResults = null },
                onLinkClick = { url ->
                    currentUrl = url
                }
            )
        }

    // 长图分享预览弹窗
    if (!isTablet && showLongImageDialog) {
        com.glassous.aime.ui.components.LongImagePreviewBottomSheet(
            messages = currentMessages,
            onDismiss = { showLongImageDialog = false },
            chatFontSize = chatFontSize,
            useCardStyleForHtmlCode = htmlCodeBlockCardEnabled,
            replyBubbleEnabled = replyBubbleEnabled,
            isSharing = isSharing,
            sharedUrl = sharedUrl,
            onShareLink = onShareLinkAction
        )
    }

    // 网页预览弹窗
    if (currentUrl != null) {
        WebViewPopup(
            url = currentUrl!!,
            onDismissRequest = { currentUrl = null },
            useCloudProxy = useCloudProxy
        )
    }

    // 图片预览弹窗
    if (previewImagePath != null) {
        com.glassous.aime.ui.components.ImagePreviewPopup(
            imagePath = previewImagePath!!,
            onDismissRequest = { previewImagePath = null }
        )
    }

    // 视频预览弹窗
    if (previewVideoPath != null) {
        com.glassous.aime.ui.components.VideoPreviewPopup(
            videoPath = previewVideoPath!!,
            onDismissRequest = { previewVideoPath = null }
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
}
