package com.rongyu.shixuji.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.R

// 设计稿主色（iOS 浅色风）
val Ink = Color(0xFF1C1C1E)
val Gray = Color(0xFF8E8E93)
val Line = Color(0xFFE5E5EA)
val BgLight = Color(0xFFF2F2F7)
val Accent = Color(0xFFFF3B30)      // 主题红（浅色）
val AccentSoft = Color(0xFFFFD9D4)  // 选中药丸底色（浅色，实色保证可读）
val AccentDark = Color(0xFFFF6B61)  // 强调红（深色，提亮不刺眼）
val Blue = Color(0xFF0A84FF)
val Purple = Color(0xFFAF52DE)
val Green = Color(0xFF34C759)       // 收入绿（浅色）
val GreenDark = Color(0xFF3ED164)   // 收入绿（深色，提亮保证对比度）
val Teal = Color(0xFF30B0C7)
val Orange = Color(0xFFFF9F0A)

/*
 * 液态玻璃配色：surface 全部是「半透明白」，底下的柔光色斑（ColorBlobsBackground）
 * 透上来就是玻璃感。background 透明，界面自己画底色。
 * 文字颜色一律保持不透明，保证对比度。
 */
private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    secondary = Blue,
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Ink,
    surface = Color.White.copy(alpha = 0.28f),
    onSurface = Ink,
    surfaceVariant = Color.White.copy(alpha = 0.34f),
    onSurfaceVariant = Gray,
    surfaceContainerLowest = Color.White.copy(alpha = 0.42f),
    surfaceContainerLow = Color.White.copy(alpha = 0.50f),
    surfaceContainer = Color.White.copy(alpha = 0.58f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.72f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.82f),
    outline = Color.White.copy(alpha = 0.72f),
    outlineVariant = Color(0x1F000000),
    error = Accent,
    scrim = Color(0x66000000)
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2622),
    onPrimaryContainer = Color(0xFFFFD9D6),
    secondary = Color(0xFF4DA6FF),
    onSecondary = Color(0xFF0A2540),
    background = Color.Transparent,
    onBackground = Color.White,
    surface = Color.White.copy(alpha = 0.10f),
    onSurface = Color.White,
    surfaceVariant = Color.White.copy(alpha = 0.07f),
    onSurfaceVariant = Color(0xFFC7C7CC),
    surfaceContainerLowest = Color.White.copy(alpha = 0.06f),
    surfaceContainerLow = Color.White.copy(alpha = 0.08f),
    surfaceContainer = Color.White.copy(alpha = 0.11f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.16f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.20f),
    outline = Color.White.copy(alpha = 0.20f),
    outlineVariant = Color(0x33FFFFFF),
    error = AccentDark,
    scrim = Color(0x99000000)
)

/** 全局字体：打包的 Songti SC（中文宋体 + 西文衬线）。西文/数字已在字体里下移过，与中文贴齐 */
val songtiFamily = FontFamily(Font(R.font.songti_sc))

private fun songtiTypography(): Typography = Typography(
    displaySmall = TextStyle(fontFamily = songtiFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = songtiFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = songtiFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = songtiFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = songtiFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = songtiFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = songtiFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = songtiFamily, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = songtiFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = songtiFamily, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = songtiFamily, fontSize = 11.sp)
)

/** 收入绿：随明暗自动切换（深色下用更亮的绿）。用 onBackground 判明暗，background 现在是透明的 */
val incomeGreen: Color
    @Composable get() = if (MaterialTheme.colorScheme.onBackground.luminance() > 0.5f) GreenDark else Green

/** 当前是否深色主题 */
val isDarkTheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.onBackground.luminance() > 0.5f

@Composable
fun ShixuJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认就用打包的宋体（用户要求）：不传就是宋体，只有设置里明确关掉才退回系统字体
    useSongti: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = if (useSongti) songtiTypography() else Typography(),
        content = content
    )
}
