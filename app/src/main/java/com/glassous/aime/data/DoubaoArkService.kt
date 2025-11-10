package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException

// 兼容 OpenAI 协议的豆包（火山引擎 Ark）聊天服务
class DoubaoArkService(
    private val client: OkHttpClient = OkHttpClient()
) {
    private data class AccToolCall(
        var id: String? = null,
        var type: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )

    // 复用 OpenAiService 中的模型定义（项目内同包可直接引用）
    data class ArkChatCompletionsRequest(
        val model: String,
        val messages: List<OpenAiChatMessage>,
        val stream: Boolean = true,
        val tools: List<Tool>? = null,
        @SerializedName("tool_choice") val toolChoice: String? = null
    )

    data class ArkError(
        val message: String?,
        val type: String?,
        val code: String?
    )

    data class ArkErrorResponse(
        val error: ArkError?
    )

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
        // Ark v3 兼容 OpenAI chat.completions 接口，路径固定为 /chat/completions
        val endpoint = "$normalized/chat/completions"
        val json = gson.toJson(
            ArkChatCompletionsRequest(
                model = model,
                messages = messages,
                stream = true,
                tools = tools,
                toolChoice = toolChoice
            )
        )
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
            // 解析错误信息（Ark 与 OpenAI 错误格式基本兼容）
            val detailed = try {
                val parsed = gson.fromJson(raw, ArkErrorResponse::class.java)
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
        val accumulatedToolCalls = mutableMapOf<Int, AccToolCall>()
        var hasEmittedToolCalls = false
        var accFunctionCall: AccToolCall? = null
        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (line.startsWith(":")) continue // SSE 注释/心跳
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
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
                        val chunk = Gson().fromJson(payload, ChatCompletionsChunk::class.java)
                        val choices = chunk?.choices ?: emptyList()
                        choices.forEach { choice ->
                            val delta = choice.delta
                            val content = delta?.content
                            if (content != null) {
                                finalText.append(content)
                                onDelta(content)
                            }
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
                            val fn = delta?.functionCall
                            if (fn != null && (fn.name != null || fn.arguments != null)) {
                                val acc = accFunctionCall ?: AccToolCall().also { accFunctionCall = it }
                                if (fn.name != null) acc.name = fn.name
                                if (fn.arguments != null) acc.arguments.append(fn.arguments)
                            }
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
                        // 忽略单个分片解析错误以保证流式鲁棒性
                    }
                }
            }
        } finally {
            response.close()
        }
        return finalText.toString()
    }
}