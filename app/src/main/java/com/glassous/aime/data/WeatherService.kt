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

// 天气每日数据
data class WeatherDaily(
    @SerializedName("date") val date: String,
    @SerializedName("temperature") val temperature: String,
    @SerializedName("weather") val weather: String,
    @SerializedName("wind") val wind: String,
    @SerializedName("air_quality") val airQuality: String
)

// API data 载荷
data class WeatherDataPayload(
    @SerializedName("city") val city: String,
    @SerializedName("data") val data: List<WeatherDaily>
)

// API 返回
data class WeatherApiResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: WeatherDataPayload?
)

// 查询结果
data class WeatherQueryResult(
    val success: Boolean,
    val city: String,
    val days: List<WeatherDaily>,
    val message: String
)

/**
 * 城市天气查询服务，基于 xxapi 天气接口：
 * GET https://v2.xxapi.cn/api/weather?city=城市名称
 */
class WeatherService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    /**
     * 查询指定城市天气（中文城市名，如：滕州/滕州市/枣庄滕州）
     */
    suspend fun query(city: String): WeatherQueryResult = withContext(Dispatchers.IO) {
        try {
            val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
            val url = "https://v2.xxapi.cn/api/weather?city=$encodedCity"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                )
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext WeatherQueryResult(
                    success = false,
                    city = city,
                    days = emptyList(),
                    message = "天气请求失败：HTTP ${response.code}"
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext WeatherQueryResult(
                    success = false,
                    city = city,
                    days = emptyList(),
                    message = "天气响应为空"
                )
            }

            val apiResp = gson.fromJson(body, WeatherApiResponse::class.java)
            val payload = apiResp.data
            val days = payload?.data ?: emptyList()
            val ok = apiResp.code == 200 && payload != null && days.isNotEmpty()

            WeatherQueryResult(
                success = ok,
                city = payload?.city ?: city,
                days = days,
                message = if (ok) "数据请求成功" else apiResp.msg.ifBlank { "未获取到天气数据" }
            )
        } catch (e: Exception) {
            WeatherQueryResult(
                success = false,
                city = city,
                days = emptyList(),
                message = "天气查询异常：${e.message}"
            )
        }
    }

    /**
     * 将天气结果格式化为可读文本，供系统消息注入给AI
     */
    fun format(result: WeatherQueryResult): String {
        if (!result.success || result.days.isEmpty()) {
            return "未能获取到「${result.city}」的天气信息：${result.message}。"
        }
        val sb = StringBuilder()
        sb.append("🌤 城市：${result.city}\n\n")
        result.days.forEach { day ->
            sb.append("• ${day.date}：${day.weather}，${day.temperature}，${day.wind}，空气质量：${day.airQuality}\n")
        }
        sb.append("\n请基于上述数据为用户提供简洁、有用的天气说明。")
        return sb.toString()
    }
}