package com.glassous.aime.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassous.aime.AIMeApplication
import com.glassous.aime.ui.screens.ChatScreen
import com.glassous.aime.ui.screens.ModelConfigScreen
import com.glassous.aime.ui.screens.SettingsScreen
import com.glassous.aime.ui.screens.MessageDetailScreen
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModelFactory
import com.glassous.aime.ui.settings.OssConfigScreen
import com.glassous.aime.data.preferences.OssPreferences

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object ModelConfig : Screen("model_config")
    object OssConfig : Screen("oss_config")
    object MessageDetail : Screen("message_detail")
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
            application.modelPreferences,
            application.autoSyncPreferences,
            application.ossPreferences,
            application.cloudSyncViewModel
        )
    )
    val ossPreferences = OssPreferences(context)
    
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToMessageDetail = { id ->
                    navController.navigate("${Screen.MessageDetail.route}/$id")
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
                onNavigateToOssConfig = {
                    navController.navigate(Screen.OssConfig.route)
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

        composable(Screen.OssConfig.route) {
            OssConfigScreen(
                ossPreferences = ossPreferences,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Screen.MessageDetail.route}/{messageId}",
            arguments = listOf(navArgument("messageId") { type = NavType.LongType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getLong("messageId") ?: 0L
            MessageDetailScreen(
                messageId = messageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}