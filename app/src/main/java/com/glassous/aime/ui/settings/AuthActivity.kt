@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import android.view.WindowManager
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModel
import com.glassous.aime.ui.viewmodel.AuthViewModelFactory
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {
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
            val monochromeTheme by themeViewModel.monochromeTheme.collectAsState() // 新增

            val darkTheme = when (selectedTheme) {
                com.glassous.aime.data.preferences.ThemePreferences.THEME_LIGHT -> false
                com.glassous.aime.data.preferences.ThemePreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            AImeTheme(
                darkTheme = darkTheme,
                isMonochrome = monochromeTheme // 传递参数
            ) {
                val context = LocalContext.current
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModelFactory(context.applicationContext as android.app.Application)
                )
                val app = context.applicationContext as com.glassous.aime.AIMeApplication
                val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)
                val userEmail by authViewModel.email.collectAsState(initial = null)
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var showPassword by remember { mutableStateOf(false) }
                var isLoggingIn by remember { mutableStateOf(false) }
                var showLogoutConfirm by remember { mutableStateOf(false) }
                var clearLocalOnLogout by remember { mutableStateOf(false) }
                var showSecurityQuestionDialog by remember { mutableStateOf(false) }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val uploadHistoryEnabled by app.syncPreferences.uploadHistoryEnabled.collectAsState(initial = true)
                val uploadModelConfigEnabled by app.syncPreferences.uploadModelConfigEnabled.collectAsState(initial = true)
                val uploadSelectedModelEnabled by app.syncPreferences.uploadSelectedModelEnabled.collectAsState(initial = true)
                val uploadApiKeyEnabled by app.syncPreferences.uploadApiKeyEnabled.collectAsState(initial = false)
                var isSyncing by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("账号登录") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
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
                        containerColor = MaterialTheme.colorScheme.background, // 确保背景色
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (isLoggedIn) {
                                        Text(
                                            text = "已登录：" + (userEmail ?: ""),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { showLogoutConfirm = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("退出登录") }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { showSecurityQuestionDialog = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("设置/修改安全问题")
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "云端同步设置",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("上传聊天记录", style = MaterialTheme.typography.titleSmall)
                                            Switch(
                                                checked = uploadHistoryEnabled,
                                                onCheckedChange = { v ->
                                                    scope.launch {
                                                        app.syncPreferences.setUploadHistoryEnabled(v)
                                                        snackbarHostState.showSnackbar("设置已更新：上传聊天记录" + if (v) "已开启" else "已关闭")
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("上传模型配置", style = MaterialTheme.typography.titleSmall)
                                            Switch(
                                                checked = uploadModelConfigEnabled,
                                                onCheckedChange = { v ->
                                                    scope.launch {
                                                        app.syncPreferences.setUploadModelConfigEnabled(v)
                                                        snackbarHostState.showSnackbar("设置已更新：上传模型配置" + if (v) "已开启" else "已关闭")
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("上传模型选择", style = MaterialTheme.typography.titleSmall)
                                            Switch(
                                                checked = uploadSelectedModelEnabled,
                                                onCheckedChange = { v ->
                                                    scope.launch {
                                                        app.syncPreferences.setUploadSelectedModelEnabled(v)
                                                        snackbarHostState.showSnackbar("设置已更新：上传模型选择" + if (v) "已开启" else "已关闭")
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("上传 API Key", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    text = "开启后可能暴露隐私，请谨慎",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = uploadApiKeyEnabled,
                                                onCheckedChange = { v ->
                                                    scope.launch {
                                                        app.syncPreferences.setUploadApiKeyEnabled(v)
                                                        snackbarHostState.showSnackbar("设置已更新：上传 API Key" + if (v) "已开启" else "已关闭")
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    if (!isSyncing) {
                                                        isSyncing = true
                                                        try {
                                                            val (ok, msg) = app.cloudSyncManager.manualSync()
                                                            snackbarHostState.showSnackbar(msg)
                                                        } catch (e: Exception) {
                                                            val detail = e.stackTraceToString().take(500)
                                                            snackbarHostState.showSnackbar("同步失败：" + (e.message ?: "未知错误") + "\n详情：" + detail)
                                                        } finally {
                                                            isSyncing = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isSyncing,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isSyncing) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                            } else {
                                                Text("立即同步")
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = email,
                                            onValueChange = { email = it },
                                            label = { Text("邮箱") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = { Text("密码") },
                                            singleLine = true,
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPassword = !showPassword }) {
                                                    if (showPassword) {
                                                        Icon(Icons.Filled.VisibilityOff, contentDescription = "隐藏密码")
                                                    } else {
                                                        Icon(Icons.Filled.Visibility, contentDescription = "显示密码")
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "密码规则：仅支持英文字母、数字和符号",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                val e = email.trim()
                                                val p = password.trim()
                                                if (e.isNotEmpty() && p.isNotEmpty()) {
                                                    scope.launch {
                                                        isLoggingIn = true
                                                        authViewModel.login(e, p) { ok, msg ->
                                                            isLoggingIn = false
                                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                                            if (ok) finish()
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isLoggingIn,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isLoggingIn) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                            } else {
                                                Text("登录")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    startActivity(android.content.Intent(this@AuthActivity, RegisterActivity::class.java))
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("注册新账户") }
                                            OutlinedButton(
                                                onClick = {
                                                    startActivity(android.content.Intent(this@AuthActivity, ForgotPasswordActivity::class.java))
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("重置密码") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showLogoutConfirm) {
                    AlertDialog(
                        onDismissRequest = { showLogoutConfirm = false },
                        title = { Text("确认退出登录") },
                        text = {
                            Column {
                                Text("是否确认退出当前账号？")
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = clearLocalOnLogout, onCheckedChange = { clearLocalOnLogout = it })
                                    Text("同时清除本地数据（会话、消息、模型配置）")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showLogoutConfirm = false
                                scope.launch {
                                    authViewModel.logout { ok, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                        if (clearLocalOnLogout) {
                                            authViewModel.clearLocalData { ok2, msg2 ->
                                                scope.launch { snackbarHostState.showSnackbar(msg2) }
                                            }
                                        }
                                        finish()
                                    }
                                }
                            }) { Text("确认") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
                        }
                    )
                }

                if (showSecurityQuestionDialog) {
                    SecurityQuestionDialog(
                        onDismiss = { showSecurityQuestionDialog = false },
                        onConfirm = { pwd, q, a ->
                            authViewModel.updateSecurityQuestion(pwd, q, a) { ok, msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                    if (ok) showSecurityQuestionDialog = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityQuestionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置安全问题") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "为了保护您的账号安全，请验证密码并设置新的安全问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("当前登录密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("新安全问题") },
                    placeholder = { Text("例如：我的出生地是？") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("问题答案") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotBlank() && question.isNotBlank() && answer.isNotBlank()) {
                        isSubmitting = true
                        onConfirm(password, question, answer)
                    }
                },
                enabled = !isSubmitting && password.isNotBlank() && question.isNotBlank() && answer.isNotBlank()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
