package com.rongyu.shixuji.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全 App 动效常量（7.4.0，按《全量交互动画重构方案》§5 / §21.2 收口）。
 *
 * 为什么要有这个文件：7.3.2 之前，同一个「240ms 淡出」在 9 个文件里各写一遍，改一次手感
 * 要 grep 十几处，而且极容易改漏 —— 历史上有过「收起 240 / 展开 320」这种用户一耳朵就听出来的
 * 不一致，就是两处数字分别写死造成的。
 *
 * 这里只放**跨模块共用**的量。单模块的主常量仍留在原文件（TodoScreen 的 SECTION_DURATION_MS、
 * SettingsScreen 的 SUB_PUSH_MS、PeriodSwipe 的 pageTurn）—— 它们是那一处的叙事节奏，
 * 搬进来反而看不出局部关系。
 *
 * 时间六档（§5.1）：
 *   T0 60–100ms   即时确认（按下、抓取、触觉）
 *   T1 140–190ms  微交互（图标状态、轻量颜色、短淡出）
 *   T2 210–290ms  控件交互（开关、分段、端点吸附）
 *   T3 280–380ms  内容交互（同周换日、弹窗、搜索面板）
 *   T4 360–500ms  结构交互（页面推入、列表展开、月份翻页）
 *   T5 600–1000ms 叙事/装饰（首次欢迎、高光一次性扫过）
 *
 * 离场通常比入场短（140–220ms），但不能短到只看到闪一下。
 */
object Motion {

    // ── 时长（毫秒）─────────────────────────────────────────
    const val T0 = 90
    const val T1 = 165
    const val T2 = 250
    const val T3 = 320
    const val T4 = 430
    const val T5 = 800

    // ── 曲线库（§5.2）───────────────────────────────────────
    /** 前半段迅速建立方向、后段柔和落定。面板 / 卡片 / 页面进入。 */
    val PaperEnter: Easing = CubicBezierEasing(0.18f, 0.72f, 0.22f, 1f)

    /** 快速离开但保留可读过程。面板 / 提示退出。 */
    val PaperExit: Easing = CubicBezierEasing(0.40f, 0.00f, 0.80f, 0.20f)

    /** 起止克制、中段推进。展开/收起、标题尺寸。 */
    val PaperInOut: Easing = CubicBezierEasing(0.65f, 0.00f, 0.35f, 1f)

    /** 滑行、逐渐减速。药丸、日期红点、普通导航。 */
    val Glide: Easing = CubicBezierEasing(0.22f, 0.70f, 0.20f, 1f)

    /** 颜色变化平稳，无突然跳色。开关、选中状态。 */
    val ColorSettle: Easing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1f)

    /**
     * 笔尖走纸：起步略快、中段匀速、收笔微减速。勾掉待办时那道划掉线。
     *
     * 为什么不用 [Glide]：Glide 在 1/4 时间就走完 68%（(0.22,0.70) 那组控制点太靠前），
     * 130ms 的线实际 2 帧就划到了头，看不出「在划」。按手感取 0.30/0.18 → 0.30/1.00，
     * 1/4 处约 26%、1/2 处约 56%、3/4 处约 84%，全段都在走。
     */
    val PenSweep: Easing = CubicBezierEasing(0.30f, 0.18f, 0.30f, 1f)

    // ── 弹簧分级（§5.3）─────────────────────────────────────
    /** 不过冲，稳稳落定。弹窗、一般吸附。 */
    val SettleFirm = spring<Float>(dampingRatio = 1.0f, stiffness = 600f)

    /** 轻柔回位。拖动取消、标题吸附。 */
    val SettleSoft = spring<Float>(dampingRatio = 0.96f, stiffness = 420f)

    /** 极短、轻微回弹。按压释放、小开关。 */
    val TouchSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 760f)

    /** 延续手指速度，不越界。带真实速度的手势翻页。 */
    val GestureSpring = spring<Float>(dampingRatio = 0.95f, stiffness = 400f)

    /** 允许微弱回弹、不超过一次。选中的胶囊 / 药丸。 */
    val AccentSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 520f)

    // ── 距离 / 缩放（§7 / §22）──────────────────────────────
    /** 页面级上下位移（弹层、提示条）。 */
    const val LIFT_DP = 10

    /** 按压力度分级：面积越小按得越深，大面积卡片几乎不下陷。 */
    const val PRESS_ICON = 0.94f
    const val PRESS_BUTTON = 0.96f
    const val PRESS_CARD = 0.985f
    const val PRESS_PILL = 0.975f

    /** 拖起时卡片放大（1.8–2.5%），配合投影才是「拿起来」。 */
    const val DRAG_LIFT = 1.022f

    // ── 同级 Tab 串行换页（7.7.0）───────────────────────────
    /**
     * 三页（待办/账目/设置）之间是**同级内容**，切换只该是「认出换了页」。
     * 但同级不等于可以叠页：旧页必须先走完再让新页进来（见 `SequentialTabContent`）。
     * 这里的两个时长是「退场让位」与「入场重建焦点」的分工，不是一次位移的两个阶段。
     *
     * ★ 7.7.2（复核报告 §8.6）：总时长 170+240=410ms → **140+200=340ms**，两段同比例收 17%，
     *   曲线与位移量都没动（所以「干净起步」的逐帧包络只是整体被压紧，观感不换风格）。
     *   再嫌慢只能到 130+180=310ms；再往下就会把退场压到 100ms 出头，玻璃那层开始像硬切。
     */
    const val TAB_EXIT_MS = 140
    const val TAB_ENTER_MS = 200

    /** 位移只交代方向：退场 8dp、入场 12dp。整页推进（屏宽的 1/3、8%）已经废掉。 */
    const val TAB_EXIT_DP = 8
    const val TAB_ENTER_DP = 12

    /** 退场：平稳离开，不要前半段几乎不动、最后突然甩走。 */
    val TabExitEase: Easing = CubicBezierEasing(0.40f, 0.00f, 0.80f, 0.55f)

    /** 入场：柔和启动、逐渐安定，不要一开始就冲到屏幕中央。 */
    val TabEnterEase: Easing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1f)

    // ── 便捷构造 ────────────────────────────────────────────
    /** 类型参数化的柔和落位弹簧（`animateItem` 的 placementSpec 要 FiniteAnimationSpec<IntOffset>，
     *  不能用上面那几个 Float 常量）。 */
    fun <T> settleSoftSpec() = spring<T>(dampingRatio = 0.96f, stiffness = 420f)

    /**
     * 7.4.1：这里四个构造器都过一遍 [MotionDebug.ms]，于是「设置页长按版本 → 慢放 2×」
     * 对所有用 token 写的动画一次生效，不必逐个改数字。
     * 注意 **只对走 `Motion.*` 的调用有效** —— 仍写裸 `tween(240)` 的地方不跟着慢放，
     * 这也是个好的「收口进度」指标：想慢放看细节却动不了的，就是还没接 token 的。
     */
    fun <T> enter(durationMs: Int = T3) = tween<T>(MotionDebug.ms(durationMs), easing = PaperEnter)
    fun <T> exit(durationMs: Int = 180) = tween<T>(MotionDebug.ms(durationMs), easing = PaperExit)
    fun <T> inOut(durationMs: Int = T4) = tween<T>(MotionDebug.ms(durationMs), easing = PaperInOut)
    fun <T> glide(durationMs: Int = T2) = tween<T>(MotionDebug.ms(durationMs), easing = Glide)
    fun <T> color(durationMs: Int = 215) = tween<T>(MotionDebug.ms(durationMs), easing = ColorSettle)

    /** 延后入场的 PaperEnter（「先出后进」专用：旧内容先撤，新内容再抬进来）。 */
    fun <T> enterDelayed(durationMs: Int = T3, delayMs: Int) =
        tween<T>(MotionDebug.ms(durationMs), delayMillis = delayMs, easing = PaperEnter)

    // ── 同级 Tab 串行换页（7.7.0）───────────────────────────
    /** 旧页退场进度 1→0（alpha 一起降到 0）。 */
    fun tabExit() = tween<Float>(MotionDebug.ms(TAB_EXIT_MS), easing = TabExitEase)

    /** 新页入场进度 0→1。 */
    fun tabEnter() = tween<Float>(MotionDebug.ms(TAB_ENTER_MS), easing = TabEnterEase)
}
