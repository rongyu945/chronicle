package com.rongyu.shixuji.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * 「液态玻璃滑块」的统一画法（8.0.0）。
 *
 * 背景：App 里有四处滑块/药丸 —— 账目与待办的分段控件、问候时段的三段选择器、
 * 时间尺的两颗端钮、底栏指示块。它们各自长得很像，但**成色不齐**：
 * 分段控件是六层精修（落影三阶 / 体 / 顶缘柔光 / 底缘内阴影 / 速度流光 / 0.6dp 收边），
 * 问候时段那颗是「渐变 + 1dp 描边 + 一层染色」，看起来像另一个 App 里的东西。
 * 用户 2026-11-12：「我希望你的这几个液态玻璃的滑块既有共同点，又都各自有特色」。
 *
 * 所以把**材质**（common）抽到这里，谁调都一样：
 *   · 胶囊形（半径恒 = 高/2，不用 CapsuleShape —— 那是另一条红线，AGSL 会退回直角）；
 *   · 三阶落影（只为了「浮起 1mm」）；
 *   · 体 = 竖向白玻璃，浅色 纯白→暖白、深色 白 26%→15%；
 *   · 顶缘柔光是一整块渐变（不是一条线）；
 *   · 底缘内阴影从 65% 才开始，越浅越好；
 *   · 一圈 0.6dp 的极淡收边（不是黑边）；
 *   · 可选：染色（tint + 染色细边）、跟手流光（velocity）。
 *
 * 而**个性**（signature）留在各自的调用点上 —— 四处四样，互不重复：
 *
 *   ① 分段控件（账目 / 待办的「收支·支出·收入」等）——「**跟手流光**」
 *      `streak = true`：跑起来那道斜光被甩在身后、停下自己归中；玻璃体常驻不透明。
 *   ② 问候时段的三段选择器 ——「**整条槽随段色**」
 *      滑块 `tint = 段色`，并且整条凹槽连描边一起淡淡染上该段色；槽、滑块、圆点同色系，
 *      换段时三者一起过渡。别处那条槽永远是中性灰白，一眼可辨。
 *   ③ 时间尺的两颗端钮 ——「**抓住光晕**」
 *      走 drawGlassKnob；捏住时放大 10%，身下亮起一圈径向同色辉光（不是硬边环），
 *      同时弹出 13:10 这样的读数气泡；放手光散。
 *   ④ 底栏指示块 ——「**折射透镜**」
 *      走 AGSL liquidGlass（lens 折射 + 按压力度驱动的高光/内阴影），底下的图标和字
 *      会被真的折射变形，未按时几乎看不出是块玻璃 —— 四处里最"液态"的一个。
 *      因为它要 shader、不是 Canvas 能画的，所以**故意不走本文件**，只共用同一套配色与光位。
 */

/**
 * 画一块玻璃胶囊。
 *
 * @param x      左边缘（px）
 * @param w      宽度（px）
 * @param h      高度（px）
 * @param dark   深色主题
 * @param tint   染色（null = 白玻璃原色）
 * @param tintAlpha 染色层不透明度；分段控件 0.24/0.34，问候时段 0.44/0.42
 * @param velocity 当前速度（px/s），给流光用；0 表示静止
 * @param streak 是否画「跟手流光」（分段控件的签名）
 */
internal fun DrawScope.drawGlassPill(
    x: Float,
    w: Float,
    h: Float,
    dark: Boolean,
    tint: Color? = null,
    tintAlpha: Float = if (dark) 0.34f else 0.24f,
    velocity: Float = 0f,
    streak: Boolean = true,
) {
    if (w <= 0f || h <= 0f) return
    val cy = h / 2f
    val cr = CornerRadius(cy, cy)

    // ① 落影：三层递进，只为了「浮起 1mm」
    if (!dark) {
        listOf(0.06f, 0.04f, 0.022f).forEachIndexed { k, al ->
            drawRoundRect(
                color = Color.Black.copy(alpha = al),
                topLeft = Offset(x, (1.0f + k * 0.9f).dp.toPx()),
                size = Size(w, h),
                cornerRadius = cr
            )
        }
    } else {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(x, 1.2.dp.toPx()),
            size = Size(w, h),
            cornerRadius = cr
        )
    }

    // ② 体：上亮下柔。白底必须带一点暖灰收尾，否则高光叠上去等于没变化。
    drawRoundRect(
        brush = if (dark)
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.15f)))
        else
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF7F3EC))),
        topLeft = Offset(x, 0f),
        size = Size(w, h),
        cornerRadius = cr
    )

    // ②b 染色层：白玻璃体上掺一层色调（不改成实色，文字仍是墨色）
    //     深色下先把色调压暗再掺，否则亮色（如深色模式的收入绿）会把药丸顶起来，
    //     近白的字压上去只剩 3.4:1。
    val ink = tint?.let { if (dark) androidx.compose.ui.graphics.lerp(it, Color.Black, 0.38f) else it }
    if (ink != null) {
        drawRoundRect(
            color = ink.copy(alpha = tintAlpha),
            topLeft = Offset(x, 0f),
            size = Size(w, h),
            cornerRadius = cr
        )
    }

    // ③ 顶缘柔光：一整块渐变，光从上方落下来铺满上半身
    if (!dark) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                startY = 0f, endY = h * 0.55f
            ),
            topLeft = Offset(x, 0f),
            size = Size(w, h),
            cornerRadius = cr
        )
    }

    // ④ 底缘内阴影：从 65% 才开始，只在最后一小段压深
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.14f else 0.055f)),
            startY = h * 0.65f,
            endY = h
        ),
        topLeft = Offset(x, 0f),
        size = Size(w, h),
        cornerRadius = cr
    )

    // ⑤ 跟手流光：跑起来光被甩在身后，停下归中
    if (streak) {
        val v = (velocity / 6000f).coerceIn(-1f, 1f)
        if (abs(v) > 0.02f) {
            val cx = x + w * (0.5f - v * 0.32f)
            val bandW = w * 0.72f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (if (dark) 0.12f else 0.45f) * (0.35f + 0.65f * abs(v))),
                        Color.Transparent
                    ),
                    start = Offset(cx - bandW / 2f, 0f),
                    end = Offset(cx + bandW / 2f, h)
                ),
                topLeft = Offset(x, 0f),
                size = Size(w, h),
                cornerRadius = cr
            )
        }
    }

    // ⑤c 染色细边：告诉眼睛「这块玻璃是染过色的」，也把两条槽的边界分清
    if (ink != null) {
        drawRoundRect(
            color = ink.copy(alpha = if (dark) 0.42f else 0.30f),
            topLeft = Offset(x, 0f),
            size = Size(w, h),
            cornerRadius = cr,
            style = Stroke(1.dp.toPx())
        )
    }

    // ⑥ 轮廓：只留一道几乎看不见的暖灰细线（不是黑边）
    val inset = 0.3.dp.toPx()
    drawRoundRect(
        color = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF8A7F6E).copy(alpha = 0.07f),
        topLeft = Offset(x + inset, inset),
        size = Size((w - inset * 2f).coerceAtLeast(1f), h - inset * 2f),
        cornerRadius = CornerRadius(cy - inset, cy - inset),
        style = Stroke(0.6.dp.toPx())
    )
}
