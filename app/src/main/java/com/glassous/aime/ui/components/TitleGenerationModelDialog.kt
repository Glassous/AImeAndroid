package com.glassous.aime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
fun TitleGenerationModelDialog(
    currentModelId: String?, // null means "Follow Current"
    modelViewModel: ModelConfigViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val groups by modelViewModel.groups.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标题生成模型") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Option 1: Follow Current Model
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(null) } // Select null for "Follow Current"
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentModelId == null,
                            onClick = { onConfirm(null) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "跟随当前模型",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "使用当前对话所选的模型生成标题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider()
                }

                // Groups
                items(groups) { group ->
                    ModelGroupItem(
                        group = group,
                        currentModelId = currentModelId,
                        modelViewModel = modelViewModel,
                        onSelectModel = onConfirm
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModelGroupItem(
    group: ModelGroup,
    currentModelId: String?,
    modelViewModel: ModelConfigViewModel,
    onSelectModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Fetch models for this group
    val models by modelViewModel.getModelsByGroupId(group.id).collectAsState(initial = emptyList())

    // Check if any model in this group is selected to auto-expand (optional, but nice)
    LaunchedEffect(models, currentModelId) {
        if (models.any { it.id == currentModelId }) {
            expanded = true
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开"
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                if (models.isEmpty()) {
                    Text(
                        text = "暂无模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model.id) }
                                .padding(vertical = 8.dp),
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
    }
}
