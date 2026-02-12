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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize

@Composable
private fun EditTitleDialog(
    initialTitle: String,
    isGenerating: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onGenerateTitle: () -> Unit
) {
    // 使用传入的 initialTitle 更新内部状态
    // 当外部的 currentEditingTitle 变化时（AI生成后），这里也需要更新
    var editingTitle by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "重命名对话") },
        text = {
            OutlinedTextField(
                value = editingTitle,
                onValueChange = { editingTitle = it },
                label = { Text("对话标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                readOnly = isGenerating,
                trailingIcon = {
                    IconButton(
                        onClick = onGenerateTitle,
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "AI生成标题",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editingTitle) },
                enabled = !isGenerating
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // 菜单状态管理
    var activeMenuConversationId by remember { mutableStateOf<Long?>(null) }
    var activeMenuBounds by remember { mutableStateOf<Rect?>(null) }
    var drawerBounds by remember { mutableStateOf<Rect?>(null) }
    var menuHeightPx by remember { mutableIntStateOf(0) }
    
    // 编辑状态管理（提升至 Drawer 层级，或通过 key 强制重组 Item）
    // 这里我们保持 Item 内部处理编辑状态，但在打开菜单时会重置其他状态
    // 如果菜单操作触发编辑，我们需要一种方式通知 Item 进入编辑模式
    // 简单起见，我们引入 editingConversationId
    var editingConversationId by remember { mutableStateOf<Long?>(null) }
    var isGeneratingTitle by remember { mutableStateOf(false) }
    
    // 删除确认弹窗状态
    var showDeleteConfirmId by remember { mutableStateOf<Long?>(null) }

    // 编辑标题弹窗
    if (editingConversationId != null) {
        val conversation = conversations.find { it.id == editingConversationId }
        if (conversation != null) {
            // 注意：这里需要确保 key 变化时重置 Dialog 状态，或者 Dialog 内部正确处理
            // EditTitleDialog 的 editingTitle 是 remember { mutableStateOf(initialTitle) }
            // 所以如果 editingConversationId 改变，initialTitle 改变，
            // 应该使用 key(editingConversationId) 来包裹 EditTitleDialog 或者在 Dialog 内部使用 LaunchedEffect 更新
            // 为了简单，我们使用 key
            key(editingConversationId) {
                // 需要一个本地状态来持有当前编辑的标题，以便 AI 生成时更新它
                // 但是 Dialog 内部已经有了 editingTitle 状态。
                // 关键问题：onGenerateTitle 的回调需要更新 Dialog 内部的 editingTitle。
                // 现在的 EditTitleDialog 实现中，editingTitle 是内部状态。
                // 如果我们想从外部更新它（AI 生成），我们需要将状态提升。
                
                // 重新设计 EditTitleDialog 调用方式：
                // 我们在这里管理 title 状态
                var currentEditingTitle by remember { mutableStateOf(conversation.title) }
                
                EditTitleDialog(
                    initialTitle = currentEditingTitle,
                    isGenerating = isGeneratingTitle,
                    onConfirm = { newTitle ->
                        onEditConversationTitle(conversation.id, newTitle)
                        editingConversationId = null
                    },
                    onCancel = {
                        editingConversationId = null
                        isGeneratingTitle = false
                    },
                    onGenerateTitle = {
                        isGeneratingTitle = true
                        onGenerateTitle(conversation.id, { generatedTitle ->
                            if (generatedTitle.isNotEmpty()) {
                                currentEditingTitle = generatedTitle
                            }
                        }, {
                            isGeneratingTitle = false
                        })
                    }
                )
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier.width(360.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        // 将 ModalDrawerSheet 的 windowInsets 设为 0，我们自己在内部控制 Padding，
        // 这样可以确保背景色延伸到底部，而内容通过 Padding 避开小白条
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { drawerBounds = it.boundsInWindow() }
        ) {
            // 1. 主要内容区域（列表）
            // 当菜单激活时，应用模糊效果
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 顶部保留 16dp，移除底部 padding
                    // 注意：start/end 需要适配系统手势区域，但这里侧边栏已经有宽度限制，通常保留 16dp 即可
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                    .then(
                        if (activeMenuConversationId != null) Modifier.blur(10.dp) else Modifier
                    )
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp)
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
                                isEditingExternal = editingConversationId == conversation.id,
                                onSelect = { onConversationSelect(conversation.id) },
                                onLongClick = { bounds ->
                                    activeMenuConversationId = conversation.id
                                    activeMenuBounds = bounds
                                    // 震动反馈?
                                },
                                onEditTitle = { conversationId, newTitle ->
                                    onEditConversationTitle(conversationId, newTitle)
                                    editingConversationId = null
                                },
                                onCancelEdit = {
                                    editingConversationId = null
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

            // 2. 菜单遮罩层与浮动内容
            // 当有激活菜单时显示
            androidx.compose.animation.AnimatedVisibility(
                visible = activeMenuConversationId != null && activeMenuBounds != null && drawerBounds != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // 计算相对位置
                val activeConv = conversations.find { it.id == activeMenuConversationId }
                
                if (activeConv != null && activeMenuBounds != null && drawerBounds != null) {
                    val bounds = activeMenuBounds!!
                    val drawer = drawerBounds!!
                    
                    val relativeLeft = bounds.left - drawer.left
                    val relativeTop = bounds.top - drawer.top
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 半透明背景遮罩（点击关闭）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.1f)) // 配合底层模糊，轻微变暗即可
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    activeMenuConversationId = null
                                }
                        )

                        // 浮起的卡片（副本）- 显示在原位
                        // 使用 offset 定位
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(relativeLeft.roundToInt(), relativeTop.roundToInt()) }
                                .width(with(LocalDensity.current) { bounds.width.toDp() })
                                .height(with(LocalDensity.current) { bounds.height.toDp() })
                        ) {
                            ConversationItem(
                                conversation = activeConv,
                                isSelected = activeConv.id == currentConversationId,
                                isEditingExternal = false, // 预览状态不处于编辑模式
                                onSelect = {}, // 禁用点击
                                onLongClick = { _ -> },
                                onDelete = {},
                                onEditTitle = { _, _ -> },
                                onCancelEdit = {},
                                onGenerateLongImage = {},
                                onGenerateTitle = { _, _, _ -> }
                            )
                        }

                        // 菜单卡片
                        // 动态计算位置：如果卡片下方空间足够，显示在下方；否则显示在上方
                        // 需要知道菜单高度 -> onSizeChanged
                        val density = LocalDensity.current
                        val spaceBelow = drawer.height - (relativeTop + bounds.height)
                        val spaceAbove = relativeTop
                        
                        // 默认显示在下方，除非下方空间不足且上方空间充足
                        // 假设菜单高度约为 160dp (3 items * 48dp + padding)
                        // 实际上我们会动态测量，但初始渲染需要一个位置
                        val showBelow = spaceBelow > menuHeightPx || spaceBelow > spaceAbove
                        
                        val menuOffset = if (showBelow) {
                            IntOffset(
                                x = relativeLeft.roundToInt(),
                                y = (relativeTop + bounds.height + 8.dp.toPx(density)).roundToInt()
                            )
                        } else {
                            IntOffset(
                                x = relativeLeft.roundToInt(),
                                y = (relativeTop - menuHeightPx - 8.dp.toPx(density)).roundToInt()
                            )
                        }

                        SidebarMenu(
                            modifier = Modifier
                                .offset { menuOffset }
                                .width(with(LocalDensity.current) { bounds.width.toDp() })
                                .onSizeChanged { menuHeightPx = it.height },
                            onEdit = {
                                activeMenuConversationId = null
                                editingConversationId = activeConv.id
                            },
                            onDelete = {
                                activeMenuConversationId = null
                                showDeleteConfirmId = activeConv.id
                            },
                            onShare = {
                                activeMenuConversationId = null
                                onGenerateLongImage(activeConv.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmId = null },
            title = { Text("确认删除对话") },
            text = { Text("删除后不可恢复，确定要删除该对话吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = showDeleteConfirmId
                        showDeleteConfirmId = null
                        if (id != null) {
                            onDeleteConversation(id)
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmId = null }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

// 辅助扩展函数：将 dp 转 px
@Composable
fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density): Float {
    return with(density) { this@toPx.toPx() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    isEditingExternal: Boolean, // 外部控制的编辑状态 - 实际上不再使用，但为了兼容保留参数，或者我们可以移除它
    onSelect: () -> Unit,
    onLongClick: (Rect) -> Unit,
    onDelete: () -> Unit = {}, // 兼容旧接口，虽然现在通过菜单删除
    onEditTitle: (Long, String) -> Unit,
    onCancelEdit: () -> Unit,
    onGenerateLongImage: (Long) -> Unit,
    onGenerateTitle: (Long, (String) -> Unit, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    // --- 顶层：对话内容卡片 ---
    // 修正：将点击和坐标捕获逻辑直接应用在 Card 的 modifier 上
    var itemBounds by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                itemBounds = coordinates.boundsInWindow()
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onSelect()
                },
                onLongClick = {
                    if (itemBounds != null) {
                        onLongClick(itemBounds!!)
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
