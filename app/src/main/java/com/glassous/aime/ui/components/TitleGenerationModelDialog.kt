@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.ui.viewmodel.ModelConfigViewModel

@Composable
fun TitleGenerationModelSelectionDialog(
    currentModelId: String?, // null means "Follow Current"
    modelViewModel: ModelConfigViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val groups by modelViewModel.groups.collectAsState()
    var selectedModelId by remember { mutableStateOf(currentModelId) }
    
    // Paging State
    var selectedGroupIndex by remember { mutableStateOf(0) }
    
    // Initialize selected group based on current model
    LaunchedEffect(groups, currentModelId) {
        if (currentModelId != null && groups.isNotEmpty()) {
            val groupIndex = groups.indexOfFirst { group ->
                // This is a bit expensive if we don't have model->group mapping directly, 
                // but we can try to find it via the viewmodel or just iterate.
                // Since we don't have a direct "getGroupForModel" here easily without async,
                // we'll just default to 0 or try to find it if possible.
                // For now, let's just default to 0. 
                // Improvements: modelViewModel could provide this info.
                false // Placeholder
            }
            if (groupIndex != -1) {
                selectedGroupIndex = groupIndex
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标题生成模型") },
        text = {
            Column {
                // Option 1: Follow Current Model
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedModelId = null } 
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModelId == null,
                        onClick = { selectedModelId = null }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "跟随当前模型",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "使用当前对话所选的模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (groups.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedGroupIndex,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        groups.forEachIndexed { index, group ->
                            Tab(
                                selected = selectedGroupIndex == index,
                                onClick = { selectedGroupIndex = index },
                                text = { Text(group.name) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Animated Content for Paging
                    AnimatedContent(
                        targetState = selectedGroupIndex,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                            } else {
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                            }
                        },
                        label = "ModelGroupPageAnimation"
                    ) { targetIndex ->
                        if (targetIndex in groups.indices) {
                            val group = groups[targetIndex]
                            ModelGroupListPage(
                                group = group,
                                currentModelId = selectedModelId,
                                modelViewModel = modelViewModel,
                                onSelectModel = { selectedModelId = it }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("暂无模型组", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedModelId) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModelGroupListPage(
    group: ModelGroup,
    currentModelId: String?,
    modelViewModel: ModelConfigViewModel,
    onSelectModel: (String) -> Unit
) {
    val models by modelViewModel.getModelsByGroupId(group.id).collectAsState(initial = emptyList())
    
    LazyColumn(
        modifier = Modifier.height(300.dp).fillMaxWidth()
    ) {
        if (models.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "该组暂无模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(models) { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectModel(model.id) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = model.id == currentModelId,
                        onClick = { onSelectModel(model.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TitleGenerationContextStrategyDialog(
    currentStrategy: Int,
    currentN: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(currentStrategy) }
    var selectedN by remember { mutableStateOf(currentN) }
    var textN by remember { mutableStateOf(currentN.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标题生成上下文设置") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                val strategies = listOf(
                    0 to "仅发送消息 (默认)",
                    1 to "发送消息 + 回复前${selectedN}字",
                    2 to "发送消息 + 回复后${selectedN}字",
                    3 to "发送消息 + 回复前后${selectedN}字",
                    4 to "全部上下文"
                )

                strategies.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStrategy = value }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStrategy == value,
                            onClick = { selectedStrategy = value }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = selectedStrategy in 1..3) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "设置 N 的值",
                                style = MaterialTheme.typography.labelLarge
                            )
                            OutlinedTextField(
                                value = textN,
                                onValueChange = { str ->
                                    textN = str
                                    str.toIntOrNull()?.let { n ->
                                        if (n >= 1) selectedN = n
                                    }
                                },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Slider(
                            value = selectedN.toFloat().coerceIn(10f, 100f),
                            onValueChange = { 
                                selectedN = it.toInt()
                                textN = selectedN.toString()
                            },
                            valueRange = 10f..100f
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "注意：包含的上下文越多，生成标题时消耗的 Token 越多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy, selectedN) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
