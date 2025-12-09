package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState // Added import
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.Conversation
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onConversationSelect: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onEditConversationTitle: (Long, String) -> Unit,
    onGenerateShareCode: (Long, (String?) -> Unit) -> Unit,
    onImportSharedConversation: (String, (Long?) -> Unit) -> Unit,
    hideImportSharedButton: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 获取当前窗口的 WindowInsets（用于后续判断导航栏位置）
    // ModalDrawerSheet 默认会处理 insets，但为了让 LazyColumn 内容能滚上来，我们需要手动传递给 contentPadding
    val windowInsets = WindowInsets.navigationBars

    // [优化] 引入 LazyListState 以便控制滚动
    val listState = rememberLazyListState()

    // [优化] 监听对话列表数量变化，当数量增加（新对话生成）时自动滚动到顶部
    var previousConversationCount by remember { mutableIntStateOf(conversations.size) }
    LaunchedEffect(conversations.size) {
        if (conversations.size > previousConversationCount) {
            listState.animateScrollToItem(0)
        }
        previousConversationCount = conversations.size
    }

    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        // 将 ModalDrawerSheet 的 windowInsets 设为 0，我们自己在内部控制 Padding，
        // 这样可以确保背景色延伸到底部，而内容通过 Padding 避开小白条
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 顶部保留 16dp，移除底部 padding
                // 注意：start/end 需要适配系统手势区域，但这里侧边栏已经有宽度限制，通常保留 16dp 即可
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
        ) {
            var showImportDialog by remember { mutableStateOf(false) }
            var importCode by remember { mutableStateOf("") }

            // Header with AIme text and buttons in one row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    // 如果顶部有状态栏遮挡，也可以在这里加 WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AIme",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Cursive
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Buttons section: 设置 -> 获取 -> 新建
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 设置按钮
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(40.dp), // 1:1 square
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. 获取分享按钮
                    if (!hideImportSharedButton) {
                        IconButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.size(40.dp), // 1:1 square
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "获取分享的对话",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 3. 新建对话按钮
                    IconButton(
                        onClick = onNewConversation,
                        modifier = Modifier.size(40.dp), // 1:1 square
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建对话",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // moved declarations above
            val context = LocalContext.current
            val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
                    importCode = code
                }
            }

            // 获取导航栏高度
            val bottomPadding = windowInsets.asPaddingValues().calculateBottomPadding()

            // Conversations List
            if (conversations.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState, // [优化] 绑定 ListState
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // 关键修改：底部 Padding = 基础间距(16.dp) + 导航栏高度(bottomPadding)
                    // 这样列表可以滚上去，避免被小白条遮挡
                    contentPadding = PaddingValues(bottom = 16.dp + bottomPadding)
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            isSelected = conversation.id == currentConversationId,
                            onSelect = { onConversationSelect(conversation.id) },
                            onDelete = { onDeleteConversation(conversation.id) },
                            onEditTitle = { conversationId, newTitle ->
                                onEditConversationTitle(conversationId, newTitle)
                            },
                            onShare = { convId, onCode ->
                                onGenerateShareCode(convId) { code ->
                                    onCode(code)
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无对话记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("获取分享的对话") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = importCode,
                                onValueChange = { importCode = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("粘贴分享码") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { importJsonLauncher.launch(arrayOf("application/json")) }) {
                                    Text("从JSON文件获取")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onImportSharedConversation(importCode) { _ ->
                                    showImportDialog = false
                                    importCode = ""
                                }
                            }
                        ) {
                            Text("插入")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

// ... existing code ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEditTitle: (Long, String) -> Unit,
    onShare: (Long, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf(conversation.title) }

    // 弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareCode by remember { mutableStateOf<String?>(null) }
    var shareJson by remember { mutableStateOf<String>("") }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(shareJson.toByteArray(Charsets.UTF_8))
            }
            showShareDialog = false
        }
    }

    // 滑动相关状态
    val density = LocalDensity.current
    val actionWidth = 150.dp // 三个按钮的总宽度
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // 确保高度一致
    ) {
        // --- 底层：操作按钮区域 ---
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart) // 左侧对齐
                .width(actionWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 编辑按钮
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    isEditing = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "重命名",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 删除按钮
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    showDeleteConfirm = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 分享按钮
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) } // 点击后关闭侧滑
                    onShare(conversation.id) { code ->
                        shareCode = code
                        if (code != null) {
                            try {
                                shareJson = String(java.util.Base64.getUrlDecoder().decode(code), Charsets.UTF_8)
                            } catch (_: Exception) {
                                shareJson = String(java.util.Base64.getDecoder().decode(code), Charsets.UTF_8)
                            }
                        } else {
                            shareJson = ""
                        }
                        showShareDialog = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // --- 顶层：对话内容卡片 ---
        Card(
            onClick = {
                // 如果处于滑动打开状态(向右偏移)，点击则关闭；否则执行选中
                if (offsetX.value > 10f) {
                    scope.launch { offsetX.animateTo(0f) }
                } else if (!isEditing) {
                    onSelect()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !isEditing, // 编辑标题时禁用滑动
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            // 限制只能向右滑动，最大不超过按钮宽度
                            val newOffset = (offsetX.value + delta).coerceIn(0f, actionWidthPx)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    onDragStopped = {
                        // 释放时根据位置自动吸附：超过一半则展开，否则收起
                        val target = if (offsetX.value > actionWidthPx / 2) actionWidthPx else 0f
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = target,
                                animationSpec = tween(durationMillis = 300)
                            )
                        }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 2.dp else 0.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), // 增加内边距，移除原来右侧的箭头空间
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editingTitle,
                        onValueChange = { editingTitle = it },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // 编辑状态下的确认/取消按钮
                    IconButton(
                        onClick = {
                            onEditTitle(conversation.id, editingTitle)
                            isEditing = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "确认",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            editingTitle = conversation.title
                            isEditing = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除对话") },
            text = { Text("删除后不可恢复，确定要删除该对话吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 分享弹窗
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("分享对话") },
            text = {
                Column {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val sanitizedTitle = conversation.title.replace(Regex("[\\/:*?\"<>|]"), "_")
                            exportLauncher.launch("${sanitizedTitle}-${System.currentTimeMillis()}.json")
                        }
                    ) { Text("导出JSON") }
                    TextButton(
                        onClick = {
                            val sanitizedTitle = conversation.title.replace(Regex("[\\/:*?\"<>|]"), "_")
                            val cacheFile = File(context.cacheDir, "${sanitizedTitle}-${System.currentTimeMillis()}.json")
                            cacheFile.writeText(shareJson, Charsets.UTF_8)
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cacheFile)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享对话 JSON"))
                            showShareDialog = false
                        }
                    ) { Text("分享") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("关闭") }
            }
        )
    }
}