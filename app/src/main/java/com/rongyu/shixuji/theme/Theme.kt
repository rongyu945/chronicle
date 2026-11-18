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

// 设计稿主色（2026-11 改为「中国风传统色」：色名取《中国风传统色卡》）
val Ink = Color(0xFF392F41)         // 乌黑：正文黑，比纯黑偏一点暖紫，配宋体像墨
/*
 * 苍色：次要文字。
 * 0xFF75878A → 0xFF5F7075（2026-11-08 审计 I 项）：旧值在宣纸底（#F7F1E6）上只有 3.33:1，
 * 13sp 的副标题/时间/备注这类小字看着发虚。新值实测 4.59:1 —— 过 WCAG AA 的 4.5:1 线，
 * 色相没动（还是那支青灰），只是压深了两档。
 */
val Gray = Color(0xFF5F7075)
/** 苍色·深：小字交互标签（收起 ▴ / 展开 ▾）用。浅色下 Gray 只有 4.6:1，这类提示字再压一档 */
val GrayStrong = Color(0xFF4C5A5E)
val Line = Color(0xFFE6DCCC)        // 缟（加深）：宣纸感的分隔线，冷灰线在暖底上会发脏
val BgLight = Color(0xFFF7F1E6)     // 宣纸：界面底色（Glass.kt 画底也用它）
val Accent = Color(0xFFED5736)      // 主题红 = 妃色
/** 弹窗「保存」这类实心主按钮的颜色 = 枣红（比主题的妃色深一档，压在奶白玻璃上更沉） */
val AccentDeep = Color(0xFFC32136)
val AccentSoft = Color(0xFFF8DCD2)  // 选中药丸底色 = 妃色淡化（实色保证可读）
val AccentDark = Color(0xFFF05654)  // 强调红（深色）= 银红，提亮不刺眼
val Blue = Color(0xFF177CB0)        // 靛青
/** 靛青（深色）：深色底上 #177CB0 只有 3.05:1，提亮到 4.6:1 —— 与 AccentDark/GreenDark 同一套做法 */
val BlueDark = Color(0xFF4C9BC8)
val Purple = Color(0xFF8D4BBB)      // 紫色
val Green = Color(0xFF057748)       // 收入绿（浅色）= 松花绿（松柏绿偏浅，金额对比度只有 2.9）
val GreenDark = Color(0xFF2EDFA3)   // 收入绿（深色）= 玉色
val Teal = Color(0xFF549688)        // 铜绿
val Orange = Color(0xFFFFA631)      // 杏黄

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
    // 未选中描边（勾选框 / 开关 / 单选圈 / 芯片）：原「白色 72%」在浅色卡上看不见，
    // 用户 2026-10-16 要求改成与主题淡红相近 —— 同色系、但比主线红淡，仍是"框"而不是"块"
    outline = Accent.copy(alpha = 0.42f),
    outlineVariant = Color(0x1F000000),
    error = Accent,
    scrim = Color(0x66000000)
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2226),      // 胭脂·深
    onPrimaryContainer = Color(0xFFF8DCD2),
    secondary = Color(0xFF7397AB),             // 苍青
    onSecondary = Color(0xFF17212B),
    background = Color.Transparent,
    onBackground = Color.White,
    surface = Color.White.copy(alpha = 0.10f),
    onSurface = Color.White,
    surfaceVariant = Color.White.copy(alpha = 0.07f),
    onSurfaceVariant = Color(0xFFC2CCD0),      // 花白
    surfaceContainerLowest = Color.White.copy(alpha = 0.06f),
    surfaceContainerLow = Color.White.copy(alpha = 0.08f),
    surfaceContainer = Color.White.copy(alpha = 0.11f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.16f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.20f),
    outline = AccentDark.copy(alpha = 0.55f),   // 深色：同上，改成亮红描边
    outlineVariant = Color(0x33FFFFFF),
    error = AccentDark,
    scrim = Color(0x99000000)
)

/** 全局字体：打包的 Songti SC（中文宋体 + 西文衬线）。西文/数字已在字体里下移过，与中文贴齐 */
val songtiFamily = FontFamily(Font(R.font.songti_sc))

/**
 * 等宽数字。宋体里的数字本身是比例宽（1 比 8 窄），金额一变动整行就会左右抖；
 * 打开 tnum 让每个数字占同样宽度，翻页/记一笔时数字「原地变化」而不是整段位移。
 */
val tabularNums = TextStyle(fontFeatureSettings = "tnum")

private fun songtiTypography(): Typography = Typography(
    // 2026-11-05 排版：大字号配负字距（-0.02em 量级）—— 宋体在大字号下字距会显得松散，
    // 收一点才紧凑上报、有"标题感"；小字号不动，收了会挤。
    displaySmall = TextStyle(fontFamily = songtiFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = songtiFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = songtiFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
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
