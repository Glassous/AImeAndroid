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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
                        chatViewModel.deleteConversation(conversationId)
                    },
                    onEditConversationTitle = { conversationId, newTitle ->
                        chatViewModel.updateConversationTitle(conversationId, newTitle)
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
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "打开导航菜单"
                                )
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
                        when {
                            hour in 5..10 -> "早上好"
                            hour in 11..12 -> "中午好"
                            hour in 13..17 -> "下午好"
                            hour in 18..22 -> "晚上好"
                            else -> "凌晨好"
                        }
                    }
                    
                    // 使用Box来叠加云端获取按钮
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
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
                        
                        // 云端获取按钮
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showCloudSyncButton,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    cloudSyncViewModel.downloadAndImport { success, message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(message)
                                            if (success) {
                                                showCloudSyncButton = false
                                            }
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
                                    onShowDetails = { onNavigateToMessageDetail(message.id) }
                                )
                            }
                            // 底部额外间距
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        
                        // 云端上传按钮 - 仅在消息列表不为空时显示，位置在回到底部按钮上方
                        androidx.compose.animation.AnimatedVisibility(
                            visible = currentMessages.isNotEmpty(),
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
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(0.7f), // 降低不透明度
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
                            visible = showScrollToBottomButton,
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
                onDismiss = { modelSelectionViewModel.hideBottomSheet() }
            )
        }
    }
}