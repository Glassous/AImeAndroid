package com.glassous.aime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URLEncoder
import com.glassous.aime.BuildConfig
import com.glassous.aime.data.ProxyBlacklist
import androidx.compose.ui.platform.LocalContext
import com.glassous.aime.AIMeApplication
import okhttp3.OkHttpClient
import okhttp3.Request

data class SearchResult(
    val id: String,
    val title: String,
    val url: String,
    val image: String? = null
)

@Composable
fun SearchResultsCard(
    resultCount: Int,
    status: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status ?: "共有${resultCount}条搜索结果",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (status == null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsBottomSheet(
    results: List<SearchResult>,
    onDismissRequest: () -> Unit,
    onLinkClick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        SearchResultsContent(
            results = results,
            onLinkClick = onLinkClick
        )
    }
}

@Composable
fun SearchResultsContent(
    results: List<SearchResult>,
    onLinkClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "搜索结果",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(results) { result ->
                SearchResultItem(
                    result = result,
                    onClick = { onLinkClick(result.url) }
                )
            }
            item { 
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.id}. ${result.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (result.image != null) {
                Spacer(modifier = Modifier.width(8.dp))
                RemoteImage(
                    url = result.image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        }
    }
}


@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // 获取全局配置
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val useCloudProxy by application.modelPreferences.useCloudProxy.collectAsState(initial = false)
    
    LaunchedEffect(url, useCloudProxy) {
        // 如果已经有缓存的bitmap，则跳过加载 (注意：这里只是简单的内存缓存，如果组件重组bitmap会丢失)
        // 实际的磁盘缓存应该由图片加载库处理，或者在这里手动实现
        // 由于这里使用的是手动下载，我们暂时依赖 Bitmap 的 remember 状态
        if (bitmap != null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                // 判断是否需要代理
                val shouldProxy = useCloudProxy && 
                                BuildConfig.CLOUDFLARE_PROXY_URL.isNotEmpty() && 
                                ProxyBlacklist.shouldUseProxy(url)
                                
                if (shouldProxy) {
                    // 使用 OkHttp 通过代理请求
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url(BuildConfig.CLOUDFLARE_PROXY_URL)
                        .header("x-target-url", url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .build()
                        
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val stream = response.body?.byteStream()
                        if (stream != null) {
                            val decoded = BitmapFactory.decodeStream(stream)
                            bitmap = decoded
                            stream.close()
                        }
                    }
                } else {
                    // 直连请求
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connectTimeout = 5000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    connection.connect()
                    
                    val stream = connection.inputStream
                    val decoded = BitmapFactory.decodeStream(stream)
                    bitmap = decoded
                    stream.close()
                    connection.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // 加载失败或加载中显示占位符
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language, // 使用通用图标作为占位符
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
