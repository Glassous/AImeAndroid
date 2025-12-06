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
import com.glassous.aime.ui.screens.MessageDetailScreen
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModelFactory
 
 

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object ModelConfig : Screen("model_config")
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
            application.modelPreferences
        )
    )
    
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToMessageDetail = { id ->
                    navController.navigate("${Screen.MessageDetail.route}/$id")
                },
                modelSelectionViewModel = modelSelectionViewModel,
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
