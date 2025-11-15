package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TicketSeatInfo(
    @SerializedName("seatname") val seatName: String?,
    @SerializedName("bookable") val bookable: String?,
    @SerializedName("seatprice") val seatPrice: Double?,
    @SerializedName("seatinventory") val seatInventory: Int?
)

data class HighSpeedTrainItem(
    @SerializedName("traintype") val trainType: String?,
    @SerializedName("trainumber") val trainNumber: String?,
    @SerializedName("departstation") val departStation: String?,
    @SerializedName("arrivestation") val arriveStation: String?,
    @SerializedName("departtime") val departTime: String?,
    @SerializedName("arrivetime") val arriveTime: String?,
    @SerializedName("runtime") val runTime: String?,
    @SerializedName("ticket_info") val ticketInfo: List<TicketSeatInfo>?
)

data class HighSpeedTicketPayload(
    val code: Int,
    val msg: String?,
    val from: String?,
    val to: String?,
    val time: String?,
    val count: Int?,
    val data: List<HighSpeedTrainItem>?
)

class HighSpeedTicketService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun query(from: String, to: String, date: String?): HighSpeedTicketPayload = withContext(Dispatchers.IO) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d = date?.takeIf { it.isNotBlank() } ?: fmt.format(Date())
        val uf = URLEncoder.encode(from, "UTF-8")
        val ut = URLEncoder.encode(to, "UTF-8")
        val ud = URLEncoder.encode(d, "UTF-8")
        val url = "https://api.pearktrue.cn/api/highspeedticket?from=$uf&to=$ut&time=$ud"
        val req = Request.Builder().url(url).addHeader("Accept", "application/json").build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string()
        if (body.isNullOrBlank()) {
            return@withContext HighSpeedTicketPayload(
                code = resp.code,
                msg = "响应为空",
                from = from,
                to = to,
                time = d,
                count = 0,
                data = emptyList()
            )
        }
        try {
            val parsed = gson.fromJson(body, HighSpeedTicketPayload::class.java)
            parsed.copy(time = parsed.time ?: d)
        } catch (_: Exception) {
            HighSpeedTicketPayload(
                code = resp.code,
                msg = "解析失败",
                from = from,
                to = to,
                time = d,
                count = 0,
                data = emptyList()
            )
        }
    }

    fun formatCondensed(payload: HighSpeedTicketPayload): String {
        val from = payload.from ?: "—"
        val to = payload.to ?: "—"
        val date = payload.time ?: "—"
        val sb = StringBuilder()
        sb.append("查询：$from→$to 日期：$date\n\n")
        if (payload.code == 201) {
            sb.append("今天的车票已售罄\n")
            return sb.toString().trim()
        }
        val items = payload.data ?: emptyList()
        if (items.isEmpty()) {
            sb.append("未查询到可用车次\n")
            return sb.toString().trim()
        }
        items.forEach { item ->
            val num = item.trainNumber ?: "—"
            val depart = item.departTime ?: "—"
            val arrive = item.arriveTime ?: "—"
            val price = cheapestAvailablePrice(item.ticketInfo)
            val line = if (price != null) {
                val priceStr = if (kotlin.math.abs(price - price.toInt()) < 0.5) price.toInt().toString() else String.format(Locale.getDefault(), "%.2f", price)
                "$num $depart-$arrive ${priceStr}元"
            } else {
                "$num $depart-$arrive 暂无可购"
            }
            sb.append(line).append("\n")
        }
        return sb.toString().trim()
    }

    private fun cheapestAvailablePrice(ticketInfo: List<TicketSeatInfo>?): Double? {
        val available = ticketInfo?.filter { it.bookable == "有车票" && it.seatPrice != null }?.map { it.seatPrice!! }
        return available?.minOrNull()
    }
}