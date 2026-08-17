package com.xdl.xledger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用语义色板。
 *
 * 色值从旧版 Expo（x-ledger）页面的硬编码色值提取，保证原生版视觉一致：
 * 页面背景 #F7F8FA、主文字 #11181C、强调 #0A7EA4、支出 #A6423A、收入 #1F7A45 等。
 */
data class XledgerColors(
    /** 页面背景。 */
    val background: Color,
    /** 卡片 / 面板背景。 */
    val card: Color,
    /** 卡片边框。 */
    val cardBorder: Color,
    /** 输入框 / 次级按钮边框。 */
    val inputBorder: Color,
    /** 列表分隔线。 */
    val divider: Color,
    /** 主文字。 */
    val textPrimary: Color,
    /** 次要文字（说明、时间等）。 */
    val textSecondary: Color,
    /** 强调色 / 链接色。 */
    val accent: Color,
    /** 强调色浅底（选中态背景）。 */
    val accentContainer: Color,
    /** 支出文字色。 */
    val expense: Color,
    /** 支出按钮边框。 */
    val expenseBorder: Color,
    /** 收入文字色。 */
    val income: Color,
    /** 重复记录文字色。 */
    val duplicate: Color,
    /** 重复记录边框。 */
    val duplicateBorder: Color,
    /** 禁用 / 低优先级元素。 */
    val disabled: Color,
)

/** 浅色语义色板（对应旧版浅色 UI）。 */
val LightXledgerColors = XledgerColors(
    background = Color(0xFFF7F8FA),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE3E6EA),
    inputBorder = Color(0xFFD8DDE3),
    divider = Color(0xFFEEF0F2),
    textPrimary = Color(0xFF11181C),
    textSecondary = Color(0xFF687076),
    accent = Color(0xFF0A7EA4),
    accentContainer = Color(0xFFEEF6F9),
    expense = Color(0xFFA6423A),
    expenseBorder = Color(0xFFD85C52),
    income = Color(0xFF1F7A45),
    duplicate = Color(0xFFC2410C),
    duplicateBorder = Color(0xFFF97316),
    disabled = Color(0xFF94A3B8),
)

/** 深色语义色板（同色调的深色映射）。 */
val DarkXledgerColors = XledgerColors(
    background = Color(0xFF101316),
    card = Color(0xFF1A1F24),
    cardBorder = Color(0xFF2A3138),
    inputBorder = Color(0xFF333B44),
    divider = Color(0xFF23282D),
    textPrimary = Color(0xFFECEDEE),
    textSecondary = Color(0xFF9BA1A6),
    accent = Color(0xFF4FB3D9),
    accentContainer = Color(0xFF16303A),
    expense = Color(0xFFE8837A),
    expenseBorder = Color(0xFFD85C52),
    income = Color(0xFF66BB8A),
    duplicate = Color(0xFFF59E6B),
    duplicateBorder = Color(0xFFF97316),
    disabled = Color(0xFF5B6470),
)

// ---- Material 语义色（fallback 色板，dynamic color 关闭时使用）----

val LightColorSchemeFallback = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0A7EA4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF6F9),
    onPrimaryContainer = Color(0xFF0B3B4A),
    secondary = Color(0xFF687076),
    onSecondary = Color(0xFFFFFFFF),
    background = LightXledgerColors.background,
    onBackground = LightXledgerColors.textPrimary,
    surface = LightXledgerColors.card,
    onSurface = LightXledgerColors.textPrimary,
    surfaceVariant = Color(0xFFF2F4F6),
    onSurfaceVariant = LightXledgerColors.textSecondary,
    outline = LightXledgerColors.cardBorder,
    error = LightXledgerColors.expense,
)

val DarkColorSchemeFallback = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF4FB3D9),
    onPrimary = Color(0xFF0B3B4A),
    primaryContainer = Color(0xFF16303A),
    onPrimaryContainer = Color(0xFFB8E4F5),
    secondary = Color(0xFF9BA1A6),
    onSecondary = Color(0xFF101316),
    background = DarkXledgerColors.background,
    onBackground = DarkXledgerColors.textPrimary,
    surface = DarkXledgerColors.card,
    onSurface = DarkXledgerColors.textPrimary,
    surfaceVariant = Color(0xFF23282D),
    onSurfaceVariant = DarkXledgerColors.textSecondary,
    outline = DarkXledgerColors.cardBorder,
    error = DarkXledgerColors.expense,
)
