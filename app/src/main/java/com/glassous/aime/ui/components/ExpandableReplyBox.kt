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
 * 支持同时展示搜索前的思考（第一段思考）、搜索结果、搜索后的思考（第二段思考）以及正式回复。
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
    useCardStyleForHtmlCode: Boolean = true,
    forceExpanded: Boolean = false, // 新增：强制展开参数
    enableTypewriterEffect: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onShowSearchResults: ((List<SearchResult>) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onUrlPreview: ((String) -> Unit)? = null,
    isShareMode: Boolean = false
) {
    // 定义解析结果变量
    var firstThought by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<String?>(null) }
    var secondThought by remember { mutableStateOf<String?>(null) }
    var officialText by remember { mutableStateOf<String?>(null) }
    var firstThinkingTime by remember { mutableStateOf<String?>(null) }
    var secondThinkingTime by remember { mutableStateOf<String?>(null) }
    
    // 辅助状态
    var isSearchMode by remember { mutableStateOf(false) }
    
    // 获取UriHandler用于打开链接
    val uriHandler = LocalUriHandler.current

    // 解析搜索结果中的引用链接和列表 (用于正式回复中的引用点击)
    val citationUrls = remember(searchResults) {
        val map = mutableMapOf<String, String>()
        if (!searchResults.isNullOrEmpty()) {
            val regex = Regex("""^(\d+)\.\s+\[.*?\]\((.*?)\)""", RegexOption.MULTILINE)
            regex.findAll(searchResults!!).forEach { matchResult ->
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
    val searchResultsList = remember(searchResults) {
        val list = mutableListOf<SearchResult>()
        if (!searchResults.isNullOrEmpty()) {
            // Regex to match: 1. [Title](Url) optional ![image](ImageUrl)
            // 优化正则：
            // 1. 标题匹配非贪婪，但在 ] 之前不应出现换行
            // 2. URL 匹配非贪婪
            // 3. 图片部分可选
            val regex = Regex("""^(\d+)\.\s+\[(.*?)\]\((.*?)\)(?:\s+!\[image\]\((.*?)\))?""", RegexOption.MULTILINE)
            regex.findAll(searchResults!!).forEach { matchResult ->
                if (matchResult.groupValues.size >= 4) {
                    val id = matchResult.groupValues[1]
                    val title = matchResult.groupValues[2]
                    val url = matchResult.groupValues[3]
                    val image = if (matchResult.groupValues.size > 4) matchResult.groupValues[4].takeIf { it.isNotBlank() } else null
                    list.add(SearchResult(id, title, url, image))
                }
            }
        }
        list
    }

    // 搜索结果BottomSheet显示状态
    var showSearchBottomSheet by remember { mutableStateOf(false) }

    // 显示搜索结果BottomSheet (委托给外部处理)
    LaunchedEffect(showSearchBottomSheet) {
        if (showSearchBottomSheet && searchResultsList.isNotEmpty()) {
            onShowSearchResults?.invoke(searchResultsList)
            showSearchBottomSheet = false
        }
    }

    // 解析逻辑
    LaunchedEffect(content) {
        var tempFirstThought: String? = null
        var tempSearchResults: String? = null
        var tempSecondThought: String? = null
        var tempOfficialText: String? = null
        var tempFirstThinkingTime: String? = null
        var tempSecondThinkingTime: String? = null

        val searchStart = content.indexOf("<search>")
        val searchEnd = content.indexOf("</search>")

        if (searchStart != -1) {
            // --- 存在搜索结果 ---
            isSearchMode = true
            
            // 1. 解析搜索前的内容 (第一段思考)
            val preSearchContent = content.substring(0, searchStart).trim()
            if (preSearchContent.isNotEmpty()) {
                val parsed = parseThoughtContent(preSearchContent)
                // 优先使用解析出的思考内容，如果没有则看是否有旧版前置回复
                tempFirstThought = parsed.thought
                tempFirstThinkingTime = parsed.time
                
                // 如果解析结果为空但内容不为空，且看起来像是前置回复
                if (tempFirstThought == null && preSearchContent.contains("【前置回复】")) {
                     tempFirstThought = preSearchContent.substringAfter("【前置回复】").trim()
                } else if (tempFirstThought == null && parsed.official != null && parsed.official.isNotEmpty()) {
                    // 如果既没有think标签也没有前置回复标签，但有内容，可能是未标记的思考
                    // 这种情况下通常不做处理，或者视情况而定。
                    // 为了防止丢失信息，如果它看起来像思考（例如在搜索之前），我们可以将其视为思考
                    // 但 DeepSeek 等模型通常会有 <think> 标签。
                    // 暂时忽略非思考标签的内容，除非它是纯文本
                }
            }

            // 2. 解析搜索结果
            if (searchEnd != -1) {
                tempSearchResults = content.substring(searchStart + 8, searchEnd).trim()
                
                // 3. 解析搜索后的内容 (第二段思考 + 正式回复)
                val postSearchContent = content.substring(searchEnd + 9).trim()
                if (postSearchContent.isNotEmpty()) {
                    val parsed = parseThoughtContent(postSearchContent)
                    tempSecondThought = parsed.thought
                    tempSecondThinkingTime = parsed.time
                    tempOfficialText = parsed.official
                }
            } else {
                // 搜索块未闭合（正在流式输出搜索结果）
                tempSearchResults = content.substring(searchStart + 8).trim()
            }
            
        } else {
            // --- 无搜索结果 (标准模式) ---
            isSearchMode = false
            val parsed = parseThoughtContent(content)
            tempFirstThought = parsed.thought
            tempFirstThinkingTime = parsed.time
            tempOfficialText = parsed.official
            
            // 兼容旧版 toolText (虽然现在很少用，但保留逻辑)
            // parseThoughtContent 不处理 toolText，如果 parsed.official 中包含 toolText 格式，需要额外处理吗？
            // 鉴于目前主要关注 <think> 和 <search>，旧版 toolText 逻辑可能不再兼容新解析器。
            // 如果需要完全兼容，可以在 parseThoughtContent 中加入 toolText 识别，或者在这里额外处理。
            // 考虑到用户需求是修复联网搜索时的思考模型问题，且新版主要使用 <search>，这里简化处理。
        }

        firstThought = tempFirstThought
        searchResults = tempSearchResults
        secondThought = tempSecondThought
        officialText = tempOfficialText
        firstThinkingTime = tempFirstThinkingTime
        secondThinkingTime = tempSecondThinkingTime
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            
            // 1. 第一段思考 / 深度思考过程
            if (!firstThought.isNullOrEmpty()) {
                val title = if (searchResults != null) "第一段思考" else "深度思考过程"
                ExpandableThoughtBlock(
                    title = title,
                    content = firstThought!!,
                    thinkingTime = firstThinkingTime,
                    isStreaming = isStreaming && searchResults == null && officialText == null, // 如果还在搜或者还在回，可能第一段还在变？通常第一段完了才搜。
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    onHtmlPreview = onHtmlPreview,
                    onHtmlPreviewSource = onHtmlPreviewSource,
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                    enableTypewriterEffect = enableTypewriterEffect,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onUrlPreview = onUrlPreview,
                    forceExpanded = forceExpanded,
                    isShareMode = isShareMode
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. 搜索结果
            if (!searchResults.isNullOrEmpty()) {
                val statusText = if (searchResultsList.isEmpty()) searchResults else null
                SearchResultsCard(
                    resultCount = searchResultsList.size,
                    status = statusText,
                    onClick = { if (searchResultsList.isNotEmpty() && !isShareMode) showSearchBottomSheet = true },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 3. 第二段思考
            if (!secondThought.isNullOrEmpty()) {
                ExpandableThoughtBlock(
                    title = "第二段思考",
                    content = secondThought!!,
                    thinkingTime = secondThinkingTime,
                    isStreaming = isStreaming && officialText == null, // 如果没有正式回复，说明可能还在输出第二段思考
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onLongClick = onLongClick,
                    onHtmlPreview = onHtmlPreview,
                    onHtmlPreviewSource = onHtmlPreviewSource,
                    useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                    enableTypewriterEffect = enableTypewriterEffect,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onUrlPreview = onUrlPreview,
                    forceExpanded = forceExpanded,
                    isShareMode = isShareMode
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. 正式回复
    if (!officialText.isNullOrEmpty()) {
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
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onUrlPreview = onUrlPreview,
            onCitationClick = { id ->
                if (isSearchMode && !isShareMode) {
                    val url = citationUrls[id]
                    if (url != null) {
                        try {
                            if (onLinkClick != null) {
                                onLinkClick.invoke(url)
                            } else {
                                uriHandler.openUri(url)
                            }
                        } catch (e: Exception) {
                            // 无法打开链接，fallback到展开详情
                            showSearchBottomSheet = true
                        }
                    } else {
                        showSearchBottomSheet = true
                    }
                }
            },
            isShareMode = isShareMode
        )
    } else if (!isStreaming && firstThought == null && searchResults == null && secondThought == null) {
                // 既没有思考也没有正式回复的异常情况
                Text(
                    text = "等待回复…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (isStreaming && officialText == null && (secondThought != null || searchResults != null)) {
                 // 正在生成中... (例如正在生成第二段思考，或者刚搜完)
                 // ExpandableThoughtBlock 会显示 "Thinking..."，这里不需要额外显示
            }
        }
    }
}

@Composable
private fun ExpandableThoughtBlock(
    title: String,
    content: String,
    thinkingTime: String?,
    isStreaming: Boolean,
    textColor: Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    onHtmlPreview: ((String) -> Unit)?,
    onHtmlPreviewSource: ((String) -> Unit)?,
    useCardStyleForHtmlCode: Boolean,
    enableTypewriterEffect: Boolean,
    onLinkClick: ((String) -> Unit)?,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    onUrlPreview: ((String) -> Unit)? = null,
    forceExpanded: Boolean,
    isShareMode: Boolean = false // Added default value
) {
    // 初始状态控制：
    // 如果是流式输出且内容还在生成（isStreaming=true），强制展开。
    // 如果 forceExpanded=true，强制展开。
    // 否则默认折叠（或者默认展开？原逻辑是默认展开，有正式回复后自动折叠。这里作为独立块，可以默认折叠，或者保持原逻辑）
    // 为了体验，如果是正在生成的思考块，应该展开。如果是历史记录，应该折叠。
    
    // 这里简化逻辑：默认折叠，但如果是正在流式传输当前块，则展开。
    var expanded by remember { mutableStateOf(false) }
    
    // 自动展开逻辑
    LaunchedEffect(isStreaming, forceExpanded) {
        if (forceExpanded || isStreaming) {
            expanded = true
        } else {
            // 生成结束后自动折叠？原逻辑是“正式回复出现后自动折叠”。
            // 这里如果是历史记录，默认为 false (remember 初始值)。
            // 如果刚生成完 (isStreaming 变 false)，可以保持 expanded 或自动折叠。
            // 为了整洁，生成完后自动折叠通常较好。
            expanded = false
        }
    }

    Surface(
        onClick = { if (!isShareMode) expanded = !expanded },
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.wrapContentWidth()
            )
            if (thinkingTime != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = thinkingTime,
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

    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    if (!isShareMode) {
                        it.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = false }
                    } else {
                        it
                    }
                }
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
        ) {
            // 思考内容使用略淡的颜色渲染，字体稍小
            MarkdownRenderer(
                markdown = content,
                textColor = textColor.copy(alpha = 0.7f),
                textSizeSp = textSizeSp * 0.9f,
                onLongClick = onLongClick,
                modifier = Modifier.fillMaxWidth(),
                enableCodeBlocks = !isStreaming, // 流式输出时禁用代码块以防抖动
                enableLatex = !isStreaming,
                onHtmlPreview = onHtmlPreview,
                onHtmlPreviewSource = onHtmlPreviewSource,
                useCardStyleForHtmlCode = useCardStyleForHtmlCode,
                isStreaming = isStreaming && enableTypewriterEffect,
                onLinkClick = onLinkClick,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onUrlPreview = onUrlPreview,
                isShareMode = isShareMode
            )

            if (isStreaming) {
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

private data class ParsedThoughtContent(
    val thought: String?,
    val time: String?,
    val official: String?
)

private fun parseThoughtContent(content: String): ParsedThoughtContent {
    // 1. 检查 <think> 标签模式
    val thinkStart = content.indexOf("<think>")
    if (thinkStart != -1) {
        val thinkEnd = content.indexOf("</think>")
        if (thinkEnd != -1) {
            val thought = content.substring(thinkStart + 7, thinkEnd).trim()
            val official = content.substring(thinkEnd + 8).trim()
            return ParsedThoughtContent(thought, null, official.ifEmpty { null })
        } else {
            // 思考未闭合
            val thought = content.substring(thinkStart + 7).trim()
            return ParsedThoughtContent(thought, null, null)
        }
    }

    // 2. 检查 Blockquote Reasoning 模式
    // 特征：以 > 开头，且包含 "*Thought for X seconds*" 样式的行
    val lines = content.lines()
    var thoughtLineIndex = -1
    var tempTime: String? = null

    // 倒序查找效率更高，通常在中间或结尾
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()
        if (line.startsWith(">") &&
            line.contains("Thought for", ignoreCase = true) &&
            line.contains("seconds", ignoreCase = true)) {
            
            thoughtLineIndex = i
            val contentLine = line.removePrefix(">").trim()
            val cleanLine = contentLine.replace("*", "").trim()
            val match = Regex("Thought for (.*)", RegexOption.IGNORE_CASE).find(cleanLine)
            if (match != null) {
                val timeStr = match.groupValues[1].trim()
                val numberMatch = Regex("(\\d+(\\.\\d+)?)").find(timeStr)
                if (numberMatch != null) {
                    tempTime = numberMatch.groupValues[1] + "秒"
                } else {
                    tempTime = timeStr
                }
            } else {
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
        val blockquoteBuilder = StringBuilder()
        for (i in 0 until thoughtLineIndex) {
            val rawLine = lines[i]
            val processedLine = if (rawLine.trimStart().startsWith(">")) {
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
        val thought = blockquoteBuilder.toString().trim()
        
        val official = if (thoughtLineIndex + 1 < lines.size) {
            lines.subList(thoughtLineIndex + 1, lines.size).joinToString("\n").trim()
        } else {
            ""
        }
        
        return ParsedThoughtContent(thought, tempTime, official.ifEmpty { null })
    }

    // 3. 检查旧版【前置回复】模式
    val preLabelNew = "【前置回复】"
    val preLabelOld = "【第一次回复】"
    val tripleSep = "\n\n\n"

    val preIndexCandidate = content.indexOf(preLabelNew)
    val preIndex = if (preIndexCandidate != -1) preIndexCandidate else content.indexOf(preLabelOld)

    if (preIndex != -1) {
        val afterPreLabelStart = preIndex + if (preIndexCandidate != -1) preLabelNew.length else preLabelOld.length
        val firstSepIndex = content.indexOf(tripleSep, afterPreLabelStart)

        val preTextEnd = if (firstSepIndex != -1) firstSepIndex else content.length
        val thought = content.substring(afterPreLabelStart, preTextEnd).trim()

        val official = if (firstSepIndex != -1) {
            val secondSepIndex = content.indexOf(tripleSep, firstSepIndex + tripleSep.length)
            if (secondSepIndex != -1) {
                // Skip tool text for thought parsing purpose, just take what's after
                content.substring(secondSepIndex + tripleSep.length).trim()
            } else {
                content.substring(firstSepIndex + tripleSep.length).trim()
            }
        } else {
            null
        }
        return ParsedThoughtContent(thought, null, official)
    }

    // 4. 无思考内容
    return ParsedThoughtContent(null, null, content)
}
