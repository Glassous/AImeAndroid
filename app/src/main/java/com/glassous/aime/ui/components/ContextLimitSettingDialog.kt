package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextLimitSettingDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var tempLimit by remember { mutableStateOf(currentLimit) }
    var textValue by remember { mutableStateOf(if (currentLimit <= 0) "0" else currentLimit.toString()) }
    var isValid by remember { mutableStateOf(true) }
    val dialogBlurState = LocalDialogBlurState.current

    // 显示时启用背景模糊，关闭时还原
    LaunchedEffect(Unit) { dialogBlurState.value = true }
    DisposableEffect(Unit) { onDispose { dialogBlurState.value = false } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "最大上下文限制",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 说明文字
                Text(
                    text = "同一对话仅向AI发送最近N条消息。选择0表示不限制。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 预设值快捷按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(0, 3, 5)
                    presets.forEach { preset ->
                        val selected = tempLimit == preset
                        FilterChip(
                            onClick = {
                                tempLimit = preset
                                textValue = preset.toString()
                                isValid = true
                            },
                            label = { Text(if (preset <= 0) "无限" else "$preset 条") },
                            selected = selected
                        )
                    }
                }

                // 自定义精确输入（非负整数）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自定义输入（≥ 0）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newText ->
                            val filtered = newText.filter { it.isDigit() }
                            textValue = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed == null) {
                                isValid = false
                            } else {
                                isValid = parsed >= 0
                                tempLimit = parsed
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：0、3、5、9、20") }
                    )
                    if (!isValid) {
                        Text(
                            text = "请输入一个大于等于 0 的整数",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // 精确调节（当不是无限时启用）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isUnlimited = tempLimit <= 0
                    Text(
                        text = if (isUnlimited) "当前：无限" else "当前：${tempLimit}条",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = if (isUnlimited) 0f else tempLimit.coerceIn(1, 50).toFloat(),
                        onValueChange = { v ->
                            if (!isUnlimited) {
                                tempLimit = v.toInt().coerceIn(1, 50)
                                textValue = tempLimit.toString()
                                isValid = true
                            }
                        },
                        valueRange = 0f..50f,
                        steps = 49,
                        enabled = !isUnlimited,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isValid && textValue.isNotBlank()) {
                                onConfirm(tempLimit)
                                onDismiss()
                            }
                        },
                        enabled = isValid && textValue.isNotBlank()
                    ) { Text("确认") }
                }
            }
        }
    }
}