package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.glassous.aime.data.model.MinimalModeConfig
import com.glassous.aime.data.model.MinimalModeItem
import com.glassous.aime.data.model.getMinimalModeItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalModeConfigDialog(
    config: MinimalModeConfig,
    onDismiss: () -> Unit,
    onConfigChange: (MinimalModeConfig) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "极简模式配置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭"
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "选择要在极简模式下隐藏的界面元素",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                     modifier = Modifier.height(300.dp),
                     verticalArrangement = Arrangement.spacedBy(8.dp)
                 ) {
                     items(getMinimalModeItems(config)) { item ->
                         MinimalModeConfigItem(
                             item = item,
                             onToggle = { enabled ->
                                 val newConfig = when (item.id) {
                                     "navigation_menu" -> config.copy(hideNavigationMenu = enabled)
                                     "welcome_text" -> config.copy(hideWelcomeText = enabled)
                                     "cloud_download" -> config.copy(hideCloudDownloadButton = enabled)
                                     "cloud_upload" -> config.copy(hideCloudUploadButton = enabled)
                                     "scroll_to_bottom" -> config.copy(hideScrollToBottomButton = enabled)
                                     "input_border" -> config.copy(hideInputBorder = enabled)
                                     "send_button_bg" -> config.copy(hideSendButtonBackground = enabled)
                                     else -> config
                                 }
                                 onConfigChange(newConfig)
                             }
                         )
                     }
                 }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun MinimalModeConfigItem(
    item: MinimalModeItem,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 组件图标预览
                Card(
                    modifier = Modifier.size(40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isEnabled) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else 
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.name,
                            tint = if (item.isEnabled) 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 开关
            Switch(
                checked = item.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }
    }
}