package com.glassous.aime.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.BuiltInModels
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel

/**
 * 模型选择Bottom Sheet
 * 实现两级菜单：第一级为分组，第二级为具体模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionBottomSheet(
    viewModel: ModelSelectionViewModel,
    onDismiss: () -> Unit,
    onSyncResult: ((Boolean, String) -> Unit)? = null,
    selectedTool: Tool? = null,
    onToolSelectionClick: () -> Unit = {},
    autoProcessing: Boolean = false,
    autoSelected: Boolean = false,
    toolCallInProgress: Boolean = false,
    currentToolType: ToolType? = null,
    showToolSelection: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
     ) {
        // 处理返回键：在模型列表页面时返回到分组列表，在分组列表页面时关闭Bottom Sheet
        BackHandler(enabled = uiState.selectedGroup != null) {
            viewModel.backToGroups()
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.selectedGroup != null) {
                    // 显示返回按钮和分组名称
                    IconButton(
                        onClick = { viewModel.backToGroups() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                    Text(
                        text = uiState.selectedGroup!!.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    // 显示"选择模型"标题和当前模型
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择模型",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // 右侧：工具调用显示 + 当前模型显示
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 当前工具显示（非入口，仅展示）
                            if (toolCallInProgress) {
                                // 工具调用进行中：显示实际调用工具（根据当前工具类型）
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = (currentToolType ?: ToolType.WEB_SEARCH).icon,
                                            contentDescription = (currentToolType ?: ToolType.WEB_SEARCH).displayName,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = (currentToolType ?: ToolType.WEB_SEARCH).displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            } else if (selectedTool != null) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = selectedTool.icon,
                                            contentDescription = selectedTool.displayName,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedTool.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            } else if (autoProcessing || autoSelected) {
                                // 自动使用工具进行中：显示自动徽标（齿轮+星星）
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(18.dp)) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "自动使用工具",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "自动使用工具",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            // 当前模型显示
                            val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
                            selectedModel?.let { model ->
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // 内容区域 - 使用AnimatedContent实现菜单切换动画
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = uiState.selectedGroup,
                    transitionSpec = {
                        if (targetState != null) {
                            // 进入模型列表：从右侧滑入 + 淡入
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> fullWidth }
                            ) + fadeIn()) togetherWith (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth / 3 }
                            ) + fadeOut())
                        } else {
                            // 返回分组列表：从左侧滑入 + 淡入
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> -fullWidth / 3 }
                            ) + fadeIn()) togetherWith (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth }
                            ) + fadeOut())
                        }
                    },
                    label = "menu_transition"
                ) { selectedGroup ->
                    if (selectedGroup == null) {
                        // 第一级：显示分组列表
                        GroupList(
                            groups = groups,
                            onGroupClick = { group ->
                                viewModel.selectGroup(group)
                            },
                            onToolSelectionClick = {
                                // 打开工具选择弹窗
                                onToolSelectionClick()
                            },
                            onBuiltInModelClick = { model ->
                                viewModel.selectModel(model, onSyncResult)
                            },
                            showToolSelection = showToolSelection
                        )
                    } else {
                        // 第二级：显示选中分组下的模型列表
                        ModelList(
                            viewModel = viewModel,
                            group = selectedGroup,
                            onModelClick = { model ->
                                viewModel.selectModel(model, onSyncResult)
                            }
                        )
                    }
                }
            }
            
            // 底部间距
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 工具选择项
 */
@Composable
private fun ToolSelectionItem(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 工具图标
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = "工具调用",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 工具名称
            Text(
                text = "工具调用",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            // 右箭头
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 分组列表
 */
@Composable
private fun GroupList(
    groups: List<ModelGroup>,
    onGroupClick: (ModelGroup) -> Unit,
    onToolSelectionClick: () -> Unit,
    onBuiltInModelClick: (Model) -> Unit,
    showToolSelection: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 工具调用模块
        if (showToolSelection) {
            item {
                ToolSelectionItem(
                    onClick = onToolSelectionClick
                )
            }
        }

        // 内置AIme模型
        item {
            ModelItem(
                model = BuiltInModels.aimeModel,
                onClick = { onBuiltInModelClick(BuiltInModels.aimeModel) }
            )
        }
        
        items(groups) { group ->
            GroupItem(
                group = group,
                onClick = { onGroupClick(group) }
            )
        }
    }
}

/**
 * 分组项
 */
@Composable
private fun GroupItem(
    group: ModelGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 模型列表
 */
@Composable
private fun ModelList(
    viewModel: ModelSelectionViewModel,
    group: ModelGroup,
    onModelClick: (Model) -> Unit
) {
    val models by viewModel.getModelsByGroupId(group.id).collectAsStateWithLifecycle(initialValue = emptyList())
    
    if (models.isEmpty()) {
        // 空状态
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "该分组下暂无模型",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(models) { model ->
                ModelItem(
                    model = model,
                    onClick = { onModelClick(model) }
                )
            }
        }
    }
}

/**
 * 模型项
 */
@Composable
private fun ModelItem(
    model: Model,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = model.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}