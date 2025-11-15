package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.glassous.aime.data.model.Tool

/**
 * 自动工具选择请求/响应模型
 */
data class AutoToolCandidate(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("route") val route: String
)

data class AutoToolSelectionPayload(
    @SerializedName("tools") val tools: List<AutoToolCandidate>
)

data class AutoToolSelectionResult(
    @SerializedName("tool_name") val toolName: String,
    @SerializedName("route") val route: String?
)

/**
 * 自动工具选择服务：将工具列表序列化并发送给AI，返回选择结果
 */
class AutoToolSelector(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String,
    private val openAiService: OpenAiService = OpenAiService()
) {
    private val gson = Gson()
    private val doubaoService: DoubaoArkService = DoubaoArkService()

    suspend fun selectTool(tools: List<Tool>): AutoToolSelectionResult {
        // 序列化工具列表
        val candidates = tools.map {
            AutoToolCandidate(
                name = it.displayName,
                description = it.description,
                route = defaultRouteOf(it)
            )
        }
        val payload = AutoToolSelectionPayload(candidates)
        val jsonPayload = gson.toJson(payload)

        val messages = listOf(
            OpenAiChatMessage(
                role = "system",
                content = "你是一个工具选择器。给定工具列表(JSON)，你必须只输出一个有效JSON，格式为{\"tool_name\":\"工具名称\",\"route\":\"路由\"}，且只输出该JSON，不要解释。"
            ),
            OpenAiChatMessage(
                role = "user",
                content = "工具列表: $jsonPayload"
            )
        )

        // 使用流式接口获取最终文本
        val finalText = if (baseUrl.contains("volces", ignoreCase = true)) {
            doubaoService.streamChatCompletions(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = modelName,
                messages = messages,
                onDelta = { }
            )
        } else {
            openAiService.streamChatCompletions(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = modelName,
                messages = messages,
                onDelta = { }
            )
        }

        // 尝试解析JSON；失败时降级为第一个工具
        return try {
            // 简单提取最外层JSON（如果存在多余文本）
            val start = finalText.indexOf('{')
            val end = finalText.lastIndexOf('}')
            val json = if (start >= 0 && end > start) finalText.substring(start, end + 1) else finalText
            gson.fromJson(json, AutoToolSelectionResult::class.java)
        } catch (_: Exception) {
            AutoToolSelectionResult(
                toolName = tools.firstOrNull()?.displayName ?: "",
                route = defaultRouteOf(tools.firstOrNull())
            )
        }
    }

    private fun defaultRouteOf(tool: Tool?): String {
        // 当前工具默认路由均回到聊天页
        return "chat"
    }
}