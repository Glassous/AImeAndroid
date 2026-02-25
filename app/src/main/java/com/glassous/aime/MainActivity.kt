package com.glassous.aime

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.glassous.aime.data.GitHubReleaseService
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.PrivacyPolicyDialog
import com.glassous.aime.ui.components.UpdateDialog
import com.glassous.aime.ui.navigation.AppNavigation
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.PrivacyUiState
import com.glassous.aime.ui.viewmodel.PrivacyViewModel
import com.glassous.aime.ui.viewmodel.PrivacyViewModelFactory
import com.glassous.aime.ui.viewmodel.UpdateCheckState
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModel
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModelFactory

import kotlinx.coroutines.flow.first

import android.widget.Toast

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装启动页 (必须在 super.onCreate 之前)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // 设置透明导航栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // 关键：设置窗口不适应系统窗口，这对全屏显示很重要
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 支持显示刘海屏区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 1. 在 onCreate 早期初始化 ViewModel
        val themeViewModel = ViewModelProvider(this)[ThemeViewModel::class.java]
        val privacyViewModel = ViewModelProvider(
            this,
            PrivacyViewModelFactory((application as AIMeApplication).privacyPreferences)
        )[PrivacyViewModel::class.java]

        // 2. 关键修复：让启动页一直显示，直到主题配置加载完成 (isReady = true)
        // 这避免了 "闪烁" 问题（先显示 Material You 颜色，然后突然变黑白）
        splashScreen.setKeepOnScreenCondition {
            !themeViewModel.isReady.value || privacyViewModel.uiState.value is PrivacyUiState.Loading
        }

        setContent {
            // 在这里获取同一个 ViewModel 实例
            // 注意：这里我们不需要再次 viewModel()，直接用上面的 themeViewModel 也是可以的，
            // 但为了保持 Compose 风格，我们重新获取（它是单例的，指向同一个对象）
            // 或者直接传递 themeViewModel 也可以。

            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val monochromeTheme by themeViewModel.monochromeTheme.collectAsState()
            val privacyUiState by privacyViewModel.uiState.collectAsState()

            val minimalMode by themeViewModel.minimalMode.collectAsState()
            val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()

            // 版本更新ViewModel
            val versionUpdateViewModel: VersionUpdateViewModel = viewModel(
                factory = VersionUpdateViewModelFactory(GitHubReleaseService(), (application as AIMeApplication).updatePreferences)
            )
            val updateCheckState by versionUpdateViewModel.updateCheckState.collectAsState()

            // 对话框显示状态
            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<com.glassous.aime.data.model.VersionUpdateInfo?>(null) }

            val darkTheme = when (selectedTheme) {
                ThemePreferences.THEME_LIGHT -> false
                ThemePreferences.THEME_DARK -> true
                else -> isSystemInDarkTheme() // THEME_SYSTEM
            }

            // 应用启动时自动检查更新
            LaunchedEffect(Unit) {
                // 延迟1秒检查，确保网络和组件准备就绪
                kotlinx.coroutines.delay(1000)
                
                // 直接从 Preferences 读取配置，避免 ViewModel StateFlow 初始值为 false 的问题
                val enabled = (application as AIMeApplication).updatePreferences.autoCheckUpdateEnabled.first()
                if (enabled) {
                    versionUpdateViewModel.checkForUpdates()
                }
            }

            // 监听更新检查结果
            LaunchedEffect(updateCheckState) {
                when (val state = updateCheckState) {
                    is UpdateCheckState.Success -> {
                        if (state.updateInfo.hasUpdate) {
                            updateInfo = state.updateInfo
                            showUpdateDialog = true
                        }
                    }
                    is UpdateCheckState.Error -> {
                        // 如果自动检查失败（非手动触发），可以选择不打扰用户，或者记录日志
                        // 这里为了调试方便，如果是开发版本可以弹出提示，但正式版最好静默
                        // Toast.makeText(this@MainActivity, "检查更新失败: ${state.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // 其他状态不处理
                    }
                }
            }

            AImeTheme(
                darkTheme = darkTheme,
                isMonochrome = monochromeTheme
            ) {
                // 根据极简模式与全屏显示设置动态隐藏系统UI
                LaunchedEffect(minimalMode, minimalModeFullscreen) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller?.let { insetsController ->
                        if (minimalMode && minimalModeFullscreen) {
                            // 设置全屏模式 - 隐藏状态栏和导航栏
                            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())

                            // 额外设置窗口标志以确保全屏效果
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.FLAG_FULLSCREEN
                            )
                        } else {
                            // 恢复正常模式 - 显示状态栏和导航栏
                            insetsController.show(WindowInsetsCompat.Type.systemBars())

                            // 清除全屏标志
                            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (privacyUiState) {
                        is PrivacyUiState.Agreed -> {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                                AppNavigation()

                                // 显示更新对话框
                                if (showUpdateDialog && updateInfo != null) {
                                    UpdateDialog(
                                        updateInfo = updateInfo!!,
                                        onDismiss = {
                                            showUpdateDialog = false
                                            versionUpdateViewModel.resetState()
                                        },
                                        onDownload = { downloadUrl ->
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                            startActivity(intent)
                                        }
                                    )
                                }

                                val musicPlayerViewModel: com.glassous.aime.ui.viewmodel.MusicPlayerViewModel = viewModel()
                                com.glassous.aime.ui.components.GlobalMusicPlayer(viewModel = musicPlayerViewModel)
                            }
                        }
                        is PrivacyUiState.NotAgreed -> {
                            PrivacyPolicyDialog(
                                isFirstRun = true,
                                onAgree = { privacyViewModel.setPrivacyPolicyAgreed(true) },
                                onDisagree = { finish() },
                                onDismissRequest = {}
                            )
                        }
                        else -> { /* Loading */ }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AImeTheme {
        AppNavigation()
    }
}