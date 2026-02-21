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
import androidx.compose.ui.platform.LocalUriHandler
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
    useCardStyleForHtmlCode: Boolean = false,
    forceExpanded: Boolean = false, // 新增：强制展开参数
    enableTypewriterEffect: Boolean = true,
    onLinkClick: ((String) -> Unit)? = null
) {
    // 定义解析结果变量
    var preText by remember { mutableStateOf("") }
    var toolText by remember { mutableStateOf<String?>(null) }
    var officialText by remember { mutableStateOf<String?>(null) }
    var isThinkTagMode by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var thinkingTime by remember { mutableStateOf<String?>(null) }
    
    // 获取UriHandler用于打开链接
    val uriHandler = LocalUriHandler.current

    // 解析搜索结果中的引用链接和列表
    val citationUrls = remember(preText, isSearchMode) {
        val map = mutableMapOf<String, String>()
        if (isSearchMode && preText.isNotEmpty()) {
            // 匹配格式: 1. [Title](URL)
            // Regex explain:
            // ^(\d+)\. : 行首数字加点，捕获数字作为ID
            // \s+ : 空格
            // \[.*?\] : 标题部分
            // \((.*?)\) : URL部分，捕获URL
            val regex = Regex("""^(\d+)\.\s+\[.*?\]\((.*?)\)""", RegexOption.MULTILINE)
            regex.findAll(preText).forEach { matchResult ->
                if (matchResult.groupValues.size >= 3) {
                    val id = matchResult.groupValues[1]
                    val url = matchResult.groupValues[2]
                    map[id] = url
                }
            }
        }
        map
    }
    
    // 解析搜索结果列表用于BottomSheet展示
    val searchResultsList = remember(preText, isSearchMode) {
        val list = mutableListOf<SearchResult>()
        if (isSearchMode && preText.isNotEmpty()) {
            val regex = Regex("""^(\d+)\.\s+\[(.*?)\]\((.*?)\)""", RegexOption.MULTILINE)
            regex.findAll(preText).forEach { matchResult ->
                if (matchResult.groupValues.size >= 4) {
                    val id = matchResult.groupValues[1]
                    val title = matchResult.groupValues[2]
                    val url = matchResult.groupValues[3]
                    list.add(SearchResult(id, title, url))
                }
            }
        }
        list
    }

    // 搜索结果BottomSheet显示状态
    var showSearchBottomSheet by remember { mutableStateOf(false) }

    // 显示搜索结果BottomSheet
    if (showSearchBottomSheet && searchResultsList.isNotEmpty()) {
        SearchResultsBottomSheet(
            results = searchResultsList,
            onDismissRequest = { showSearchBottomSheet = false },
            onLinkClick = { url ->
                // 点击搜索结果链接
                try {
                    if (onLinkClick != null) {
                        onLinkClick.invoke(url)
                    } else {
                        uriHandler.openUri(url)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        )
    }


    // 解析逻辑
    LaunchedEffect(content) {
        // 0. 优先检查 <search> 标签模式 (搜索结果)
        val searchStart = content.indexOf("<search>")
        if (searchStart != -1) {
            isSearchMode = true
            isThinkTagMode = false
            val searchEnd = content.indexOf("</search>")
            
            if (searchEnd != -1) {
                preText = content.substring(searchStart + 8, searchEnd).trim()
                officialText = content.substring(searchEnd + 9).trim()
            } else {
                preText = content.substring(searchStart + 8).trim()
                officialText = null
            }
            toolText = null
            thinkingTime = null
        } else {
            isSearchMode = false
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
            thinkingTime = null // <think> 标签模式下通常没有时间信息，除非从 API 另外获取
        } else {
            // 2. 检查 Blockquote Reasoning 模式 (Gemini/DeepSeek 变体)
            // 特征：以 > 开头，且包含 "*Thought for X seconds*" 样式的行
            var isBlockquoteReasoning = false
            // 只要内容中包含这个特定的思考时间结束标记，我们就尝试解析，而不再严格要求每行都以 > 开头
            // 因为某些模型输出的代码块可能不会每行都加 >
            val lines = content.lines()
            var thoughtLineIndex = -1
            var tempTime: String? = null

            // 倒序查找效率更高，通常在中间或结尾
            for (i in lines.indices.reversed()) {
                val line = lines[i].trim()
                // 必须是引用行，且包含特定关键词
                if (line.startsWith(">") &&
                    line.contains("Thought for", ignoreCase = true) &&
                    line.contains("seconds", ignoreCase = true)) {
                    
                    thoughtLineIndex = i
                    val contentLine = line.removePrefix(">").trim()
                    val cleanLine = contentLine.replace("*", "").trim()
                    val match = Regex("Thought for (.*)", RegexOption.IGNORE_CASE).find(cleanLine)
                    if (match != null) {
                        // 如果能提取到具体内容，尝试只保留数字部分
                        val timeStr = match.groupValues[1].trim()
                        val numberMatch = Regex("(\\d+(\\.\\d+)?)").find(timeStr)
                        if (numberMatch != null) {
                            tempTime = numberMatch.groupValues[1] + "秒"
                        } else {
                            tempTime = timeStr
                        }
                    } else {
                        // 降级：直接从 cleanLine 中尝试提取数字
                        val numberMatch = Regex("(\\d+(\\.\\d+)?)").find(cleanLine)
                        if (numberMatch != null) {
                            tempTime = numberMatch.groupValues[1] + "秒"
                        } else {
                            tempTime = cleanLine
                        }
                    }
                    break
                }
            }

            if (thoughtLineIndex != -1) {
                isBlockquoteReasoning = true
                isThinkTagMode = true
                thinkingTime = tempTime
                
                // 提取思考内容：从开始到 thoughtLineIndex 之前的所有行
                val blockquoteBuilder = StringBuilder()
                for (i in 0 until thoughtLineIndex) {
                    val rawLine = lines[i]
                    // 如果行以 > 开头，去除它；否则保留原样（处理未引用的代码块等）
                    val processedLine = if (rawLine.trimStart().startsWith(">")) {
                        // 移除第一个 > 和随后的一个空格（如果有）
                        val trimmedStart = rawLine.trimStart()
                        val contentWithoutQuote = trimmedStart.removePrefix(">")
                        if (contentWithoutQuote.startsWith(" ")) {
                            contentWithoutQuote.substring(1)
                        } else {
                            contentWithoutQuote
                        }
                    } else {
                        rawLine
                    }
                    blockquoteBuilder.append(processedLine).append("\n")
                }
                preText = blockquoteBuilder.toString().trim()
                
                // 提取正式回复：thoughtLineIndex 之后的所有行
                officialText = if (thoughtLineIndex + 1 < lines.size) {
                    lines.subList(thoughtLineIndex + 1, lines.size).joinToString("\n").trim()
                } else {
                    ""
                }
                toolText = null
            }

            if (!isBlockquoteReasoning) {
                // 3. 回退到旧的 【前置回复】/【第一次回复】 模式
                isThinkTagMode = false
                thinkingTime = null
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
            useCardStyleForHtmlCode = useCardStyleForHtmlCode,
            isStreaming = isStreaming && enableTypewriterEffect
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
    LaunchedEffect(officialText, forceExpanded) {
        if (forceExpanded) {
            // 如果强制展开，则始终保持展开状态
            expanded = true
        } else if (!officialText.isNullOrBlank()) {
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
                if (isSearchMode) {
                    // 搜索模式：显示搜索结果卡片
                    SearchResultsCard(
                        resultCount = searchResultsList.size,
                        onClick = { showSearchBottomSheet = true },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    // 思考/前置模式：原有的折叠/展开控制栏
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
                                text = when {
                                    isThinkTagMode -> "深度思考过程"
                                    else -> "前置回复"
                                },
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.wrapContentWidth()
                            )
                            if (thinkingTime != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = thinkingTime!!,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.wrapContentWidth()
                                )
                            }
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
                            // 如果正在流式输出且还没有正式回复（说明正在输出思考内容），则禁用代码块和LaTeX渲染以防抖动
                            val isPreTextStreaming = isStreaming && officialText == null
                            MarkdownRenderer(
                                markdown = preText,
                                textColor = textColor.copy(alpha = 0.7f),
                                textSizeSp = textSizeSp * 0.9f,
                                onLongClick = onLongClick,
                                modifier = Modifier.fillMaxWidth(),
                                enableCodeBlocks = !isPreTextStreaming,
                                enableLatex = !isPreTextStreaming,
                                onHtmlPreview = onHtmlPreview,
                                onHtmlPreviewSource = onHtmlPreviewSource,
                                useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                                isStreaming = isPreTextStreaming && enableTypewriterEffect,
                                onLinkClick = onLinkClick
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
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                    isStreaming = isStreaming && officialText == null && enableTypewriterEffect,
                    onLinkClick = onLinkClick
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
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                    enableTypewriterEffect = enableTypewriterEffect,
                    onLinkClick = onLinkClick,
                    onCitationClick = { id ->
                        if (isSearchMode) {
                            val url = citationUrls[id]
                            if (url != null) {
                                try {
                                    if (onLinkClick != null) {
                                        onLinkClick.invoke(url)
                                    } else {
                                        uriHandler.openUri(url)
                                    }
                                } catch (e: Exception) {
                                    // 无法打开链接，可能URL无效， fallback到展开详情
                                    expanded = true
                                }
                            } else {
                                expanded = true
                            }
                        }
                    }
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