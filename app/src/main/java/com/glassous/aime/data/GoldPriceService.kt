package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class BankGoldBarPrice(
    val bank: String?,
    val price: String?
)

data class GoldRecyclePrice(
    @SerializedName("gold_type") val goldType: String?,
    @SerializedName("recycle_price") val recyclePrice: String?,
    @SerializedName("updated_date") val updatedDate: String?
)

data class PreciousMetalPrice(
    val brand: String?,
    @SerializedName("bullion_price") val bullionPrice: String?,
    @SerializedName("gold_price") val goldPrice: String?,
    @SerializedName("platinum_price") val platinumPrice: String?,
    @SerializedName("updated_date") val updatedDate: String?
)

data class GoldPricePayload(
    @SerializedName("bank_gold_bar_price") val bankGoldBarPrice: List<BankGoldBarPrice>?,
    @SerializedName("gold_recycle_price") val goldRecyclePrice: List<GoldRecyclePrice>?,
    @SerializedName("precious_metal_price") val preciousMetalPrice: List<PreciousMetalPrice>?
)

data class GoldPriceApiResponse(
    val code: Int,
    val msg: String?,
    val data: GoldPricePayload?
)

data class GoldPriceResult(
    val success: Boolean,
    val message: String,
    val bankBars: List<BankGoldBarPrice>,
    val recyclePrices: List<GoldRecyclePrice>,
    val preciousPrices: List<PreciousMetalPrice>
)

class GoldPriceService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun query(): GoldPriceResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://v2.xxapi.cn/api/goldprice")
            .addHeader("Accept", "application/json")
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string()
        if (!resp.isSuccessful || body.isNullOrBlank()) {
            return@withContext GoldPriceResult(
                success = false,
                message = "黄金价格接口请求失败：HTTP ${resp.code}",
                bankBars = emptyList(),
                recyclePrices = emptyList(),
                preciousPrices = emptyList()
            )
        }
        val parsed = gson.fromJson(body, GoldPriceApiResponse::class.java)
        val payload = parsed.data
        val ok = parsed.code == 200 && payload != null
        GoldPriceResult(
            success = ok,
            message = parsed.msg?.ifBlank { if (ok) "数据请求成功" else "未获取到黄金价格数据" } ?: (if (ok) "数据请求成功" else "未获取到黄金价格数据"),
            bankBars = payload?.bankGoldBarPrice ?: emptyList(),
            recyclePrices = payload?.goldRecyclePrice ?: emptyList(),
            preciousPrices = payload?.preciousMetalPrice ?: emptyList()
        )
    }

    fun formatAsMarkdownParagraphs(result: GoldPriceResult): String {
        if (!result.success) {
            return "黄金价格数据暂不可用：${result.message}。"
        }
        val sb = StringBuilder()
        if (result.bankBars.isNotEmpty()) {
            sb.append("银行投资金条价格\n")
            result.bankBars.forEach { item ->
                val bank = item.bank ?: "—"
                val price = item.price ?: "—"
                sb.append("- $bank：$price\n")
            }
            sb.append("\n")
        }
        if (result.recyclePrices.isNotEmpty()) {
            sb.append("黄金/钯金/银回收价\n")
            result.recyclePrices.forEach { item ->
                val t = item.goldType ?: "—"
                val p = item.recyclePrice ?: "—"
                val d = item.updatedDate ?: "—"
                sb.append("- $t：$p（更新：$d）\n")
            }
            sb.append("\n")
        }
        if (result.preciousPrices.isNotEmpty()) {
            sb.append("品牌贵金属价格\n")
            result.preciousPrices.forEach { item ->
                val brand = item.brand ?: "—"
                val bullion = item.bullionPrice ?: "-"
                val gold = item.goldPrice ?: "-"
                val platinum = item.platinumPrice ?: "-"
                val d = item.updatedDate ?: "—"
                sb.append("- $brand：金条 $bullion，黄金 $gold，铂金 $platinum（更新：$d）\n")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }
}