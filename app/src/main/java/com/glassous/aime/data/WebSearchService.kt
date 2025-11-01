package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// 搜索结果数据模型
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val fullContent: String = "" // 添加完整网页内容字段
)

// 网络搜索响应
data class WebSearchResponse(
    val results: List<SearchResult>,
    val query: String,
    val totalResults: Int = 0
)

// PearAPI 响应数据模型
data class PearApiResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("search") val search: String,
    @SerializedName("page") val page: Int,
    @SerializedName("data") val data: List<PearApiSearchResult>,
    @SerializedName("api_source") val apiSource: String
)

data class PearApiSearchResult(
    @SerializedName("title") val title: String,
    @SerializedName("href") val href: String,
    @SerializedName("cache_link") val cacheLink: String,
    @SerializedName("abstract") val abstract: String
)

// 网络搜索服务
class WebSearchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    
    /**
     * 执行网络搜索 - 使用PearAPI搜索引擎
     * @param query 搜索查询
     * @param maxResults 最大结果数量
     * @return 搜索结果
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5
    ): WebSearchResponse = withContext(Dispatchers.IO) {
        try {
            // URL编码搜索查询
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            // 构建PearAPI搜索URL
            val searchUrl = "https://api.pearktrue.cn/api/universalsearch/?search=$encodedQuery&page=1"
            
            // 创建HTTP请求
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Connection", "keep-alive")
                .build()
            
            // 执行请求
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("搜索请求失败: ${response.code}")
            }
            
            val jsonResponse = response.body?.string() ?: throw IOException("响应体为空")
            
            // 解析JSON获取搜索结果
            val searchResults = parseJsonResponse(jsonResponse, maxResults)
            
            WebSearchResponse(
                results = searchResults,
                query = query,
                totalResults = searchResults.size
            )
            
        } catch (e: Exception) {
            // 如果网络搜索失败，返回错误信息
            WebSearchResponse(
                results = listOf(
                    SearchResult(
                        title = "搜索失败",
                        url = "",
                        snippet = "无法执行网络搜索：${e.message}。请检查网络连接或稍后重试。",
                        fullContent = ""
                    )
                ),
                query = query,
                totalResults = 0
            )
        }
    }
    
    /**
     * 抓取网页内容
     * @param url 网页URL
     * @return 网页文本内容
     */
    private suspend fun fetchWebContent(url: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Connection", "keep-alive")
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext "无法访问网页：HTTP ${response.code}"
            }

            val html = response.body?.string() ?: return@withContext "网页内容为空"
            
            // 使用Jsoup解析HTML并提取文本内容
            val document: Document = Jsoup.parse(html)
            
            // 移除脚本和样式标签
            document.select("script, style, nav, footer, header, aside").remove()
            
            // 提取主要内容
            val content = document.select("article, main, .content, .post, .entry, p, h1, h2, h3, h4, h5, h6")
                .text()
                .trim()
            
            // 如果没有找到主要内容，则提取body中的文本
            val finalContent = if (content.isNotBlank()) content else document.body()?.text()?.trim() ?: ""
            
            // 限制内容长度，避免过长
            if (finalContent.length > 2000) {
                finalContent.substring(0, 2000) + "..."
            } else {
                finalContent
            }
            
        } catch (e: Exception) {
            "无法抓取网页内容：${e.message}"
        }
    }

    /**
     * 解析JSON响应并抓取网页内容
     * @param jsonResponse JSON响应字符串
     * @param maxResults 最大结果数量
     * @return 搜索结果列表
     */
    private suspend fun parseJsonResponse(jsonResponse: String, maxResults: Int): List<SearchResult> {
        return try {
            val gson = Gson()
            val pearApiResponse = gson.fromJson(jsonResponse, PearApiResponse::class.java)
            
            // 检查API响应状态
            if (pearApiResponse.code != 200) {
                return listOf(
                    SearchResult(
                        title = "搜索失败",
                        url = "",
                        snippet = "API返回错误：${pearApiResponse.msg}",
                        fullContent = ""
                    )
                )
            }
            
            // 转换PearAPI结果为SearchResult并抓取网页内容
            val results = mutableListOf<SearchResult>()
            for (item in pearApiResponse.data.take(maxResults)) {
                try {
                    // 清理URL（移除可能的空格、引号、反引号）
                    fun cleanse(raw: String): String {
                        return raw
                            .trim()
                            .removeSurrounding("\"")
                            .removeSurrounding("'")
                            .removeSurrounding("`")
                            .replace("`", "")
                            .trim()
                    }

                    val hrefRaw = item.href
                    val cacheRaw = item.cacheLink
                    val cleanHref = cleanse(hrefRaw)
                    val cleanCache = cleanse(cacheRaw)

                    fun isValid(url: String): Boolean =
                        url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))

                    val chosenUrl = if (isValid(cleanHref)) cleanHref else cleanCache

                    // 验证URL有效性（优先使用 href，其次使用 cache_link）
                    if (isValid(chosenUrl)) {
                        // 抓取网页内容
                        val fullContent = fetchWebContent(chosenUrl)

                        results.add(
                            SearchResult(
                                title = item.title.trim(),
                                url = chosenUrl,
                                snippet = item.abstract.trim(),
                                fullContent = fullContent
                            )
                        )
                    }
                } catch (e: Exception) {
                    // 跳过解析失败的单个结果
                    continue
                }
            }
            
            results
        } catch (e: Exception) {
            // JSON解析失败时返回错误信息
            listOf(
                SearchResult(
                    title = "解析失败",
                    url = "",
                    snippet = "无法解析搜索结果：${e.message}",
                    fullContent = ""
                )
            )
        }
    }
    
    /**
     * 格式化搜索结果为文本
     * @param searchResponse 搜索响应
     * @return 格式化的文本
     */
    fun formatSearchResults(searchResponse: WebSearchResponse): String {
        if (searchResponse.results.isEmpty()) {
            return "未找到关于「${searchResponse.query}」的相关搜索结果。"
        }
        
        val formatted = StringBuilder()
        formatted.append("🔍 搜索结果：「${searchResponse.query}」\n\n")
        
        searchResponse.results.forEachIndexed { index, result ->
            formatted.append("${index + 1}. **${result.title}**\n")
            formatted.append("   ${result.snippet}\n")
            formatted.append("   🔗 ${result.url}\n")
            
            // 如果有完整网页内容，添加到格式化结果中
            if (result.fullContent.isNotEmpty()) {
                formatted.append("   📄 网页内容：\n")
                formatted.append("   ${result.fullContent}\n")
            }
            formatted.append("\n")
        }
        
        return formatted.toString()
    }
}