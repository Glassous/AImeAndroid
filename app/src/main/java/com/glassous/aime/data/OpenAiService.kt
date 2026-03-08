package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// OpenAI Chat Completions standard message format
data class OpenAiChatMessage(
    val role: String,
    val content: Any // String or List<OpenAiContentPart>
)

data class OpenAiContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: OpenAiImageUrl? = null,
    @SerializedName("input_audio") val inputAudio: OpenAiInputAudio? = null,
    @SerializedName("video_url") val videoUrl: OpenAiVideoUrl? = null,
    val file: OpenAiFile? = null
)

data class OpenAiFile(
    val data: String,
    val filename: String
)

data class OpenAiVideoUrl(
    val url: String
)

data class OpenAiInputAudio(
    val data: String,
    val format: String
)

data class OpenAiImageUrl(
    val url: String,
    val detail: String? = "auto"
)

// Tool function parameter definition
data class ToolFunctionParameter(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

// Tool function parameters schema
data class ToolFunctionParameters(
    val type: String = "object",
    val properties: Map<String, ToolFunctionParameter>,
    val required: List<String>? = null
)

// Tool function definition
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolFunctionParameters
)

// Tool definition
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

// Request body for chat.completions
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = true,
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null,
    // OpenRouter image generation fields
    val modalities: List<String>? = null,
    @SerializedName("image_config") val imageConfig: ImageConfig? = null
)

data class ImageConfig(
    @SerializedName("aspect_ratio") val aspectRatio: String? = null,
    @SerializedName("image_size") val imageSize: String? = null
)

// Streaming chunk models (delta)
data class ChatCompletionsChunkChoiceDelta(
    @SerializedName("content") val content: String?,
    @SerializedName("reasoning", alternate = ["reasoning_content"]) val reasoning: String?,
    @SerializedName("role") val role: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("function_call") val functionCall: FunctionCall? = null,
    // OpenRouter image response field
    @SerializedName("images") val images: List<DeltaImage>? = null
)

data class DeltaImage(
    val type: String?,
    @SerializedName("image_url") val imageUrl: DeltaImageUrl?
)

data class DeltaImageUrl(
    val url: String?
)

// Tool call definition for streaming responses
data class ToolCall(
    @SerializedName("index") val index: Int? = null,
    val id: String?,
    val type: String?,
    val function: ToolCallFunction?
)

data class ToolCallFunction(
    val name: String?,
    val arguments: String?
)

// Some providers (e.g., Aliyun DashScope compatible) emit `function_call` instead of `tool_calls`
data class FunctionCall(
    val name: String?,
    val arguments: String?
)

data class ChatCompletionsChunkChoice(
    val index: Int? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
    val delta: ChatCompletionsChunkChoiceDelta?
)

data class ChatCompletionsChunk(
    val choices: List<ChatCompletionsChunkChoice>?
)

// Image Generation models
data class ImageGenerationRequest(
    val prompt: String? = null,
    val model: String? = null,
    val n: Int? = 1,
    val size: String? = "1024x1024",
    @SerializedName("response_format") val responseFormat: String? = "url",
    val messages: List<OpenAiChatMessage>? = null
)

data class ImageGenerationResponse(
    val created: Long?,
    val data: Any? // Can be List<ImageData> or a custom Map/Object
)

/**
 * 支持用户提供的非标准生图响应格式
 */
data class CustomImageGenerationResponse(
    val data: CustomImageData?
)

data class CustomImageData(
    @SerializedName("image_urls") val imageUrls: List<String>?
)

data class ImageData(
    val url: String? = null,
    @SerializedName("b64_json") val b64Json: String? = null,
    @SerializedName("revised_prompt") val revisedPrompt: String? = null
)

// Error response models (OpenAI-style)
data class OpenAiError(
    val message: String?,
    val type: String?,
    val param: String?,
    val code: String?
)

data class OpenAiErrorResponse(
    val error: OpenAiError?
)

class OpenAiService(
    private val client: OkHttpClient = OkHttpClient()
) {
    private data class AccToolCall(
        var id: String? = null,
        var type: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )
    // Stream chat completion tokens; returns final aggregated text
    suspend fun streamChatCompletions(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<OpenAiChatMessage>,
        tools: List<Tool>? = null,
        toolChoice: String? = null,
        useCloudProxy: Boolean = false,
        proxyUrl: String? = null,
        supabaseAnonKey: String? = null,
        onDelta: suspend (String) -> Unit,
        onToolCall: suspend (ToolCall) -> Unit = {},
        // Added for OpenRouter image generation
        modalities: List<String>? = null,
        imageConfig: ImageConfig? = null,
        onImageDelta: suspend (String) -> Unit = {}
    ): String {
        val gson = Gson()
        val requestPayload = ChatCompletionsRequest(
            model = model, 
            messages = messages, 
            stream = true,
            tools = tools,
            toolChoice = toolChoice,
            modalities = modalities,
            imageConfig = imageConfig
        )
        
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun writeTo(sink: okio.BufferedSink) {
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(sink.outputStream(), java.nio.charset.StandardCharsets.UTF_8))
                gson.toJson(requestPayload, writer)
                writer.flush()
            }
        }

        val normalized = baseUrl.trimEnd('/')
        val endpoint = if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            val withV1 = if ("/v1" in normalized || "/v1beta" in normalized) normalized else "$normalized/v1"
            "$withV1/chat/completions"
        }

        val requestBuilder = Request.Builder()
            .post(body)
            .addHeader("Accept", "text/event-stream")

        if (useCloudProxy && !proxyUrl.isNullOrBlank()) {
            requestBuilder.url(proxyUrl)
                .addHeader("x-target-url", endpoint)
                .addHeader("x-target-api-key", apiKey)
            // ⚠️ 绝对不要在这里添加 Authorization 头！
        } else {
            requestBuilder.url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val raw = response.body?.string()
            response.close()

            // Try to parse OpenAI-style error body
            val detailed = try {
                val parsed = gson.fromJson(raw, OpenAiErrorResponse::class.java)
                val e = parsed?.error
                listOfNotNull(e?.message, e?.code, e?.type)
                    .filter { it.isNotBlank() }
                    .joinToString(" | ")
                    .ifEmpty { raw ?: "" }
            } catch (_: Exception) {
                raw ?: ""
            }
            val baseMsg = "HTTP ${response.code}: ${response.message}"
            val finalMsg = if (detailed.isNotBlank()) "$baseMsg - $detailed" else baseMsg
            throw IOException(finalMsg)
        }

        val respBody = response.body ?: run {
            response.close()
            throw IOException("Empty response body")
        }
        val source: BufferedSource = respBody.source()
        val finalText = StringBuilder()
        // Accumulate tool calls across deltas to ensure complete arguments
        val accumulatedToolCalls = mutableMapOf<Int, AccToolCall>()
        var hasEmittedToolCalls = false
        var accFunctionCall: AccToolCall? = null
        var isThinking = false

        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                // Ignore keep-alive/comment lines per SSE spec
                if (line.startsWith(":")) continue
                // Only process data lines; ignore id/event/retry
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
                        // Close thinking tag if still open
                        if (isThinking) {
                            val endTag = "</think>"
                            finalText.append(endTag)
                            onDelta(endTag)
                            isThinking = false
                        }

                        // Flush accumulated tool calls at end if not emitted
                        if (!hasEmittedToolCalls) {
                            accumulatedToolCalls.forEach { (_, acc) ->
                                val tc = ToolCall(
                                    id = acc.id,
                                    type = acc.type,
                                    function = ToolCallFunction(
                                        name = acc.name,
                                        arguments = acc.arguments.toString()
                                    )
                                )
                                onToolCall(tc)
                            }
                            if (accumulatedToolCalls.isEmpty()) {
                                accFunctionCall?.let { acc ->
                                    val tc = ToolCall(
                                        id = null,
                                        type = "function",
                                        function = ToolCallFunction(
                                            name = acc.name,
                                            arguments = acc.arguments.toString()
                                        )
                                    )
                                    onToolCall(tc)
                                }
                            }
                            hasEmittedToolCalls = true
                        }
                        break
                    }
                    try {
                        val chunk = gson.fromJson(payload, ChatCompletionsChunk::class.java)
                        val choices = chunk?.choices ?: emptyList()
                        choices.forEach { choice ->
                            val delta = choice.delta
                            
                            // Handle reasoning delta
                            val reasoning = delta?.reasoning
                            if (!reasoning.isNullOrEmpty()) {
                                if (!isThinking) {
                                    val startTag = "<think>"
                                    finalText.append(startTag)
                                    onDelta(startTag)
                                    isThinking = true
                                }
                                finalText.append(reasoning)
                                onDelta(reasoning)
                            }

                            // Handle content delta
                            val content = delta?.content
                            if (!content.isNullOrEmpty()) {
                                if (isThinking) {
                                    val endTag = "</think>"
                                    finalText.append(endTag)
                                    onDelta(endTag)
                                    isThinking = false
                                }
                                finalText.append(content)
                                onDelta(content)
                            }
                            
                            // Handle image delta (OpenRouter)
                            val images = delta?.images
                            if (!images.isNullOrEmpty()) {
                                images.forEach { img ->
                                    val url = img.imageUrl?.url
                                    if (!url.isNullOrEmpty()) {
                                        onImageDelta(url)
                                    }
                                }
                            }
                            
                            // Accumulate tool_calls deltas
                            val toolCalls = delta?.toolCalls
                            if (!toolCalls.isNullOrEmpty()) {
                                toolCalls.forEach { toolCall ->
                                    val idx = toolCall.index ?: 0
                                    val acc = accumulatedToolCalls.getOrPut(idx) { AccToolCall() }
                                    if (toolCall.id != null) acc.id = toolCall.id
                                    if (toolCall.type != null) acc.type = toolCall.type
                                    val name = toolCall.function?.name
                                    if (name != null) acc.name = name
                                    val argsChunk = toolCall.function?.arguments
                                    if (argsChunk != null) acc.arguments.append(argsChunk)
                                }
                            }
                            // Accumulate function_call deltas (compat)
                            val fn = delta?.functionCall
                            if (fn != null && (fn.name != null || fn.arguments != null)) {
                                val acc = accFunctionCall ?: AccToolCall().also { accFunctionCall = it }
                                if (fn.name != null) acc.name = fn.name
                                if (fn.arguments != null) acc.arguments.append(fn.arguments)
                            }
                            // Flush when finish_reason indicates tool_calls
                            val finish = choice.finishReason
                            if (finish == "tool_calls" && !hasEmittedToolCalls) {
                                accumulatedToolCalls.forEach { (_, acc) ->
                                    val tc = ToolCall(
                                        id = acc.id,
                                        type = acc.type,
                                        function = ToolCallFunction(
                                            name = acc.name,
                                            arguments = acc.arguments.toString()
                                        )
                                    )
                                    onToolCall(tc)
                                }
                                if (accumulatedToolCalls.isEmpty()) {
                                    accFunctionCall?.let { acc ->
                                        val tc = ToolCall(
                                            id = null,
                                            type = "function",
                                            function = ToolCallFunction(
                                                name = acc.name,
                                                arguments = acc.arguments.toString()
                                            )
                                        )
                                        onToolCall(tc)
                                    }
                                }
                                hasEmittedToolCalls = true
                            }
                        }
                    } catch (_: Exception) {
                        // ignore per-chunk parse errors to keep stream resilient
                    }
                }
            }
        } finally {
            response.close()
        }
        return finalText.toString()
    }

    suspend fun generateImage(
        baseUrl: String,
        apiKey: String,
        prompt: String,
        model: String? = null,
        size: String? = "1024x1024",
        messages: List<OpenAiChatMessage>? = null,
        useCloudProxy: Boolean = false,
        proxyUrl: String? = null
    ): List<ImageData> {
        val gson = Gson()
        val endpoint = baseUrl.trim()

        val requestBuilder = Request.Builder()

        if (useCloudProxy && !proxyUrl.isNullOrBlank()) {
            requestBuilder.url(proxyUrl)
                .addHeader("x-target-url", endpoint)
                .addHeader("x-target-api-key", apiKey)
        } else {
            requestBuilder.url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
        }

        // 使用更长的超时时间（生图通常较慢）
        val imageClient = client.newBuilder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // 策略1：优先尝试标准模式（只发送 prompt，不发送 messages）
        val json1 = gson.toJson(ImageGenerationRequest(
            prompt = prompt,
            model = model,
            size = size,
            messages = null // 显式置空
        ))
        val body1 = json1.toRequestBody("application/json".toMediaType())
        val request1 = requestBuilder.post(body1).build()

        var response = withContext(Dispatchers.IO) { imageClient.newCall(request1).execute() }
        var respBody = response.body?.string() ?: ""

        // 如果标准模式失败，且提供了 messages，且错误提示可能需要 messages，则尝试 URL 模式
        if (!response.isSuccessful && messages != null && response.code == 400) {
            val errorMsg = respBody.lowercase()
            // 如果错误信息包含 "messages" 相关的提示，或者是 Sourceful V2 的特征错误，则重试
            if (errorMsg.contains("messages") || errorMsg.contains("sourceful")) {
                response.close()
                
                // 策略2：尝试 URL 模式（只发送 messages，不发送 prompt）
                // 注意：prompt 必须设为 null 以避免 "Cannot have both" 错误
                val json2 = gson.toJson(ImageGenerationRequest(
                    prompt = null,
                    model = model,
                    size = size,
                    messages = messages
                ))
                val body2 = json2.toRequestBody("application/json".toMediaType())
                val request2 = requestBuilder.post(body2).build()
                
                response = withContext(Dispatchers.IO) { imageClient.newCall(request2).execute() }
                respBody = response.body?.string() ?: ""
            }
        }

        if (!response.isSuccessful) {
            response.close()
            throw IOException("Image generation failed: ${response.code} ${response.message} - $respBody")
        }

        response.close()

        // 1. 尝试解析 OpenAI 标准格式
        try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            val rawMap: Map<String, Any?> = gson.fromJson(respBody, type)
            
            // 如果 data 是个列表，按标准格式解析
            if (rawMap["data"] is List<*>) {
                val parsed = gson.fromJson(respBody, ImageGenerationResponse::class.java)
                val dataList = parsed?.data
                if (dataList is List<*>) {
                    // 转回 ImageData 列表
                    val list = dataList.mapNotNull { 
                        val item = it as? Map<*, *>
                        val url = item?.get("url") as? String
                        // 兼容某些服务商可能直接在 data 列表里放字符串 URL
                        val urlStr = if (url == null && it is String) it else url
                        
                        if (urlStr != null) {
                            ImageData(
                                url = urlStr,
                                revisedPrompt = item?.get("revised_prompt") as? String
                            )
                        } else null
                    }
                    if (list.isNotEmpty()) return list
                }
            }
            
            // 2. 尝试解析用户提供的自定义格式 (data.image_urls)
            val customParsed = gson.fromJson(respBody, CustomImageGenerationResponse::class.java)
            val urls = customParsed?.data?.imageUrls
            if (!urls.isNullOrEmpty()) {
                return urls.map { ImageData(url = it) }
            }
            
            // 3. 兼容 SiliconFlow 等直接返回 { "images": [ { "url": "..." } ] } 的情况
            if (rawMap["images"] is List<*>) {
                val images = rawMap["images"] as List<*>
                val list = images.mapNotNull {
                    val item = it as? Map<*, *>
                    val url = item?.get("url") as? String
                    if (url != null) ImageData(url = url) else null
                }
                if (list.isNotEmpty()) return list
            }
            
            // 4. 兼容直接返回 { "data": [ "url1", "url2" ] } 的情况 (字符串数组)
            if (rawMap["data"] is List<*>) {
                val dataList = rawMap["data"] as List<*>
                if (dataList.isNotEmpty() && dataList[0] is String) {
                    return dataList.map { ImageData(url = it as String) }
                }
            }

            // 如果都解析失败，抛出包含原始响应的异常以便调试
            throw IOException("Unknown image response format: $respBody")
            
        } catch (e: Exception) {
            // 解析失败，记录原始数据以便排查
            throw IOException("Failed to parse image response: ${e.message}. Raw body: $respBody")
        }
    }
}