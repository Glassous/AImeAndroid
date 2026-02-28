package com.glassous.aime.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.glassous.aime.data.repository.ModelConfigRepository
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.ContextPreferences
 
import com.glassous.aime.data.model.BuiltInModels
import com.glassous.aime.data.model.Tool
import com.glassous.aime.data.model.ToolType
 
import com.google.gson.Gson
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.IOException
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink


class ChatRepository(
    private val context: Context,
    private val chatDao: ChatDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val modelPreferences: ModelPreferences,
    private val contextPreferences: ContextPreferences,
    private val toolPreferences: com.glassous.aime.data.preferences.ToolPreferences,
    private val openAiService: OpenAiService = OpenAiService(),
    private val doubaoService: DoubaoArkService = DoubaoArkService(),
    private val webSearchService: WebSearchService = WebSearchService(),
    private val weatherService: WeatherService = WeatherService(),
    private val musicSearchService: MusicSearchService = MusicSearchService()
) {
    
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return chatDao.getAllConversations()
    }

    private suspend fun buildSystemPrompt(): String {
        // 注入用户配置的系统提示词
        var systemPrompt = modelPreferences.systemPrompt.first()
        
        // 如果是内置模型，添加默认前缀
        val selectedModelId = modelPreferences.selectedModelId.first()
        if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
            val aimePrompt = "你的名称是“AIme”，由 FiaCloud 开发。"
            systemPrompt = if (systemPrompt.isEmpty()) aimePrompt else "$aimePrompt\n$systemPrompt"
        }

        val enableDate = modelPreferences.enableDynamicDate.first()
        val enableTimestamp = modelPreferences.enableDynamicTimestamp.first()
        val enableLocation = modelPreferences.enableDynamicLocation.first()
        val enableDeviceModel = modelPreferences.enableDynamicDeviceModel.first()
        val enableLanguage = modelPreferences.enableDynamicLanguage.first()
        
        val dynamicInfos = mutableListOf<String>()
        
        if (enableDate) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dynamicInfos.add("当前日期: ${dateFormat.format(Date())}")
        }
        
        if (enableTimestamp) {
            dynamicInfos.add("当前时间戳: ${System.currentTimeMillis()}")
        }

        if (enableDeviceModel) {
            dynamicInfos.add("设备型号: ${android.os.Build.MODEL}")
        }

        if (enableLanguage) {
            dynamicInfos.add("系统语言: ${Locale.getDefault()}")
        }
        
        if (enableLocation) {
            try {
                val location = getLastKnownLocation()
                if (location != null) {
                    dynamicInfos.add("当前位置: ${location.latitude}, ${location.longitude}")
                    // Add instruction for weather tool if location is available
                    dynamicInfos.add("注意：天气查询工具是基于经纬度运行的。如果用户查询天气，**必须**使用上述“当前位置”的经纬度调用 city_weather 工具（传递 latitude 和 longitude 参数），不要仅使用 city 参数")
                } else {
                    dynamicInfos.add("当前位置: 未知 (无法获取)")
                }
            } catch (e: Exception) {
                dynamicInfos.add("当前位置: 未知 (权限或服务异常)")
            }
        }
        
        if (dynamicInfos.isNotEmpty()) {
            val dynamicInfoStr = "\n\n[系统环境信息]\n" + dynamicInfos.joinToString("\n")
            systemPrompt = if (systemPrompt.isEmpty()) dynamicInfoStr.trim() else systemPrompt + dynamicInfoStr
        }
        
        return systemPrompt
    }

    private fun getTools(selectedTool: Tool?): List<com.glassous.aime.data.Tool>? {
        val webSearchTool = com.glassous.aime.data.Tool(
            type = "function",
            function = com.glassous.aime.data.ToolFunction(
                name = "web_search",
                description = "搜索互联网获取实时信息。当用户询问需要最新信息、实时数据或当前事件时使用此工具。重要：必须使用中文关键词进行搜索，以获得更准确的中文搜索结果。在回答中，必须使用 `(ref:n)` 格式引用搜索结果（n为搜索结果序号，例如 `(ref:1)`、`(ref:2)`），严禁使用其他引用格式（如 `[1]` 或 `【1】`）。",
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
                description = "查询指定城市或当前位置的未来几天天气与空气质量。此API基于地理坐标（经纬度）运行。如果你知道目标位置的经纬度（例如当前位置），必须使用 `latitude` 和 `longitude` 参数。仅当你不知道经纬度时（例如查询其他城市），才使用 `city` 参数（系统将尝试自动查找坐标）。",
                parameters = com.glassous.aime.data.ToolFunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "city" to com.glassous.aime.data.ToolFunctionParameter(
                            type = "string",
                            description = "目标城市或区县中文名称（仅当经纬度未知时使用）"
                        ),
                        "latitude" to com.glassous.aime.data.ToolFunctionParameter(
                            type = "number",
                            description = "纬度 (例如 39.9)"
                        ),
                        "longitude" to com.glassous.aime.data.ToolFunctionParameter(
                            type = "number",
                            description = "经度 (例如 116.4)"
                        )
                    ),
                    required = listOf() // No required parameters because either city or lat/long is sufficient
                )
            )
        )
        val musicSearchTool = com.glassous.aime.data.Tool(
            type = "function",
            function = com.glassous.aime.data.ToolFunction(
                name = "music_search",
                description = "搜索音乐。支持按歌曲名、歌手名或“歌手名+歌曲名”进行搜索。例如：“Love Story” 或 “周杰伦 七里香” 或 “陈奕迅”。",
                parameters = com.glassous.aime.data.ToolFunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "keyword" to com.glassous.aime.data.ToolFunctionParameter(
                            type = "string",
                            description = "搜索关键词（可以是歌名、歌手名，或两者结合）"
                        )
                    ),
                    required = listOf("keyword")
                )
            )
        )
        return when {
            selectedTool?.type == ToolType.WEB_SEARCH -> listOf(webSearchTool)
            selectedTool?.type == ToolType.MUSIC_SEARCH -> listOf(musicSearchTool)
            selectedTool?.type == ToolType.WEATHER_QUERY -> listOf(cityWeatherTool)
            else -> null
        }
    }

    suspend fun sendMessage(
        conversationId: Long,
        message: String,
        imagePaths: List<String> = emptyList(),
        selectedTool: Tool? = null,
        aspectRatio: String = "1:1",
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
    ): Result<ChatMessage> {
        return try {
            var processedMessage = message
            var webAnalysisToolUsed = false

            // Client-side handling for Image Generation
            if (selectedTool?.type == ToolType.IMAGE_GENERATION) {
                // Save user message first
                val userMsg = ChatMessage(
                    conversationId = conversationId,
                    content = message,
                    isFromUser = true,
                    timestamp = Date(),
                    imagePaths = imagePaths
                )
                chatDao.insertMessage(userMsg)
                updateConversationAfterMessage(conversationId, message)

                return handleImageGeneration(
                    conversationId = conversationId,
                    prompt = message,
                    selectedTool = selectedTool,
                    aspectRatio = aspectRatio,
                    onToolCallStart = onToolCallStart,
                    onToolCallEnd = onToolCallEnd
                )
            }

            // Client-side handling for Web Analysis
            if (selectedTool?.type == ToolType.WEB_ANALYSIS && imagePaths.isEmpty()) {
                // 1. Extract URL
                val url = safeExtractUrl(null, message)
                // Remove url != message check to allow single URL messages
                if (url.isNotEmpty() && url.startsWith("http")) {
                    try {
                        onToolCallStart?.invoke(ToolType.WEB_ANALYSIS)
                        // 2. Fetch content
                        val useCloudProxy = modelPreferences.useCloudProxy.first()
                        val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL
                        val result = withContext(Dispatchers.IO) {
                            webSearchService.fetchWebPage(url, useCloudProxy, proxyUrl)
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
                        processedMessage = if (message.trim() == url.trim()) {
                            "帮我分析以下网页的内容：\n\n" + cardMarkdown
                        } else {
                            val msgWithoutUrl = message.replace(url, "").trim()
                            if (msgWithoutUrl.isEmpty()) {
                                "帮我分析以下网页的内容：\n\n" + cardMarkdown
                            } else {
                                "帮我分析以下网页的内容：\n" + msgWithoutUrl + "\n\n" + cardMarkdown
                            }
                        }
                        
                        webAnalysisToolUsed = true
                    } catch (e: Exception) {
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
                timestamp = Date(),
                imagePaths = imagePaths
            )
            val insertedId = chatDao.insertMessage(userMessage)

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
            val model = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeModel
            } else {
                modelConfigRepository.getModelById(selectedModelId)
            }

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

            val group = if (model.groupId == BuiltInModels.AIME_GROUP_ID) {
                BuiltInModels.aimeGroup
            } else {
                modelConfigRepository.getGroupById(model.groupId)
            }

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

            val useCloudProxy = if (model.id == BuiltInModels.AIME_MODEL_ID) {
                true
            } else {
                modelPreferences.useCloudProxy.first()
            }
            val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL

            // Build conversation context for OpenAI standard
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val baseMessages = history
                .filter { !it.isError }
                .map { toOpenAiMessage(it) }
                .toMutableList()
            
            val isMessageIncluded = history.any { it.id == insertedId }
            if (!isMessageIncluded) {
                val userMsg = ChatMessage(
                    conversationId = conversationId,
                    content = processedMessage,
                    isFromUser = true,
                    timestamp = Date(),
                    imagePaths = imagePaths
                )
                baseMessages.add(toOpenAiMessage(userMsg))
            }
            
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

            val systemPrompt = buildSystemPrompt()
            if (systemPrompt.isNotBlank()) {
                messages.add(0, OpenAiChatMessage(role = "system", content = systemPrompt))
            }

            val tools = getTools(selectedTool)

            // Insert assistant placeholder for streaming
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在加载 ${model.name}...",
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
            var toolCallHandled = false 
            
            try {
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
                            if (currentTime - lastUpdateTime >= updateInterval) {
                                val updated = assistantMessage.copy(content = aggregated.toString())
                                chatDao.updateMessage(updated)
                                lastUpdateTime = currentTime
                            }
                        },
                        onToolCall = { toolCall ->
                            if (toolCallHandled) return@streamWithFallback
                            toolCallHandled = true
                            
                when (toolCall.function?.name) {
                    "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                    "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
                    "music_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.MUSIC_SEARCH)
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
                                        val searchResultCount = toolPreferences.webSearchResultCount.first()
                                        val searchEngine = toolPreferences.webSearchEngine.first()
                                        val tavilyKey = toolPreferences.tavilyApiKey.first()
                                        val tavilyUseProxy = toolPreferences.tavilyUseProxy.first()
                                        
                                        val searchResponse = webSearchService.search(
                                            query = query,
                                            maxResults = searchResultCount,
                                            useCloudProxy = useCloudProxy,
                                            proxyUrl = proxyUrl,
                                            onProgress = { progress ->
                                                val progressContent = aggregated.toString() + "\n\n<search>\n" + progress + "\n</search>"
                                                val progressMessage = assistantMessage.copy(content = progressContent)
                                                chatDao.updateMessage(progressMessage)
                                            },
                                            engine = searchEngine,
                                            apiKey = tavilyKey,
                                            tavilyUseProxy = tavilyUseProxy
                                        )

                                        if (searchResponse.results.isNotEmpty()) {
                                            val linksMarkdown = searchResponse.results.mapIndexed { index, r ->
                                                val base = "${index + 1}. [${r.title}](${r.url})"
                                                if (r.image != null) "$base ![image](${r.image})" else base
                                            }.joinToString("\n")
                                        aggregated.append("\n\n\n")
                                        aggregated.append("<search>\n")
                                        aggregated.append(linksMarkdown)
                                        aggregated.append("\n</search>")
                                        aggregated.append("\n\n\n")
                                            postLabelAdded = true
                                            val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                            chatDao.updateMessage(updatedBeforeOfficial)
                                        }

                                        val searchResultsText = if (searchResponse.results.isNotEmpty()) {
                                            val formatted = webSearchService.formatSearchResults(searchResponse)
                                            "$formatted\n\n请基于以上搜索结果回答用户问题。不要在末尾附加网址或参考链接。"
                                        } else {
                                            "搜索未找到相关结果，请基于你的知识回答用户的问题。不要在末尾附加网址或参考链接。"
                                        }
                                        
                                        val messagesWithSearch = messages.toMutableList()
                                        messagesWithSearch.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = searchResultsText
                                            )
                                        )
                                        
                                        val searchBasedResponse = streamWithFallback(
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
                                            },
                                            onToolCall = { }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n搜索工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "city_weather") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        var lat: Double? = null
                                        var lon: Double? = null
                                        var cityArg: String? = null
                                        
                                        try {
                                            val gson = Gson()
                                            val type = object : TypeToken<Map<String, Any?>>() {}.type
                                            val map: Map<String, Any?> = gson.fromJson(arguments, type)
                                            lat = (map["latitude"] as? Number)?.toDouble()
                                            lon = (map["longitude"] as? Number)?.toDouble()
                                            cityArg = map["city"] as? String
                                        } catch (e: Exception) {
                                            // ignore
                                        }

                                        val weatherResult = if (lat != null && lon != null) {
                                            weatherService.query(lat, lon)
                                        } else {
                                            val finalCity = if (!cityArg.isNullOrBlank()) cityArg else safeExtractCity(arguments, message)
                                            weatherService.query(finalCity)
                                        }
                                        
                                        val weatherText = weatherService.format(weatherResult)

                                        val weatherTable = weatherService.formatAsMarkdownTable(weatherResult)
                                        aggregated.append("\n\n\n") 
                                        aggregated.append(weatherTable.trim())
                                        aggregated.append("\n\n\n") 
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
                                            onToolCall = { }
                                        )
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n天气工具暂时不可用：${e.message}\n\n")
                                }
                            } else if (toolCall.function?.name == "music_search") {
                                try {
                                    val arguments = toolCall.function.arguments
                                    if (arguments != null) {
                                        val keyword = safeExtractKeyword(arguments)
                                        val source = toolPreferences.musicSearchSource.first()
                                        val limit = toolPreferences.musicSearchResultCount.first()
                                        
                                        val sourceName = when (source) {
                                            "wy" -> "网易云"
                                            "qq" -> "QQ音乐"
                                            "kw" -> "酷我"
                                            "mg" -> "咪咕"
                                            "qi" -> "千千"
                                            else -> source
                                        }
                                        
                                        aggregated.append("\n\n\n正在搜索音乐：$keyword (来源: $sourceName)...\n\n\n")
                                        val loadingMsg = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(loadingMsg)

                                        val candidates = musicSearchService.search(keyword, source, limit)
                                        
                                        if (candidates.isEmpty()) {
                                            aggregated.append("未找到相关歌曲。")
                                        } else {
                                            val candidatesJson = Gson().toJson(candidates.map { 
                                                mapOf("id" to it.id, "name" to it.name, "artist" to it.artist, "album" to it.album) 
                                            })
                                            
                                            val fullUserQuery = messages.lastOrNull { it.role == "user" }?.content ?: keyword

                                            val selectionPrompt = """
                                                用户原始请求: "$fullUserQuery"
                                                搜索关键词: "$keyword"
                                                候选歌曲列表:
                                                $candidatesJson
                                                
                                                请从列表中选择最符合用户原始请求的 $limit 首歌曲。
                                                必须同时匹配歌曲名和歌手名（如果用户提到了歌手）。
                                                请只返回这 $limit 首歌曲的ID，用逗号分隔（例如：id1,id2,id3...）。
                                                如果匹配数量不足 $limit 首，则返回所有匹配的 ID。
                                                如果都不匹配，返回 "None"。
                                            """.trimIndent()
                                            
                                            val selectionMessages = listOf(
                                                OpenAiChatMessage(role = "system", content = "你是一个精准的音乐选择助手。只返回用逗号分隔的ID列表。"),
                                                OpenAiChatMessage(role = "user", content = selectionPrompt)
                                            )
                                            
                                            val selectedIdsRaw = try {
                                                val idBuilder = StringBuilder()
                                                streamWithFallback(
                                                    primaryGroup = group,
                                                    primaryModel = model,
                                                    messages = selectionMessages,
                                                    tools = null,
                                                    toolChoice = null,
                                                    onDelta = { idBuilder.append(it) }
                                                )
                                                idBuilder.toString().trim()
                                            } catch (e: Exception) {
                                                ""
                                            }
                                            val selectedIdsStr = selectedIdsRaw.replace("\"", "").replace("'", "")
                                            
                                            val finalIds = if (selectedIdsStr.isBlank() || selectedIdsStr.equals("None", ignoreCase = true)) {
                                                candidates.take(limit).mapNotNull { it.id }
                                            } else {
                                                selectedIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            }
                                            
                                            if (finalIds.isNotEmpty()) {
                                                aggregated.setLength(0)
                                                
                                                val details = try {
                                                    supervisorScope {
                                                        finalIds.map { id ->
                                                            async { musicSearchService.fetchDetail(id, source) }
                                                        }.awaitAll().filterNotNull()
                                                    }
                                                } catch (e: Exception) {
                                                    emptyList()
                                                }

                                                if (details.isNotEmpty()) {
                                                    details.forEach { detail ->
                                                        aggregated.append("<music>\n")
                                                        aggregated.append("Name: ${detail.name}\n")
                                                        aggregated.append("Artist: ${detail.artist}\n")
                                                        aggregated.append("Album: ${detail.album}\n")
                                                        aggregated.append("URL: ${detail.url}\n")
                                                        aggregated.append("Pic: ${detail.pic}\n")
                                                        if (!detail.lrc.isNullOrEmpty()) {
                                                            aggregated.append("Lrc: ${detail.lrc}\n")
                                                        }
                                                        aggregated.append("</music>\n\n")
                                                    }
                                                } else {
                                                    aggregated.append("无法获取歌曲详情。")
                                                }
                                            } else {
                                                aggregated.append("未找到匹配的歌曲。")
                                            }
                                        }
                                        
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                    }
                                } catch (e: Exception) {
                                    aggregated.append("\n\n音乐搜索工具暂时不可用：${e.message}\n\n")
                                }
                            }
                            onToolCallEnd?.invoke()
                        }
                    )
                }

            val finalUpdated = assistantMessage.copy(content = if (aggregated.isEmpty()) "生成失败或内容为空" else aggregated.toString())
            chatDao.updateMessage(finalUpdated)

                updateConversationAfterMessage(conversationId, message)

                Result.success(finalUpdated)
            } catch (e: Exception) {
                val errorMessage = getErrorMessage(e)
                val finalContent = if (aggregated.isNotEmpty()) {
                    aggregated.toString() + "\n\n" + errorMessage
                } else {
                    errorMessage
                }
                val errorUpdated = assistantMessage.copy(
                    content = finalContent,
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
        aspectRatio: String = "1:1",
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
                    return Result.failure(IllegalStateException("No user messages in conversation"))
                }
            }

            val userMessage = history[prevUserIndex].content

            // Client-side handling for Image Generation in Regenerate
            if (selectedTool?.type == ToolType.IMAGE_GENERATION) {
                handleImageGeneration(
                    conversationId = conversationId,
                    prompt = userMessage,
                    selectedTool = selectedTool,
                    aspectRatio = aspectRatio,
                    onToolCallStart = onToolCallStart,
                    onToolCallEnd = onToolCallEnd,
                    existingAssistantMessageId = assistantMessageId
                )
                return Result.success(Unit)
            }

            // 解析模型配置
            val selectedModelId = modelPreferences.selectedModelId.first()
            if (selectedModelId.isNullOrBlank()) {
                chatDao.updateMessage(
                    target.copy(content = "请先选择模型（设置→模型配置）", isError = true)
                )
                return Result.failure(IllegalStateException("No selected model"))
            }
            val model = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeModel
            } else {
                modelConfigRepository.getModelById(selectedModelId)
            } ?: run {
                    chatDao.updateMessage(target.copy(content = "所选模型不存在或已删除，请重新选择。", isError = true))
                    return Result.failure(IllegalStateException("Selected model not found"))
                }
            val group = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeGroup
            } else {
                modelConfigRepository.getGroupById(model.groupId)
            } ?: run {
                    chatDao.updateMessage(target.copy(content = "模型分组配置缺失，无法请求，请检查 base url 与 api key。", isError = true))
                    return Result.failure(IllegalStateException("Model group not found"))
                }

            val useCloudProxy = if (model.id == BuiltInModels.AIME_MODEL_ID) {
                true
            } else {
                modelPreferences.useCloudProxy.first()
            }
            val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL

            // 删除目标消息及其后的所有消息
            chatDao.deleteMessagesAfter(conversationId, target.timestamp)

            // 插入新的助手消息占位，用于流式输出
            val newAssistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在加载 ${model.name}...",
                isFromUser = false,
                timestamp = Date(),
                modelDisplayName = model.name
            )
            val newAssistantId = chatDao.insertMessage(newAssistantMessage)

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

            // Inject system prompt
            val systemPrompt = buildSystemPrompt()
            if (systemPrompt.isNotBlank()) {
                messagesWithBias.add(0, OpenAiChatMessage(role = "system", content = systemPrompt))
            }

            // 构建工具定义（当选择了工具时）
            val tools = getTools(selectedTool)

            val aggregated = StringBuilder()
            var lastUpdateTime = 0L
            val updateInterval = 0L
            var preLabelAdded = false
            var postLabelAdded = false
            var toolCallHandled = false // 标志位：确保每次生成只处理一个工具调用

            // 使用新插入的助手消息作为目标消息
            var assistantMessage = newAssistantMessage.copy(id = newAssistantId)
            assistantMessage = assistantMessage.copy(modelDisplayName = model.name)
            chatDao.updateMessage(assistantMessage)

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
                            if (toolCallHandled) return@streamWithFallback
                            toolCallHandled = true

                            // 处理工具调用：切换UI状态为调用中，并将已流出的首段内容包装为“第一次回复”
                            // 通知UI具体的工具类型以正确显示图标
                            when (toolCall.function?.name) {
                                "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                                "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
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
                                    val userTextForIntent = if (prevUserIndex != -1) history[prevUserIndex].content else ""
                                    val query = safeExtractQuery(arguments, userTextForIntent)
                                    
                                    if (query.isNotEmpty()) {
                                        val searchResultCount = toolPreferences.webSearchResultCount.first()
                                        val searchResponse = webSearchService.search(query, searchResultCount, useCloudProxy, proxyUrl)

                                        // 在工具调用回复区域渲染搜索结果（Markdown：标题可点击跳转）
                                        if (searchResponse.results.isNotEmpty()) {
                                            val linksMarkdown = searchResponse.results.mapIndexed { index, r ->
                                                "${index + 1}. [${r.title}](${r.url})"
                                            }.joinToString("\n")
                                            aggregated.append("\n\n\n")
                                            aggregated.append("<search>\n")
                                            aggregated.append(linksMarkdown)
                                            aggregated.append("\n</search>")
                                            aggregated.append("\n\n\n")
                                            postLabelAdded = true
                                            val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                            chatDao.updateMessage(updatedBeforeOfficial)
                                        }

                                        // 将搜索结果传递给AI进行总结
                                        val messagesWithSearch = contextMessages.toMutableList()
                                        val searchResultsText = if (searchResponse.results.isNotEmpty()) {
                                            val formatted = webSearchService.formatSearchResults(searchResponse)
                                            "$formatted\n\n请基于以上搜索结果回答用户问题。不要在末尾附加网址或参考链接。"
                                        } else {
                                            "搜索未找到相关结果，请基于你的知识回答用户的问题。不要在末尾附加网址或参考链接。"
                                        }
                                        messagesWithSearch.add(
                                            OpenAiChatMessage(
                                                role = "system",
                                                content = searchResultsText
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
                                    var lat: Double? = null
                                    var lon: Double? = null
                                    var cityArg: String? = null
                                    
                                    try {
                                        val gson = Gson()
                                        val type = object : TypeToken<Map<String, Any?>>() {}.type
                                        val map: Map<String, Any?> = gson.fromJson(arguments, type)
                                        lat = (map["latitude"] as? Number)?.toDouble()
                                        lon = (map["longitude"] as? Number)?.toDouble()
                                        cityArg = map["city"] as? String
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                    
                                    val weatherResult = if (lat != null && lon != null) {
                                        weatherService.query(lat, lon)
                                    } else {
                                        val userTextForIntent = if (prevUserIndex != -1) history[prevUserIndex].content else ""
                                        val finalCity = if (!cityArg.isNullOrBlank()) cityArg else safeExtractCity(arguments, userTextForIntent)
                                        if (finalCity.isNotEmpty()) weatherService.query(finalCity) else null
                                    }
                                    
                                    if (weatherResult != null) {
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
                            }
                            onToolCallEnd?.invoke()
                        }
                    )
                } catch (e: Exception) {
                    val errorMessage = getErrorMessage(e)
                    val finalContent = if (aggregated.isNotEmpty()) {
                        aggregated.toString() + "\n\n" + errorMessage
                    } else {
                        errorMessage
                    }
                    val errorUpdated = assistantMessage.copy(
                        content = finalContent,
                        isError = true,
                        errorDetails = e.message
                    )
                    chatDao.updateMessage(errorUpdated)
                    throw e
                }
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
                     val model = if (targetModelId == BuiltInModels.AIME_MODEL_ID) {
                         BuiltInModels.aimeModel
                     } else {
                         modelConfigRepository.getModelById(targetModelId)
                     }
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
                
                val model = if (targetModelId == BuiltInModels.AIME_MODEL_ID) {
                    BuiltInModels.aimeModel
                } else {
                    modelConfigRepository.getModelById(targetModelId)
                }
                if (model == null) {
                    onTitleGenerated("新对话")
                    return@withContext
                }
                
                val group = if (targetModelId == BuiltInModels.AIME_MODEL_ID) {
                    BuiltInModels.aimeGroup
                } else {
                    modelConfigRepository.getGroupById(model.groupId)
                }
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
        aspectRatio: String = "1:1",
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null
    ): Result<Unit> {
        return try {
            val history = chatDao.getMessagesForConversation(conversationId).first()
            val targetIndex = history.indexOfFirst { it.id == failedMessageId }
            if (targetIndex == -1) return Result.failure(IllegalArgumentException("Message not found"))
            val target = history[targetIndex]
            if (target.isFromUser) return Result.failure(IllegalArgumentException("Cannot retry user message"))
            // We can retry if it's an error OR if it's a placeholder that didn't finish (like "正在生成图片...")
            // if (!target.isError) return Result.failure(IllegalArgumentException("Message is not an error"))

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
                    return Result.failure(IllegalStateException("No user messages in conversation"))
                }
            }

            val userMessage = history[prevUserIndex].content

            // Client-side handling for Image Generation in Retry
            if (selectedTool?.type == ToolType.IMAGE_GENERATION) {
                handleImageGeneration(
                    conversationId = conversationId,
                    prompt = userMessage,
                    selectedTool = selectedTool,
                    aspectRatio = aspectRatio,
                    onToolCallStart = onToolCallStart,
                    onToolCallEnd = onToolCallEnd,
                    existingAssistantMessageId = failedMessageId
                )
                return Result.success(Unit)
            }

            // 删除失败的消息及其后的所有消息
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
            val model = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeModel
            } else {
                modelConfigRepository.getModelById(selectedModelId)
            } ?: run {
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
            val group = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeGroup
            } else {
                modelConfigRepository.getGroupById(model.groupId)
            } ?: run {
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
                .map { toOpenAiMessage(it) }
            val contextMessages = limitContext(contextMessagesBase)
            val messagesWithBias = contextMessages.toMutableList()
            
            val systemPrompt = buildSystemPrompt()
            if (systemPrompt.isNotBlank()) {
                messagesWithBias.add(0, OpenAiChatMessage(role = "system", content = systemPrompt))
            }

            // 构建工具定义
            val tools = getTools(selectedTool)

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
                    val errorMessage = getErrorMessage(e)
                    val finalContent = if (aggregated.isNotEmpty()) {
                        aggregated.toString() + "\n\n" + errorMessage
                    } else {
                        errorMessage
                    }
                    val errorUpdated = assistantMessage.copy(
                        content = finalContent,
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
        aspectRatio: String = "1:1",
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

            // 删除其后的所有消息
            chatDao.deleteMessagesAfterExclusive(conversationId, target.timestamp)

            // Client-side handling for Image Generation in Edit
            if (selectedTool?.type == ToolType.IMAGE_GENERATION) {
                handleImageGeneration(
                    conversationId = conversationId,
                    prompt = trimmed,
                    selectedTool = selectedTool,
                    aspectRatio = aspectRatio,
                    onToolCallStart = onToolCallStart,
                    onToolCallEnd = onToolCallEnd
                )
                return Result.success(Unit)
            }

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
            val model = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeModel
            } else {
                modelConfigRepository.getModelById(selectedModelId)
            } ?: run {
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
            val group = if (selectedModelId == BuiltInModels.AIME_MODEL_ID) {
                BuiltInModels.aimeGroup
            } else {
                modelConfigRepository.getGroupById(model.groupId)
            } ?: run {
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

            val useCloudProxy = if (model.id == BuiltInModels.AIME_MODEL_ID) {
                true
            } else {
                modelPreferences.useCloudProxy.first()
            }
            val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL

            // 构造到该用户消息为止的上下文（包含编辑后的用户消息）并应用限制
            val contextMessagesBase = history.take(targetIndex) // 不含旧用户消息
                .filter { !it.isError }
                .map { toOpenAiMessage(it) }
                .toMutableList()
            contextMessagesBase.add(OpenAiChatMessage(role = "user", content = trimmed))
            val contextMessages = limitContext(contextMessagesBase)
            val messagesWithBias = contextMessages.toMutableList()
            
            val systemPrompt = buildSystemPrompt()
            if (systemPrompt.isNotBlank()) {
                messagesWithBias.add(0, OpenAiChatMessage(role = "system", content = systemPrompt))
            }

            var preLabelAdded = false
            var postLabelAdded = false

            // 插入新的助手消息占位以进行流式写入
            var assistantMessage = ChatMessage(
                conversationId = conversationId,
                content = "正在加载 ${model.name}...",
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
            val tools = getTools(selectedTool)

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
                        // 处理工具调用：切换UI状态为调用中，并将已流出的首段内容包装为“第一次回复”
                        // 通知UI具体的工具类型以正确显示图标
                        when (toolCall.function?.name) {
                            "web_search" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEB_SEARCH)
                                "city_weather" -> onToolCallStart?.invoke(com.glassous.aime.data.model.ToolType.WEATHER_QUERY)
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
                                    val searchResultCount = toolPreferences.webSearchResultCount.first()
                                    val searchEngine = toolPreferences.webSearchEngine.first()
                                    val tavilyKey = toolPreferences.tavilyApiKey.first()
                                    val tavilyUseProxy = toolPreferences.tavilyUseProxy.first()
                                    
                                    val searchResponse = webSearchService.search(
                                        query = query,
                                        maxResults = searchResultCount,
                                        useCloudProxy = useCloudProxy,
                                        proxyUrl = proxyUrl,
                                        onProgress = { progress ->
                                            val progressContent = aggregated.toString() + "\n\n<search>\n" + progress + "\n</search>"
                                            val progressMessage = assistantMessage.copy(content = progressContent)
                                            chatDao.updateMessage(progressMessage)
                                        },
                                        engine = searchEngine,
                                        apiKey = tavilyKey,
                                        tavilyUseProxy = tavilyUseProxy
                                    )

                                    // 在工具调用回复区域渲染搜索结果（Markdown：标题可点击跳转）
                                    if (searchResponse.results.isNotEmpty()) {
                                        val linksMarkdown = searchResponse.results.mapIndexed { index, r ->
                                            val base = "${index + 1}. [${r.title}](${r.url})"
                                            if (r.image != null) "$base ![image](${r.image})" else base
                                        }.joinToString("\n")
                                        aggregated.append("\n\n\n")
                                        aggregated.append("<search>\n")
                                        aggregated.append(linksMarkdown)
                                        aggregated.append("\n</search>")
                                        aggregated.append("\n\n\n")
                                        postLabelAdded = true
                                        val updatedBeforeOfficial = assistantMessage.copy(content = aggregated.toString())
                                        chatDao.updateMessage(updatedBeforeOfficial)
                                    }

                                    // 构建包含搜索结果的系统消息
                                    val searchResultsText = if (searchResponse.results.isNotEmpty()) {
                                        val formatted = webSearchService.formatSearchResults(searchResponse)
                                        "$formatted\n\n请基于以上搜索结果回答用户问题。不要在末尾附加网址或参考链接。"
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
                                    var lat: Double? = null
                                    var lon: Double? = null
                                    var cityArg: String? = null
                                    
                                    try {
                                        val gson = Gson()
                                        val type = object : TypeToken<Map<String, Any?>>() {}.type
                                        val map: Map<String, Any?> = gson.fromJson(arguments, type)
                                        lat = (map["latitude"] as? Number)?.toDouble()
                                        lon = (map["longitude"] as? Number)?.toDouble()
                                        cityArg = map["city"] as? String
                                    } catch (e: Exception) {
                                        // ignore
                                    }

                                    val weatherResult = if (lat != null && lon != null) {
                                        weatherService.query(lat, lon)
                                    } else {
                                        val finalCity = if (!cityArg.isNullOrBlank()) cityArg else safeExtractCity(arguments, "")
                                        weatherService.query(finalCity)
                                    }

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
                        }
                        onToolCallEnd?.invoke()
                    }
                )
                } catch (e: Exception) {
                    val errorMessage = getErrorMessage(e)
                    val finalContent = if (aggregated.isNotEmpty()) {
                        aggregated.toString() + "\n\n" + errorMessage
                    } else {
                        errorMessage
                    }
                    val errorUpdated = assistantMessage.copy(
                        content = finalContent,
                        isError = true,
                        errorDetails = e.message
                    )
                    chatDao.updateMessage(errorUpdated)
                    throw e
                }
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

        val useCloudProxy = if (primaryModel.id == BuiltInModels.AIME_MODEL_ID) {
            true
        } else {
            modelPreferences.useCloudProxy.first()
        }
        val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL
        val supabaseAnonKey = com.glassous.aime.BuildConfig.SUPABASE_KEY

        // 直接调用OpenAI服务，失败时抛出异常
        return openAiService.streamChatCompletions(
            baseUrl = primaryGroup.baseUrl,
            apiKey = primaryGroup.apiKey,
            model = primaryModel.modelName,
            messages = messages,
            tools = tools,
            toolChoice = toolChoice,
            useCloudProxy = useCloudProxy,
            proxyUrl = proxyUrl,
            supabaseAnonKey = supabaseAnonKey,
            onDelta = onDelta,
            onToolCall = onToolCall
        )
    }

    /**
     * 过滤掉 <think> 标签及其内容
     */
    suspend fun handleImageGeneration(
        conversationId: Long,
        prompt: String,
        selectedTool: com.glassous.aime.data.model.Tool,
        aspectRatio: String,
        onToolCallStart: ((com.glassous.aime.data.model.ToolType) -> Unit)? = null,
        onToolCallEnd: (() -> Unit)? = null,
        existingAssistantMessageId: Long? = null
    ): Result<ChatMessage> {
        var assistantMsg: ChatMessage? = null
        try {
            val toolType = selectedTool.type
            onToolCallStart?.invoke(toolType)
            
            // 尝试从旧的 OPENAI 键和新的通用键中获取配置，优先使用非空的
            val baseUrl1 = toolPreferences.openaiImageGenBaseUrl.first()
            val apiKey1 = toolPreferences.openaiImageGenApiKey.first()
            val model1 = toolPreferences.openaiImageGenModel.first()
            val modelName1 = toolPreferences.openaiImageGenModelName.first()

            val baseUrl2 = toolPreferences.imageGenBaseUrl.first()
            val apiKey2 = toolPreferences.imageGenApiKey.first()
            val model2 = toolPreferences.imageGenModel.first()
            val modelName2 = toolPreferences.imageGenModelName.first()

            val baseUrl = baseUrl1.ifBlank { baseUrl2 }
            val apiKey = apiKey1.ifBlank { apiKey2 }
            val prefModel = model1.ifBlank { model2 }
            val prefModelName = modelName1.ifBlank { modelName2 }
            val modelName = if (prefModel.isNotBlank()) prefModel else if (prefModelName.isNotBlank()) prefModelName else "image-model"

            if (baseUrl.isBlank()) {
                throw IllegalStateException("请在工具配置中设置 Endpoint URL")
            }

            if (apiKey.isBlank()) {
                throw IllegalStateException("请在工具配置中设置 API Key")
            }

            // Get or Insert assistant placeholder
            assistantMsg = if (existingAssistantMessageId != null) {
                chatDao.getMessageById(existingAssistantMessageId)
            } else {
                val newMsg = ChatMessage(
                    conversationId = conversationId,
                    content = "正在生成图片...",
                    isFromUser = false,
                    timestamp = Date(),
                    modelDisplayName = modelName,
                    metadata = "aspect_ratio:$aspectRatio"
                )
                val id = chatDao.insertMessage(newMsg)
                newMsg.copy(id = id)
            }

            // Update existing message if needed
            if (assistantMsg != null && assistantMsg.id != 0L) {
                assistantMsg = assistantMsg.copy(
                    content = "正在生成图片...",
                    isError = false,
                    metadata = "aspect_ratio:$aspectRatio",
                    modelDisplayName = modelName
                )
                chatDao.updateMessage(assistantMsg)
            } else if (assistantMsg == null) {
                 // Fallback if existing message not found
                 val newMsg = ChatMessage(
                    conversationId = conversationId,
                    content = "正在生成图片...",
                    isFromUser = false,
                    timestamp = Date(),
                    modelDisplayName = modelName,
                    metadata = "aspect_ratio:$aspectRatio"
                )
                val id = chatDao.insertMessage(newMsg)
                assistantMsg = newMsg.copy(id = id)
            }

            // Map aspect ratio to size
            val size = when (aspectRatio) {
                "1:1" -> "1024x1024"
                "16:9" -> "1792x1024"
                "9:16" -> "1024x1792"
                "4:3" -> "1024x1024" 
                "3:4" -> "1024x1024"
                else -> "1024x1024"
            }

            val useCloudProxy = modelPreferences.useCloudProxy.first()
            val proxyUrl = com.glassous.aime.BuildConfig.ALIYUN_FC_PROXY_URL

            val images = openAiService.generateImage(
                baseUrl = baseUrl,
                apiKey = apiKey,
                prompt = prompt,
                model = modelName,
                size = size,
                useCloudProxy = useCloudProxy,
                proxyUrl = proxyUrl
            )

            if (images.isNotEmpty()) {
                val localPaths = images.mapNotNull { imageData ->
                    imageData.url?.let { downloadAndSaveImage(it) } ?: imageData.url
                }
                
                if (localPaths.isNotEmpty()) {
                    val finalMsg = assistantMsg!!.copy(
                        content = images[0].revisedPrompt ?: "",
                        imagePaths = localPaths,
                        isError = false
                    )
                    chatDao.updateMessage(finalMsg)
                    return Result.success(finalMsg)
                }
            }
            throw IOException("未获取到生成的图片链接")
        } catch (e: Exception) {
            val errorDetails = e.toString()
            val errorMessage = "图片生成失败: ${e.message ?: "未知错误"}\n\n详细信息: $errorDetails"
            
            val finalErrorMsg = (assistantMsg ?: ChatMessage(
                conversationId = conversationId,
                content = errorMessage,
                isFromUser = false,
                timestamp = Date(),
                isError = true,
                errorDetails = errorDetails
            )).copy(
                content = errorMessage,
                isError = true,
                errorDetails = errorDetails
            )
            
            if (finalErrorMsg.id != 0L) {
                chatDao.updateMessage(finalErrorMsg)
            } else {
                chatDao.insertMessage(finalErrorMsg)
            }
            
            return Result.failure(e)
        } finally {
            onToolCallEnd?.invoke()
        }
    }

    private fun filterThinkTags(text: String): String {
        // 使用正则表达式移除 <think>...</think> 标签及其内容
        return text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "") // 处理未闭合的标签
    }

    private fun safeExtractKeyword(arguments: String?, default: String = ""): String {
        if (arguments.isNullOrBlank()) return default
        val raw = arguments.trim()
        val gson = Gson()
        fun tryParse(text: String): String? {
            return try {
                val reader = JsonReader(StringReader(text))
                reader.isLenient = true
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(reader, type)
                (map["keyword"] as? String)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }
        tryParse(raw)?.let { return it }
        val normalizedSingleQuotes = if (raw.startsWith("{") && raw.contains("'")) raw.replace("'", "\"") else raw
        tryParse(normalizedSingleQuotes)?.let { return it }
        val regexQuoted = Regex("""(?i)\"?keyword\"?\s*[:=]\s*\"([^\"\n\r}]*)\"""")
        val regexUnquoted = Regex("""(?i)\"?keyword\"?\s*[:=]\s*([^,}\n\r]+)""")
        regexQuoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        regexUnquoted.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return raw
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? {
        return withContext(Dispatchers.Main) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = locationManager.getProviders(true)
                var bestLocation: Location? = null
                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                }
                bestLocation
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getErrorMessage(e: Exception): String {
        return when {
            e.message?.contains("HTTP 401") == true -> "认证失败：API Key 无效或已过期"
            e.message?.contains("HTTP 403") == true -> "访问被拒绝：没有权限访问该模型"
            e.message?.contains("HTTP 429") == true -> "请求过于频繁：已达到速率限制"
            e.message?.contains("HTTP 500") == true || e.message?.contains("HTTP 502") == true || 
            e.message?.contains("HTTP 503") == true -> "服务器错误：模型服务暂时不可用"
            e.message?.contains("timeout") == true -> "请求超时：网络连接超时"
            e.message?.contains("Connection") == true -> "网络错误：无法连接到服务器"
            else -> "生成失败：${e.message ?: "未知错误"}"
        }
    }

    private fun toOpenAiMessage(message: ChatMessage): OpenAiChatMessage {
        val content: Any = if (message.imagePaths.isNotEmpty()) {
            val parts = mutableListOf<OpenAiContentPart>()
            if (message.content.isNotBlank()) {
                parts.add(OpenAiContentPart(type = "text", text = message.content))
            }
            message.imagePaths.forEach { path ->
                val lowerPath = path.lowercase()
                if (lowerPath.endsWith(".mp4") || lowerPath.endsWith(".mov") || lowerPath.endsWith(".webm")) {
                    val file = java.io.File(path)
                    if (file.exists() && file.length() > 50 * 1024 * 1024) {
                        parts.add(OpenAiContentPart(
                            type = "text",
                            text = "\n[Video too large (>50MB): ${file.name}]"
                        ))
                    } else {
                        val base64 = encodeFileToBase64(path)
                        if (base64 != null) {
                            val mimeType = when {
                                lowerPath.endsWith(".mp4") -> "video/mp4"
                                lowerPath.endsWith(".mov") -> "video/quicktime"
                                lowerPath.endsWith(".webm") -> "video/webm"
                                else -> "video/mp4"
                            }
                            parts.add(OpenAiContentPart(
                                type = "video_url",
                                videoUrl = OpenAiVideoUrl(url = "data:$mimeType;base64,$base64")
                            ))
                        }
                    }
                } else if (lowerPath.endsWith(".m4a") || lowerPath.endsWith(".mp3") || lowerPath.endsWith(".wav")) {
                    val file = java.io.File(path)
                    if (file.exists() && file.length() > 50 * 1024 * 1024) {
                        parts.add(OpenAiContentPart(
                            type = "text",
                            text = "\n[Audio too large (>50MB): ${file.name}]"
                        ))
                    } else {
                        val base64 = encodeFileToBase64(path)
                        if (base64 != null) {
                            val format = when {
                                lowerPath.endsWith(".mp3") -> "mp3"
                                lowerPath.endsWith(".wav") -> "wav"
                                else -> "wav" // Default fallback
                            }
                            parts.add(OpenAiContentPart(
                                type = "input_audio",
                                inputAudio = OpenAiInputAudio(
                                    data = base64,
                                    format = format
                                )
                            ))
                        }
                    }
                } else {
                    val base64 = if (lowerPath.endsWith(".gif")) {
                        encodeFileToBase64(path)
                    } else {
                        encodeImageToBase64(path)
                    }
                    if (base64 != null) {
                        val mimeType = when {
                            lowerPath.endsWith(".png") -> "image/png"
                            lowerPath.endsWith(".webp") -> "image/webp"
                            lowerPath.endsWith(".gif") -> "image/gif"
                            else -> "image/jpeg"
                        }
                        parts.add(OpenAiContentPart(
                            type = "image_url",
                            imageUrl = OpenAiImageUrl(url = "data:$mimeType;base64,$base64")
                        ))
                    }
                }
            }
            if (parts.isEmpty()) message.content else parts
        } else {
            message.content
        }
        return OpenAiChatMessage(
            role = if (message.isFromUser) "user" else "assistant",
            content = content
        )
    }

    private fun encodeFileToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return null
            
            // Resize if too large (e.g. max 1024px)
            val maxDim = 1024
            val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                if (ratio > 1) {
                    maxDim.toFloat() / bitmap.width
                } else {
                    maxDim.toFloat() / bitmap.height
                }
            } else {
                1f
            }
            
            val finalBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            val format = when {
                path.lowercase().endsWith(".png") -> android.graphics.Bitmap.CompressFormat.PNG
                path.lowercase().endsWith(".webp") -> if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS else android.graphics.Bitmap.CompressFormat.WEBP
                else -> android.graphics.Bitmap.CompressFormat.JPEG
            }
            finalBitmap.compress(format, 80, outputStream)
            val bytes = outputStream.toByteArray()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun downloadAndSaveImage(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                
                val imagesDir = java.io.File(context.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                
                val fileName = "gen_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.jpg"
                val file = java.io.File(imagesDir, fileName)
                
                val source = response.body?.source() ?: return@withContext null
                val sink = file.sink().buffer()
                sink.writeAll(source)
                sink.close()
                response.close()
                
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
