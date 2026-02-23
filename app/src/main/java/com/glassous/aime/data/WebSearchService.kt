package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    val fullContent: String = "", // 添加完整网页内容字段
    val image: String? = null // 添加图片URL
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

// Tavily API 响应数据模型
data class TavilyApiResponse(
    @SerializedName("results") val results: List<TavilySearchResult>,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("answer") val answer: String? = null
)

data class TavilySearchResult(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("content") val content: String,
    @SerializedName("raw_content") val rawContent: String? = null,
    @SerializedName("score") val score: Double,
    @SerializedName("published_date") val publishedDate: String?
)

data class TavilyRequest(
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("query") val query: String,
    @SerializedName("search_depth") val searchDepth: String = "basic",
    @SerializedName("include_images") val includeImages: Boolean = true,
    @SerializedName("include_answer") val includeAnswer: Boolean = false,
    @SerializedName("include_raw_content") val includeRawContent: Boolean = true,
    @SerializedName("max_results") val maxResults: Int = 6
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
     * 执行网络搜索
     * @param query 搜索查询
     * @param maxResults 最大结果数量
     * @param onProgress 进度回调，用于更新UI状态
     * @return 搜索结果
     */
    suspend fun search(
        query: String,
        maxResults: Int = 6,
        useCloudProxy: Boolean = false,
        proxyUrl: String? = null,
        onProgress: (suspend (String) -> Unit)? = null,
        engine: String = "pear",
        apiKey: String? = null
    ): WebSearchResponse = withContext(Dispatchers.IO) {
        if (engine == "tavily") {
            if (apiKey.isNullOrBlank()) {
                onProgress?.invoke("Tavily API Key 为空，自动切换回 Pear API...")
                return@withContext searchPear(query, maxResults, useCloudProxy, proxyUrl, onProgress)
            }
            try {
                return@withContext searchTavily(query, apiKey, maxResults, useCloudProxy, proxyUrl, onProgress)
            } catch (e: Exception) {
                onProgress?.invoke("Tavily 搜索失败：${e.message}，自动切换回 Pear API...")
                return@withContext searchPear(query, maxResults, useCloudProxy, proxyUrl, onProgress)
            }
        } else {
            return@withContext searchPear(query, maxResults, useCloudProxy, proxyUrl, onProgress)
        }
    }

    private suspend fun searchPear(
        query: String,
        maxResults: Int,
        useCloudProxy: Boolean,
        proxyUrl: String?,
        onProgress: (suspend (String) -> Unit)?
    ): WebSearchResponse {
        return try {
            onProgress?.invoke("正在全网搜索「$query」...")
            
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
            val searchResults = parseJsonResponse(jsonResponse, maxResults, useCloudProxy, proxyUrl, onProgress)
            
            WebSearchResponse(
                results = searchResults,
                query = query,
                totalResults = searchResults.size
            )
            
        } catch (e: Exception) {
            onProgress?.invoke("搜索遇到错误：${e.message}")
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

    private suspend fun searchTavily(
        query: String,
        apiKey: String,
        maxResults: Int,
        useCloudProxy: Boolean,
        proxyUrl: String?,
        onProgress: (suspend (String) -> Unit)?
    ): WebSearchResponse {
        onProgress?.invoke("正在使用 Tavily 搜索「$query」...")
        
        val tavilyRequest = TavilyRequest(
            apiKey = apiKey,
            query = query,
            maxResults = maxResults
        )
        
        val jsonBody = Gson().toJson(tavilyRequest)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        
        val requestBuilder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        if (useCloudProxy && !proxyUrl.isNullOrBlank()) {
             requestBuilder.url(proxyUrl)
                .addHeader("x-target-url", "https://api.tavily.com/search")
        } else {
             requestBuilder.url("https://api.tavily.com/search")
        }
        
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Tavily API Error: ${response.code} ${response.message}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response from Tavily")
        val tavilyResponse = Gson().fromJson(responseBody, TavilyApiResponse::class.java)
        
        val results = tavilyResponse.results.mapIndexed { index, item ->
            val image = tavilyResponse.images?.getOrNull(index)?.takeIf { it.isNotBlank() }
            
            // 清理 Title 和 URL，防止破坏 Markdown 格式
            val cleanTitle = item.title.replace("[", "(").replace("]", ")").replace("\n", " ").trim()
            val cleanUrl = item.url.trim()
            
            SearchResult(
                title = cleanTitle,
                url = cleanUrl,
                snippet = item.content,
                fullContent = item.rawContent ?: item.content,
                image = image
            )
        }
        
        onProgress?.invoke("Tavily 搜索完成，找到 ${results.size} 条结果")
        
        return WebSearchResponse(
            results = results,
            query = query,
            totalResults = results.size
        )
    }
    
    /**
     * 抓取网页完整信息（标题+内容）
     */
    suspend fun fetchWebPage(url: String, useCloudProxy: Boolean = false, proxyUrl: String? = null): SearchResult = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            
            if (useCloudProxy && !proxyUrl.isNullOrBlank()) {
                requestBuilder.url(proxyUrl)
                    .addHeader("x-target-url", url)
            } else {
                requestBuilder.url(url)
            }

            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext SearchResult("访问失败", url, "HTTP ${response.code}", "")
            }

            val html = response.body?.string() ?: ""
            val document = Jsoup.parse(html)
            val title = document.title()
            
            // 移除脚本和样式标签
            document.select("script, style, nav, footer, header, aside").remove()
            
            // 提取主要内容
            // 优化：避免重复提取。先尝试提取正文容器，若为空则提取body文本。
            
            var finalContent = ""
            val article = document.select("article, main, .content, .post, .entry").first()
            if (article != null) {
                finalContent = article.text().trim()
            } else {
                // Fallback: collect paragraphs
                finalContent = document.select("p, h1, h2, h3, h4, h5, h6").text().trim()
            }
            
            if (finalContent.isBlank()) {
                finalContent = document.body()?.text()?.trim() ?: ""
            }
            
            SearchResult(
                title = title.ifBlank { "无标题" },
                url = url,
                snippet = finalContent.take(200),
                fullContent = if (finalContent.length > 6000) {
                    finalContent.take(6000) + "\n\n[...由于长度限制，剩余内容已截断...]"
                } else {
                    finalContent
                }
            )
        } catch (e: Exception) {
            SearchResult("抓取失败", url, e.message ?: "未知错误", "")
        }
    }

    /**
     * 抓取网页内容
     * @param url 网页URL
     * @return 网页文本内容
     */
    suspend fun fetchWebContent(url: String, useCloudProxy: Boolean = false, proxyUrl: String? = null): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val requestBuilder = Request.Builder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Connection", "keep-alive")

            if (useCloudProxy && !proxyUrl.isNullOrBlank()) {
                requestBuilder.url(proxyUrl)
                    .addHeader("x-target-url", url)
            } else {
                requestBuilder.url(url)
            }

            val request = requestBuilder.build()

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
            if (finalContent.length > 6000) {
                finalContent.substring(0, 6000) + "\n\n[...由于长度限制，剩余内容已截断...]"
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
     * @param onProgress 进度回调
     * @return 搜索结果列表
     */
    private suspend fun parseJsonResponse(
        jsonResponse: String,
        maxResults: Int,
        useCloudProxy: Boolean = false,
        proxyUrl: String? = null,
        onProgress: (suspend (String) -> Unit)? = null
    ): List<SearchResult> = coroutineScope {
        try {
            val gson = Gson()
            val pearApiResponse = gson.fromJson(jsonResponse, PearApiResponse::class.java)
            
            // 检查API响应状态
            if (pearApiResponse.code != 200) {
                return@coroutineScope listOf(
                    SearchResult(
                        title = "搜索失败",
                        url = "",
                        snippet = "API返回错误：${pearApiResponse.msg}",
                        fullContent = ""
                    )
                )
            }
            
            val itemsToProcess = pearApiResponse.data.take(maxResults)
            if (itemsToProcess.isEmpty()) {
                return@coroutineScope emptyList()
            }
            
            onProgress?.invoke("已找到 ${itemsToProcess.size} 条结果，正在阅读网页正文...")

            // 并发抓取所有结果
            val deferredResults = itemsToProcess.map { item ->
                async {
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
                            val fullContent = fetchWebContent(chosenUrl, useCloudProxy, proxyUrl)
                            
                            SearchResult(
                                title = item.title.trim(),
                                url = chosenUrl,
                                snippet = item.abstract.trim(),
                                fullContent = fullContent
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        // 跳过解析失败的单个结果
                        null
                    }
                }
            }
            
            // 等待所有结果并过滤掉失败的(null)
            val results = deferredResults.awaitAll().filterNotNull()
            
            if (results.isEmpty()) {
                 onProgress?.invoke("所有网页抓取失败或无有效内容")
            } else {
                 onProgress?.invoke("成功抓取 ${results.size} 个网页内容")
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