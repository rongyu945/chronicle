package com.rongyu.shixuji.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 胶囊形液态玻璃的统一配方（2026-11-10，7.2.2 起）。
 *
 * 样板是整理页底部那三颗（改日期 / 优先级 / 删除）。之前每个胶囊各写一套参数，
 * 尺寸一变就各长各的样 —— 这里把「跟尺寸无关的观感」和「跟尺寸有关的折射」分开：
 *
 * - 折射带 = 位移 = **高度的 1/4**（56dp 的胶囊 ⇒ 14dp）。再大边缘就被拉成果冻。
 * - 模糊 = **高度的 0.4**，封顶 22dp、下限 6dp。小胶囊给 22dp 就糊成一团奶白，
 *   大胶囊给少了又透出背后的字。
 * - `depthEffect = false`：胶囊本来就该是平的玻璃片，再叠整块位移会往同一方向推两次，
 *   边缘出月牙。
 * - 体色 = 白 0.14（深）/ 0.20（浅）；边缘环境光 0.10、饱和度 1.08 —— 这三项与尺寸无关，
 *   是所有胶囊共用的「同一块介质」的观感来源。
 *
 * ⚠️ `shape` 必须是 shader 能识别的圆角矩形（见 skill：`CapsuleShape` 这种自定义 Outline
 * 会被 AGSL 退回成直角）。要胶囊就把半径给到半高。
 *
 * 体色 / 描边 / 高光都留了覆盖口子：底栏、撤回提示条这类各自调校过的地方，可以只借用
 * 「折射与模糊跟尺寸成比例」这条关系，其余照旧。
 */
@Composable
fun Modifier.liquidCapsule(
    backdrop: LiquidGlassBackdropState,
    shape: Shape,
    height: Dp,
    dark: Boolean,
    tint: Color? = null,
    tintBottom: Color? = null,
    rim: GlassRim = GlassRim.LEGACY,
    rimAlpha: Float = -1f,
    highlightAlpha: Float = 0f,
    highlightWidth: Dp = 0.dp,
    highlightColor: Color = Color.White,
    highlightFalloff: Float = 1f,
    strokeInset: Dp = 0.dp,
    depthEffect: Boolean = false,
): Modifier = liquidGlass(
    backdrop = backdrop,
    shape = shape,
    refractionHeight = height * 0.25f,
    refractionAmount = height * 0.25f,
    blurRadius = (height * 0.40f).coerceIn(6.dp, 22.dp),
    depthEffect = depthEffect,
    tint = tint ?: Color.White.copy(alpha = if (dark) 0.14f else 0.20f),
    tintBottom = tintBottom,
    rimLight = 0.10f,
    saturation = 1.08f,
    rim = rim,
    rimAlpha = rimAlpha,
    highlightAlpha = highlightAlpha,
    highlightWidth = highlightWidth,
    highlightColor = highlightColor,
    highlightFalloff = highlightFalloff,
    strokeInset = strokeInset,
)
