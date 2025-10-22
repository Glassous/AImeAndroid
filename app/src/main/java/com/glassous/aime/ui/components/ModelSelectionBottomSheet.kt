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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel

/**
 * 模型选择Bottom Sheet
 * 实现两级菜单：第一级为分组，第二级为具体模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionBottomSheet(
    viewModel: ModelSelectionViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
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
                    // 显示"选择模型"标题
                    Text(
                        text = "选择模型",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp)
                    )
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
                            }
                        )
                    } else {
                        // 第二级：显示选中分组下的模型列表
                        ModelList(
                            viewModel = viewModel,
                            group = selectedGroup,
                            onModelClick = { model ->
                                viewModel.selectModel(model)
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
 * 分组列表
 */
@Composable
private fun GroupList(
    groups: List<ModelGroup>,
    onGroupClick: (ModelGroup) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
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
                Text(
                    text = group.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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