package com.glassous.aime.data

import com.glassous.aime.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MusicSearchResponse(
    val code: Int,
    val msg: String,
    val data: List<MusicSearchItem>?
)

data class MusicSearchItem(
    val type: String?,
    val id: String?,
    val name: String?,
    val artist: String?,
    val album: String?
)

data class MusicDetailResponse(
    val code: Int,
    val msg: String,
    val data: MusicDetailItem?
)

data class MusicDetailItem(
    val name: String?,
    val artist: String?,
    val album: String?,
    val url: String?,
    val pic: String?,
    val lrc: String?
)

class MusicSearchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun search(keyword: String, source: String, limit: Int = 10): List<MusicSearchItem> = withContext(Dispatchers.IO) {
        val token = BuildConfig.YUNZHI_API_TOKEN
        if (token.isBlank() || token == "your-yunzhi-api-token") {
            return@withContext emptyList()
        }

        try {
            // Remove spaces from keyword as requested
            val cleanKeyword = keyword.replace(" ", "")
            val encodedName = URLEncoder.encode(cleanKeyword, "UTF-8")
            val searchUrl = "https://yunzhiapi.cn/API/hqyyid.php?name=$encodedName&type=$source&page=1&limit=$limit&token=$token"
            
            val searchReq = Request.Builder().url(searchUrl).build()
            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string()

            if (!searchResp.isSuccessful || searchBody.isNullOrBlank()) {
                return@withContext emptyList()
            }

            val searchResult = try {
                gson.fromJson(searchBody, MusicSearchResponse::class.java)
            } catch (e: Exception) {
                return@withContext emptyList()
            }

            // Status code 1 means success, -1 means failure (per user instruction)
            if (searchResult == null || (searchResult.code != 1 && searchResult.code != 200) || searchResult.data.isNullOrEmpty()) {
                return@withContext emptyList()
            }

            return@withContext searchResult.data
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    suspend fun fetchDetail(id: String?, source: String): MusicDetailItem? = withContext(Dispatchers.IO) {
        val token = BuildConfig.YUNZHI_API_TOKEN
        if (id == null || token.isBlank() || token == "your-yunzhi-api-token") return@withContext null
        try {
            val detailUrl = "https://yunzhiapi.cn/API/yyjhss.php?id=$id&type=$source&token=$token"
            val req = Request.Builder().url(detailUrl).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()

            if (!resp.isSuccessful || body.isNullOrBlank()) return@withContext null

            val result = try {
                gson.fromJson(body, MusicDetailResponse::class.java)
            } catch (e: Exception) {
                null
            } ?: return@withContext null

            if (result.code == 200 || result.code == 1) {
                if (result.data != null) {
                    return@withContext result.data
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // Deprecated: use search() and fetchDetail() orchestrated by ChatRepository
    suspend fun searchAndGetDetails(keyword: String, source: String): String {
        return "Deprecated"
    }
}
