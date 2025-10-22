package com.glassous.aime.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.screens.ChatScreen
import com.glassous.aime.ui.screens.ModelConfigScreen
import com.glassous.aime.ui.screens.SettingsScreen
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModelFactory

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object ModelConfig : Screen("model_config")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val themeViewModel: ThemeViewModel = viewModel()
    
    // 获取Application实例和ModelSelectionViewModel
    val context = LocalContext.current
    val application = context.applicationContext as AIMeApplication
    val modelSelectionViewModel: ModelSelectionViewModel = viewModel(
        factory = ModelSelectionViewModelFactory(
            application.modelConfigRepository,
            application.modelPreferences
        )
    )
    
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                modelSelectionViewModel = modelSelectionViewModel
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToModelConfig = {
                    navController.navigate(Screen.ModelConfig.route)
                },
                themeViewModel = themeViewModel
            )
        }
        
        composable(Screen.ModelConfig.route) {
            ModelConfigScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}