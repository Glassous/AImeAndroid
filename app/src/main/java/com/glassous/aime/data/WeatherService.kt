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

data class WeatherDaily(
    @SerializedName("date") val date: String,
    @SerializedName("temperature") val temperature: String,
    @SerializedName("weather") val weather: String,
    @SerializedName("wind") val wind: String,
    @SerializedName("air_quality") val airQuality: String
)

// 查询结果
data class WeatherQueryResult(
    val success: Boolean,
    val city: String,
    val days: List<WeatherDaily>,
    val message: String
)

/**
 * 城市天气查询服务，基于 Open‑Meteo：
 * - 地理编码：https://geocoding-api.open-meteo.com/v1/search
 * - 天气每日：https://api.open-meteo.com/v1/forecast
 * - 空气质量：https://air-quality-api.open-meteo.com/v1/air-quality
 */
class WeatherService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    private data class GeocodingResult(val results: List<GeocodeItem>?)
    private data class GeocodeItem(
        val name: String?,
        val latitude: Double?,
        val longitude: Double?,
        val country: String?,
        val admin1: String?
    )

    private data class ForecastDaily(
        val time: List<String>?,
        @SerializedName("temperature_2m_max") val tMax: List<Double>?,
        @SerializedName("temperature_2m_min") val tMin: List<Double>?,
        @SerializedName("weather_code") val wcode: List<Int>?,
        @SerializedName("wind_speed_10m_max") val windSpeedMax: List<Double>?,
        @SerializedName("wind_direction_10m_dominant") val windDirDominant: List<Int>?
    )
    private data class ForecastResp(val daily: ForecastDaily?)

    private data class AirQualityHourly(
        val time: List<String>?,
        @SerializedName("european_aqi") val aqi: List<Double>?
    )
    private data class AirQualityResp(val hourly: AirQualityHourly?)

    /**
     * 查询指定城市天气（中文城市名）
     */
    suspend fun query(city: String): WeatherQueryResult = withContext(Dispatchers.IO) {
        try {
            val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=zh&format=json"
            val geoReq = Request.Builder()
                .url(geoUrl)
                .addHeader("Accept", "application/json")
                .build()
            val geoResp = client.newCall(geoReq).execute()
            val geoBody = geoResp.body?.string()
            if (!geoResp.isSuccessful || geoBody.isNullOrBlank()) {
                return@withContext WeatherQueryResult(false, city, emptyList(), "地理编码失败：HTTP ${geoResp.code}")
            }
            val geo = gson.fromJson(geoBody, GeocodingResult::class.java)
            val first = geo.results?.firstOrNull()
            val lat = first?.latitude
            val lon = first?.longitude
            val displayCity = listOfNotNull(first?.name, first?.admin1, first?.country).joinToString(" ").ifBlank { city }
            if (lat == null || lon == null) {
                return@withContext WeatherQueryResult(false, city, emptyList(), "未找到城市坐标")
            }

            queryByCoords(lat, lon, displayCity)
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
     * 查询指定经纬度的天气
     */
    suspend fun query(lat: Double, lon: Double): WeatherQueryResult = withContext(Dispatchers.IO) {
        val displayCity = "当前位置 (${String.format("%.2f", lat)}, ${String.format("%.2f", lon)})"
        try {
            queryByCoords(lat, lon, displayCity)
        } catch (e: Exception) {
            WeatherQueryResult(
                success = false,
                city = displayCity,
                days = emptyList(),
                message = "天气查询异常：${e.message}"
            )
        }
    }

    private fun queryByCoords(lat: Double, lon: Double, displayCity: String): WeatherQueryResult {
            val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=temperature_2m_max,temperature_2m_min,weather_code,wind_speed_10m_max,wind_direction_10m_dominant&timezone=auto&forecast_days=5"
            val fcReq = Request.Builder().url(forecastUrl).addHeader("Accept", "application/json").build()
            val fcResp = client.newCall(fcReq).execute()
            val fcBody = fcResp.body?.string()
            if (!fcResp.isSuccessful || fcBody.isNullOrBlank()) {
                return WeatherQueryResult(false, displayCity, emptyList(), "天气数据获取失败：HTTP ${fcResp.code}")
            }
            val fc = gson.fromJson(fcBody, ForecastResp::class.java)
            val d = fc.daily
            val times = d?.time ?: emptyList()
            val tmax = d?.tMax ?: emptyList()
            val tmin = d?.tMin ?: emptyList()
            val wcodes = d?.wcode ?: emptyList()
            val wsp = d?.windSpeedMax ?: emptyList()
            val wdir = d?.windDirDominant ?: emptyList()

            val days = mutableListOf<WeatherDaily>()
            val count = listOf(times.size, tmax.size, tmin.size).minOrNull() ?: 0
            repeat(count) { i ->
                val date = times.getOrNull(i) ?: return@repeat
                val min = tmin.getOrNull(i)
                val max = tmax.getOrNull(i)
                val code = wcodes.getOrNull(i)
                val ws = wsp.getOrNull(i)
                val wd = wdir.getOrNull(i)
                val tempStr = if (min != null && max != null) "${min.toInt()}~${max.toInt()}℃" else "—"
                val weatherStr = weatherCodeToZh(code)
                val windStr = windToZh(ws, wd)
                days.add(WeatherDaily(date = date, temperature = tempStr, weather = weatherStr, wind = windStr, airQuality = ""))
            }

            val aqUrl = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&hourly=european_aqi&timezone=auto"
            val aqReq = Request.Builder().url(aqUrl).addHeader("Accept", "application/json").build()
            val aqResp = client.newCall(aqReq).execute()
            val aqBody = aqResp.body?.string()
            val aq = if (aqResp.isSuccessful && !aqBody.isNullOrBlank()) gson.fromJson(aqBody, AirQualityResp::class.java) else null
            val aqiHourly = aq?.hourly
            if (aqiHourly != null && !aqiHourly.time.isNullOrEmpty() && !aqiHourly.aqi.isNullOrEmpty()) {
                val perDayMax = mutableMapOf<String, Double>()
                for (idx in aqiHourly.time!!.indices) {
                    val t = aqiHourly.time!![idx]
                    val v = aqiHourly.aqi!![idx]
                    val day = t.substring(0, 10)
                    val prev = perDayMax[day]
                    if (prev == null || v > prev) perDayMax[day] = v
                }
                days.replaceAll { dItem ->
                    val dayKey = dItem.date.take(10)
                    val v = perDayMax[dayKey]
                    val aqStr = v?.let { mapAqiToZh(it) } ?: "—"
                    dItem.copy(airQuality = aqStr)
                }
            } else {
                days.replaceAll { it.copy(airQuality = "—") }
            }

            val ok = days.isNotEmpty()
            return WeatherQueryResult(
                success = ok,
                city = displayCity,
                days = days,
                message = if (ok) "数据请求成功" else "未获取到天气数据"
            )
    }

    private fun weatherCodeToZh(code: Int?): String {
        return when (code) {
            0 -> "晴"
            1, 2, 3 -> "多云"
            45, 48 -> "雾/霾"
            51, 53, 55 -> "毛毛雨"
            56, 57 -> "冻毛毛雨"
            61, 63, 65 -> "小雨/中雨/大雨"
            66, 67 -> "冻雨"
            71, 73, 75 -> "小雪/中雪/大雪"
            77 -> "飘雪"
            80, 81, 82 -> "阵雨"
            85, 86 -> "阵雪"
            95 -> "雷阵雨"
            96, 99 -> "雷暴/冰雹"
            else -> "不明"
        }
    }

    private fun windToZh(speed: Double?, dirDeg: Int?): String {
        val dir = when (dirDeg ?: -1) {
            in 23..67 -> "东北"
            in 68..112 -> "东"
            in 113..157 -> "东南"
            in 158..202 -> "南"
            in 203..247 -> "西南"
            in 248..292 -> "西"
            in 293..337 -> "西北"
            else -> "北"
        }
        val s = speed?.let { String.format("%.1f", it) } ?: "—"
        return "风速 ${s} m/s，风向 ${dir}"
    }

    private fun mapAqiToZh(v: Double): String {
        return when {
            v <= 50 -> "优"
            v <= 100 -> "良"
            v <= 150 -> "轻度污染"
            v <= 200 -> "中度污染"
            v <= 300 -> "重度污染"
            else -> "严重污染"
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
        var overallMin: Int? = null
        var overallMax: Int? = null
        result.days.forEach { day ->
            sb.append("• ${day.date}：${day.weather}，${day.temperature}，${day.wind}，空气质量：${day.airQuality}\n")

            // 基于温度/天气/空气质量生成生活提示
            val temps = Regex("-?\\d+").findAll(day.temperature).map { it.value.toIntOrNull() }.filterNotNull().toList()
            val min = temps.minOrNull()
            val max = temps.maxOrNull()
            if (min != null) overallMin = (overallMin?.let { kotlin.math.min(it, min) } ?: min)
            if (max != null) overallMax = (overallMax?.let { kotlin.math.max(it, max) } ?: max)

            val tips = mutableListOf<String>()
            if (min != null && min <= 10) tips.add("气温较低，注意保暖，适当加衣")
            if (max != null && max >= 30) tips.add("气温偏高，注意防暑，多喝水")
            if (min != null && max != null && (max - min) >= 8) tips.add("昼夜温差较大，注意增减衣物")

            val w = day.weather
            if (w.contains("雨")) tips.add("可能有降雨，出门记得带伞")
            if (w.contains("雪")) tips.add("可能降雪，注意防滑与保暖")
            if (w.contains("雾") || w.contains("霾")) tips.add("能见度较低，驾车谨慎")

            val aq = day.airQuality
            if (aq.contains("污染") || aq.contains("重度") || aq.contains("严重")) {
                tips.add("空气质量欠佳，减少户外活动，佩戴口罩")
            }

            if (tips.isNotEmpty()) {
                sb.append("  建议：${tips.joinToString("，")}。\n")
            }
        }

        // 综合提示
        val summaryTips = mutableListOf<String>()
        if ((overallMin ?: 99) <= 10) summaryTips.add("天气偏冷，外出注意保暖")
        if ((overallMax ?: -99) >= 30) summaryTips.add("天气炎热，注意防暑补水")
        if (summaryTips.isNotEmpty()) {
            sb.append("\n综合建议：${summaryTips.joinToString("，")}。\n")
        }

        sb.append("\n请基于上述数据提供简洁、有用的天气说明，并在回答中自然加入贴心生活提示（如穿衣、防雨、防晒、通勤等）。")
        return sb.toString()
    }

    /**
     * 将天气结果格式化为 Markdown 表格，用于 UI 的工具调用结果区域展示
     */
    fun formatAsMarkdownTable(result: WeatherQueryResult): String {
        if (!result.success || result.days.isEmpty()) {
            return "未能获取到「${result.city}」的天气信息：${result.message}。"
        }
        val sb = StringBuilder()
        // 城市说明（非标题）
        sb.append("城市：${result.city}\n\n")
        // 表头
        sb.append("| 日期 | 天气 | 温度 | 风向 | 空气质量 |\n")
        sb.append("| --- | --- | --- | --- | --- |\n")
        // 行数据
        result.days.forEach { day ->
            sb.append("| ${day.date} | ${day.weather} | ${day.temperature} | ${day.wind} | ${day.airQuality} |\n")
        }
        return sb.toString()
    }
}