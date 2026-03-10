@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.ContextLimitSettingDialog
import com.glassous.aime.ui.components.TitleGenerationContextStrategyDialog
import com.glassous.aime.ui.components.TitleGenerationModelSelectionDialog
import com.glassous.aime.ui.screens.ModelConfigActivity
import com.glassous.aime.ui.screens.SystemPromptConfigActivity
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.ModelConfigViewModel
import com.glassous.aime.ui.viewmodel.ModelConfigViewModelFactory
import kotlinx.coroutines.launch

class ConfigSettingsActivity : ComponentActivity() {
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
                ConfigSettingsContent(
                    onNavigateToModelConfig = {
                        startActivity(Intent(this, ModelConfigActivity::class.java))
                    },
                    onNavigateToSystemPromptConfig = {
                        startActivity(Intent(this, SystemPromptConfigActivity::class.java))
                    },
                    onNavigateToToolConfig = {
                        startActivity(Intent(this, com.glassous.aime.ui.screens.ToolConfigActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun ConfigSettingsContent(
    onNavigateToModelConfig: () -> Unit,
    onNavigateToSystemPromptConfig: () -> Unit,
    onNavigateToToolConfig: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()

    val modelConfigViewModel: ModelConfigViewModel = viewModel(
        factory = ModelConfigViewModelFactory(
            application.modelConfigRepository,
            application.s3Preferences
        )
    )

    // Title Generation Model State
    val titleGenerationModelId by application.modelPreferences.titleGenerationModelId.collectAsState(initial = null)
    val titleGenerationContextStrategy by application.modelPreferences.titleGenerationContextStrategy.collectAsState(initial = 0)
    val titleGenerationContextN by application.modelPreferences.titleGenerationContextN.collectAsState(initial = 20)
    val titleGenerationAutoGenerate by application.modelPreferences.titleGenerationAutoGenerate.collectAsState(initial = false)
    
    var showTitleGenModelSelectionDialog by remember { mutableStateOf(false) }
    var showTitleGenContextStrategyDialog by remember { mutableStateOf(false) }
    var titleGenModelName by remember { mutableStateOf("跟随当前模型") }
    
    val contextLimit by application.contextPreferences.maxContextMessages.collectAsState(initial = 5)
    var showContextLimitDialog by remember { mutableStateOf(false) }

    val contextStrategyLabel = remember(titleGenerationContextStrategy, titleGenerationContextN) {
        when (titleGenerationContextStrategy) {
            0 -> "仅发送消息"
            1 -> "发送消息 + 回复前${titleGenerationContextN}字"
            2 -> "发送消息 + 回复后${titleGenerationContextN}字"
            3 -> "发送消息 + 回复前后${titleGenerationContextN}字"
            4 -> "全部上下文"
            else -> "未知"
        }
    }

    LaunchedEffect(titleGenerationModelId) {
        if (titleGenerationModelId == null) {
            titleGenModelName = "跟随当前模型"
        } else {
            val model = application.modelConfigRepository.getModelById(titleGenerationModelId!!)
            if (model != null) {
                titleGenModelName = model.name
            } else {
                titleGenModelName = "跟随当前模型 (原模型已删除)"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置设置") },
                navigationIcon = {
                    IconButton(onClick = { (context as ConfigSettingsActivity).finish() }) {
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
                // --- 模型配置 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "模型配置",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "配置 OpenAI 兼容的 API 服务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = onNavigateToModelConfig,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "模型设置")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("模型设置")
                            }
                        }
                    }
                }

                // --- 系统提示词配置 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "系统提示词",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "设定 AI 的行为模式、角色或规则",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = onNavigateToSystemPromptConfig,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "配置系统提示词")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("配置系统提示词")
                            }
                        }
                    }
                }

                // --- Tools Configuration ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "工具配置",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "配置联网搜索、天气等内置工具",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = onNavigateToToolConfig,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "工具设置")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("工具设置")
                            }
                        }
                    }
                }

                // --- 上下文限制 ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "最大上下文限制",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "同一对话仅向AI发送最近N条消息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showContextLimitDialog = true }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "当前限制", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if ((contextLimit) <= 0) "无限" else "${contextLimit}条",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showContextLimitDialog = true }) { Text("设置") }
                            }
                        }
                    }
                }
                
                // --- Title Generation Model ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "提示词优化与标题生成设置",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "配置提示词优化、翻译以及生成对话标题时的模型和上下文策略",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            // Row 1: Model Selection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTitleGenModelSelectionDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                 Column(Modifier.weight(1f)) {
                                    Text(text = "生成模型", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = titleGenModelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showTitleGenModelSelectionDialog = true }) { Text("修改") }
                            }

                            // Row 2: Context Strategy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTitleGenContextStrategyDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                 Column(Modifier.weight(1f)) {
                                    Text(text = "上下文策略", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = contextStrategyLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showTitleGenContextStrategyDialog = true }) { Text("修改") }
                            }

                            // Row 3: Auto Generate Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                 Column(Modifier.weight(1f)) {
                                    Text(text = "自动生成标题", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "首轮对话完成后自动生成",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = titleGenerationAutoGenerate,
                                    onCheckedChange = { 
                                        scope.launch { 
                                            application.modelPreferences.setTitleGenerationAutoGenerate(it) 
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContextLimitDialog) {
        ContextLimitSettingDialog(
            currentLimit = contextLimit,
            onDismiss = { showContextLimitDialog = false },
            onConfirm = {
                scope.launch {
                    application.contextPreferences.setMaxContextMessages(it)
                }
            }
        )
    }

    if (showTitleGenModelSelectionDialog) {
        TitleGenerationModelSelectionDialog(
            currentModelId = titleGenerationModelId,
            modelViewModel = modelConfigViewModel,
            onDismiss = { showTitleGenModelSelectionDialog = false },
            onConfirm = { newId ->
                scope.launch {
                    application.modelPreferences.setTitleGenerationModelId(newId)
                }
                showTitleGenModelSelectionDialog = false
            }
        )
    }

    if (showTitleGenContextStrategyDialog) {
        TitleGenerationContextStrategyDialog(
            currentStrategy = titleGenerationContextStrategy,
            currentN = titleGenerationContextN,
            onDismiss = { showTitleGenContextStrategyDialog = false },
            onConfirm = { newStrategy, newN ->
                scope.launch {
                    application.modelPreferences.setTitleGenerationContextStrategy(newStrategy)
                    application.modelPreferences.setTitleGenerationContextN(newN)
                }
                showTitleGenContextStrategyDialog = false
            }
        )
    }
}
