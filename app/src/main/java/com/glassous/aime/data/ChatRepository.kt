package com.glassous.aime.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.AutoSyncPreferences
import com.glassous.aime.data.preferences.ContextPreferences
import com.glassous.aime.data.preferences.UserProfilePreferences
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
import com.glassous.aime.data.model.UserProfile
import com.google.gson.Gson
import java.util.Date
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val modelPreferences: ModelPreferences,
    private val autoSyncPreferences: AutoSyncPreferences,
    private val cloudSyncViewModel: CloudSyncViewModel,
    private val contextPreferences: ContextPreferences,
    private val userProfilePreferences: UserProfilePreferences,
    private val openAiService: OpenAiService = OpenAiService(),
    private val webSearchService: WebSearchService = WebSearchService(),
    private val weatherService: WeatherService = WeatherService(),
    private val stockService: StockService = StockService()
) {
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return chatDao.getAllConversations()
    }

    suspend fun sendMessage(
        conversationId: Long,
        message: String,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
    ): Result<ChatMessage> {
        return try {
            // Save user message
            val userMessage = ChatMessage(
                conversationId = conversationId,
                content = message,
                isFromUser = true,
                timestamp = Date()
            )
            chatDao.insertMessage(userMessage)

            // Update conversation (user side)
            updateConversationAfterMessage(conversationId, message)

            // Resolve selected model config
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "请先选择模型（设置→模型配置）",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
            if (model == null) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "所选模型不存在或已删除，请重新选择。",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("Selected model not found"))
            }
            val group = modelConfigRepository.getGroupById(model.groupId)
            if (group == null) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("Model group not found"))
            }

            // Build conversation context for OpenAI standard
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val baseMessages = history
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }
                .toMutableList()
            
            baseMessages.add(OpenAiChatMessage(role = "user", content = message))
            val messages = limitContext(baseMessages).toMutableList()

            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
            buildUserProfileSystemMessage()?.let { messages.add(it) }

            // 关键词偏好：提升天气工具的选择概率
            val weatherKeywords = listOf(
                "天气", "气温", "气候", "下雨", "降雨", "降雪", "风力", "空气质量",
                "雾霾", "穿衣", "紫外线", "晴", "阴", "多云", "预报", "未来",
                "今日", "明天", "后天", "温度", "湿度"
            )
            val isWeatherIntent = weatherKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            // 股票/证券相关关键词与意图识别
            val stockKeywords = listOf(
                "股票", "股价", "行情", "证券", "K线", "分时", "上证", "深证", "沪深",
                "A股", "港股", "美股", "涨跌", "成交量", "市值", "收盘", "开盘", "最高",
                "最低", "板块", "龙头", "代码", "证券代码", "指数"
            )
            val isStockIntent = stockKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            
            // 构建工具定义（当选择了工具或处于自动模式时）
            val webSearchTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "web_search",
                    description = "搜索互联网获取实时信息。当用户询问需要最新信息、实时数据或当前事件时使用此工具。重要：必须使用中文关键词进行搜索，以获得更准确的中文搜索结果。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "query" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "搜索查询词，必须使用中文关键词，应该是简洁明确的中文词汇或短语"
                            )
                        ),
                        required = listOf("query")
                    )
                )
            )
            val cityWeatherTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "city_weather",
                    description = "查询指定城市未来几天天气与空气质量。使用中文城市或区县名，如“滕州市”。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "city" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目标城市或区县中文名称"
                            )
                        ),
                        required = listOf("city")
                    )
                )
            )
            val stockDataTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "stock_query",
                    description = "查询指定证券的历史行情数据（K线）。参数 `secid` 支持 `交易所.代码`（如 `sh.600519`、`sz.000001`）或纯数字代码（如 `600519`、`000001`）；可选参数 `num` 表示返回的天数，默认 30。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "secid" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "证券标识，支持 `交易所.代码` 或纯代码，如 `sh.600519` 或 `600519`"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "返回的交易日天数，默认 30"
                            )
                        ),
                        required = listOf("secid")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                isAutoMode -> when {
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool)
                }
                else -> null
            }

            // 在自动模式下，若检测到天气意图，加入系统提示以偏向使用天气工具
            if (isAutoMode && isWeatherIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "本条消息可能涉及天气相关，请优先考虑调用工具 city_weather 获取天气与空气质量信息。若用户未提供城市，请结合上下文推测或礼貌询问其所在城市名称。"
                    )
                )
            }
            if (isAutoMode && isStockIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及股票/股价，请优先考虑调用工具 stock_query 获取指定证券的历史行情数据。若未明确证券代码，请礼貌询问或结合上下文推测（如名称/代码）。"
                    )
                )
            }

            // Insert assistant placeholder for streaming
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在思考...",
                isFromUser = false,
                timestamp = Date()
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L // 限制更新频率为每300ms一次，减少频繁重组引发的抖动
            var preLabelAdded = false
            var postLabelAdded = false
            
            try {
                // Switch blocking network streaming to IO dispatcher to avoid main-thread networking
                val finalText = withContext(Dispatchers.IO) {
                    openAiService.streamChatCompletions(
                        baseUrl = group.baseUrl,
                        apiKey = group.apiKey,
                        model = model.modelName,
                        messages = messages,
                        tools = tools,
                        toolChoice = if (tools != null) "auto" else null,
                        onDelta = { delta ->
                            aggregated.append(delta)
                            val currentTime = System.currentTimeMillis()
                            
                            // 节流更新：只有当距离上次更新超过指定间隔时才更新数据库
                            if (currentTime - lastUpdateTime >= updateInterval) {
                                val updated = assistantMessage.copy(content = aggregated.toString())
                                chatDao.updateMessage(updated)
                                lastUpdateTime = currentTime
                            }
                        },
                        onToolCall = { toolCall ->
                            // 处理工具调用：切换UI状态为调用中，并将已流出的首段内容包装为“第一次回复”
                            // 通知UI具体的工具类型以正确显示图标
                            when (toolCall.function?.name) {
                                "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                                "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                                "stock_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.STOCK_QUERY)
                                else -> {}
                            }
                            if (!preLabelAdded) {
                                val pre = aggregated.toString().trim()
                                if (pre.isNotEmpty()) {
                                    aggregated.setLength(0)
                                    aggregated.append("【前置回复】\n")
                                    aggregated.append(pre)
                                    aggregated.append("\n\n")
                                    val updated = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updated)
                                }
                                preLabelAdded = true
                            }
                            if (toolCall.function?.name == "web_search") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val query = safeExtractQuery(arguments, message)
                                        
                                        // 执行网络搜索
                                        val searchResponse = webSearchService.search(query)

                                        // 在工具调用回复区域渲染搜索结果（Markdown：标题可点击跳转）
                                        if (searchResponse.results.isNotEmpty()) {
                                            val linksMarkdown = searchResponse.results.joinToString("\n") { r ->
                                                "- [${r.title}](${r.url})"
                                            }
                                            aggregated.append("\n\n\n")
                                            aggregated.append("## 搜索结果\n")
                                            aggregated.append(linksMarkdown)
                                            aggregated.append("\n\n\n")
                                            postLabelAdded = true
                                            val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                            chatDao.updateMessage(updatedBeforeOfficial)
                                        }

                                        // 构建最小化系统消息，避免消耗额外token（不附带搜索结果文本）
                                        val searchResultsText = if (searchResponse.results.isNotEmpty()) {
                                            "已完成联网搜索，请继续回答用户问题。不要在末尾附加网址或参考链接。"
                                        } else {
                                            "搜索未找到相关结果，请基于你的知识回答用户的问题。不要在末尾附加网址或参考链接。"
                                        }
                                        
                                        // 将搜索结果作为系统消息添加到消息列表中
                                        val messagesWithSearch = messages.toMutableList()
                                        messagesWithSearch.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = searchResultsText
                                            )
                                        )
                                        
                                        // 重新调用AI，让它基于搜索结果生成回答
                                        // 注意：这里不再传递工具，避免无限循环
                                        val searchBasedResponse = openAiService.streamChatCompletions(
                                            baseUrl = group.baseUrl,
                                            apiKey = group.apiKey,
                                            model = model.modelName,
                                            messages = messagesWithSearch,
                                            tools = null, // 不再传递工具，避免循环调用
                                            toolChoice = null,
                                            onDelta = { delta ->
                                                aggregated.append(delta)
                                                val currentTime = System.currentTimeMillis()
                                                
                                                // 节流更新
                                                if (currentTime - lastUpdateTime >= updateInterval) {
                                                    val updated = assistantMessage.copy(content = aggregated.toString())
                                                    chatDao.updateMessage(updated)
                                                    lastUpdateTime = currentTime
                                                }
                                            },
                                            onToolCall = { /* 不处理工具调用，避免循环 */ }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n搜索工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "city_weather") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val city = safeExtractCity(arguments, message)
                                        val weatherResult = weatherService.query(city)
                                        val weatherText = weatherService.format(weatherResult)

                                        // 在前置回复与正式回复之间插入工具调用结果（Markdown表格），移除可见标签
                                        val weatherTable = weatherService.formatAsMarkdownTable(weatherResult)
                                        aggregated.append("\n\n\n") // 工具结果开始分隔
                                        aggregated.append(weatherTable.trim())
                                        aggregated.append("\n\n\n") // 工具结果结束分隔/正式回复起始分隔
                                        // 已设置正式回复起始分隔，避免在流中再次插入
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                        
                                        val messagesWithWeather = messages.toMutableList()
                                        messagesWithWeather.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = weatherText
                                            )
                                        )
                                        
                                        openAiService.streamChatCompletions(
                                            baseUrl = group.baseUrl,
                                            apiKey = group.apiKey,
                                            model = model.modelName,
                                            messages = messagesWithWeather,
                                            tools = null,
                                            toolChoice = null,
                                            onDelta = { delta ->
                                                if (!postLabelAdded) {
                                                    aggregated.append("\n\n\n")
                                                    postLabelAdded = true
                                                }
                                                aggregated.append(delta)
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - lastUpdateTime >= updateInterval) {
                                                    val updated = assistantMessage.copy(content = aggregated.toString())
                                                    chatDao.updateMessage(updated)
                                                    lastUpdateTime = currentTime
                                                }
                                            },
                                            onToolCall = { /* 不处理工具调用，避免循环 */ }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n天气工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "stock_query") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val secid = safeExtractSecId(arguments, message)
                                        val numRaw = safeExtractNum(arguments, 30)
                                        val num = minOf(numRaw, 15)
                                        val stockResult = stockService.query(secid, num)
                                        // 本地插入Markdown表格到工具调用区域，避免向模型注入大数据
                                        val stockMarkdown = stockService.formatAsMarkdownTable(stockResult)
                                        aggregated.append("\n\n\n")
                                        aggregated.append(stockMarkdown)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)

                                        openAiService.streamChatCompletions(
                                            baseUrl = group.baseUrl,
                                            apiKey = group.apiKey,
                                            model = model.modelName,
                                            messages = messages,
                                            tools = null,
                                            toolChoice = null,
                                            onDelta = { delta ->
                                                if (!postLabelAdded) {
                                                    aggregated.append("\n\n\n")
                                                    postLabelAdded = true
                                                }
                                                aggregated.append(delta)
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - lastUpdateTime >= updateInterval) {
                                                    val updated = assistantMessage.copy(content = aggregated.toString())
                                                    chatDao.updateMessage(updated)
                                                    lastUpdateTime = currentTime
                                                }
                                            },
                                            onToolCall = { /* 不处理工具调用，避免循环 */ }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n股票工具暂时不可用：${e.message}\n\n")
                                }
                            }
                            onToolCallEnd?.invoke()
                        }
                    )
                }

                // 最终一次写入完整文本
                val finalUpdated = assistantMessage.copy(content = aggregated.toString())
                chatDao.updateMessage(finalUpdated)

                // Update conversation (assistant side)
                updateConversationAfterMessage(conversationId, message)

                Result.success(finalUpdated)
            } catch (e: Exception) {
                // On error, update assistant message with error content
                val errorUpdated = assistantMessage.copy(
                    content = "生成失败：${e.message}",
                    isError = true
                )
                chatDao.updateMessage(errorUpdated)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regenerateFromAssistant(
        conversationId: Long,
        assistantMessageId: Long,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
    ): Result<Unit> {
        return try {
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == assistantMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (target.isFromUser) return Result.failure(IllegalArgumentException("Cannot regenerate user message"))

            // 寻找前置用户消息；若找不到，则回退到最近一条有效用户消息
            var prevUserIndex = -1
            for (i in targetIndex - 1 downTo 0) {
                if (history[i].isFromUser && !history[i].isError) {
                    prevUserIndex = i
                    break
                }
            }
            if (prevUserIndex == -1) {
                // 回退：使用整个会话中最近一条有效用户消息
                val lastValidUserIndex = history.indexOfLast { it.isFromUser && !it.isError }
                if (lastValidUserIndex != -1) {
                    prevUserIndex = lastValidUserIndex
                } else {
                    // 仍然找不到任何用户消息，提示错误
                    chatDao.updateMessage(
                        target.copy(
                            content = "无法重新生成：缺少用户消息。",
                            isError = true
                        )
                    )
                    return Result.failure(IllegalStateException("No user messages in conversation"))
                }
            }

            // 删除目标消息后的所有消息
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 清空目标消息，作为新的流式输出占位
            chatDao.updateMessage(target.copy(content = "正在思考..."))

            // 解析模型配置
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                chatDao.updateMessage(
                    target.copy(content = "请先选择模型（设置→模型配置）", isError = true)
                )
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
                ?: run {
                    chatDao.updateMessage(target.copy(content = "所选模型不存在或已删除，请重新选择。", isError = true))
                    return Result.failure(IllegalStateException("Selected model not found"))
                }
            val group = modelConfigRepository.getGroupById(model.groupId)
                ?: run {
                    chatDao.updateMessage(target.copy(content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。", isError = true))
                    return Result.failure(IllegalStateException("Model group not found"))
                }

            // 构造到选定用户消息为止的上下文，并应用限制
            val contextMessagesBase = history.take(prevUserIndex + 1)
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }
            val contextMessages = limitContext(contextMessagesBase)
            val messagesWithBias = contextMessages.toMutableList()

            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
            buildUserProfileSystemMessage()?.let { messagesWithBias.add(it) }

            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
            buildUserProfileSystemMessage()?.let { messagesWithBias.add(it) }

            // 关键词偏好：提升天气/股票工具的选择概率（根据关联的用户消息内容）
            val weatherKeywords = listOf(
                "天气", "气温", "气候", "下雨", "降雨", "降雪", "风力", "空气质量",
                "雾霾", "穿衣", "紫外线", "晴", "阴", "多云", "预报", "未来",
                "今日", "明天", "后天", "温度", "湿度"
            )
            val userTextForIntent = history[prevUserIndex].content
            val isWeatherIntent = weatherKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }
            val stockKeywords = listOf(
                "股票", "股价", "证券", "行情", "涨跌", "K线", "成交量", "成交额", "换手率",
                "300", "600", "SH", "SZ", "同花顺", "东财", "收盘", "开盘", "历史"
            )
            val isStockIntent = stockKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }

            // 构建工具定义（当选择了工具或处于自动模式时）
            val webSearchTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "web_search",
                    description = "搜索互联网获取实时信息。当用户询问需要最新信息、实时数据或当前事件时使用此工具。重要：必须使用中文关键词进行搜索，以获得更准确的中文搜索结果。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "query" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "搜索查询词，必须使用中文关键词，应该是简洁明确的中文词汇或短语"
                            )
                        ),
                        required = listOf("query")
                    )
                )
            )
            val cityWeatherTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "city_weather",
                    description = "查询指定城市未来几天天气与空气质量。使用中文城市或区县名，如“滕州市”。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "city" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目标城市或区县中文名称"
                            )
                        ),
                        required = listOf("city")
                    )
                )
            )
            val stockDataTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "stock_query",
                    description = "查询指定证券代码的历史行情数据（开盘/收盘/振幅等）。适用于用户询问股价走势、成交量、涨跌幅等问题。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "secid" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "证券代码，例如：300033"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "返回条数，默认30"
                            )
                        ),
                        required = listOf("secid")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                isAutoMode -> when {
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool)
                }
                else -> null
            }

            // 在自动模式下，若检测到天气/股票意图，加入系统提示以偏向调用对应工具
            if (isAutoMode && isWeatherIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话与天气相关，请优先考虑调用工具 city_weather 获取指定城市的天气与空气质量信息。若城市不明确，请礼貌询问或依据上下文推测。"
                    )
                )
            }
            if (isAutoMode && isStockIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及股票/股价，请优先考虑调用工具 stock_query 获取指定证券的历史行情数据。若未明确证券代码，请礼貌询问或结合上下文推测（如名称/代码）。"
                    )
                )
            }

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L
            var preLabelAdded = false
            var postLabelAdded = false

            withContext(Dispatchers.IO) {
                openAiService.streamChatCompletions(
                    baseUrl = group.baseUrl,
                    apiKey = group.apiKey,
                    model = model.modelName,
                    messages = messagesWithBias,
                    tools = tools,
                    toolChoice = if (tools != null) "auto" else null,
                    onDelta = { delta ->
                        aggregated.append(delta)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= updateInterval) {
                            val updated = target.copy(content = aggregated.toString())
                            chatDao.updateMessage(updated)
                            lastUpdateTime = currentTime
                        }
                    },
                    onToolCall = { toolCall ->
                        // 处理工具调用：切换UI状态为调用中，并将已流出的首段内容包装为“第一次回复”
                        // 通知UI具体的工具类型以正确显示图标
                        when (toolCall.function?.name) {
                            "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                            "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                            "stock_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.STOCK_QUERY)
                            else -> {}
                        }
                        if (!preLabelAdded) {
                            val pre = aggregated.toString().trim()
                            if (pre.isNotEmpty()) {
                                aggregated.setLength(0)
                                aggregated.append("【前置回复】\n")
                                aggregated.append(pre)
                                aggregated.append("\n\n")
                                val updated = target.copy(content = aggregated.toString())
                                chatDao.updateMessage(updated)
                            }
                            preLabelAdded = true
                        }
                        if (toolCall.function?.name == "web_search") {
                            try {
                                val arguments = toolCall.function.arguments
                                val query = safeExtractQuery(arguments, "")
                                
                                if (query.isNotEmpty()) {
                                    val searchResponse = webSearchService.search(query)

                                    // 在工具调用回复区域渲染搜索结果（Markdown：标题可点击跳转）
                                    if (searchResponse.results.isNotEmpty()) {
                                        val linksMarkdown = searchResponse.results.joinToString("\n") { r ->
                                            "- [${r.title}](${r.url})"
                                        }
                                        aggregated.append("\n\n\n")
                                        aggregated.append("## 搜索结果\n")
                                        aggregated.append(linksMarkdown)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = target.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                    }

                                    // 将最小化系统消息传递给AI进行总结（不包含搜索结果文本）
                                    val messagesWithSearch = contextMessages.toMutableList()
                                    messagesWithSearch.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = if (searchResponse.results.isNotEmpty()) {
                                                "已完成联网搜索，请继续回答用户问题。不要在末尾附加网址或参考链接。"
                                            } else {
                                                "搜索未找到相关结果，请基于你的知识回答用户的问题。不要在末尾附加网址或参考链接。"
                                            }
                                        )
                                    )
                                    
                                    // 重新调用AI进行总结（不传递tools避免无限循环）
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = messagesWithSearch,
                                        onDelta = { delta ->
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = target.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n搜索功能暂时不可用：${e.message}")
                            }
                        } else if (toolCall.function?.name == "city_weather") {
                            try {
                                val arguments = toolCall.function.arguments
                                val city = safeExtractCity(arguments, "")
                                
                                if (city.isNotEmpty()) {
                                    val weatherResult = weatherService.query(city)
                                    // 插入工具调用结果（Markdown表格）到消息流中，位于前置回复和正式回复之间
                                    val weatherTable = weatherService.formatAsMarkdownTable(weatherResult)
                                    aggregated.append("\n\n\n") // 工具结果开始分隔
                                    aggregated.append(weatherTable.trim())
                                    aggregated.append("\n\n\n") // 工具结果结束分隔/正式回复起始分隔
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = target.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)
                                    val messagesWithWeather = contextMessages.toMutableList()
                                    messagesWithWeather.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = weatherService.format(weatherResult)
                                        )
                                    )
                                    
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = messagesWithWeather,
                                        onDelta = { delta ->
                                            if (!postLabelAdded) {
                                                aggregated.append("\n\n\n")
                                                postLabelAdded = true
                                            }
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = target.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n天气功能暂时不可用：${e.message}")
                            }
                        } else if (toolCall.function?.name == "stock_query") {
                            try {
                                val arguments = toolCall.function.arguments
                                val secid = safeExtractSecId(arguments, "")
                                val numRaw = safeExtractNum(arguments, 30)
                                val num = minOf(numRaw, 15)
                                
                                if (secid.isNotEmpty()) {
                                    val stockResult = stockService.query(secid, num)
                                    // 本地插入Markdown表格到工具调用区域，避免向模型注入大数据
                                    val stockMarkdown = stockService.formatAsMarkdownTable(stockResult)
                                    aggregated.append("\n\n\n")
                                    aggregated.append(stockMarkdown)
                                    aggregated.append("\n\n\n")
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = target.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)
                                    
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = contextMessages,
                                        onDelta = { delta ->
                                            if (!postLabelAdded) {
                                                aggregated.append("\n\n\n")
                                                postLabelAdded = true
                                            }
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = target.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n股票功能暂时不可用：${e.message}")
                            }
                        }
                        onToolCallEnd?.invoke()
                    }
                )
            }

            // 最终写入完整文本
            chatDao.updateMessage(target.copy(content = aggregated.toString()))
            refreshConversationMetadata(conversationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewConversation(): Conversation {
        val conversation = Conversation(
            title = "新对话",
            lastMessage = "",
            lastMessageTime = Date(),
            messageCount = 0
        )
        val conversationId = chatDao.insertConversation(conversation)
        return conversation.copy(id = conversationId)
    }

    suspend fun updateConversationTitle(conversationId: Long, newTitle: String, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val updatedConversation = conversation.copy(title = newTitle)
            chatDao.updateConversation(updatedConversation)
            
            // 如果启用了自动同步，则自动上传
            if (autoSyncPreferences.autoSyncEnabled.first()) {
                cloudSyncViewModel.uploadBackup { success, message ->
                    // 通知UI更新同步状态
                    onSyncResult?.invoke(success, message)
                }
            }
        }
    }

    private suspend fun updateConversationAfterMessage(conversationId: Long, message: String) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val messageCount = chatDao.getMessageCount(conversationId)
            val updatedConversation = conversation.copy(
                title = if (conversation.title == "新对话" && messageCount > 0) {
                    message.take(20) + if (message.length > 20) "..." else ""
                } else conversation.title,
                lastMessage = message,
                lastMessageTime = Date(),
                messageCount = messageCount
            )
            chatDao.updateConversation(updatedConversation)
        }
    }

    private suspend fun refreshConversationMetadata(conversationId: Long) {
        val conversation = chatDao.getConversation(conversationId) ?: return
        val messageCount = chatDao.getMessageCount(conversationId)
        val lastMsg = chatDao.getLastMessage(conversationId)
        val updated = conversation.copy(
            lastMessage = lastMsg?.content ?: "",
            lastMessageTime = Date(),
            messageCount = messageCount
        )
        chatDao.updateConversation(updated)
    }

    suspend fun hasValidMessages(conversationId: Long): Boolean {
        return chatDao.getMessageCount(conversationId) > 0
    }

    suspend fun deleteConversation(conversationId: Long, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            chatDao.deleteMessagesForConversation(conversationId)
            chatDao.deleteConversation(conversation)
            
            // 如果启用了自动同步，则自动上传
        if (autoSyncPreferences.autoSyncEnabled.first()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                // 通知UI更新同步状态
                onSyncResult?.invoke(success, message)
            }
        }
        }
    }

    // Added: fetch single message by id
    suspend fun getMessageById(id: Long): ChatMessage? {
        return chatDao.getMessageById(id)
    }

    // Added: update message content
    suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(message)
    }

    // Added: edit user message and resend from original position
    suspend fun editUserMessageAndResend(
        conversationId: Long,
        userMessageId: Long,
        newContent: String,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null,
        onSyncResult: ((Boolean, String) -> Unit)? = null
    ): Result<Unit> {
        return try {
            // 获取完整历史
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == userMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (!target.isFromUser) return Result.failure(IllegalArgumentException("Only user message can be edited"))

            val trimmed = newContent.trim()
            val updatedUser = target.copy(content = trimmed)
            chatDao.updateMessage(updatedUser)

            // 删除其后的所有消息（保证重新生成上下文正确）
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 解析模型配置
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                val errorMessage = ChatMessage(
                    conversationId = conversationId,
                    content = "请先选择模型（设置→模型配置）",
                    isFromUser = false,
                    timestamp = Date(),
                    isError = true
                )
                chatDao.insertMessage(errorMessage)
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = modelConfigRepository.getModelById(selectedModelId)
                ?: run {
                    val errorMessage = ChatMessage(
                        conversationId = conversationId,
                        content = "所选模型不存在或已删除，请重新选择。",
                        isFromUser = false,
                        timestamp = Date(),
                        isError = true
                    )
                    chatDao.insertMessage(errorMessage)
                    return Result.failure(IllegalStateException("Selected model not found"))
                }
            val group = modelConfigRepository.getGroupById(model.groupId)
                ?: run {
                    val errorMessage = ChatMessage(
                        conversationId = conversationId,
                        content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。",
                        isFromUser = false,
                        timestamp = Date(),
                        isError = true
                    )
                    chatDao.insertMessage(errorMessage)
                    return Result.failure(IllegalStateException("Model group not found"))
                }

            // 构造到该用户消息为止的上下文（包含编辑后的用户消息）并应用限制
            val contextMessagesBase = history.take(targetIndex) // 不含旧用户消息
                .filter { !it.isError }
                .map {
                    OpenAiChatMessage(
                        role = if (it.isFromUser) "user" else "assistant",
                        content = it.content
                    )
                }
                .toMutableList()
            contextMessagesBase.add(OpenAiChatMessage(role = "user", content = trimmed))
            val contextMessages = limitContext(contextMessagesBase)
            val messagesWithBias = contextMessages.toMutableList()

            // 关键词偏好：提升天气工具的选择概率（基于编辑后的用户内容）
            val weatherKeywords = listOf(
                "天气", "气温", "气候", "下雨", "降雨", "降雪", "风力", "空气质量",
                "雾霾", "穿衣", "紫外线", "晴", "阴", "多云", "预报", "未来",
                "今日", "明天", "后天", "温度", "湿度"
            )
            val isWeatherIntent = weatherKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }
            // 股票关键词与意图识别（基于编辑后的用户内容）
            val stockKeywords = listOf(
                "股票", "股价", "证券", "行情", "K线", "分时", "上证", "深证", "沪深",
                "A股", "港股", "美股", "涨跌", "成交量", "市值", "收盘", "开盘", "最高",
                "最低", "板块", "龙头", "代码", "证券代码", "指数"
            )
            val isStockIntent = stockKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }

            var preLabelAdded = false
            var postLabelAdded = false

            // 插入新的助手消息占位以进行流式写入
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在思考...",
                isFromUser = false,
                timestamp = Date()
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 300L

            // 定义工具（当选择了工具或处于自动模式时）
            val webSearchTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "web_search",
                    description = "搜索互联网获取实时信息。当用户询问需要最新信息、实时数据或当前事件时使用此工具。重要：必须使用中文关键词进行搜索，以获得更准确的中文搜索结果。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "query" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "搜索查询词，必须使用中文关键词，应该是简洁明确的中文词汇或短语"
                            )
                        ),
                        required = listOf("query")
                    )
                )
            )
            val cityWeatherTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "city_weather",
                    description = "查询指定城市未来几天天气与空气质量。使用中文城市或区县名，如“滕州市”。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "city" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目标城市或区县中文名称"
                            )
                        ),
                        required = listOf("city")
                    )
                )
            )
            val stockDataTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "stock_query",
                    description = "查询指定证券的历史行情数据（K线）。参数 `secid` 支持 `交易所.代码`（如 `sh.600519`、`sz.000001`）或纯数字代码（如 `600519`、`000001`）；可选参数 `num` 表示返回的天数，默认 30。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "secid" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "证券标识，支持 `交易所.代码` 或纯代码，如 `sh.600519` 或 `600519`"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "返回的交易日天数，默认 30"
                            )
                        ),
                        required = listOf("secid")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                isAutoMode -> when {
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool)
                }
                else -> null
            }

            // 在自动模式下，若检测到天气意图，加入系统提示以偏向使用天气工具
            if (isAutoMode && isWeatherIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及天气，请优先考虑调用工具 city_weather 获取天气与空气质量信息。若城市未给出，请礼貌询问或依据上下文推测。"
                    )
                )
            }
            if (isAutoMode && isStockIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及股票/股价，请优先考虑调用工具 stock_query 获取指定证券的历史行情数据。若未明确证券代码，请礼貌询问或结合上下文推测（如名称/代码）。"
                    )
                )
            }

            withContext(Dispatchers.IO) {
                openAiService.streamChatCompletions(
                    baseUrl = group.baseUrl,
                    apiKey = group.apiKey,
                    model = model.modelName,
                    messages = messagesWithBias,
                    tools = tools,
                    toolChoice = if (tools != null) "auto" else null,
                    onDelta = { delta ->
                        aggregated.append(delta)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= updateInterval) {
                            val updated = assistantMessage.copy(content = aggregated.toString())
                            chatDao.updateMessage(updated)
                            lastUpdateTime = currentTime
                        }
                    },
                    onToolCall = { toolCall ->
                        // 处理工具调用：切换UI状态为调用中，并将已流出的首段内容包装为“第一次回复”
                        // 通知UI具体的工具类型以正确显示图标
                        when (toolCall.function?.name) {
                            "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                            "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                            "stock_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.STOCK_QUERY)
                            else -> {}
                        }
                        if (!preLabelAdded) {
                            val pre = aggregated.toString().trim()
                            if (pre.isNotEmpty()) {
                                aggregated.setLength(0)
                                aggregated.append("【前置回复】\n")
                                aggregated.append(pre)
                                aggregated.append("\n\n")
                                val updated = assistantMessage.copy(content = aggregated.toString())
                                chatDao.updateMessage(updated)
                            }
                            preLabelAdded = true
                        }
                        if (toolCall.function?.name == "web_search") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val query = safeExtractQuery(arguments, "")
                                    
                                    // 执行网络搜索
                                    val searchResponse = webSearchService.search(query)

                                    // 在工具调用回复区域渲染搜索结果（Markdown：标题可点击跳转）
                                    if (searchResponse.results.isNotEmpty()) {
                                        val linksMarkdown = searchResponse.results.joinToString("\n") { r ->
                                            "- [${r.title}](${r.url})"
                                        }
                                        aggregated.append("\n\n\n")
                                        aggregated.append("## 搜索结果\n")
                                        aggregated.append(linksMarkdown)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                    }

                                    // 构建最小化系统消息，避免消耗额外token（不附带搜索结果文本）
                                    val searchResultsText = if (searchResponse.results.isNotEmpty()) {
                                        "已完成联网搜索，请继续回答用户问题。不要在末尾附加网址或参考链接。"
                                    } else {
                                        "搜索未找到相关结果，请基于你的知识回答用户的问题。不要在末尾附加网址或参考链接。"
                                    }
                                    
                                    // 将搜索结果作为系统消息添加到消息列表中
                                    val messagesWithSearch = contextMessages.toMutableList()
                                    // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
                                    buildUserProfileSystemMessage()?.let { messagesWithSearch.add(it) }
                                    messagesWithSearch.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = searchResultsText
                                        )
                                    )
                                    
                                    // 重新调用AI，让它基于搜索结果生成回答
                                    // 注意：这里不再传递工具，避免无限循环
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = messagesWithSearch,
                                        tools = null, // 不再传递工具，避免循环调用
                                        toolChoice = null,
                                            onDelta = { delta ->
                                                aggregated.append(delta)
                                                val currentTime = System.currentTimeMillis()
                                            
                                            // 节流更新
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = assistantMessage.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        },
                                        onToolCall = { /* 不处理工具调用，避免循环 */ }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n搜索工具暂时不可用：${e.message}\n\n")
                            }
                        } else if (toolCall.function?.name == "city_weather") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val city = safeExtractCity(arguments, "")
                                    val weatherResult = weatherService.query(city)
                                    val weatherText = weatherService.format(weatherResult)

                                    // 插入工具调用结果（Markdown表格）到消息中，位于前置回复与正式回复之间
                                    val weatherTable = weatherService.formatAsMarkdownTable(weatherResult)
                                    aggregated.append("\n\n\n") // 工具结果开始分隔
                                    aggregated.append(weatherTable.trim())
                                    aggregated.append("\n\n\n") // 工具结果结束分隔/正式回复起始分隔
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)
                                    
                                    val messagesWithWeather = contextMessages.toMutableList()
                                    // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
                                    buildUserProfileSystemMessage()?.let { messagesWithWeather.add(it) }
                                    messagesWithWeather.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = weatherText
                                        )
                                    )
                                    
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = messagesWithWeather,
                                        tools = null,
                                        toolChoice = null,
                                        onDelta = { delta ->
                                            if (!postLabelAdded) {
                                                aggregated.append("\n\n\n")
                                                postLabelAdded = true
                                            }
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = assistantMessage.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        },
                                        onToolCall = { /* 不处理工具调用，避免循环 */ }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n天气工具暂时不可用：${e.message}\n\n")
                            }
                        } else if (toolCall.function?.name == "stock_query") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val secid = safeExtractSecId(arguments, "")
                                    val numRaw = safeExtractNum(arguments, 30)
                                    val num = minOf(numRaw, 15)
                                    val stockResult = stockService.query(secid, num)
                                    val stockMarkdown = stockService.formatAsMarkdownTable(stockResult)

                                    // 插入工具调用结果到消息中，位于前置回复与正式回复之间
                                    aggregated.append("\n\n\n") // 工具结果开始分隔
                                    aggregated.append(stockMarkdown.trim())
                                    aggregated.append("\n\n\n") // 工具结果结束分隔/正式回复起始分隔
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)
                                    
                                    openAiService.streamChatCompletions(
                                        baseUrl = group.baseUrl,
                                        apiKey = group.apiKey,
                                        model = model.modelName,
                                        messages = buildList {
                                            addAll(contextMessages)
                                            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
                                            buildUserProfileSystemMessage()?.let { add(it) }
                                        },
                                        tools = null,
                                        toolChoice = null,
                                        onDelta = { delta ->
                                            if (!postLabelAdded) {
                                                // 使用三个换行作为正式回复开始分隔，移除可见标签
                                                aggregated.append("\n\n\n")
                                                postLabelAdded = true
                                            }
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = assistantMessage.copy(content = aggregated.toString())
                                                chatDao.updateMessage(updated)
                                                lastUpdateTime = currentTime
                                            }
                                        },
                                        onToolCall = { /* 不处理工具调用，避免循环 */ }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n股票工具暂时不可用：${e.message}\n\n")
                            }
                        }
                        onToolCallEnd?.invoke()
                    }
                )
            }

            // 最终写入完整文本并刷新会话元数据
            chatDao.updateMessage(assistantMessage.copy(content = aggregated.toString()))
            refreshConversationMetadata(conversationId)
            
            // 如果启用了自动同步，则自动上传
            if (autoSyncPreferences.autoSyncEnabled.first()) {
                cloudSyncViewModel.uploadBackup { success, message ->
                    // 通知UI更新同步状态
                    onSyncResult?.invoke(success, message)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 应用最大上下文限制（值<=0表示无限）
    private suspend fun limitContext(messages: List<OpenAiChatMessage>): List<OpenAiChatMessage> {
        val limit = contextPreferences.maxContextMessages.first()
        return if (limit <= 0) messages else messages.takeLast(limit)
    }

    // 构建“非必要的用户背景”系统消息。仅在存在已填写字段时返回。
    private suspend fun buildUserProfileSystemMessage(): OpenAiChatMessage? {
        val profile = try { userProfilePreferences.profile.first() } catch (_: Exception) { UserProfile() }
        val parts = mutableListOf<String>()

        fun add(label: String, raw: String?) {
            val v = raw?.trim()
            if (!v.isNullOrEmpty()) parts.add("$label：$v")
        }
        fun addInt(label: String, raw: Int?) {
            val v = raw ?: return
            if (v > 0) parts.add("$label：$v")
        }

        add("昵称", profile.nickname)
        add("城市", profile.city)
        add("语言偏好", profile.preferredLanguage)
        addInt("年龄", profile.age)
        add("性别", profile.gender)
        add("生日", profile.birthday)
        add("职业", profile.occupation)
        add("公司", profile.company)
        add("时区", profile.timezone)
        add("网站", profile.website)
        add("地址", profile.address)
        add("爱好", profile.hobbies)
        add("简介", profile.bio)
        add("邮箱", profile.email)
        add("电话", profile.phone)

        if (profile.customFields.isNotEmpty()) {
            val customJoined = profile.customFields.entries
                .filter { it.value.isNotBlank() }
                .joinToString(
                    separator = ", ",
                    transform = { "${it.key}=${it.value}" }
                )
            if (customJoined.isNotBlank()) parts.add("其他：$customJoined")
        }

        if (parts.isEmpty()) return null
        val content = buildString {
            append("以下是用户的背景资料（非必要信息，若与当前任务无关请忽略）：")
            append(parts.joinToString("；"))
        }
        return OpenAiChatMessage(role = "system", content = content)
    }

    private fun safeExtractQuery(arguments: String?, default: String): String {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()

        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                val value = map["query"] as? String
                if (value.isNullOrBlank()) null else value
            } catch (_: Exception) {
                null
            }
        }

        tryParse(raw)?.let { return it }

        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }

        val regexQuoted = Regex("""(?i)\"?query\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""")
        val regexUnquoted = Regex("""(?i)\"?query\"?\s*[:=]\s*([^,}\n\r]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        // Fallback: if arguments is plain text, use it directly
        return raw
    }
    private fun safeExtractCity(arguments: String?, default: String): String {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()

        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                val value = map["city"] as? String
                if (value.isNullOrBlank()) null else value
            } catch (_: Exception) {
                null
            }
        }

        tryParse(raw)?.let { return it }

        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }

        val regexQuoted = Regex("""(?i)\"?city\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""")
        val regexUnquoted = Regex("""(?i)\"?city\"?\s*[:=]\s*([^,}\n\r]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        // Fallback: if arguments is plain text, use it directly
        return raw
    }

    private fun safeExtractSecId(arguments: String?, default: String): String {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()

        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                val value = map["secid"] as? String
                if (value.isNullOrBlank()) null else value
            } catch (_: Exception) {
                null
            }
        }

        fun normalize(code: String): String {
            // 提取数字部分，适配如 "sh.600519"、"sz000001" 等形式
            val digits = Regex("""([0-9]{5,6})""").find(code)?.groupValues?.getOrNull(1)
            return digits ?: code
        }

        tryParse(raw)?.let { return normalize(it) }

        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return normalize(it) }

        val regexQuoted = Regex("""(?i)\"?secid\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""")
        val regexUnquoted = Regex("""(?i)\"?secid\"?\s*[:=]\s*([^,}\n\r]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return normalize(it) }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return normalize(it) }

        // 直接从原始文本中提取可能的 5-6 位数字代码
        Regex("""([0-9]{5,6})""").find(raw)?.groupValues?.getOrNull(1)?.let { return it }

        // Fallback
        return default
    }

    private fun safeExtractNum(arguments: String?, default: Int): Int {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()

        fun tryParse(text: String): Int? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                when (val v = map["num"]) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull()
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        tryParse(raw)?.let { return it }

        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }

        val regexQuoted = Regex("""(?i)\"?num\"?\s*[:=]\s*\"([0-9]+)\"""")
        val regexUnquoted = Regex("""(?i)\"?num\"?\s*[:=]\s*([0-9]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()?.let { return it }

        return default
    }
}