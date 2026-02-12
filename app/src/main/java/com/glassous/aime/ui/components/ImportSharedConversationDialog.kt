package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ImportSharedConversationDialog(
    onDismiss: () -> Unit,
    onImport: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading && !isSuccess) onDismiss() else if (isSuccess) onDismiss() },
        icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
        title = { Text(text = "获取分享对话") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isSuccess) {
                    Text(
                        text = "请输入分享链接或 UUID，将自动从云端拉取对话并导入到本地。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://... 或 UUID") },
                        singleLine = true,
                        enabled = !isLoading
                    )
                }

                if (isLoading) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "正在导入...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                resultMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (isSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            } else {
                Button(
                    onClick = {
                        if (input.isBlank()) return@Button
                        isLoading = true
                        resultMessage = null
                        onImport(input) { success, msg ->
                            isLoading = false
                            isSuccess = success
                            resultMessage = msg
                        }
                    },
                    enabled = input.isNotBlank() && !isLoading
                ) {
                    Text("导入")
                }
            }
        },
        dismissButton = {
            if (!isSuccess && !isLoading) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
