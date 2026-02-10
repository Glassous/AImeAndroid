package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onConversationSelect: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onEditConversationTitle: (Long, String) -> Unit,
    onGenerateLongImage: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onGenerateTitle: (Long, (String) -> Unit, () -> Unit) -> Unit,
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

                // Buttons section: 设置 -> 新建
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

                    // 2. 新建对话按钮
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
                            onGenerateLongImage = onGenerateLongImage,
                            onGenerateTitle = onGenerateTitle
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
    onGenerateLongImage: (Long) -> Unit,
    onGenerateTitle: (Long, (String) -> Unit, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf(conversation.title) }
    var isGeneratingTitle by remember { mutableStateOf(false) }

    // 弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // 滑动相关状态
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
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
            
            // 分享按钮（放到最右边）
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onGenerateLongImage(conversation.id)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.tertiary,
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
                // 使用 pointerInput 替代 draggable 以实现自定义的手势拦截逻辑
                .pointerInput(isEditing) {
                    if (isEditing) return@pointerInput // 编辑时禁用滑动

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var drag: androidx.compose.ui.input.pointer.PointerInputChange? = null
                        var overSlop = 0f

                        // 1. 手动检测 TouchSlop，并在此时判断手势方向
                        var totalDx = 0f
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            // 查找当前触摸点的变化
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            // 修复：替换 change.changedToUp()，兼容不同 Compose 版本
                            if (!change.pressed) {
                                // 手指抬起，手势结束
                                break
                            }

                            val dx = change.positionChange().x
                            totalDx += dx

                            // 累积滑动距离超过阈值，判定为拖拽
                            if (abs(totalDx) > touchSlop) {
                                // 关键逻辑：如果当前处于关闭状态 (offsetX <= 0) 且向左滑动 (totalDx < 0)
                                // 则判定为关闭侧边栏手势，不消耗该事件，让父组件（Drawer）处理。
                                if (offsetX.value <= 0.5f && totalDx < 0) {
                                    return@awaitEachGesture
                                }

                                // 否则判定为打开按钮手势，消耗事件并开始拖拽
                                change.consume()
                                drag = change
                                overSlop = totalDx
                                break
                            }
                        } while (true)

                        // 2. 如果判定为有效的列表项拖拽，开始处理后续移动
                        if (drag != null) {
                            // 应用初始的过量滑动
                            scope.launch {
                                val newOffset = (offsetX.value + overSlop).coerceIn(0f, actionWidthPx)
                                offsetX.snapTo(newOffset)
                            }

                            // 继续监听后续的拖拽事件
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                // 修复：替换 change.changedToUp()
                                if (!change.pressed) break

                                val delta = change.positionChange().x
                                if (delta != 0f) {
                                    change.consume()
                                    scope.launch {
                                        val newOffset = (offsetX.value + delta).coerceIn(0f, actionWidthPx)
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            }

                            // 3. 手势结束，根据位置吸附
                            val target = if (offsetX.value > actionWidthPx / 2) actionWidthPx else 0f
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = target,
                                    animationSpec = tween(durationMillis = 300)
                                )
                            }
                        }
                    }
                },
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
                        ),
                        readOnly = isGeneratingTitle,
                        trailingIcon = {
                            // 魔法棒图标按钮 - 用于AI生成标题
                            IconButton(
                                onClick = {
                                    isGeneratingTitle = true
                                    onGenerateTitle(conversation.id, { generatedTitle ->
                                        if (generatedTitle.isNotEmpty()) {
                                            editingTitle = generatedTitle
                                        }
                                    }, {
                                        isGeneratingTitle = false
                                    })
                                },
                                enabled = !isGeneratingTitle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isGeneratingTitle) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "AI生成标题",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    // 编辑状态下的确认/取消按钮
                    AnimatedVisibility(
                        visible = !isGeneratingTitle,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row {
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
                        }
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
}