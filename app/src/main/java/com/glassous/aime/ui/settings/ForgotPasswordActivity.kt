@file:OptIn(ExperimentalMaterial3Api::class)
package com.glassous.aime.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

class ForgotPasswordActivity : ComponentActivity() {
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
            val darkTheme = when (selectedTheme) {
                com.glassous.aime.data.preferences.ThemePreferences.THEME_LIGHT -> false
                com.glassous.aime.data.preferences.ThemePreferences.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AImeTheme(darkTheme = darkTheme) {
                val context = LocalContext.current
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModelFactory(context.applicationContext as android.app.Application)
                )

                // 状态控制
                var currentStep by remember { mutableStateOf(1) } // 1: 输入邮箱获取问题, 2: 回答问题重置密码
                var email by remember { mutableStateOf("") }
                var retrievedQuestion by remember { mutableStateOf("") }
                var securityAnswer by remember { mutableStateOf("") }
                var newPassword by remember { mutableStateOf("") }

                var showPassword by remember { mutableStateOf(false) }
                var isLoading by remember { mutableStateOf(false) }
                var showInvalid by remember { mutableStateOf<String?>(null) }
                var showSuccess by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("找回密码") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        if (currentStep == 2) {
                                            currentStep = 1 // 返回上一步
                                        } else {
                                            finish()
                                        }
                                    }) {
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
                        containerColor = Color.Transparent,
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

                                    // 第一步：输入邮箱
                                    AnimatedVisibility(visible = currentStep == 1) {
                                        Column {
                                            Text(
                                                text = "第一步：验证账号",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedTextField(
                                                value = email,
                                                onValueChange = { email = it },
                                                label = { Text("邮箱") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    val e = email.trim()
                                                    if (e.isNotEmpty()) {
                                                        scope.launch {
                                                            isLoading = true
                                                            authViewModel.fetchSecurityQuestion(e) { ok, result ->
                                                                isLoading = false
                                                                if (ok) {
                                                                    retrievedQuestion = result
                                                                    currentStep = 2
                                                                } else {
                                                                    scope.launch { snackbarHostState.showSnackbar(result) }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        showInvalid = "请输入邮箱"
                                                    }
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (isLoading) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                                } else {
                                                    Text("下一步")
                                                }
                                            }
                                        }
                                    }

                                    // 第二步：回答问题并重置
                                    AnimatedVisibility(visible = currentStep == 2) {
                                        Column {
                                            Text(
                                                text = "第二步：安全验证",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "安全问题：",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = retrievedQuestion,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedTextField(
                                                value = securityAnswer,
                                                onValueChange = { securityAnswer = it },
                                                label = { Text("请输入答案") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = newPassword,
                                                onValueChange = { newPassword = it },
                                                label = { Text("设置新密码") },
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

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    val ans = securityAnswer.trim()
                                                    val pass = newPassword.trim()

                                                    if (ans.isNotEmpty() && pass.isNotEmpty()) {
                                                        val valid = pass.matches(Regex("^[A-Za-z\\d\\p{Punct}]+$"))
                                                        if (!valid) {
                                                            showInvalid = "密码格式不符合规则"
                                                        } else {
                                                            scope.launch {
                                                                isLoading = true
                                                                authViewModel.resetPasswordWithAnswer(email, ans, pass) { ok, msg ->
                                                                    isLoading = false
                                                                    if (ok) {
                                                                        showSuccess = true
                                                                    } else {
                                                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        showInvalid = "请填写答案和新密码"
                                                    }
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (isLoading) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                                } else {
                                                    Text("重置密码")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                showInvalid?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { showInvalid = null },
                        title = { Text("提示") },
                        text = { Text(msg) },
                        confirmButton = { TextButton(onClick = { showInvalid = null }) { Text("确定") } }
                    )
                }
                if (showSuccess) {
                    AlertDialog(
                        onDismissRequest = { showSuccess = false },
                        title = { Text("成功") },
                        text = { Text("密码已成功重置，请返回登录。") },
                        confirmButton = { TextButton(onClick = { showSuccess = false; finish() }) { Text("确定") } }
                    )
                }
            }
        }
    }
}