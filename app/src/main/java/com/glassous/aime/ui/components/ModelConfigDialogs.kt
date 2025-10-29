package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, providerUrl: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var providerUrl by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建模型分组") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分组名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入分组名称") }
                )
                
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入Base URL") }
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入API Key") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = providerUrl,
                    onValueChange = { providerUrl = it },
                    label = { Text("服务商官网") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入服务商官网") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), baseUrl.trim(), apiKey.trim(), providerUrl.trim().ifBlank { null })
                },
                enabled = true
            ) {
                Text("创建")
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
fun AddModelDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, modelName: String, remark: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入模型显示名称") }
                )
                
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入模型名称") }
                )
                
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    placeholder = { Text("例如价格、用途、限制说明等") }
                )

                Text(
                    text = "提示：模型名称应与API提供商的实际模型名称一致",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), modelName.trim(), remark.trim().ifBlank { null })
                },
                enabled = true
            ) {
                Text("添加")
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
fun EditGroupDialog(
    group: ModelGroup,
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, providerUrl: String?) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var baseUrl by remember { mutableStateOf(group.baseUrl) }
    var apiKey by remember { mutableStateOf(group.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var providerUrl by remember { mutableStateOf(group.providerUrl ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑分组") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分组名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入分组名称") }
                )
                
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入Base URL") }
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入API Key") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = providerUrl,
                    onValueChange = { providerUrl = it },
                    label = { Text("服务商官网") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入服务商官网") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), baseUrl.trim(), apiKey.trim(), providerUrl.trim().ifBlank { null })
                },
                enabled = true
            ) {
                Text("保存")
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
fun EditModelDialog(
    model: Model,
    onDismiss: () -> Unit,
    onConfirm: (name: String, modelName: String, remark: String?) -> Unit
) {
    var name by remember { mutableStateOf(model.name) }
    var modelName by remember { mutableStateOf(model.modelName) }
    var remark by remember { mutableStateOf(model.remark ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模型") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入模型显示名称") }
                )
                
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入模型名称") }
                )
                
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    placeholder = { Text("例如价格、用途、限制说明等") }
                )

                Text(
                    text = "提示：模型名称应与API提供商的实际模型名称一致",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), modelName.trim(), remark.trim().ifBlank { null })
                },
                enabled = true
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}