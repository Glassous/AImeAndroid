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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
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

class RegisterActivity : ComponentActivity() {
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
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }
                var securityQuestion by remember { mutableStateOf("") }
                var securityAnswer by remember { mutableStateOf("") }

                var showPassword by remember { mutableStateOf(false) }
                var showConfirm by remember { mutableStateOf(false) }
                var isRegistering by remember { mutableStateOf(false) }
                var showInvalid by remember { mutableStateOf<String?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("账号注册") },
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = confirm,
                                        onValueChange = { confirm = it },
                                        label = { Text("确认密码") },
                                        singleLine = true,
                                        visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showConfirm = !showConfirm }) {
                                                if (showConfirm) {
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
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "安全设置 (用于找回密码)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = securityQuestion,
                                        onValueChange = { securityQuestion = it },
                                        label = { Text("设置安全问题") },
                                        placeholder = { Text("例如：我的小学班主任是谁？") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = securityAnswer,
                                        onValueChange = { securityAnswer = it },
                                        label = { Text("问题答案") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            val e = email.trim()
                                            val p = password.trim()
                                            val c = confirm.trim()
                                            val q = securityQuestion.trim()
                                            val a = securityAnswer.trim()

                                            if (e.isNotEmpty() && p.isNotEmpty() && c.isNotEmpty() && q.isNotEmpty() && a.isNotEmpty()) {
                                                val valid = p.matches(Regex("^[A-Za-z\\d\\p{Punct}]+$"))
                                                if (!valid) {
                                                    showInvalid = "密码只能包含英文字母、数字和符号"
                                                } else if (p != c) {
                                                    showInvalid = "两次密码不一致"
                                                } else {
                                                    scope.launch {
                                                        isRegistering = true
                                                        authViewModel.register(e, p, q, a) { ok, msg ->
                                                            isRegistering = false
                                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                                            if (ok) finish()
                                                        }
                                                    }
                                                }
                                            } else {
                                                showInvalid = "请填写所有字段"
                                            }
                                        },
                                        enabled = !isRegistering,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isRegistering) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                        } else {
                                            Text("注册")
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
                        confirmButton = {
                            TextButton(onClick = { showInvalid = null }) { Text("确定") }
                        }
                    )
                }
            }
        }
    }
}