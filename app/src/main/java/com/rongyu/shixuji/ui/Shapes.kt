package com.rongyu.shixuji.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 卡片/条目的圆角集中在这里。
 *
 * 历史：6.9.1 曾把 22 处圆角散着写死（18/14/20/22/24/28/30dp），换形状时到处漏改，
 * 还引出过胶囊碎裂和描边外溢两个回归。现在统一走 [cardShape]，半径只从这里取。
 *
 * 当前采用「方案 A」：正圆弧（RoundedCornerShape），条目 20dp、大卡 26dp。
 * 6.9.1–6.9.2 试过的连续曲率超椭圆已停用 —— 同半径下超椭圆在 45° 方向比正圆弧
 * 更靠外、更接近直角，条目看着偏方偏硬。Squircle.kt 保留下来（Glass.kt 的形状
 * 分派仍依赖它），但不再作为卡片的默认形状。
 */
object CardRadius {
    /** 列表条目：待办卡、账目行、日历格 —— 最常用的一档 */
    val ROW: Dp = 20.dp

    /** 内层小卡：设置页分组里面的子块（比条目小 4dp，视觉上收进去一层） */
    val ROW_TIGHT: Dp = 16.dp

    /** 分组卡：设置页大分组、批量整理页 */
    val SECTION: Dp = 20.dp

    /** 搜索面板 */
    val SEARCH: Dp = 24.dp

    /** 账目页大卡（内部小格由它减去 HERO_PAD 得出，保证同心） */
    val HERO: Dp = 26.dp

    /** 操作胶囊：固定记账、整理页底部三颗 */
    val ACTION: Dp = 22.dp

    /** 饼图图例行的点击剪裁（不是卡片，只为水波纹有圆角；跟着整体加大的幅度走） */
    val CELL: Dp = 12.dp

    /** 启动页「开启时序集」按钮。半径 ≥ 半高，实际渲染为完整胶囊 */
    val SPLASH_BTN: Dp = 28.dp
}

/** 全 App 统一的圆角入口。
 *
 * 2026-11-12：条目/大卡回到正圆弧（用户：「条目形状不需要改」）。
 * 超椭圆 G3 只保留在**小控件**上（输入框、金额显示框、时段高亮带、调色方块、数字键），
 * 见 [smoothShape]；那些填充一律用 [aaFill] 自绘，避免 clip 的二值裁剪把曲线切成阶梯。
 */
fun cardShape(radius: Dp): Shape = RoundedCornerShape(radius)

/** 小控件用的 G3 平滑量：0.6（用户 7.6.14 定的那一档，只是当时错用在了条目上）。 */
const val G3_SMOOTHING = 0.6f
