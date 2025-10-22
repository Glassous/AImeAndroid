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

// Request body for chat.completions
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = true
)

// Streaming chunk models (delta)
data class ChatCompletionsChunkChoiceDelta(
    @SerializedName("content") val content: String?,
    @SerializedName("role") val role: String? = null
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
        onDelta: suspend (String) -> Unit
    ): String {
        val gson = Gson()
        val normalized = baseUrl.trimEnd('/')
        val withV1 = if ("/v1" in normalized) normalized else normalized + "/v1"
        val endpoint = withV1 + "/chat/completions"
        val json = gson.toJson(ChatCompletionsRequest(model = model, messages = messages, stream = true))
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
                        val delta = chunk?.choices?.firstOrNull()?.delta?.content
                        if (delta != null) {
                            finalText.append(delta)
                            onDelta(delta)
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