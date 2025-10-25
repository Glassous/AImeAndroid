package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSizeSettingDialog(
    currentFontSize: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var tempFontSize by remember { mutableStateOf(currentFontSize) }
    val dialogBlurState = LocalDialogBlurState.current

    // 当弹窗显示时启用背景模糊
    LaunchedEffect(Unit) {
        dialogBlurState.value = true
    }

    // 当弹窗关闭时禁用背景模糊
    DisposableEffect(Unit) {
        onDispose {
            dialogBlurState.value = false
        }
    }

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
                // 标题
                Text(
                    text = "聊天字体大小",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 字体大小滑动条
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "字体大小: ${tempFontSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Slider(
                        value = tempFontSize,
                        onValueChange = { tempFontSize = it },
                        valueRange = 10f..24f,
                        steps = 13, // 10-24共15个值，减去两端点，中间13个步骤
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 字体预览区域
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "预览效果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 中文预览
                        Text(
                            text = "这是中文预览文本",
                            fontSize = tempFontSize.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // 英文预览
                        Text(
                            text = "This is English preview text",
                            fontSize = tempFontSize.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // 数字和符号预览
                        Text(
                            text = "1234567890 !@#$%^&*()",
                            fontSize = tempFontSize.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            onConfirm(tempFontSize)
                            onDismiss()
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}