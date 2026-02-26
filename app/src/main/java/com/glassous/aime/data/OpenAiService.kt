package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException

// OpenAI Chat Completions standard message format
data class OpenAiChatMessage(
    val role: String,
    val content: Any // String or List<OpenAiContentPart>
)

data class OpenAiContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: OpenAiImageUrl? = null
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
    @SerializedName("tool_choice") val toolChoice: String? = null
)

// Streaming chunk models (delta)
data class ChatCompletionsChunkChoiceDelta(
    @SerializedName("content") val content: String?,
    @SerializedName("reasoning", alternate = ["reasoning_content"]) val reasoning: String?,
    @SerializedName("role") val role: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("function_call") val functionCall: FunctionCall? = null
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
        onToolCall: suspend (ToolCall) -> Unit = {}
    ): String {
        val gson = Gson()
        val json = gson.toJson(ChatCompletionsRequest(
            model = model, 
            messages = messages, 
            stream = true,
            tools = tools,
            toolChoice = toolChoice
        ))
        val body = json.toRequestBody("application/json".toMediaType())

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
}