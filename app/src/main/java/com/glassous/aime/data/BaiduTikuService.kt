package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BaiduTikuApiResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: BaiduTikuData?
)

data class BaiduTikuData(
    @SerializedName("question") val question: String?,
    @SerializedName("options") val options: List<String>?,
    @SerializedName("answer") val answer: String?
)

data class BaiduTikuResult(
    val success: Boolean,
    val question: String,
    val options: List<String>,
    val answer: String,
    val message: String
)

class BaiduTikuService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun query(question: String): BaiduTikuResult = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(question.trim(), "UTF-8")
            val url = "https://api.pearktrue.cn/api/baidutiku/?question=$encoded"
            val req = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext BaiduTikuResult(false, question, emptyList(), "", "HTTP ${resp.code}")
            }
            val body = resp.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext BaiduTikuResult(false, question, emptyList(), "", "响应为空")
            }
            val api = gson.fromJson(body, BaiduTikuApiResponse::class.java)
            val ok = (api.code == 200)
            val q = api.data?.question?.ifBlank { question } ?: question
            val opts = api.data?.options?.filter { it.isNotBlank() } ?: emptyList()
            val ans = api.data?.answer?.orEmpty() ?: ""
            if (!ok) {
                return@withContext BaiduTikuResult(false, q, opts, ans, api.msg ?: "查询失败")
            }
            BaiduTikuResult(true, q, opts, ans, "查询成功")
        } catch (e: Exception) {
            BaiduTikuResult(false, question, emptyList(), "", "查询异常：${e.message}")
        }
    }

    fun formatAsMarkdown(result: BaiduTikuResult): String {
        if (!result.success) {
            return "题库查询失败：${result.message}。"
        }
        val sb = StringBuilder()
        sb.append("## 题库查询结果\n")
        sb.append("> 题目：${result.question}\n\n")
        val opts = result.options
        val type = when {
            opts.isNotEmpty() && opts.size in 2..8 -> "选择题"
            result.answer.replace(" ", "").contains("对") || result.answer.replace(" ", "").contains("错") -> "判断题"
            else -> "填空题"
        }
        sb.append("类型：$type\n\n")
        if (opts.isNotEmpty()) {
            sb.append("选项：\n")
            val labels = listOf("A","B","C","D","E","F","G","H")
            opts.forEachIndexed { i, t ->
                val label = labels.getOrNull(i) ?: (i + 1).toString()
                val isAns = normalizeAnswerLetter(result.answer) == label
                val text = if (isAns) "**$label. $t**" else "$label. $t"
                sb.append("- $text\n")
            }
            sb.append("\n")
        }
        val cleaned = result.answer.replace("[", "").replace("]", "").replace("答案", "").replace(":", "").trim()
        sb.append("答案：${cleaned.ifBlank { "暂无" }}\n")
        return sb.toString()
    }

    private fun normalizeAnswerLetter(answer: String): String {
        val m = Regex("(?i)\\b([A-H])\\b").find(answer)
        return m?.groupValues?.getOrNull(1)?.uppercase() ?: ""
    }
}