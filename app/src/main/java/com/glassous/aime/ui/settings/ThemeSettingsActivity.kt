@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.FontSizeSettingDialog
import com.glassous.aime.ui.components.MinimalModeConfigDialog
import com.glassous.aime.ui.components.TransparencySettingDialog
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

class ThemeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val monochromeTheme by themeViewModel.monochromeTheme.collectAsState()

            val darkTheme = when (selectedTheme) {
                ThemePreferences.THEME_LIGHT -> false
                ThemePreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            AImeTheme(
                darkTheme = darkTheme,
                isMonochrome = monochromeTheme
            ) {
                ThemeSettingsContent(themeViewModel = themeViewModel)
            }
        }
    }
}

@Composable
fun ThemeSettingsContent(
    themeViewModel: ThemeViewModel
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()

    // 主题相关状态
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val monochromeTheme by themeViewModel.monochromeTheme.collectAsState()
    val htmlCodeBlockCardEnabled by themeViewModel.htmlCodeBlockCardEnabled.collectAsState()
    val minimalMode by themeViewModel.minimalMode.collectAsState()
    val replyBubbleEnabled by themeViewModel.replyBubbleEnabled.collectAsState()
    val chatFontSize by themeViewModel.chatFontSize.collectAsState()
    val chatUiOverlayAlpha by themeViewModel.chatUiOverlayAlpha.collectAsState()
    val topBarHamburgerAlpha by themeViewModel.topBarHamburgerAlpha.collectAsState()
    val topBarModelTextAlpha by themeViewModel.topBarModelTextAlpha.collectAsState()
    val chatInputInnerAlpha by themeViewModel.chatInputInnerAlpha.collectAsState()
    val minimalModeConfig by themeViewModel.minimalModeConfig.collectAsState()
    val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()

    // 请求模式状态
    val useCloudProxy by application.modelPreferences.useCloudProxy.collectAsState(initial = false)

    // Dialog States
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showTransparencyDialog by remember { mutableStateOf(false) }
    var showMinimalModeConfigDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置") },
                navigationIcon = {
                    IconButton(onClick = { (context as ThemeSettingsActivity).finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                verticalItemSpacing = 16.dp,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- 颜色模式 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "颜色模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val themeOptions = listOf(
                                    ThemePreferences.THEME_SYSTEM to "跟随系统",
                                    ThemePreferences.THEME_LIGHT to "浅色",
                                    ThemePreferences.THEME_DARK to "深色"
                                )

                                themeOptions.forEach { (value, label) ->
                                    val isSelected = selectedTheme == value
                                    FilterChip(
                                        onClick = { themeViewModel.setTheme(value) },
                                        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                                        selected = isSelected,
                                        modifier = Modifier.weight(1f).selectable(
                                            selected = isSelected,
                                            onClick = { themeViewModel.setTheme(value) },
                                            role = Role.RadioButton
                                        ),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 主题样式 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "主题样式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val styleOptions = listOf(
                                    false to "Material You",
                                    true to "经典"
                                )

                                styleOptions.forEach { (value, label) ->
                                    val isSelected = monochromeTheme == value
                                    FilterChip(
                                        onClick = { themeViewModel.setMonochromeTheme(value) },
                                        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                                        selected = isSelected,
                                        modifier = Modifier.weight(1f).selectable(
                                            selected = isSelected,
                                            onClick = { themeViewModel.setMonochromeTheme(value) },
                                            role = Role.RadioButton
                                        ),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 界面选项 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "界面选项",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // --- HTML代码块卡片显示开关 ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "HTML代码块卡片显示", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "将HTML代码块显示为卡片样式",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = htmlCodeBlockCardEnabled,
                                    onCheckedChange = { themeViewModel.setHtmlCodeBlockCardEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // 极简模式开关
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showMinimalModeConfigDialog = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "极简模式", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "点击配置隐藏的界面元素",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = minimalMode,
                                    onCheckedChange = { themeViewModel.setMinimalMode(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // 回复气泡开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "启用回复气泡", style = MaterialTheme.typography.titleSmall)
                                }
                                Switch(
                                    checked = replyBubbleEnabled,
                                    onCheckedChange = { themeViewModel.setReplyBubbleEnabled(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // 字体设置入口
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showFontSizeDialog = true }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "修改聊天字体大小", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${chatFontSize.toInt()}sp",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // 透明度设置入口
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showTransparencyDialog = true }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "修改聊天页面组件透明度", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }

                // --- Request Mode (Cloud Proxy) ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "请求模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val proxyOptions = listOf(
                                    false to "本地直连",
                                    true to "云端代理"
                                )

                                proxyOptions.forEach { (value, label) ->
                                    val isSelected = useCloudProxy == value
                                    FilterChip(
                                        onClick = { 
                                            scope.launch {
                                                application.modelPreferences.setUseCloudProxy(value)
                                            }
                                        },
                                        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                                        selected = isSelected,
                                        modifier = Modifier.weight(1f).selectable(
                                            selected = isSelected,
                                            onClick = { 
                                                scope.launch {
                                                    application.modelPreferences.setUseCloudProxy(value)
                                                }
                                            },
                                            role = Role.RadioButton
                                        ),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (useCloudProxy) "通过云函数进行转发请求，可解决部分模型无法直连的问题" else "直接从设备发起网络请求，速度更快但可能受网络限制",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFontSizeDialog) {
        FontSizeSettingDialog(
            currentFontSize = chatFontSize,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { themeViewModel.setChatFontSize(it) }
        )
    }

    if (showTransparencyDialog) {
        TransparencySettingDialog(
            currentAlpha = chatUiOverlayAlpha,
            onAlphaChange = { themeViewModel.setChatUiOverlayAlpha(it) },
            currentHamburgerAlpha = topBarHamburgerAlpha,
            onHamburgerAlphaChange = { themeViewModel.setTopBarHamburgerAlpha(it) },
            currentModelTextAlpha = topBarModelTextAlpha,
            onModelTextAlphaChange = { themeViewModel.setTopBarModelTextAlpha(it) },
            currentInputInnerAlpha = chatInputInnerAlpha,
            onInputInnerAlphaChange = { themeViewModel.setChatInputInnerAlpha(it) },
            onDismiss = { showTransparencyDialog = false },
            onConfirm = { showTransparencyDialog = false }
        )
    }

    if (showMinimalModeConfigDialog) {
        MinimalModeConfigDialog(
            config = minimalModeConfig,
            onDismiss = { showMinimalModeConfigDialog = false },
            onConfigChange = { themeViewModel.setMinimalModeConfig(it) },
            fullscreenEnabled = minimalModeFullscreen,
            onFullscreenChange = { themeViewModel.setMinimalModeFullscreen(it) }
        )
    }
}
