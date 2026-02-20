package com.glassous.aime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.glassous.aime.AIMeApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptConfigScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val scope = rememberCoroutineScope()
    
    // 从 ModelPreferences 读取系统提示词
    val savedSystemPrompt by application.modelPreferences.systemPrompt.collectAsState(initial = "")
    
    // 本地状态，用于编辑
    var currentPrompt by remember { mutableStateOf("") }
    
    // 当读取到保存的提示词时，更新本地状态（仅一次，或者当保存的值改变且当前为空时）
    LaunchedEffect(savedSystemPrompt) {
        if (currentPrompt != savedSystemPrompt) {
            currentPrompt = savedSystemPrompt
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统提示词配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            application.modelPreferences.setSystemPrompt(currentPrompt)
                            snackbarHostState.showSnackbar("保存成功")
                        }
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
                    .fillMaxSize()
                    .weight(1f),
                label = { Text("系统提示词 (System Prompt)") },
                placeholder = { Text("在此输入系统提示词，这将作为第一条消息发送给 AI，用于设定 AI 的行为模式、角色或规则。留空则不发送。") },
                shape = RoundedCornerShape(12.dp), // 圆角
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
