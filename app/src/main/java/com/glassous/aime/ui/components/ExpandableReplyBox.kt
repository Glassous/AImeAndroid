package com.glassous.aime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 识别并展示“【第一次回复】…【正式回复】…”格式的可折叠框。
 * - 顶部展示“第一次回复”的概要
 * - 正文为“正式回复”，默认折叠，可点击展开/收起
 */
@Composable
fun ExpandableReplyBox(
    content: String,
    textColor: Color,
    textSizeSp: Float,
    isStreaming: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 兼容旧标记，同时统一渲染为“【前置回复】”
    val preLabelNew = "【前置回复】"
    val preLabelOld = "【第一次回复】"
    // 移除“正式回复”和“工具调用结果”的可见标签，改用三个换行作为分隔符
    val tripleSep = "\n\n\n"

    val preIndexCandidate = content.indexOf(preLabelNew)
    val preIndex = if (preIndexCandidate != -1) preIndexCandidate else content.indexOf(preLabelOld)
    // 使用分隔符识别中间工具结果与正式回复
    val afterPreLabelStart = preIndex + if (preIndexCandidate != -1) preLabelNew.length else preLabelOld.length
    val firstSepIndex = content.indexOf(tripleSep, afterPreLabelStart)

    // 若没有“第一次回复”标记，则不展示折叠框
    if (preIndex == -1) {
        MarkdownRenderer(
            markdown = content,
            textColor = textColor,
            textSizeSp = textSizeSp,
            onLongClick = onLongClick,
            modifier = modifier
        )
        return
    }

    val preLabelLength = if (preIndexCandidate != -1) preLabelNew.length else preLabelOld.length
    val preTextEnd = if (firstSepIndex != -1) firstSepIndex else content.length
    val preText = content.substring(preIndex + preLabelLength, preTextEnd).trim()

    var toolText: String? = null
    var officialText: String? = null
    if (firstSepIndex != -1) {
        val secondSepIndex = content.indexOf(tripleSep, firstSepIndex + tripleSep.length)
        if (secondSepIndex != -1) {
            toolText = content.substring(firstSepIndex + tripleSep.length, secondSepIndex).trim()
            officialText = content.substring(secondSepIndex + tripleSep.length).trim()
        } else {
            officialText = content.substring(firstSepIndex + tripleSep.length).trim()
        }
    }

    // 初始保持展开；在正式回复出现后自动折叠
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(officialText) {
        // 正式回复开始（非空）时自动折叠；正式回复未开始前保持展开
        expanded = officialText.isNullOrEmpty()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            // 顶部：折叠/展开第一次回复
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (expanded) {
                    Text(
                        text = preLabelNew,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 折叠时隐藏“前置回复”文字，但保留右侧按钮位置
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (expanded && preText.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MarkdownRenderer(
                        markdown = preText,
                        textColor = textColor,
                        textSizeSp = textSizeSp,
                        onLongClick = onLongClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (toolText != null && toolText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
            // 中间：工具调用结果区域（无标题、无折叠），与正式回复视觉一致
            if (toolText != null && toolText.isNotEmpty()) {
                MarkdownRenderer(
                    markdown = toolText,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 正式回复：正常渲染，不折叠
            if (officialText != null && officialText.isNotEmpty()) {
                StreamingMarkdownRenderer(
                    markdown = officialText,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    isStreaming = isStreaming
                )
            } else {
                Text(
                    text = "等待正式回复…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}