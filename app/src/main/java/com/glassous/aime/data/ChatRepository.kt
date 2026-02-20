package com.glassous.aime.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.ContextPreferences
 
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
 
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
    private val contextPreferences: ContextPreferences,
    private val openAiService: OpenAiService = OpenAiService(),
    private val doubaoService: DoubaoArkService = DoubaoArkService(),
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
            var processedMessage = message
            var webAnalysisToolUsed = false

            // Client-side handling for Web Analysis
            if (selectedTool?.type == ToolType.WEB_ANALYSIS) {
                // 1. Extract URL
                val url = safeExtractUrl(null, message)
                // Remove url != message check to allow single URL messages
                if (url.isNotEmpty() && url.startsWith("http")) {
                    try {
                        onToolCallStart?.invoke(ToolType.WEB_ANALYSIS)
                        // 2. Fetch content
                        val result = withContext(Dispatchers.IO) {
                            webSearchService.fetchWebPage(url)
                        }
                        
                        // 3. Format User Message with card
                        val cardMarkdown = """
                        ### ${result.title}
                        
                        ```html
                        <!-- type: web_analysis url:${result.url} web_title:${result.title.replace("-->", "")} -->
                        ${result.fullContent}
                        ```
                        """.trimIndent()
                        
                        // Insert prompt to guide the AI
                        // If extracted URL is the entire message, replace it. Otherwise append.
                        processedMessage = if (message.trim() == url.trim()) {
                            "帮我分析以下网页的内容：\n\n" + cardMarkdown
                        } else {
                            // 保留用户输入的其他文本
                            val msgWithoutUrl = message.replace(url, "").trim()
                            if (msgWithoutUrl.isEmpty()) {
                                "帮我分析以下网页的内容：\n\n" + cardMarkdown
                            } else {
                                // 用户的提示词 + 网页内容卡片
                                // Swap order: User Text first, then Header, then Card
                                // User request: "插入的“请帮我分析以下网页”与用户自己的提示词之间顺序搞反了，请调换"
                                // Current: User Text + "\n\n" + Header + Card
                                // New: Header + "\n\n" + User Text + "\n\n" + Card?
                                // Wait, the user said "插入的“请帮我分析以下网页”与用户自己的提示词之间顺序搞反了"
                                // Previous code: "帮我分析以下网页的内容：" was inside cardMarkdown at the top.
                                // And we did: msgWithoutUrl + "\n\n" + cardMarkdown
                                // Result: User Text -> "帮我分析..." -> Title -> Content
                                // User wants: "帮我分析..." -> User Text -> Title -> Content?
                                // Or maybe: User Text should be AFTER "帮我分析..."?
                                // Let's re-read carefully: "1.插入的“请帮我分析以下网页”与用户自己的提示词之间顺序搞反了，请调换"
                                // Likely means: "帮我分析..." should come BEFORE User Text? Or User Text should come BEFORE "帮我分析..."?
                                // If I wrote "Summary this", output was "Summary this" -> "Help me analyze..." -> Card.
                                // If user thinks this is swapped, maybe they want "Help me analyze..." -> "Summary this" -> Card?
                                // Or maybe they want the Card to be clearly separated?
                                // Let's try: "帮我分析以下网页的内容：" -> User Text -> Card (without the header inside it).
                                
                                // Let's remove "帮我分析以下网页的内容：" from cardMarkdown first (done above).
                                
                                "帮我分析以下网页的内容：\n" + msgWithoutUrl + "\n\n" + cardMarkdown
                            }
                        }
                        
                        webAnalysisToolUsed = true
                    } catch (e: Exception) {
                        // Ignore error, proceed with original message
                        // Maybe append error hint?
                        processedMessage = message + "\n\n(网页内容获取失败: ${e.message})"
                    } finally {
                        onToolCallEnd?.invoke()
                    }
                }
            }

            // Save user message
            val userMessage = ChatMessage(
                conversationId = conversationId,
                content = processedMessage,
                isFromUser = true,
                timestamp = Date()
            )
            chatDao.insertMessage(userMessage)

            // Update conversation (user side)
            updateConversationAfterMessage(conversationId, message) // Use original message for preview

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
            
            baseMessages.add(OpenAiChatMessage(role = "user", content = processedMessage))
            
            // 针对网页分析，注入一次性系统提示（仅在本次请求有效）
            if (webAnalysisToolUsed) {
                baseMessages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "以上是用户提供的网页正文内容。请根据这些内容回答用户的请求。请注意：\n" +
                                "1. 不需要分析网页的HTML结构或技术实现，除非用户明确询问。\n" +
                                "2. 重点关注网页所传达的文章、新闻、数据或信息本身。\n" +
                                "3. 如果内容较长，请先进行摘要，再回答具体问题。"
                    )
                )
            }
            
            val messages = limitContext(baseMessages).toMutableList()

            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
            

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
            // 黄金相关关键词与意图识别
            val goldKeywords = listOf(
                "黄金", "金价", "金条", "回收价", "回收黄金", "铂金", "银价", "金店",
                "购买黄金", "投资黄金", "金饰", "贵金属"
            )
            val isGoldIntent = goldKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            val hsKeywords = listOf(
                "高铁", "动车", "火车票", "车次", "一等座", "二等座", "商务座", "余票", "票价", "购票", "直达"
            )
            val isHsIntent = hsKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            val tikuKeywords = listOf(
                "题库", "百度题库", "考试", "选择题", "填空题", "判断题", "解析", "答案", "真题", "单选", "多选", "题目"
            )
            val isTikuIntent = tikuKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            val lotteryKeywords = listOf(
                "彩票", "彩票开奖", "开奖", "开奖公告", "开奖时间", "开奖号码", "中奖号码", "中奖",
                "快乐8", "双色球", "大乐透", "福彩3D", "排列3", "排列5", "七乐彩", "7星彩", "七星彩", "胜负彩", "进球彩", "半全场",
                "kl8", "ssq", "dlt", "fc3d", "pl3", "pl5", "qlc", "qxc", "sfc", "jqc", "bqc",
                "第"
            )
            val isLotteryIntent = lotteryKeywords.any { kw -> message.contains(kw, ignoreCase = true) }
            
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
            val goldPriceTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "gold_price",
                    description = "查询黄金相关价格数据（银行金条、回收价、品牌贵金属）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = emptyMap(),
                        required = null
                    )
                )
            )
            val hsTicketTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "hs_ticket_query",
                    description = "查询高铁/动车车次、时间与价格（默认为当天日期）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "from" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "出发城市或车站中文名称"
                            ),
                            "to" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目的城市或车站中文名称"
                            ),
                            "date" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "查询日期（yyyy-MM-dd），未提供则默认为当天"
                            )
                        ),
                        required = listOf("from", "to")
                    )
                )
            )
            val baiduTikuTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "baidu_tiku",
                    description = "检索题库并返回题干/选项/答案。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "question" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "完整题干文本"
                            )
                        ),
                        required = listOf("question")
                    )
                )
            )
            val lotteryTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "lottery_query",
                    description = "查询指定彩种的最近开奖信息。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "get" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "彩种缩写：kl8、ssq、dlt、fc3d、pl3、pl5、qlc、qxc、sfc、jqc、bqc"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "查询天数（1-100），默认5"
                            )
                        ),
                        required = listOf("get")
                    )
                )
            )
            val webAnalysisTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "web_analysis",
                    description = "分析指定网页的内容。当用户提供URL并要求分析时使用此工具。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "url" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "需要分析的网页URL"
                            )
                        ),
                        required = listOf("url")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                // selectedTool?.type == ToolType.WEB_ANALYSIS -> listOf(webAnalysisTool) // Client-side handled, no tool for LLM
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                selectedTool?.type == ToolType.GOLD_PRICE -> listOf(goldPriceTool)
                selectedTool?.type == ToolType.HIGH_SPEED_TICKET -> listOf(hsTicketTool)
                selectedTool?.type == ToolType.LOTTERY_QUERY -> listOf(lotteryTool)
                selectedTool?.type == ToolType.BAIDU_TIKU -> listOf(baiduTikuTool)
                isAutoMode -> when {
                    isLotteryIntent -> listOf(lotteryTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isTikuIntent -> listOf(baiduTikuTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, lotteryTool)
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isGoldIntent -> listOf(goldPriceTool, webSearchTool, cityWeatherTool, stockDataTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isHsIntent -> listOf(hsTicketTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, baiduTikuTool, lotteryTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
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
            if (isAutoMode && isGoldIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及黄金/贵金属，请优先考虑调用工具 gold_price 获取银行金条、回收价与品牌贵金属价格。"
                    )
                )
            }
            if (isAutoMode && isHsIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及高铁/动车车票，请优先考虑调用工具 hs_ticket_query 获取当日或指定日期的车次、时间与价格。"
                    )
                )
            }
            if (isAutoMode && isTikuIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及题库/考试，请优先考虑调用工具 baidu_tiku 进行题目检索与答案获取。如题干不完整，请礼貌询问或提示用户补充题目。"
                    )
                )
            }
            if (isAutoMode && isLotteryIntent) {
                messages.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及彩票开奖，请优先考虑调用工具 lottery_query 进行查询。若未明确彩种或期数，请礼貌询问或根据上下文推测。"
                    )
                )
            }

            // Insert assistant placeholder for streaming
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在思考...",
                isFromUser = false,
                timestamp = Date(),
                modelDisplayName = model.name
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 0L
            var preLabelAdded = false
            var postLabelAdded = false
            
            try {
                // Switch blocking network streaming to IO dispatcher to avoid main-thread networking
                val finalText = withContext(Dispatchers.IO) {
                    streamWithFallback(
                        primaryGroup = group,
                        primaryModel = model,
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
                    "web_analysis" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_ANALYSIS)
                    "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                    "stock_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.STOCK_QUERY)
                    "gold_price" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.GOLD_PRICE)
                    "hs_ticket_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.HIGH_SPEED_TICKET)
                    "baidu_tiku" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.BAIDU_TIKU)
                    "lottery_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.LOTTERY_QUERY)
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
                                        val searchBasedResponse = streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
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
                                        
                                        streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
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

                                        streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
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
                            } else if (toolCall.function?.name == "gold_price") {
                                try {
                                    val goldResult = GoldPriceService().query()
                                    val md = GoldPriceService().formatAsMarkdownParagraphs(goldResult)
                                    aggregated.append("\n\n\n")
                                    aggregated.append(md)
                                    aggregated.append("\n\n\n")
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)

                                    val messagesWithGold = messages.toMutableList()
                                    messagesWithGold.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = md
                                        )
                                    )
                                    messagesWithGold.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = "已获取黄金价格数据，请结合用户需求给出购买建议（如购买金条/首饰或回收差价等），并提示价格波动与风险。"
                                        )
                                    )
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = messagesWithGold,
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
                                        onToolCall = { }
                                    )
                                } catch (e: Exception) {
                                    aggregated.append("\n\n黄金价格工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "baidu_tiku") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val question = safeExtractQuestion(arguments, message)
                                        val tikuResult = BaiduTikuService().query(question)
                                        val md = BaiduTikuService().formatAsMarkdown(tikuResult)
                                        aggregated.append("\n\n\n")
                                        aggregated.append(md)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)

                                        val messagesWithTiku = messages.toMutableList()
                                        val summary = if (tikuResult.success) {
                                            val ans = tikuResult.answer.ifBlank { "暂无" }
                                            "题目：${tikuResult.question}；答案：${ans}。请基于此给出简洁回答。"
                                        } else {
                                            "题库查询失败：${tikuResult.message}，请基于已有信息回复用户。"
                                        }
                                        messagesWithTiku.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = summary
                                            )
                                        )

                                        streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
                                            messages = messagesWithTiku,
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
                                            onToolCall = { }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n题库工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "lottery_query") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val getVal = safeExtractGet(arguments, message)
                                        val numVal = safeExtractNum(arguments, 5).coerceIn(1, 100)
                                        val lot = LotteryService().query(getVal.ifBlank { "ssq" }, numVal)
                                        val md = LotteryService().formatAsMarkdown(lot)
                                        aggregated.append("\n\n\n")
                                        aggregated.append(md)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)

                                        val messagesWithLottery = messages.toMutableList()
                                        val summary = if (lot.success && lot.items.isNotEmpty()) {
                                            val first = lot.items.first()
                                            val firstIssue = first.issue ?: ""
                                            val firstDraw = first.drawnumber ?: ""
                                            "彩种：${lot.name}；最新期号：${firstIssue}；开奖号码：${firstDraw}。请据此简洁回答。"
                                        } else {
                                            "彩票开奖查询失败：${lot.message}，请基于已有信息回复用户。"
                                        }
                                        messagesWithLottery.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = summary
                                            )
                                        )

                                        streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
                                            messages = messagesWithLottery,
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
                                            onToolCall = { }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n彩票开奖工具暂时不可用：${e.message}\n\n")
                                }
                            }
                            onToolCallEnd?.invoke()
                        }
                    )
                }

            // 最终一次写入完整文本
            val finalUpdated = assistantMessage.copy(content = if (aggregated.isEmpty()) "生成失败或内容为空" else aggregated.toString())
            chatDao.updateMessage(finalUpdated)

                // Update conversation (assistant side)
                updateConversationAfterMessage(conversationId, message)

                Result.success(finalUpdated)
            } catch (e: Exception) {
                // 调用失败，保存详细错误信息
                val errorMessage = when {
                    e.message?.contains("HTTP 401") == true -> "认证失败：API Key 无效或已过期"
                    e.message?.contains("HTTP 403") == true -> "访问被拒绝：没有权限访问该模型"
                    e.message?.contains("HTTP 429") == true -> "请求过于频繁：已达到速率限制"
                    e.message?.contains("HTTP 500") == true || e.message?.contains("HTTP 502") == true || 
                    e.message?.contains("HTTP 503") == true -> "服务器错误：模型服务暂时不可用"
                    e.message?.contains("timeout") == true -> "请求超时：网络连接超时"
                    e.message?.contains("Connection") == true -> "网络错误：无法连接到服务器"
                    else -> "生成失败：${e.message ?: "未知错误"}"
                }
                val errorUpdated = assistantMessage.copy(
                    content = errorMessage,
                    isError = true,
                    errorDetails = e.message
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

            // 删除目标消息及其后的所有消息
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 插入新的助手消息占位，用于流式输出
            val newAssistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在思考...",
                isFromUser = false,
                timestamp = Date()
            )
            val newAssistantId = chatDao.insertMessage(newAssistantMessage)

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
            val goldKeywords = listOf(
                "黄金", "金价", "金条", "回收价", "回收黄金", "铂金", "银价", "金店",
                "购买黄金", "投资黄金", "金饰", "贵金属"
            )
            val isGoldIntent = goldKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }
            val hsKeywords = listOf(
                "高铁", "动车", "火车票", "车次", "一等座", "二等座", "商务座", "余票", "票价", "购票", "直达"
            )
            val isHsIntent = hsKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }
            val tikuKeywords = listOf(
                "题库", "百度题库", "考试", "选择题", "填空题", "判断题", "解析", "答案", "真题", "单选", "多选", "题目"
            )
            val isTikuIntent = tikuKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }
            val lotteryKeywords = listOf(
                "彩票", "彩票开奖", "开奖", "开奖公告", "开奖时间", "开奖号码", "中奖号码", "中奖",
                "快乐8", "双色球", "大乐透", "福彩3D", "排列3", "排列5", "七乐彩", "7星彩", "七星彩", "胜负彩", "进球彩", "半全场",
                "kl8", "ssq", "dlt", "fc3d", "pl3", "pl5", "qlc", "qxc", "sfc", "jqc", "bqc",
                "第"
            )
            val isLotteryIntent = lotteryKeywords.any { kw -> userTextForIntent.contains(kw, ignoreCase = true) }

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
            val goldPriceTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "gold_price",
                    description = "查询黄金相关价格数据（银行金条、回收价、品牌贵金属）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = emptyMap(),
                        required = null
                    )
                )
            )
            val hsTicketTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "hs_ticket_query",
                    description = "查询高铁/动车车次、时间与价格（默认为当天日期）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "from" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "出发城市或车站中文名称"
                            ),
                            "to" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目的城市或车站中文名称"
                            ),
                            "date" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "查询日期（yyyy-MM-dd），未提供则默认为当天"
                            )
                        ),
                        required = listOf("from", "to")
                    )
                )
            )
            val baiduTikuTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "baidu_tiku",
                    description = "检索题库并返回题干/选项/答案。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "question" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "完整题干文本"
                            )
                        ),
                        required = listOf("question")
                    )
                )
            )
            val lotteryTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "lottery_query",
                    description = "查询指定彩种的最近开奖信息。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "get" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "彩种缩写：kl8、ssq、dlt、fc3d、pl3、pl5、qlc、qxc、sfc、jqc、bqc"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "查询天数（1-100），默认5"
                            )
                        ),
                        required = listOf("get")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                selectedTool?.type == ToolType.GOLD_PRICE -> listOf(goldPriceTool)
                selectedTool?.type == ToolType.HIGH_SPEED_TICKET -> listOf(hsTicketTool)
                selectedTool?.type == ToolType.BAIDU_TIKU -> listOf(baiduTikuTool)
                selectedTool?.type == ToolType.LOTTERY_QUERY -> listOf(lotteryTool)
                isAutoMode -> when {
                    isLotteryIntent -> listOf(lotteryTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isTikuIntent -> listOf(baiduTikuTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, lotteryTool)
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isGoldIntent -> listOf(goldPriceTool, webSearchTool, cityWeatherTool, stockDataTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isHsIntent -> listOf(hsTicketTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, baiduTikuTool, lotteryTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
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
            val updateInterval = 0L
            var preLabelAdded = false
            var postLabelAdded = false
            
            // 使用新插入的助手消息作为目标消息
            var assistantMessage = newAssistantMessage.copy(id = newAssistantId)
            assistantMessage = assistantMessage.copy(modelDisplayName = model.name)
            chatDao.updateMessage(assistantMessage)

            withContext(Dispatchers.IO) {
                streamWithFallback(
                    primaryGroup = group,
                    primaryModel = model,
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
                            "gold_price" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.GOLD_PRICE)
                            "hs_ticket_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.HIGH_SPEED_TICKET)
                            "baidu_tiku" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.BAIDU_TIKU)
                            "lottery_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.LOTTERY_QUERY)
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
                                val query = safeExtractQuery(arguments, userTextForIntent)
                                
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
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
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
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = messagesWithSearch,
                                        tools = null,
                                        toolChoice = null,
                                        onDelta = { delta ->
                                            aggregated.append(delta)
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastUpdateTime >= updateInterval) {
                                                val updated = assistantMessage.copy(content = aggregated.toString())
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
                                val city = safeExtractCity(arguments, userTextForIntent)
                                
                                if (city.isNotEmpty()) {
                                    val weatherResult = weatherService.query(city)
                                    // 插入工具调用结果（Markdown表格）到消息流中，位于前置回复和正式回复之间
                                    val weatherTable = weatherService.formatAsMarkdownTable(weatherResult)
                                    aggregated.append("\n\n\n") // 工具结果开始分隔
                                        aggregated.append(weatherTable.trim())
                                        aggregated.append("\n\n\n") // 工具结果结束分隔/正式回复起始分隔
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                    val messagesWithWeather = contextMessages.toMutableList()
                                    messagesWithWeather.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = weatherService.format(weatherResult)
                                        )
                                    )
                                    
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
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
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n天气功能暂时不可用：${e.message}")
                            }
                        } else if (toolCall.function?.name == "stock_query") {
                            try {
                                val arguments = toolCall.function.arguments
                                val secid = safeExtractSecId(arguments, userTextForIntent)
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
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)
                                    
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = contextMessages,
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
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n股票功能暂时不可用：${e.message}")
                            }
                        } else if (toolCall.function?.name == "baidu_tiku") {
                            try {
                                val arguments = toolCall.function.arguments
                                val question = safeExtractQuestion(arguments, userTextForIntent)
                                if (question.isNotEmpty()) {
                                    val tikuResult = BaiduTikuService().query(question)
                                    val md = BaiduTikuService().formatAsMarkdown(tikuResult)
                                    aggregated.append("\n\n\n")
                                    aggregated.append(md)
                                    aggregated.append("\n\n\n")
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)

                                    val messagesWithTiku = contextMessages.toMutableList()
                                    val summary = if (tikuResult.success) {
                                        val ans = tikuResult.answer.ifBlank { "暂无" }
                                        "题目：${tikuResult.question}；答案：${ans}。请基于此给出简洁回答。"
                                    } else {
                                        "题库查询失败：${tikuResult.message}，请基于已有信息回复用户。"
                                    }
                                    messagesWithTiku.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = summary
                                        )
                                    )

                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = messagesWithTiku,
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
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n题库功能暂时不可用：${e.message}")
                            }
                        }
                        onToolCallEnd?.invoke()
                    }
                )
            }

            // 最终写入完整文本
            chatDao.updateMessage(assistantMessage.copy(content = aggregated.toString()))
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
        // 移除立即同步，等待对话完成后再同步
        return conversation.copy(id = conversationId)
    }

    suspend fun updateConversationTitle(conversationId: Long, newTitle: String) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val updatedConversation = conversation.copy(title = newTitle)
            chatDao.updateConversation(updatedConversation)
            
        }
    }

    suspend fun generateConversationTitle(conversationId: Long, onTitleGenerated: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Determine which model ID to use
                val titleGenModelId = modelPreferences.titleGenerationModelId.first()
                var targetModelId = titleGenModelId

                // If specific model set, check if exists
                if (!targetModelId.isNullOrBlank()) {
                     val model = modelConfigRepository.getModelById(targetModelId)
                     if (model == null) {
                         targetModelId = null // Fallback to default
                     }
                }

                // If null (either not set or deleted), use selected model
                if (targetModelId == null) {
                    targetModelId = modelPreferences.selectedModelId.first()
                }

                if (targetModelId.isNullOrBlank()) {
                    onTitleGenerated("新对话")
                    return@withContext
                }
                
                val model = modelConfigRepository.getModelById(targetModelId)
                if (model == null) {
                    onTitleGenerated("新对话")
                    return@withContext
                }
                
                val group = modelConfigRepository.getGroupById(model.groupId)
                if (group == null) {
                    onTitleGenerated("新对话")
                    return@withContext
                }

                // 获取对话中所有消息内容
                val messages = chatDao.getMessagesForConversation(conversationId).first()
                
                if (messages.isEmpty()) {
                    onTitleGenerated("新对话")
                    return@withContext
                }

                // 获取策略和N值
                val contextStrategy = modelPreferences.titleGenerationContextStrategy.first()
                val contextN = modelPreferences.titleGenerationContextN.first()

                // 构建提示词上下文
                val contextContent = StringBuilder()
                
                for (message in messages) {
                    if (message.isFromUser) {
                        contextContent.append("User: ${message.content}\n")
                    } else {
                        // AI回复处理
                        val content = message.content
                        if (contextStrategy == 0) {
                            // 策略0：仅发送消息（AI回复不包含）
                            continue 
                        } else if (contextStrategy == 4) {
                            // 策略4：全部上下文
                            contextContent.append("AI: $content\n")
                        } else {
                            // 截取逻辑
                            val len = content.length
                            val sb = StringBuilder()
                            
                            // 前N字
                            if (contextStrategy == 1 || contextStrategy == 3) {
                                if (len <= contextN) {
                                    sb.append(content)
                                } else {
                                    sb.append(content.substring(0, contextN))
                                    if (contextStrategy == 1) sb.append("...")
                                }
                            }
                            
                            // ... 连接符 (如果是前后N字且中间有省略)
                            if (contextStrategy == 3 && len > 2 * contextN) {
                                sb.append(" ... ")
                            }

                            // 后N字
                            if (contextStrategy == 2 || contextStrategy == 3) {
                                if (len <= contextN) {
                                    if (contextStrategy == 2) sb.append(content)
                                    // if strategy 3, already appended above or overlap handled logic below
                                    // 简化逻辑：如果是3且长度小，上面已经加了，这里不需要重复
                                    // 但为了严谨：
                                    // 策略3：前N + 后N。如果总长度 <= 2N，直接显示全部。
                                    // 如果总长度 > 2N，显示 前N ... 后N
                                } else {
                                    sb.append(content.substring(len - contextN))
                                }
                            }
                            
                            // 修正策略3的逻辑：
                            // 上面的逻辑在策略3时有点问题。重写截取部分：
                            var processedContent = ""
                            if (contextStrategy == 1) { // 前N
                                processedContent = if (len <= contextN) content else "${content.substring(0, contextN)}..."
                            } else if (contextStrategy == 2) { // 后N
                                processedContent = if (len <= contextN) content else "...${content.substring(len - contextN)}"
                            } else if (contextStrategy == 3) { // 前后N
                                processedContent = if (len <= 2 * contextN) {
                                    content
                                } else {
                                    "${content.substring(0, contextN)} ... ${content.substring(len - contextN)}"
                                }
                            }
                            
                            contextContent.append("AI: $processedContent\n")
                        }
                    }
                }

                if (contextContent.isEmpty()) {
                    // 如果过滤后没有内容（例如只有AI回复且策略为0），尝试回退到仅用户
                    val userMessages = messages.filter { it.isFromUser }
                    if (userMessages.isNotEmpty()) {
                        contextContent.append(userMessages.joinToString("\n") { "User: ${it.content}" })
                    } else {
                        onTitleGenerated("新对话")
                        return@withContext
                    }
                }

                val prompt = "请根据以下对话内容生成一个简短的标题（不超过20个字），只返回标题本身，不要有任何其他内容：\n\n$contextContent"

                val titleMessages = listOf(
                    OpenAiChatMessage(role = "user", content = prompt)
                )

                // 使用流式输出生成标题
                val titleBuilder = StringBuilder()
                var insideThinkTag = false
                
                // 根据 baseUrl 判断提供商类型
                if (group.baseUrl.contains("volces", ignoreCase = true)) {
                    // 豆包服务
                    doubaoService.streamChatCompletions(
                        baseUrl = group.baseUrl,
                        apiKey = group.apiKey,
                        model = model.modelName,
                        messages = titleMessages,
                        tools = null,
                        toolChoice = null,
                        onDelta = { delta ->
                            titleBuilder.append(delta)
                            // 过滤掉 <think> 标签内容
                            val filtered = filterThinkTags(titleBuilder.toString())
                            onTitleGenerated(filtered.trim())
                        }
                    )
                } else {
                    // OpenAI 兼容服务
                    openAiService.streamChatCompletions(
                        baseUrl = group.baseUrl,
                        apiKey = group.apiKey,
                        model = model.modelName,
                        messages = titleMessages,
                        tools = null,
                        toolChoice = null,
                        onDelta = { delta ->
                            titleBuilder.append(delta)
                            // 过滤掉 <think> 标签内容
                            val filtered = filterThinkTags(titleBuilder.toString())
                            onTitleGenerated(filtered.trim())
                        }
                    )
                }
            } catch (e: Exception) {
                onTitleGenerated("新对话")
            }
        }
    }

    suspend fun tryAutoGenerateTitle(conversationId: Long) {
        val autoGen = modelPreferences.titleGenerationAutoGenerate.first()
        if (!autoGen) return

        val messages = chatDao.getMessagesForConversation(conversationId).first()
        val validUserMessages = messages.count { it.isFromUser && !it.isError }
        
        if (validUserMessages == 1) {
            var finalTitle = ""
            generateConversationTitle(conversationId) { title ->
                finalTitle = title
            }
            if (finalTitle.isNotBlank() && finalTitle != "新对话") {
                updateConversationTitle(conversationId, finalTitle)
            }
        }
    }

    private suspend fun updateConversationAfterMessage(conversationId: Long, message: String) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val messageCount = chatDao.getMessageCount(conversationId)
            val lastMessage = chatDao.getLastMessage(conversationId)
            val updatedConversation = conversation.copy(
                title = if (conversation.title == "新对话" && messageCount > 0) {
                    message.take(20) + if (message.length > 20) "..." else ""
                } else conversation.title,
                lastMessage = message,
                lastMessageTime = lastMessage?.timestamp ?: Date(),
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
            lastMessageTime = lastMsg?.timestamp ?: Date(),
            messageCount = messageCount
        )
        chatDao.updateConversation(updated)
    }

    suspend fun hasValidMessages(conversationId: Long): Boolean {
        return chatDao.getMessageCount(conversationId) > 0
    }

    suspend fun deleteConversation(conversationId: Long) {
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val now = java.util.Date()
            chatDao.markMessagesDeletedForConversation(conversationId, now)
            chatDao.markConversationDeleted(conversationId, now)
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

    // Added: retry failed assistant message
    suspend fun retryFailedMessage(
        conversationId: Long,
        failedMessageId: Long,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
    ): Result<Unit> {
        return try {
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == failedMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (target.isFromUser) return Result.failure(IllegalArgumentException("Cannot retry user message"))
            if (!target.isError) return Result.failure(IllegalArgumentException("Message is not an error"))

            // 找到前一条用户消息
            var prevUserIndex = -1
            for (i in targetIndex - 1 downTo 0) {
                if (history[i].isFromUser && !history[i].isError) {
                    prevUserIndex = i
                    break
                }
            }
            if (prevUserIndex == -1) {
                val lastValidUserIndex = history.indexOfLast { it.isFromUser && !it.isError }
                if (lastValidUserIndex != -1) {
                    prevUserIndex = lastValidUserIndex
                } else {
                    chatDao.updateMessage(
                        target.copy(
                            content = "无法重新发送：缺少用户消息。",
                            isError = true
                        )
                    )
                    return Result.failure(IllegalStateException("No user messages in conversation"))
                }
            }

            // 删除失败的消息及其后的所有消息
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 获取用户消息内容
            val userMessage = history[prevUserIndex].content

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

            // 构造上下文
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

            // 关键词意图识别
            val weatherKeywords = listOf(
                "天气", "气温", "气候", "下雨", "降雨", "降雪", "风力", "空气质量",
                "雾霾", "穿衣", "紫外线", "晴", "阴", "多云", "预报", "未来",
                "今日", "明天", "后天", "温度", "湿度"
            )
            val isWeatherIntent = weatherKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }
            val stockKeywords = listOf(
                "股票", "股价", "证券", "行情", "涨跌", "K线", "成交量", "成交额", "换手率",
                "300", "600", "SH", "SZ", "同花顺", "东财", "收盘", "开盘", "历史"
            )
            val isStockIntent = stockKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }
            val goldKeywords = listOf(
                "黄金", "金价", "金条", "回收价", "回收黄金", "铂金", "银价", "金店",
                "购买黄金", "投资黄金", "金饰", "贵金属"
            )
            val isGoldIntent = goldKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }
            val hsKeywords = listOf(
                "高铁", "动车", "火车票", "车次", "一等座", "二等座", "商务座", "余票", "票价", "购票", "直达"
            )
            val isHsIntent = hsKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }
            val tikuKeywords = listOf(
                "题库", "百度题库", "考试", "选择题", "填空题", "判断题", "解析", "答案", "真题", "单选", "多选", "题目"
            )
            val isTikuIntent = tikuKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }
            val lotteryKeywords = listOf(
                "彩票", "彩票开奖", "开奖", "开奖公告", "开奖时间", "开奖号码", "中奖号码", "中奖",
                "快乐8", "双色球", "大乐透", "福彩3D", "排列3", "排列5", "七乐彩", "7星彩", "七星彩", "胜负彩", "进球彩", "半全场",
                "kl8", "ssq", "dlt", "fc3d", "pl3", "pl5", "qlc", "qxc", "sfc", "jqc", "bqc",
                "第"
            )
            val isLotteryIntent = lotteryKeywords.any { kw -> userMessage.contains(kw, ignoreCase = true) }

            // 构建工具定义
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
                    description = "查询指定城市未来几天天气与空气质量。使用中文城市或区县名，如\"滕州市\"。",
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
            val goldPriceTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "gold_price",
                    description = "查询黄金相关价格数据（银行金条、回收价、品牌贵金属）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = emptyMap(),
                        required = null
                    )
                )
            )
            val hsTicketTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "hs_ticket_query",
                    description = "查询高铁/动车车次、时间与价格（默认为当天日期）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "from" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "出发城市或车站中文名称"
                            ),
                            "to" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目的城市或车站中文名称"
                            ),
                            "date" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "查询日期（yyyy-MM-dd），未提供则默认为当天"
                            )
                        ),
                        required = listOf("from", "to")
                    )
                )
            )
            val baiduTikuTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "baidu_tiku",
                    description = "检索题库并返回题干/选项/答案。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "question" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "完整题干文本"
                            )
                        ),
                        required = listOf("question")
                    )
                )
            )
            val lotteryTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "lottery_query",
                    description = "查询指定彩种的最近开奖信息。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "get" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "彩种缩写：kl8、ssq、dlt、fc3d、pl3、pl5、qlc、qxc、sfc、jqc、bqc"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "查询天数（1-100），默认5"
                            )
                        ),
                        required = listOf("get")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                selectedTool?.type == ToolType.GOLD_PRICE -> listOf(goldPriceTool)
                selectedTool?.type == ToolType.HIGH_SPEED_TICKET -> listOf(hsTicketTool)
                selectedTool?.type == ToolType.BAIDU_TIKU -> listOf(baiduTikuTool)
                selectedTool?.type == ToolType.LOTTERY_QUERY -> listOf(lotteryTool)
                isAutoMode -> when {
                    isLotteryIntent -> listOf(lotteryTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isTikuIntent -> listOf(baiduTikuTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, lotteryTool)
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isGoldIntent -> listOf(goldPriceTool, webSearchTool, cityWeatherTool, stockDataTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isHsIntent -> listOf(hsTicketTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, baiduTikuTool, lotteryTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                }
                else -> null
            }

            // 添加系统提示
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
            if (isAutoMode && isGoldIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及黄金/贵金属，请优先考虑调用工具 gold_price 获取银行金条、回收价与品牌贵金属价格。"
                    )
                )
            }
            if (isAutoMode && isHsIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及高铁/动车车票，请优先考虑调用工具 hs_ticket_query 获取当日或指定日期的车次、时间与价格。"
                    )
                )
            }
            if (isAutoMode && isTikuIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及题库/考试，请优先考虑调用工具 baidu_tiku 进行题目检索与答案获取。如题干不完整，请礼貌询问或提示用户补充题目。"
                    )
                )
            }
            if (isAutoMode && isLotteryIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮对话涉及彩票开奖，请优先考虑调用工具 lottery_query 进行查询。若未明确彩种或期数，请礼貌询问或根据上下文推测。"
                    )
                )
            }

            // 插入新的助手消息占位
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在重新生成...",
                isFromUser = false,
                timestamp = Date(),
                modelDisplayName = model.name
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 0L
            var preLabelAdded = false
            var postLabelAdded = false

            withContext(Dispatchers.IO) {
                try {
                    streamWithFallback(
                        primaryGroup = group,
                        primaryModel = model,
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
                            when (toolCall.function?.name) {
                                "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                                "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                                "stock_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.STOCK_QUERY)
                                "gold_price" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.GOLD_PRICE)
                                "hs_ticket_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.HIGH_SPEED_TICKET)
                                "baidu_tiku" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.BAIDU_TIKU)
                                "lottery_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.LOTTERY_QUERY)
                                else -> {}
                            }
                            // 注意：工具调用在重试时不再处理，只进行简单的文本生成
                            // 如果需要工具调用，用户可以使用"重新生成"功能
                            onToolCallEnd?.invoke()
                        }
                    )

                    // 最终更新
                    val finalUpdated = assistantMessage.copy(
                        content = if (aggregated.isEmpty()) "生成失败或内容为空" else aggregated.toString()
                    )
                    chatDao.updateMessage(finalUpdated)
                    updateConversationAfterMessage(conversationId, userMessage)
                    Result.success(Unit)
                } catch (e: Exception) {
                    // 重试失败，保存详细错误信息
                    val errorMessage = when {
                        e.message?.contains("HTTP 401") == true -> "认证失败：API Key 无效或已过期"
                        e.message?.contains("HTTP 403") == true -> "访问被拒绝：没有权限访问该模型"
                        e.message?.contains("HTTP 429") == true -> "请求过于频繁：已达到速率限制"
                        e.message?.contains("HTTP 500") == true || e.message?.contains("HTTP 502") == true || 
                        e.message?.contains("HTTP 503") == true -> "服务器错误：模型服务暂时不可用"
                        e.message?.contains("timeout") == true -> "请求超时：网络连接超时"
                        e.message?.contains("Connection") == true -> "网络错误：无法连接到服务器"
                        else -> "生成失败：${e.message ?: "未知错误"}"
                    }
                    val errorUpdated = assistantMessage.copy(
                        content = errorMessage,
                        isError = true,
                        errorDetails = e.message
                    )
                    chatDao.updateMessage(errorUpdated)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Added: edit user message and resend from original position
    suspend fun editUserMessageAndResend(
        conversationId: Long,
        userMessageId: Long,
        newContent: String,
        selectedTool: Tool? = null,
        isAutoMode: Boolean = false,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
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

            // 删除其后的所有消息（保证重新生成上下文正确），保留当前用户消息本身
            chatDao.deleteMessagesAfterExclusive(conversationId, target.timestamp)

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
            val goldKeywords = listOf(
                "黄金", "金价", "金条", "回收价", "回收黄金", "铂金", "银价", "金店",
                "购买黄金", "投资黄金", "金饰", "贵金属"
            )
            val isGoldIntent = goldKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }
            val hsKeywords = listOf(
                "高铁", "动车", "火车票", "车次", "一等座", "二等座", "商务座", "余票", "票价", "购票", "直达"
            )
            val isHsIntent = hsKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }
            val tikuKeywords = listOf(
                "题库", "百度题库", "考试", "选择题", "填空题", "判断题", "解析", "答案", "真题", "单选", "多选", "题目"
            )
            val isTikuIntent = tikuKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }
            val lotteryKeywords = listOf(
                "彩票", "彩票开奖", "开奖", "开奖公告", "开奖时间", "开奖号码", "中奖号码", "中奖",
                "快乐8", "双色球", "大乐透", "福彩3D", "排列3", "排列5", "七乐彩", "7星彩", "七星彩", "胜负彩", "进球彩", "半全场",
                "kl8", "ssq", "dlt", "fc3d", "pl3", "pl5", "qlc", "qxc", "sfc", "jqc", "bqc",
                "第"
            )
            val isLotteryIntent = lotteryKeywords.any { kw -> trimmed.contains(kw, ignoreCase = true) }

            var preLabelAdded = false
            var postLabelAdded = false

            // 插入新的助手消息占位以进行流式写入
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在思考...",
                isFromUser = false,
                timestamp = Date(),
                modelDisplayName = model.name
            )
            val assistantId = chatDao.insertMessage(assistantMessage)
            assistantMessage = assistantMessage.copy(id = assistantId)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 0L

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
            val goldPriceTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "gold_price",
                    description = "查询黄金相关价格数据（银行金条、回收价、品牌贵金属）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = emptyMap(),
                        required = null
                    )
                )
            )
            val hsTicketTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "hs_ticket_query",
                    description = "查询高铁/动车车次、时间与价格（默认为当天日期）。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "from" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "出发城市或车站中文名称"
                            ),
                            "to" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "目的城市或车站中文名称"
                            ),
                            "date" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "查询日期（yyyy-MM-dd），未提供则默认为当天"
                            )
                        ),
                        required = listOf("from", "to")
                    )
                )
            )
            val baiduTikuTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "baidu_tiku",
                    description = "检索题库并返回题干/选项/答案。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "question" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "完整题干文本"
                            )
                        ),
                        required = listOf("question")
                    )
                )
            )
            val lotteryTool = com.glassous.aime.data.Tool(
                type = "function",
                function = com.glassous.aime.data.ToolFunction(
                    name = "lottery_query",
                    description = "查询指定彩种的最近开奖信息。",
                    parameters = com.glassous.aime.data.ToolFunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "get" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "string",
                                description = "彩种缩写：kl8、ssq、dlt、fc3d、pl3、pl5、qlc、qxc、sfc、jqc、bqc"
                            ),
                            "num" to com.glassous.aime.data.ToolFunctionParameter(
                                type = "integer",
                                description = "查询天数（1-100），默认5"
                            )
                        ),
                        required = listOf("get")
                    )
                )
            )
            val tools = when {
                selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
                selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
                selectedTool?.type == ToolType.STOCK_QUERY -> listOf(stockDataTool)
                selectedTool?.type == ToolType.GOLD_PRICE -> listOf(goldPriceTool)
                selectedTool?.type == ToolType.HIGH_SPEED_TICKET -> listOf(hsTicketTool)
                selectedTool?.type == ToolType.BAIDU_TIKU -> listOf(baiduTikuTool)
                selectedTool?.type == ToolType.LOTTERY_QUERY -> listOf(lotteryTool)
                isAutoMode -> when {
                    isLotteryIntent -> listOf(lotteryTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool)
                    isTikuIntent -> listOf(baiduTikuTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, lotteryTool)
                    isWeatherIntent -> listOf(cityWeatherTool, webSearchTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isStockIntent -> listOf(stockDataTool, webSearchTool, cityWeatherTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isGoldIntent -> listOf(goldPriceTool, webSearchTool, cityWeatherTool, stockDataTool, hsTicketTool, baiduTikuTool, lotteryTool)
                    isHsIntent -> listOf(hsTicketTool, webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, baiduTikuTool, lotteryTool)
                    else -> listOf(webSearchTool, cityWeatherTool, stockDataTool, goldPriceTool, hsTicketTool, baiduTikuTool, lotteryTool)
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
            if (isAutoMode && isGoldIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及黄金/贵金属，请优先考虑调用工具 gold_price 获取银行金条、回收价与品牌贵金属价格。"
                    )
                )
            }
            if (isAutoMode && isHsIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及高铁/动车车票，请优先考虑调用工具 hs_ticket_query 获取当日或指定日期的车次、时间与价格。"
                    )
                )
            }
            if (isAutoMode && isTikuIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及题库/考试，请优先考虑调用工具 baidu_tiku 进行题目检索与答案获取。如题干不完整，请礼貌询问或提示用户补充题目。"
                    )
                )
            }
            if (isAutoMode && isLotteryIntent) {
                messagesWithBias.add(
                    OpenAiChatMessage(
                        role = "system",
                        content = "该轮编辑后的用户消息涉及彩票开奖，请优先考虑调用工具 lottery_query 进行查询。若未明确彩种或期数，请礼貌询问或根据上下文推测。"
                    )
                )
            }

            withContext(Dispatchers.IO) {
                streamWithFallback(
                    primaryGroup = group,
                    primaryModel = model,
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
                            "gold_price" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.GOLD_PRICE)
                            "hs_ticket_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.HIGH_SPEED_TICKET)
                            "baidu_tiku" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.BAIDU_TIKU)
                            "lottery_query" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.LOTTERY_QUERY)
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
                                    
                                    messagesWithSearch.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = searchResultsText
                                        )
                                    )
                                    
                                    // 重新调用AI，让它基于搜索结果生成回答
                                    // 注意：这里不再传递工具，避免无限循环
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
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
                                    
                                    messagesWithWeather.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = weatherText
                                        )
                                    )
                                    
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
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
                                    
                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = buildList {
                                            addAll(contextMessages)
                                            // 注入“非必要的用户背景”系统消息（仅当存在已填写字段时）
                                            
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
                        } else if (toolCall.function?.name == "baidu_tiku") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val question = safeExtractQuestion(arguments, trimmed)
                                    val tikuResult = BaiduTikuService().query(question)
                                    val md = BaiduTikuService().formatAsMarkdown(tikuResult)
                                    aggregated.append("\n\n\n")
                                    aggregated.append(md)
                                    aggregated.append("\n\n\n")
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)

                                    val messagesWithTiku = contextMessages.toMutableList()
                                    val summary = if (tikuResult.success) {
                                        val ans = tikuResult.answer.ifBlank { "暂无" }
                                        "题目：${tikuResult.question}；答案：${ans}。请基于此给出简洁回答。"
                                    } else {
                                        "题库查询失败：${tikuResult.message}，请基于已有信息回复用户。"
                                    }
                                    messagesWithTiku.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = summary
                                        )
                                    )

                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = messagesWithTiku,
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
                                aggregated.append("\n\n题库工具暂时不可用：${e.message}\n\n")
                            }
                        } else if (toolCall.function?.name == "lottery_query") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val getVal = safeExtractGet(arguments, trimmed)
                                    val numVal = safeExtractNum(arguments, 5).coerceIn(1, 100)
                                    val lot = LotteryService().query(getVal.ifBlank { "ssq" }, numVal)
                                    val md = LotteryService().formatAsMarkdown(lot)
                                    aggregated.append("\n\n\n")
                                    aggregated.append(md)
                                    aggregated.append("\n\n\n")
                                    postLabelAdded = true
                                    val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                    chatDao.updateMessage(updatedBeforeOfficial)

                                    val messagesWithLottery = contextMessages.toMutableList()
                                    val summary = if (lot.success && lot.items.isNotEmpty()) {
                                        val first = lot.items.first()
                                        val firstIssue = first.issue ?: ""
                                        val firstDraw = first.drawnumber ?: ""
                                        "彩种：${lot.name}；最新期号：${firstIssue}；开奖号码：${firstDraw}。请据此简洁回答。"
                                    } else {
                                        "彩票开奖查询失败：${lot.message}，请基于已有信息回复用户。"
                                    }
                                    messagesWithLottery.add(
                                        OpenAiChatMessage(
                                            role = "system",
                                            content = summary
                                        )
                                    )

                                    streamWithFallback(
                                        primaryGroup = group,
                                        primaryModel = model,
                                        messages = messagesWithLottery,
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
                                aggregated.append("\n\n彩票开奖工具暂时不可用：${e.message}\n\n")
                            }
                        } else if (toolCall.function?.name == "gold_price") {
                            try {
                                val goldResult = GoldPriceService().query()
                                val md = GoldPriceService().formatAsMarkdownParagraphs(goldResult)
                                aggregated.append("\n\n\n")
                                aggregated.append(md)
                                aggregated.append("\n\n\n")
                                postLabelAdded = true
                                val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                chatDao.updateMessage(updatedBeforeOfficial)

                                val messagesWithGold = contextMessages.toMutableList()
                                messagesWithGold.add(
                                    OpenAiChatMessage(
                                        role = "system",
                                        content = md
                                    )
                                )
                                messagesWithGold.add(
                                    OpenAiChatMessage(
                                        role = "system",
                                        content = "已获取黄金价格数据，请结合用户需求给出购买建议（如购买金条/首饰或回收差价等），并提示价格波动与风险。"
                                    )
                                )
                                streamWithFallback(
                                    primaryGroup = group,
                                    primaryModel = model,
                                    messages = messagesWithGold,
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
                            } catch (e: Exception) {
                                aggregated.append("\n\n黄金价格工具暂时不可用：${e.message}\n\n")
                            }
                        } else if (toolCall.function?.name == "hs_ticket_query") {
                            try {
                                val arguments = toolCall.function.arguments
                                if (arguments != null) {
                                    val from = safeExtractFrom(arguments) ?: ""
                                    val to = safeExtractTo(arguments) ?: ""
                                    val date = safeExtractDate(arguments)
                                    if (from.isNotEmpty() && to.isNotEmpty()) {
                                        val payload = HighSpeedTicketService().query(from, to, date)
                                        val summaryText = HighSpeedTicketService().formatCondensed(payload)
                                        aggregated.append("\n\n\n")
                                        aggregated.append(summaryText)
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)

                                        val messagesWithHs = contextMessages.toMutableList()
                                        messagesWithHs.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = summaryText
                                            )
                                        )
                                        streamWithFallback(
                                            primaryGroup = group,
                                            primaryModel = model,
                                            messages = messagesWithHs,
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
                                }
                            } catch (e: Exception) {
                                aggregated.append("\n\n高铁车票工具暂时不可用：${e.message}\n\n")
                            }
                        }
                        onToolCallEnd?.invoke()
                    }
                )
            }

            // 最终写入完整文本并刷新会话元数据
            val finalText = if (aggregated.isEmpty()) "生成失败或内容为空" else aggregated.toString()
            chatDao.updateMessage(assistantMessage.copy(content = finalText))
            refreshConversationMetadata(conversationId)
            
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

    

    private fun safeExtractUrl(arguments: String?, default: String): String {
        // If arguments is null (client-side extraction from message), use default (message) as source
        val raw = if (arguments.isNullOrBlank()) default.trim() else arguments.trim()
        val gson = Gson()

        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                val value = map["url"] as? String
                if (value.isNullOrBlank()) null else value
            } catch (_: Exception) {
                null
            }
        }

        tryParse(raw)?.let { return it }

        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }

        val regexQuoted = Regex("""(?i)\"?url\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""")
        val regexUnquoted = Regex("""(?i)\"?url\"?\s*[:=]\s*([^,}\n\r]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        // Fallback: Check if the argument itself is a URL
        if (raw.startsWith("http")) return raw
        
        // Check if message contains url
        // Updated Regex to handle URL inside text more robustly
        val urlInMessage = Regex("""https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]""").find(raw)?.value
        if (!urlInMessage.isNullOrBlank()) return urlInMessage

        // If extraction failed, return empty string to indicate failure (instead of default which might be the whole message text)
        // BUT for existing logic compatibility (arguments != null case), we might need to be careful.
        // For client-side logic (arguments=null), we want URL or empty.
        if (arguments.isNullOrBlank()) return ""

        return default
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

    private fun safeExtractQuestion(arguments: String?, default: String): String {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()
        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                val value = map["question"] as? String
                if (value.isNullOrBlank()) null else value
            } catch (_: Exception) {
                null
            }
        }
        tryParse(raw)?.let { return it }
        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }
        val regexQuoted = Regex("""(?i)\"?question\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""
        )
        val regexUnquoted = Regex("""(?i)\"?question\"?\s*[:=]\s*([^,}\n\r]+)"""
        )
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return raw
    }

    private fun safeExtractGet(arguments: String?, defaultText: String): String {
        if (!arguments.isNullOrBlank()) {
            val raw = arguments.trim()
            val gson = Gson()
            fun tryParse(text: String): String? {
                return try {
                    val reader = JsonReader(StringReader(text))
                    reader.isLenient = true
                    val type = object : TypeToken<Map<String, Any?>>() {}.type
                    val map: Map<String, Any?> = gson.fromJson(reader, type)
                    (map["get"] as? String)?.lowercase()?.takeIf { it.isNotBlank() }
                } catch (_: Exception) { null }
            }
            tryParse(raw)?.let { return it }
            val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
            tryParse(normalizedSingleQuotes)?.let { return it }
            val regexQuoted = Regex("""(?i)\"?get\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""
            )
            val regexUnquoted = Regex("""(?i)\"?get\"?\s*[:=]\s*([^,}\n\r]+)"""
            )
            regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }
            regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val text = defaultText.lowercase()
        val mapping = listOf(
            "快乐8" to "kl8",
            "kl8" to "kl8",
            "双色球" to "ssq",
            "ssq" to "ssq",
            "大乐透" to "dlt",
            "dlt" to "dlt",
            "福彩3d" to "fc3d",
            "fc3d" to "fc3d",
            "排列3" to "pl3",
            "pl3" to "pl3",
            "排列5" to "pl5",
            "pl5" to "pl5",
            "七乐彩" to "qlc",
            "qlc" to "qlc",
            "7星彩" to "qxc",
            "七星彩" to "qxc",
            "qxc" to "qxc",
            "胜负彩" to "sfc",
            "sfc" to "sfc",
            "进球彩" to "jqc",
            "jqc" to "jqc",
            "半全场" to "bqc",
            "bqc" to "bqc"
        )
        for ((k, v) in mapping) {
            if (text.contains(k)) return v
        }
        return "ssq"
    }

    private fun safeExtractFrom(arguments: String?): String? {
        if (arguments.isNullOrBlank()) return null
        val raw = arguments.trim()
        val gson = Gson()
        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                (map["from"] as? String)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }
        tryParse(raw)?.let { return it }
        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }
        val regexQuoted = Regex("""(?i)\"?from\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""
        )
        val regexUnquoted = Regex("""(?i)\"?from\"?\s*[:=]\s*([^,}\n\r]+)"""
        )
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun safeExtractTo(arguments: String?): String? {
        if (arguments.isNullOrBlank()) return null
        val raw = arguments.trim()
        val gson = Gson()
        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                (map["to"] as? String)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }
        tryParse(raw)?.let { return it }
        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }
        val regexQuoted = Regex("""(?i)\"?to\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""
        )
        val regexUnquoted = Regex("""(?i)\"?to\"?\s*[:=]\s*([^,}\n\r]+)"""
        )
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun safeExtractDate(arguments: String?): String? {
        if (arguments.isNullOrBlank()) return null
        val raw = arguments.trim()
        val gson = Gson()
        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                (map["date"] as? String)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }
        tryParse(raw)?.let { return it }
        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }
        val regexQuoted = Regex("""(?i)\"?date\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""
        )
        val regexUnquoted = Regex("""(?i)\"?date\"?\s*[:=]\s*([^,}\n\r]+)"""
        )
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    // 统一的流式调用，失败时不再自动回调，而是抛出异常让上层处理
    private suspend fun streamWithFallback(
        primaryGroup: ModelGroup,
        primaryModel: Model,
        messages: List<OpenAiChatMessage>,
        tools: List<com.glassous.aime.data.Tool>? = null,
        toolChoice: String? = null,
        onDelta: suspend (String) -> Unit,
        onToolCall: suspend (ToolCall) -> Unit = {}
    ): String {
        if (primaryGroup.baseUrl.contains("volces", ignoreCase = true)) {
            return doubaoService.streamChatCompletions(
                baseUrl = primaryGroup.baseUrl,
                apiKey = primaryGroup.apiKey,
                model = primaryModel.modelName,
                messages = messages,
                tools = tools,
                toolChoice = toolChoice,
                onDelta = onDelta,
                onToolCall = onToolCall
            )
        }
        // 直接调用OpenAI服务，失败时抛出异常
        return openAiService.streamChatCompletions(
            baseUrl = primaryGroup.baseUrl,
            apiKey = primaryGroup.apiKey,
            model = primaryModel.modelName,
            messages = messages,
            tools = tools,
            toolChoice = toolChoice,
            onDelta = onDelta,
            onToolCall = onToolCall
        )
    }

    /**
     * 过滤掉 <think> 标签及其内容
     */
    private fun filterThinkTags(text: String): String {
        // 使用正则表达式移除 <think>...</think> 标签及其内容
        return text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "") // 处理未闭合的标签
    }
}
