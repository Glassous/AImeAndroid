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
    val content: String
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
    @SerializedName("role") val role: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("function_call") val functionCall: FunctionCall? = null
)

// Tool call definition for streaming responses
data class ToolCall(
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
    // Stream chat completion tokens; returns final aggregated text
    suspend fun streamChatCompletions(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<OpenAiChatMessage>,
        tools: List<Tool>? = null,
        toolChoice: String? = null,
        onDelta: suspend (String) -> Unit,
        onToolCall: suspend (ToolCall) -> Unit = {}
    ): String {
        val gson = Gson()
        val normalized = baseUrl.trimEnd('/')
        val withV1 = if ("/v1" in normalized) normalized else normalized + "/v1"
        val endpoint = withV1 + "/chat/completions"
        val json = gson.toJson(ChatCompletionsRequest(
            model = model, 
            messages = messages, 
            stream = true,
            tools = tools,
            toolChoice = toolChoice
        ))
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

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
        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                // Ignore keep-alive/comment lines per SSE spec
                if (line.startsWith(":")) continue
                // Only process data lines; ignore id/event/retry
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(payload, ChatCompletionsChunk::class.java)
                        val delta = chunk?.choices?.firstOrNull()?.delta
                        
                        // Handle content delta
                        val content = delta?.content
                        if (content != null) {
                            finalText.append(content)
                            onDelta(content)
                        }
                        
                        // Handle tool calls
                        val toolCalls = delta?.toolCalls
                        if (!toolCalls.isNullOrEmpty()) {
                            toolCalls.forEach { toolCall ->
                                onToolCall(toolCall)
                            }
                        }

                        // Handle function_call (compat for providers emitting `function_call`)
                        val fn = delta?.functionCall
                        if (fn != null && (fn.name != null || fn.arguments != null)) {
                            val synthetic = ToolCall(
                                id = null,
                                type = "function",
                                function = ToolCallFunction(name = fn.name, arguments = fn.arguments)
                            )
                            onToolCall(synthetic)
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