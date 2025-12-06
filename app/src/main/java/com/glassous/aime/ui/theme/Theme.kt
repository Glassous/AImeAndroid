package com.glassous.aime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 默认深色主题
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// 默认浅色主题
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// --- 新增：黑白深色主题 ---
// 主要是黑色背景，白色文字和边框
private val MonochromeDarkColorScheme = darkColorScheme(
    primary = PureWhite,           // 主要操作按钮为白色
    onPrimary = PureBlack,         // 按钮文字为黑色
    primaryContainer = Gray20,     // 容器背景深灰
    onPrimaryContainer = PureWhite,
    secondary = Gray80,
    onSecondary = PureBlack,
    tertiary = Gray80,
    background = PureBlack,        // 纯黑背景
    surface = PureBlack,           // 纯黑表面
    surfaceContainer = Gray10,     // 稍微浅一点的表面（用于卡片等）
    surfaceVariant = Gray20,
    onBackground = PureWhite,
    onSurface = PureWhite,
    onSurfaceVariant = Gray80,
    outline = Gray80
)

// --- 新增：黑白浅色主题 ---
// 主要是白色背景，黑色文字和边框
private val MonochromeLightColorScheme = lightColorScheme(
    primary = PureBlack,           // 主要操作按钮为黑色
    onPrimary = PureWhite,         // 按钮文字为白色
    primaryContainer = Gray90,     // 容器背景浅灰
    onPrimaryContainer = PureBlack,
    secondary = Gray20,
    onSecondary = PureWhite,
    tertiary = Gray20,
    background = PureWhite,        // 纯白背景
    surface = PureWhite,           // 纯白表面
    surfaceContainer = Gray95,     // 稍微深一点的表面
    surfaceVariant = Gray90,
    onBackground = PureBlack,
    onSurface = PureBlack,
    onSurfaceVariant = Gray20,
    outline = Gray20
)

@Composable
fun AImeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    // 新增参数：是否启用黑白主题
    isMonochrome: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 优先级 1: 如果启用了黑白主题，直接使用黑白配色
        isMonochrome -> {
            if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
        }
        // 优先级 2: 如果启用了动态取色且系统支持 (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 优先级 3: 默认 Material 配色
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}