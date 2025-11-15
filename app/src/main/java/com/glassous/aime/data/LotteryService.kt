package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LotteryItem(
    @SerializedName("issue") val issue: String?,
    @SerializedName("opentime") val opentime: String?,
    @SerializedName("salemoney") val salemoney: String?,
    @SerializedName("drawnumber") val drawnumber: String?,
    @SerializedName("trailnumber") val trailnumber: String?
)

data class LotteryApiResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("count") val count: Int?,
    @SerializedName("data") val data: List<LotteryItem>?
)

data class LotteryResult(
    val success: Boolean,
    val name: String,
    val count: Int,
    val items: List<LotteryItem>,
    val message: String
)

class LotteryService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun query(get: String, num: Int): LotteryResult = withContext(Dispatchers.IO) {
        val n = num.coerceIn(1, 100)
        return@withContext try {
            val g = URLEncoder.encode(get.trim(), "UTF-8")
            val url = "https://api.pearktrue.cn/api/lottery/?get=$g&num=$n"
            val req = Request.Builder().url(url).addHeader("Accept", "application/json").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) {
                LotteryResult(false, "", 0, emptyList(), "HTTP ${resp.code}")
            } else {
                val api = gson.fromJson(body, LotteryApiResponse::class.java)
                val ok = api.code == 200
                val name = api.name?.ifBlank { "" } ?: ""
                val count = api.count ?: 0
                val items = api.data?.filterNotNull() ?: emptyList()
                if (!ok) LotteryResult(false, name, count, items, api.msg ?: "查询失败")
                else LotteryResult(true, name, count, items, "查询成功")
            }
        } catch (e: Exception) {
            LotteryResult(false, "", 0, emptyList(), "查询异常：${e.message}")
        }
    }

    fun formatAsMarkdown(result: LotteryResult): String {
        if (!result.success || result.items.isEmpty()) {
            return "彩票开奖查询失败：${result.message}。"
        }
        val sb = StringBuilder()
        sb.append("## 彩票开奖查询结果\n")
        sb.append("彩种：${result.name}\n")
        sb.append("查询天数：${result.count}\n\n")
        sb.append("| 期号 | 开奖时间 | 开奖号码 | 特/蓝球 | 销售额 |\n")
        sb.append("| --- | --- | --- | --- | --- |\n")
        val maxRows = result.items.take(20)
        maxRows.forEach { it ->
            val issue = it.issue ?: ""
            val opentime = it.opentime ?: ""
            val draw = it.drawnumber ?: ""
            val trail = it.trailnumber?.ifBlank { "—" } ?: "—"
            val sale = it.salemoney ?: ""
            sb.append("| $issue | $opentime | $draw | $trail | $sale |\n")
        }
        return sb.toString()
    }
}