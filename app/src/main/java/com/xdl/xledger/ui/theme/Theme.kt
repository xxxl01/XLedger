package com.xdl.xledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalXledgerColors = staticCompositionLocalOf { LightXledgerColors }

/** 应用语义色板，通过 MaterialTheme.xledgerColors 访问。 */
val MaterialTheme.xledgerColors: XledgerColors
    @Composable get() = LocalXledgerColors.current

/**
 * 应用主题。
 *
 * - 默认关闭 dynamic color，忠实还原旧版固定配色（记账语义色不随壁纸变化）；
 *   需要系统动态取色时把 [dynamicColor] 置 true。
 * - [darkTheme] 跟随系统深色模式，语义色板自动切换。
 */
@Composable
fun XLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorSchemeFallback
        else -> LightColorSchemeFallback
    }
    val xledgerColors = if (darkTheme) DarkXledgerColors else LightXledgerColors

    CompositionLocalProvider(LocalXledgerColors provides xledgerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = XledgerShapes,
            content = content,
        )
    }
}
