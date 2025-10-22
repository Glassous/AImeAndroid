package com.glassous.aime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.7f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            
            // 内容区域
            if (uiState.selectedGroup == null) {
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
                    group = uiState.selectedGroup!!,
                    onModelClick = { model ->
                        viewModel.selectModel(model)
                    }
                )
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                .fillMaxWidth()
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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