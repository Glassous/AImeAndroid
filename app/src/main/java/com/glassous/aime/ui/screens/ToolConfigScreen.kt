package com.glassous.aime.ui.screens

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.aime.AIMeApplication
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.glassous.aime.data.model.ToolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConfigScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Read preferences
    val webSearchResultCount by application.toolPreferences.webSearchResultCount.collectAsState(initial = 6)
    val webSearchEngine by application.toolPreferences.webSearchEngine.collectAsState(initial = "pear")
    val tavilyApiKey by application.toolPreferences.tavilyApiKey.collectAsState(initial = "")
    val tavilyUseProxy by application.toolPreferences.tavilyUseProxy.collectAsState(initial = false)
    val musicSearchSource by application.toolPreferences.musicSearchSource.collectAsState(initial = "wy")
    val musicSearchResultCount by application.toolPreferences.musicSearchResultCount.collectAsState(initial = 5)

    // Image Generation preferences
    val imageGenBaseUrl by application.toolPreferences.imageGenBaseUrl.collectAsState(initial = "")
    val imageGenApiKey by application.toolPreferences.imageGenApiKey.collectAsState(initial = "")
    val imageGenModel by application.toolPreferences.imageGenModel.collectAsState(initial = "")
    val imageGenModelName by application.toolPreferences.imageGenModelName.collectAsState(initial = "")
    val openaiImageGenApiKey by application.toolPreferences.openaiImageGenApiKey.collectAsState(initial = "")
    val openaiImageGenModel by application.toolPreferences.openaiImageGenModel.collectAsState(initial = "")
    val openaiImageGenModelName by application.toolPreferences.openaiImageGenModelName.collectAsState(initial = "")
    val openaiImageGenBaseUrl by application.toolPreferences.openaiImageGenBaseUrl.collectAsState(initial = "")
    
    // 沉浸式UI设置
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.let {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            val decorView = it.window.decorView
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.window.isNavigationBarContrastEnforced = false
            }
            it.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val screenWidth = maxWidth
            val columns = when {
                screenWidth < 600.dp -> 1
                screenWidth < 840.dp -> 2
                else -> 3
            }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalItemSpacing = 16.dp,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Iterate over all available tools
                items(ToolType.values().size) { index ->
                val toolType = ToolType.values()[index]
                val isVisible by application.toolPreferences.getToolVisibility(toolType.name).collectAsState(initial = true)
                
                ToolConfigItem(
                    title = toolType.displayName,
                    description = toolType.description,
                    enabled = isVisible,
                    onEnabledChange = { enabled ->
                        scope.launch {
                            application.toolPreferences.setToolVisibility(toolType.name, enabled)
                        }
                    },
                    hasMoreSettings = toolType == ToolType.WEB_SEARCH || toolType == ToolType.MUSIC_SEARCH || 
                                     toolType == ToolType.IMAGE_GENERATION
                ) {
                    if (toolType == ToolType.WEB_SEARCH) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "搜索结果数量: $webSearchResultCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            val sliderValue = webSearchResultCount.toFloat().coerceIn(1f, 20f)
                            var textValue by remember { mutableStateOf(webSearchResultCount.toString()) }
                            
                            // Sync text with external changes
                            LaunchedEffect(webSearchResultCount) {
                                textValue = webSearchResultCount.toString()
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value = sliderValue,
                                    onValueChange = { value ->
                                        scope.launch {
                                            application.toolPreferences.setWebSearchResultCount(value.roundToInt())
                                        }
                                    },
                                    valueRange = 1f..20f,
                                    steps = 0
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = { newValue ->
                                        textValue = newValue
                                        val num = newValue.toIntOrNull()
                                        if (num != null && num >= 1) {
                                            scope.launch {
                                                application.toolPreferences.setWebSearchResultCount(num)
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true
                                )
                            }
                            Text(
                                text = "设置每次搜索返回的结果条数",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "搜索引擎",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch { application.toolPreferences.setWebSearchEngine("pear") }
                                }
                            ) {
                                RadioButton(
                                    selected = webSearchEngine == "pear",
                                    onClick = { scope.launch { application.toolPreferences.setWebSearchEngine("pear") } }
                                )
                                Text(
                                    text = "Pear API (默认)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch { application.toolPreferences.setWebSearchEngine("tavily") }
                                }
                            ) {
                                RadioButton(
                                    selected = webSearchEngine == "tavily",
                                    onClick = { scope.launch { application.toolPreferences.setWebSearchEngine("tavily") } }
                                )
                                Text(
                                    text = "Tavily Search API",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            AnimatedVisibility(
                                visible = webSearchEngine == "tavily",
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = tavilyApiKey,
                                        onValueChange = { 
                                            scope.launch { application.toolPreferences.setTavilyApiKey(it) } 
                                        },
                                        label = { Text("Tavily API Key") },
                                        placeholder = { Text("tvly-...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Text(
                                        text = "Tavily 免费额度有限，请使用自己的 API Key",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "使用 Aliyun FC 代理",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "通过阿里云函数计算代理请求，解决网络问题",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = tavilyUseProxy,
                                            onCheckedChange = { 
                                                scope.launch { application.toolPreferences.setTavilyUseProxy(it) } 
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (toolType == ToolType.MUSIC_SEARCH) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "回复歌曲数量: $musicSearchResultCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val musicSliderValue = musicSearchResultCount.toFloat().coerceIn(1f, 10f)
                            var musicTextValue by remember { mutableStateOf(musicSearchResultCount.toString()) }

                            LaunchedEffect(musicSearchResultCount) {
                                musicTextValue = musicSearchResultCount.toString()
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Slider(
                                     modifier = Modifier.weight(1f),
                                     value = musicSliderValue,
                                     onValueChange = { value ->
                                         scope.launch {
                                             application.toolPreferences.setMusicSearchResultCount(value.roundToInt())
                                         }
                                     },
                                     valueRange = 1f..10f,
                                     steps = 0
                                 )
                                 Spacer(modifier = Modifier.width(16.dp))
                                 OutlinedTextField(
                                     value = musicTextValue,
                                     onValueChange = { newValue ->
                                         musicTextValue = newValue
                                         val num = newValue.toIntOrNull()
                                         if (num != null && num >= 1) {
                                             scope.launch {
                                                 application.toolPreferences.setMusicSearchResultCount(num)
                                             }
                                         }
                                     },
                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                     modifier = Modifier.width(80.dp),
                                     singleLine = true
                                 )
                             }
                             Text(
                                 text = "设置搜索结果返回的歌曲条数",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "音乐源选择:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val sources = listOf(
                                "wy" to "网易云",
                                "qq" to "QQ音乐",
                                "kw" to "酷我",
                                "mg" to "咪咕",
                                "qi" to "千千"
                            )
                            sources.forEach { (key, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                application.toolPreferences.setMusicSearchSource(key)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = musicSearchSource == key,
                                        onClick = null // Handled by Row clickable
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    } else if (toolType == ToolType.IMAGE_GENERATION) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = imageGenBaseUrl,
                                onValueChange = { 
                                    scope.launch { application.toolPreferences.setImageGenBaseUrl(it) } 
                                },
                                label = { Text("Endpoint URL") },
                                placeholder = { Text("https://api.example.com/v1/images/generations") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageGenApiKey,
                                onValueChange = { 
                                    scope.launch { application.toolPreferences.setImageGenApiKey(it) } 
                                },
                                label = { Text("API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageGenModel,
                                onValueChange = { 
                                    scope.launch { application.toolPreferences.setImageGenModel(it) } 
                                },
                                label = { Text("Model 字段") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageGenModelName,
                                onValueChange = { 
                                    scope.launch { application.toolPreferences.setImageGenModelName(it) } 
                                },
                                label = { Text("Model 外显名称") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun ToolConfigItem(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hasMoreSettings: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (hasMoreSettings) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Expand/Collapse button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起设置" else "展开设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    content()
                }
            }
        }
    }
}
