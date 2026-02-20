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
import com.glassous.aime.ui.screens.HtmlPreviewScreen
import com.glassous.aime.ui.screens.ModelConfigScreen
import com.glassous.aime.ui.screens.MessageDetailScreen
import com.glassous.aime.ui.theme.ThemeViewModel
import com.glassous.aime.ui.viewmodel.HtmlPreviewViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModel
import com.glassous.aime.ui.viewmodel.ModelSelectionViewModelFactory
 
 

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object ModelConfig : Screen("model_config")
    object MessageDetail : Screen("message_detail")
    object HtmlPreview : Screen("html_preview")
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
    
    // 创建共享的HtmlPreviewViewModel实例
    val htmlPreviewViewModel: HtmlPreviewViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToMessageDetail = { id ->
                    navController.navigate("${Screen.MessageDetail.route}/$id")
                },
                onNavigateToHtmlPreview = { htmlCode ->
                    // 将HTML代码存储到共享ViewModel中，设置为预览模式，然后导航
                    val isWebAnalysis = htmlCode.contains("<!-- type: web_analysis")
                    htmlPreviewViewModel.setHtmlCode(htmlCode)
                    
                    if (isWebAnalysis) {
                        // 尝试提取URL
                        val urlRegex = Regex("url:(http[^\\s]+)")
                        val match = urlRegex.find(htmlCode)
                        val url = match?.groupValues?.get(1)
                        htmlPreviewViewModel.setPreviewUrl(url)
                        htmlPreviewViewModel.setIsSourceMode(false) // 预览模式加载URL
                    } else {
                        htmlPreviewViewModel.setPreviewUrl(null)
                        htmlPreviewViewModel.setIsSourceMode(false)
                    }
                    
                    htmlPreviewViewModel.setIsRestrictedMode(isWebAnalysis)
                    navController.navigate(Screen.HtmlPreview.route)
                },
                onNavigateToHtmlPreviewSource = { htmlCode ->
                    // 将HTML代码存储到共享ViewModel中，设置为源码模式，然后导航
                    val isWebAnalysis = htmlCode.contains("<!-- type: web_analysis")
                    htmlPreviewViewModel.setHtmlCode(htmlCode)
                    
                    if (isWebAnalysis) {
                        // 网页分析模式下，源码按钮显示提取的内容（即之前的预览内容）
                        // 且不加载URL
                        htmlPreviewViewModel.setPreviewUrl(null)
                        htmlPreviewViewModel.setIsSourceMode(false) // 显示渲染后的内容而非源码
                    } else {
                        htmlPreviewViewModel.setPreviewUrl(null)
                        htmlPreviewViewModel.setIsSourceMode(true)
                    }
                    
                    htmlPreviewViewModel.setIsRestrictedMode(isWebAnalysis)
                    navController.navigate(Screen.HtmlPreview.route)
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

        composable(
            route = Screen.HtmlPreview.route
        ) {
            HtmlPreviewScreen(
                htmlCode = htmlPreviewViewModel.htmlCode.value.orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                isSourceMode = htmlPreviewViewModel.isSourceMode.value ?: false,
                isRestricted = htmlPreviewViewModel.isRestrictedMode.value ?: false,
                previewUrl = htmlPreviewViewModel.previewUrl.value
            )
        }
    }
}
