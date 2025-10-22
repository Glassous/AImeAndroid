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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.ui.components.ChatInput
import com.glassous.aime.ui.components.MessageBubble
import com.glassous.aime.ui.components.ModelSelectionBottomSheet
import com.glassous.aime.ui.components.NavigationDrawer
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.viewmodel.ChatViewModel
import com.glassous.aime.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit,
    chatViewModel: ChatViewModel = viewModel(),
    modelSelectionViewModel: ModelSelectionViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val conversations by chatViewModel.conversations.collectAsState()
    val currentMessages by chatViewModel.currentMessages.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    
    // 模型选择相关状态
    val modelSelectionUiState by modelSelectionViewModel.uiState.collectAsState()
    val selectedModel by modelSelectionViewModel.selectedModel.collectAsState()
    val selectedModelDisplayName = selectedModel?.name ?: "请先选择模型"
    
    val listState = rememberLazyListState()
    
    // 当前时段问候语
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
    
    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    // Handle back button to close drawer if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

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
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "打开导航菜单"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (currentMessages.isEmpty()) {
                    // Empty state: 仅一行问候语
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
                } else {
                    // Messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentMessages) { message ->
                            MessageBubble(
                                message = message,
                                onShowDetails = { onNavigateToMessageDetail(message.id) }
                            )
                        }
                        
                        // Add extra space at bottom for better UX
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
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
            onDismiss = {
                modelSelectionViewModel.hideBottomSheet()
            }
        )
    }
}