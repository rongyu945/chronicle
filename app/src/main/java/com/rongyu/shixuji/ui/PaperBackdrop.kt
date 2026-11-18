package com.rongyu.shixuji.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 「纸底」采样源 —— 只录了 [ColorBlobsBackground]（宣纸底 + 三团柔光色斑），
 * **不含任何页面内容**。
 *
 * 为什么需要单独一层，而不是复用 MainScreen 里那个 `backdrop`：
 * 周条（`SmoothWeekStrip`）本身就在 `recordBackdrop(backdrop)` 那棵子树里，
 * 拿 `backdrop` 当采样源 = 采到自己 → 录制递归，底栏当年踩过同一个坑
 * （见 MainScreen 里「底栏必须写在挂了 recordBackdrop 的那层外面」那条注释）。
 *
 * 而周条身后**永远只有纸底**：它的上边是表头、下边是日列表，都不跟它重叠，
 * 列表滚动也不会钻到它下面。所以「纸底」就是它唯一该透出来的东西 ——
 * 录一层纸底既够用，也最省（这层没有内容变化，GPU 每帧只是重拷一遍）。
 *
 * 没有提供时（预览、单测、其它宿主），周条自动退化成静态玻璃
 * （`glassSurface`），不会崩、也不会画出透明的空壳。
 */
val LocalPaperBackdrop = compositionLocalOf<LiquidGlassBackdropState?> { null }
