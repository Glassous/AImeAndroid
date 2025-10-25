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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.ui.navigation.AppNavigation
import com.glassous.aime.ui.theme.AImeTheme
import com.glassous.aime.ui.theme.ThemeViewModel
import android.view.View
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure navigation bar is translucent and content lays out behind it
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            
            val darkTheme = when (selectedTheme) {
                ThemePreferences.THEME_LIGHT -> false
                ThemePreferences.THEME_DARK -> true
                else -> isSystemInDarkTheme() // THEME_SYSTEM
            }
            
            AImeTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
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