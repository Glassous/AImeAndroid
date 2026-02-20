package com.glassous.aime.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glassous.aime.AIMeApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemPromptConfigScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()
    
    // 从 ModelPreferences 读取系统提示词
    val savedSystemPrompt by application.modelPreferences.systemPrompt.collectAsState(initial = "")
    val savedEnableDate by application.modelPreferences.enableDynamicDate.collectAsState(initial = false)
    val savedEnableTimestamp by application.modelPreferences.enableDynamicTimestamp.collectAsState(initial = false)
    val savedEnableLocation by application.modelPreferences.enableDynamicLocation.collectAsState(initial = false)
    val savedEnableDeviceModel by application.modelPreferences.enableDynamicDeviceModel.collectAsState(initial = false)
    val savedEnableLanguage by application.modelPreferences.enableDynamicLanguage.collectAsState(initial = false)
    
    // 本地状态，用于编辑
    var currentPrompt by remember { mutableStateOf(TextFieldValue("")) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 当读取到保存的提示词时，更新本地状态
    LaunchedEffect(savedSystemPrompt) {
        if (currentPrompt.text != savedSystemPrompt) {
            currentPrompt = TextFieldValue(text = savedSystemPrompt, selection = TextRange(savedSystemPrompt.length))
        }
    }
    
    val hasChanges = currentPrompt.text != savedSystemPrompt
    
    val saveChanges = {
        scope.launch {
            application.modelPreferences.setSystemPrompt(currentPrompt.text)
            snackbarHostState.showSnackbar("保存成功")
        }
    }
    
    BackHandler(enabled = hasChanges) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("未保存的更改") },
            text = { Text("您有未保存的系统提示词更改，是否保存？") },
            confirmButton = {
                TextButton(onClick = {
                    saveChanges()
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text("不保存")
                }
            }
        )
    }

    // 权限请求启动器
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
             scope.launch {
                 application.modelPreferences.setEnableDynamicLocation(true)
             }
        } else {
             scope.launch {
                 snackbarHostState.showSnackbar("获取位置权限失败，无法启用位置信息")
                 application.modelPreferences.setEnableDynamicLocation(false)
             }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统提示词配置") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) {
                            showUnsavedDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        saveChanges()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = currentPrompt,
                onValueChange = { currentPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("系统提示词 (System Prompt)") },
                placeholder = { Text("在此输入系统提示词，这将作为第一条消息发送给 AI，用于设定 AI 的行为模式、角色或规则。留空则不发送。") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 动态信息卡片区域
            Text("动态信息注入 (发送时自动获取)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = savedEnableDate,
                    onClick = { 
                        scope.launch { application.modelPreferences.setEnableDynamicDate(!savedEnableDate) }
                    },
                    label = { Text("当前日期") },
                    leadingIcon = if (savedEnableDate) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                
                FilterChip(
                    selected = savedEnableTimestamp,
                    onClick = { 
                        scope.launch { application.modelPreferences.setEnableDynamicTimestamp(!savedEnableTimestamp) }
                    },
                    label = { Text("时间戳") },
                    leadingIcon = if (savedEnableTimestamp) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                
                FilterChip(
                    selected = savedEnableLocation,
                    onClick = {
                        if (!savedEnableLocation) {
                            // 检查权限
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasFine || hasCoarse) {
                                scope.launch { application.modelPreferences.setEnableDynamicLocation(true) }
                            } else {
                                locationPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            }
                        } else {
                            scope.launch { application.modelPreferences.setEnableDynamicLocation(false) }
                        }
                    },
                    label = { Text("地理位置") },
                    leadingIcon = if (savedEnableLocation) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = savedEnableDeviceModel,
                    onClick = { 
                        scope.launch { application.modelPreferences.setEnableDynamicDeviceModel(!savedEnableDeviceModel) }
                    },
                    label = { Text("设备型号") },
                    leadingIcon = if (savedEnableDeviceModel) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = savedEnableLanguage,
                    onClick = { 
                        scope.launch { application.modelPreferences.setEnableDynamicLanguage(!savedEnableLanguage) }
                    },
                    label = { Text("系统语言") },
                    leadingIcon = if (savedEnableLanguage) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 推荐内容卡片区域
            Text("推荐内容 (点击插入)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val recommendations = listOf(
                    "请使用中文回答",
                    "请称呼我为[输入昵称]",
                    "我是男生",
                    "我是女生",
                    "请一步步思考",
                    "回复简短精炼",
                    "简单解释",
                    "翻译成英文",
                    "风格幽默",
                    "风格严肃"
                )
                
                recommendations.forEach { text ->
                    SuggestionChip(
                        onClick = {
                            val newText = StringBuilder(currentPrompt.text)
                            val selectionStart = currentPrompt.selection.start
                            val textToInsert = " $text "
                            newText.insert(selectionStart, textToInsert)
                            currentPrompt = TextFieldValue(
                                text = newText.toString(),
                                selection = TextRange(selectionStart + textToInsert.length)
                            )
                        },
                        label = { Text(text) }
                    )
                }
            }
        }
    }
}
