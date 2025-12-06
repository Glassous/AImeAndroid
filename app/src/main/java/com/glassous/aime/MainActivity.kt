package com.glassous.aime

import android.os.Bundle
import android.os.Build
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.glassous.aime.data.GitHubReleaseService
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.components.UpdateDialog
import com.glassous.aime.ui.navigation.AppNavigation
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.UpdateCheckState
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModel
import com.glassous.aime.ui.viewmodel.VersionUpdateViewModelFactory
import android.view.View
import android.view.WindowManager
import android.content.Intent
import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val minimalMode by themeViewModel.minimalMode.collectAsState()
            val minimalModeFullscreen by themeViewModel.minimalModeFullscreen.collectAsState()
            
            // 版本更新ViewModel
            val versionUpdateViewModel: VersionUpdateViewModel = viewModel(
                factory = VersionUpdateViewModelFactory(GitHubReleaseService())
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
            
            // 应用启动时自动检查更新（延迟执行，避免影响启动速度）
            LaunchedEffect(Unit) {
                // 延迟2秒检查，让应用先启动完成
                kotlinx.coroutines.delay(2000)
                versionUpdateViewModel.checkForUpdates()
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
                    else -> {
                        // 其他状态不处理
                    }
                }
            }
            
            AImeTheme(darkTheme = darkTheme) {
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