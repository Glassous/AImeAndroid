package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// 单日股票数据项
data class StockDailyItem(
    @SerializedName("time") val time: String,
    @SerializedName("opening") val opening: String,
    @SerializedName("closing") val closing: String,
    @SerializedName("highest") val highest: String,
    @SerializedName("lowest") val lowest: String,
    @SerializedName("trading_volume") val tradingVolume: String,
    @SerializedName("turnover") val turnover: String,
    @SerializedName("amplitude") val amplitude: String,
    @SerializedName("inorde") val inorde: String,
    @SerializedName("inorde_amount") val inordeAmount: String,
    @SerializedName("turnover_rate") val turnoverRate: String
)

// API返回
data class StockApiResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("secid") val secid: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("data") val data: List<StockDailyItem>?,
    @SerializedName("api_source") val apiSource: String?
)

// 查询结果
data class StockQueryResult(
    val success: Boolean,
    val secid: String,
    val name: String,
    val items: List<StockDailyItem>,
    val message: String,
    val apiSource: String?
)

/**
 * 股票数据查询服务
 * 参考：GET https://api.pearktrue.cn/api/stock/?secid=300033&num=100
 */
class StockService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    /**
     * 查询指定证券代码的历史数据
     * @param secid 证券代码（如：300033）
     * @param num 返回条数（默认30）
     */
    suspend fun query(secid: String, num: Int = 30): StockQueryResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.pearktrue.cn/api/stock/?secid=$secid&num=$num"
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
                return@withContext StockQueryResult(
                    success = false,
                    secid = secid,
                    name = "",
                    items = emptyList(),
                    message = "股票请求失败：HTTP ${response.code}",
                    apiSource = null
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext StockQueryResult(
                    success = false,
                    secid = secid,
                    name = "",
                    items = emptyList(),
                    message = "股票响应为空",
                    apiSource = null
                )
            }

            val apiResp = gson.fromJson(body, StockApiResponse::class.java)
            val items = apiResp.data ?: emptyList()
            val ok = apiResp.code == 200 && items.isNotEmpty()
            val finalSecid = apiResp.secid ?: secid
            val finalName = apiResp.name ?: ""

            StockQueryResult(
                success = ok,
                secid = finalSecid,
                name = finalName,
                items = items,
                message = if (ok) (apiResp.msg.ifBlank { "获取成功" }) else apiResp.msg.ifBlank { "未获取到股票数据" },
                apiSource = apiResp.apiSource
            )
        } catch (e: Exception) {
            StockQueryResult(
                success = false,
                secid = secid,
                name = "",
                items = emptyList(),
                message = "股票查询异常：${e.message}",
                apiSource = null
            )
        }
    }

    /**
     * 将股票结果格式化为可读文本，供系统消息注入给AI
     */
    fun format(result: StockQueryResult, maxRows: Int = 10): String {
        if (!result.success || result.items.isEmpty()) {
            return "未能获取到「${result.secid}」的股票数据：${result.message}。"
        }

        val sb = StringBuilder()
        val namePart = if (result.name.isNotBlank()) "${result.name}" else ""
        sb.append("📈 股票：${namePart} (${result.secid})\n")
        result.apiSource?.let { sb.append("来源：$it\n\n") }

        val latest = result.items.firstOrNull()
        if (latest != null) {
            sb.append("最新(${latest.time}) 收盘：${latest.closing}，涨跌：${latest.inorde}，成交额：${latest.turnover}，换手率：${latest.turnoverRate}，振幅：${latest.amplitude}\n\n")
        }

        sb.append("近${minOf(result.items.size, maxRows)}日数据：\n")
        result.items.take(maxRows).forEach { d ->
            sb.append("• ${d.time} 开:${d.opening} 收:${d.closing} 高:${d.highest} 低:${d.lowest} 量:${d.tradingVolume} 额:${d.turnover} 涨跌:${d.inorde}(${d.inordeAmount}) 换手:${d.turnoverRate} 振幅:${d.amplitude}\n")
        }

        sb.append("\n请基于以上数据进行简洁的市场解读（趋势、波动、成交量变化等），并提醒用户投资需谨慎，以上信息仅供参考，不构成投资建议。")
        return sb.toString()
    }

    /**
     * 将股票结果格式化为 Markdown 表格，用于 UI 的工具调用结果区域展示（本地插入，不参与模型上下文）
     * 表结构参考：
     * | 日期 | 开盘 | 收盘 | 最高 | 最低 | 成交量 | 成交额 | 振幅 | 涨跌幅 | 涨跌额 | 换手率 |
     */
    fun formatAsMarkdownTable(result: StockQueryResult, maxRows: Int = 15): String {
        if (!result.success || result.items.isEmpty()) {
            return "未能获取到「${result.secid}」的股票数据：${result.message}。"
        }

        val sb = StringBuilder()
        val titleName = if (result.name.isNotBlank()) result.name else ""
        sb.append("### 股票数据 · ${titleName} (${result.secid})\n\n")

        // 最新一日高亮（引用块）
        result.items.firstOrNull()?.let { latest ->
            sb.append(
                "> 最新 ${latest.time} 收盘：${latest.closing} | 涨跌：${latest.inorde}（${latest.inordeAmount}） | 成交额：${latest.turnover} | 换手率：${latest.turnoverRate} | 振幅：${latest.amplitude}\n\n"
            )
        }

        // 表头
        sb.append("| 日期 | 开盘 | 收盘 | 最高 | 最低 | 成交量 | 成交额 | 振幅 | 涨跌幅 | 涨跌额 | 换手率 |\n")
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")

        // 行数据（限制行数）
        result.items.take(maxRows).forEach { d ->
            sb.append("| ${d.time} | ${d.opening} | ${d.closing} | ${d.highest} | ${d.lowest} | ${d.tradingVolume} | ${d.turnover} | ${d.amplitude} | ${d.inorde} | ${d.inordeAmount} | ${d.turnoverRate} |\n")
        }

        // 免责声明（不作为模型指令，仅UI展示）
        sb.append("\n> 注：以上数据仅供参考，不构成投资建议。")
        return sb.toString()
    }
}