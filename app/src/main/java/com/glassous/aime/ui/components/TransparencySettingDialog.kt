package com.glassous.aime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import kotlin.math.roundToInt

@Composable
fun TransparencySettingDialog(
    currentAlpha: Float,
    onAlphaChange: (Float) -> Unit,
    currentHamburgerAlpha: Float,
    onHamburgerAlphaChange: (Float) -> Unit,
    currentModelTextAlpha: Float,
    onModelTextAlphaChange: (Float) -> Unit,
    currentInputInnerAlpha: Float,
    onInputInnerAlphaChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var tempAlpha by remember { mutableStateOf(currentAlpha) }
    var tempHamburgerAlpha by remember { mutableStateOf(currentHamburgerAlpha) }
    var tempModelTextAlpha by remember { mutableStateOf(currentModelTextAlpha) }
    var tempInputInnerAlpha by remember { mutableStateOf(currentInputInnerAlpha) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(16.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "透明度设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    // 铺设超长文本：使用《罪与罚》公共领域译文片段
                    val previewText = stringResource(id = com.glassous.aime.R.string.preview_long_text_dostoevsky)
                    Text(
                        text = previewText,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start
                    )
                    Column(Modifier.fillMaxSize()) {
                        // 顶部栏（整体遮罩 + 按钮背景）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = tempAlpha))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = tempHamburgerAlpha),
                                    shape = CircleShape,
                                    tonalElevation = 0.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = tempModelTextAlpha),
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = 0.dp
                                ) {
                                    Text(
                                        text = "模型选择",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 中部内容占位留白（由底层超长文本覆盖）
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp)
                        )

                        // 输入区域（外层遮罩 + 内部背景）
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = tempAlpha),
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 预览中采用简化的输入容器以降低高度
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = tempInputInnerAlpha))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "输入消息…",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        softWrap = false,
                                        fontSize = 12.sp
                                    )
                                }
                                FilledIconButton(
                                    onClick = {},
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 滚动区域：将设置项置于可滚动容器，固定预览框
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // 顶部栏整体遮罩透明度
                Text(
                    text = "顶部栏整体遮罩透明度",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presetValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                    val presetLabels = listOf("0", "25", "50", "75", "100")

                    presetValues.forEachIndexed { index, value ->
                        val isSelected = (tempAlpha - value).let { kotlin.math.abs(it) < 0.01f }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempAlpha = value
                                    onAlphaChange(value)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = presetLabels[index],
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 顶部栏整体遮罩透明度滑块
                Text(
                    text = "精确调节: ${(tempAlpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = tempAlpha,
                    onValueChange = { 
                        tempAlpha = it
                        onAlphaChange(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 顶部栏按钮背景透明度（汉堡/模型）快捷设置
                Text(
                    text = "顶部栏按钮背景透明度（汉堡/模型）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presetValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                    val presetLabels = listOf("0", "25", "50", "75", "100")

                    presetValues.forEachIndexed { index, value ->
                        val isSelected = (tempHamburgerAlpha - value).let { kotlin.math.abs(it) < 0.01f } &&
                                (tempModelTextAlpha - value).let { kotlin.math.abs(it) < 0.01f }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempHamburgerAlpha = value
                                    tempModelTextAlpha = value
                                    onHamburgerAlphaChange(value)
                                    onModelTextAlphaChange(value)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = presetLabels[index],
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "精确调节: ${(tempHamburgerAlpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = tempHamburgerAlpha,
                    onValueChange = {
                        tempHamburgerAlpha = it
                        tempModelTextAlpha = it
                        onHamburgerAlphaChange(it)
                        onModelTextAlphaChange(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // 输入框内部背景透明度（快捷设置 + 精确调节）
                Text(
                    text = "输入框内部背景透明度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presetValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                    val presetLabels = listOf("0", "25", "50", "75", "100")

                    presetValues.forEachIndexed { index, value ->
                        val isSelected = (tempInputInnerAlpha - value).let { kotlin.math.abs(it) < 0.01f }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempInputInnerAlpha = value
                                    onInputInnerAlphaChange(value)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = presetLabels[index],
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "精确调节: ${(tempInputInnerAlpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = tempInputInnerAlpha,
                    onValueChange = {
                        tempInputInnerAlpha = it
                        onInputInnerAlphaChange(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
                }

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Text("确定")
                    }
                }
            }
        }
    }
}