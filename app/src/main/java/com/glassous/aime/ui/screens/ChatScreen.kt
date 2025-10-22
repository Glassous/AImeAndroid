package com.glassous.aime.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.ui.components.ChatInput
import com.glassous.aime.ui.components.MessageBubble
import com.glassous.aime.ui.components.ModelSelectionBottomSheet
import com.glassous.aime.ui.components.NavigationDrawer
import com.glassous.aime.ui.components.LocalDialogBlurState
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit,
    modelSelectionViewModel: ModelSelectionViewModel
) {
    val chatViewModel: ChatViewModel = viewModel()

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

    // 背景模糊共享状态（弹窗显示期间启用）
    val dialogBlurState = remember { mutableStateOf(false) }

    // 新消息到达时滚动到底部
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    // 返回键先关闭抽屉
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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
                    onNavigateToSettings = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(
                                text = selectedModelDisplayName,
                                style = MaterialTheme.typography.titleLarge
                            ) 
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
                        },
                        isLoading = isLoading,
                        selectedModelName = selectedModelDisplayName,
                        onModelSelectClick = {
                            modelSelectionViewModel.showBottomSheet()
                        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
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
                } else {
                    // 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentMessages) { message ->
                            MessageBubble(
                                message = message,
                                onShowDetails = { onNavigateToMessageDetail(message.id) }
                            )
                        }
                        // 底部额外间距
                        item { Spacer(modifier = Modifier.height(16.dp)) }
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