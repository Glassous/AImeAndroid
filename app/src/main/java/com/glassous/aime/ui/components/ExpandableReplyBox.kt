package com.glassous.aime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 识别并展示“<think>”或“【前置回复】”格式的可折叠框。
 * - 顶部展示思考过程/前置回复
 * - 正文为正式回复，默认折叠思考过程，可点击展开/收起
 */
@Composable
fun ExpandableReplyBox(
    content: String,
    textColor: Color,
    textSizeSp: Float,
    isStreaming: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHtmlPreview: ((String) -> Unit)? = null,
    onHtmlPreviewSource: ((String) -> Unit)? = null,
    useCardStyleForHtmlCode: Boolean = false
) {
    // 定义解析结果变量
    var preText by remember { mutableStateOf("") }
    var toolText by remember { mutableStateOf<String?>(null) }
    var officialText by remember { mutableStateOf<String?>(null) }
    var isThinkTagMode by remember { mutableStateOf(false) }

    // 解析逻辑
    LaunchedEffect(content) {
        // 1. 优先检查 <think> 标签模式 (DeepSeek 等推理模型)
        val thinkStart = content.indexOf("<think>")
        if (thinkStart != -1) {
            isThinkTagMode = true
            val thinkEnd = content.indexOf("</think>")

            if (thinkEnd != -1) {
                // 思考已结束
                // 提取标签内的内容（+7 是 <think> 的长度）
                preText = content.substring(thinkStart + 7, thinkEnd).trim()
                // 提取 </think> 之后的内容作为正式回复 (+8 是 </think> 的长度)
                val afterThink = content.substring(thinkEnd + 8).trim()
                if (afterThink.isNotEmpty()) {
                    officialText = afterThink
                } else {
                    officialText = null
                }
            } else {
                // 思考仍在流式输出中（未闭合）
                preText = content.substring(thinkStart + 7).trim()
                officialText = null
            }
            toolText = null // Think 模式下暂时不混合旧版工具文本逻辑
        } else {
            // 2. 回退到旧的 【前置回复】/【第一次回复】 模式
            isThinkTagMode = false
            val preLabelNew = "【前置回复】"
            val preLabelOld = "【第一次回复】"
            val tripleSep = "\n\n\n"

            val preIndexCandidate = content.indexOf(preLabelNew)
            val preIndex = if (preIndexCandidate != -1) preIndexCandidate else content.indexOf(preLabelOld)

            if (preIndex != -1) {
                val afterPreLabelStart = preIndex + if (preIndexCandidate != -1) preLabelNew.length else preLabelOld.length
                val firstSepIndex = content.indexOf(tripleSep, afterPreLabelStart)

                val preTextEnd = if (firstSepIndex != -1) firstSepIndex else content.length
                preText = content.substring(afterPreLabelStart, preTextEnd).trim()

                if (firstSepIndex != -1) {
                    val secondSepIndex = content.indexOf(tripleSep, firstSepIndex + tripleSep.length)
                    if (secondSepIndex != -1) {
                        toolText = content.substring(firstSepIndex + tripleSep.length, secondSepIndex).trim()
                        officialText = content.substring(secondSepIndex + tripleSep.length).trim()
                    } else {
                        officialText = content.substring(firstSepIndex + tripleSep.length).trim()
                    }
                } else {
                    toolText = null
                    officialText = null
                }
            } else {
                // 既没有 think 也没有前置标签，降级处理（直接渲染全文）
                officialText = content
                preText = ""
            }
        }
    }

    // 若解析后没有前置文本，直接渲染全文
    if (preText.isEmpty() && officialText == content) {
        MarkdownRenderer(
            markdown = content,
            textColor = textColor,
            textSizeSp = textSizeSp,
            onLongClick = onLongClick,
            modifier = modifier,
            onHtmlPreview = onHtmlPreview,
            onHtmlPreviewSource = onHtmlPreviewSource,
            useCardStyleForHtmlCode = useCardStyleForHtmlCode
        )
        return
    }

    // 初始状态控制：
    // 如果是思考模式且正式回复还没出来（正在思考），强制展开以便用户看到过程。
    // 一旦正式回复有了内容，就可以折叠起来不占地方。
    var expanded by remember { mutableStateOf(true) }
    // 记录是否已经执行过自动折叠，防止流式输出过程中用户手动展开后被再次折叠
    var hasAutoCollapsed by remember { mutableStateOf(false) }

    // 监听状态变化自动折叠：当正式回复开始出现时，自动收起思考过程
    LaunchedEffect(officialText) {
        if (!officialText.isNullOrBlank()) {
            if (!hasAutoCollapsed) {
                expanded = false
                hasAutoCollapsed = true
            }
        } else {
            // 还在思考（或内容被重置），保持展开并重置自动折叠标记
            expanded = true
            hasAutoCollapsed = false
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            // 顶部：折叠/展开控制栏（仅当前置文本不为空时显示）
            if (preText.isNotEmpty()) {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = MaterialTheme.shapes.small,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isThinkTagMode) "深度思考过程" else "前置回复",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.wrapContentWidth()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // 思考内容区域
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { expanded = false }
                            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                    ) {
                        // 思考内容使用略淡的颜色渲染，字体稍小
                        MarkdownRenderer(
                            markdown = preText,
                            textColor = textColor.copy(alpha = 0.7f),
                            textSizeSp = textSizeSp * 0.9f,
                            onLongClick = onLongClick,
                            modifier = Modifier.fillMaxWidth(),
                            onHtmlPreview = onHtmlPreview,
                            onHtmlPreviewSource = onHtmlPreviewSource,
                            useCardStyleForHtmlCode = useCardStyleForHtmlCode
                        )

                        // 如果思考还在继续（流式且无正式回复），显示个简单的提示
                        if (isStreaming && officialText == null) {
                            Text(
                                text = "Thinking...",
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // 中间：工具调用结果区域（兼容旧逻辑）
            if (toolText != null && toolText!!.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                MarkdownRenderer(
                    markdown = toolText!!,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    modifier = Modifier.fillMaxWidth(),
                    onHtmlPreview = onHtmlPreview,
                    onHtmlPreviewSource = onHtmlPreviewSource,
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 正式回复：正常渲染
            if (officialText != null && officialText!!.isNotEmpty()) {
                StreamingMarkdownRenderer(
                    markdown = officialText!!,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    isStreaming = isStreaming,
                    onHtmlPreview = onHtmlPreview,
                    onHtmlPreviewSource = onHtmlPreviewSource,
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode
                )
            } else if (!isStreaming && preText.isEmpty()) {
                // 既没有思考也没有正式回复的异常情况
                Text(
                    text = "等待回复…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}