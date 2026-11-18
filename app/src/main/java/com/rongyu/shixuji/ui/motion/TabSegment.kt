package com.rongyu.shixuji.ui.motion

/**
 * 一次换页所处的阶段。物理上只有三种状态：静止 / 旧页让位 / 新页入场。
 */
internal enum class TabPhase { Idle, Exiting, Entering }

/**
 * 一段换页动效的**起点快照** + 本段目标（7.7.1）。
 *
 * ### 为什么不能只要「phase + 一条 0↔1 进度」
 * 7.7.0 的位移/缩放是两个**固定锚点**之间的映射：
 *   · 退场：(tx = 0, s = 1) → (tx = −8dp, s = 0.996)
 *   · 入场：(tx = +12dp, s = 0.994) → (0, 1)
 * 不被打算断时画面完全符合方案。但在途中点别的页时，[TabPhase] 一变，**同一时刻的
 * progress 会被另一套锚点解释** —— 位移在切换那一帧直接跳：
 *
 * | 场景 | 切换点 p | 跳变 |
 * |---|---|---|
 * | 入场途中点别的页（连点，最常见） | 0.15 | 17.00dp = 44.6px |
 * | 〃 | 0.50 | 10.00dp = 26.2px |
 * | 退场途中点回原页（折返） | 0.20 | 16.00dp = 42.0px |
 * | 〃 | 0.60 | 8.00dp = 21.0px |
 *
 * 那就是用户看到的「顿一下」。（外部复核报告 §8.3，原文给的三种修法里第 3/4 种）
 *
 * ### 现在的做法：每段都从「画面此刻真实的值」出发
 * [fromTx] / [fromScale] 是开段那一刻**屏幕上正在用的**位移与缩放（不是方案里的理想初值），
 * 本段只往自己的目标插值（[targetTx] / [targetScale]），推进比例见 [fraction]。
 *
 * · **干净起步**时，调用方给的 fromTx/fromScale 恰好等于方案初值（退场 0/1、入场
 *   +12dp/0.994），所以单看一次完整的切换，观感与 7.7.0 **逐像素一致**（见
 *   `TabSegmentTest.cleanExitMatchesSpec` / `cleanEnterMatchesSpec`）；
 * · **被打断**时起点继承当前值 → 映射天然连续，跳变恒为 0。
 *
 * 只读数据 + 纯函数，所以能直接在 JVM 上单测（`app/src/test/.../ui/motion/TabSegmentTest.kt`）。
 */
internal data class TabSegment(
    val phase: TabPhase,
    /** 开段那一刻的进度（0 = 完全不可见，1 = 完全可见） */
    val fromProgress: Float,
    /** 开段那一刻屏幕上的横向位移（px，正 = 向右） */
    val fromTx: Float,
    /** 开段那一刻屏幕上的缩放 */
    val fromScale: Float,
    /** 1 = 新页在右侧；-1 = 在左侧。由底栏顺序算出，不写死枚举下标。 */
    val dir: Int,
    val exitPx: Float,
    val enterPx: Float,
) {
    /** 本段终点的横向位移：退场让到反方向，入场回到 0。 */
    val targetTx: Float
        get() = when (phase) {
            TabPhase.Exiting -> -dir * exitPx
            TabPhase.Entering, TabPhase.Idle -> 0f
        }

    /** 本段终点的缩放：退场收到最紧，入场回到原大。 */
    val targetScale: Float
        get() = when (phase) {
            TabPhase.Exiting -> EXIT_SCALE
            TabPhase.Entering, TabPhase.Idle -> 1f
        }

    /**
     * 本段的推进比例：起点 0、终点 1。
     * 退场是 progress 从 [fromProgress] 降到 0，入场是从 [fromProgress] 升到 1 ——
     * 两个方向的 span 不同，所以分别归一化。
     */
    fun fraction(progress: Float): Float {
        if (phase == TabPhase.Idle) return 1f
        val span = if (phase == TabPhase.Exiting) fromProgress else 1f - fromProgress
        // 退化段（起点就压在终点上，例如 alpha 已经是 0 还在退场）：不做插值，直接给终值，
        // 免得除零 Inf/NaN 传到 graphicsLayer 上（Skia 收到 NaN 会整层不画）。
        if (span <= EPS) return 1f
        val f = if (phase == TabPhase.Exiting) {
            (fromProgress - progress) / span
        } else {
            (progress - fromProgress) / span
        }
        return f.coerceIn(0f, 1f)
    }

    fun txAt(progress: Float): Float {
        if (phase == TabPhase.Idle) return 0f
        val f = fraction(progress)
        return fromTx + (targetTx - fromTx) * f
    }

    fun scaleAt(progress: Float): Float {
        if (phase == TabPhase.Idle) return 1f
        val f = fraction(progress)
        return fromScale + (targetScale - fromScale) * f
    }

    companion object {
        /** 退场末端的收束：缩 0.4%，肉眼只当「纸被推远一点」。 */
        const val EXIT_SCALE = 0.996f

        /** 入场起点：比退场再深一点，形成「新纸从更远处来」的层次。 */
        const val ENTER_SCALE = 0.994f

        private const val EPS = 1e-4f

        /** 静止：无位移、无缩放、完全可见。 */
        fun idle(dir: Int = 1, exitPx: Float = 0f, enterPx: Float = 0f) =
            TabSegment(TabPhase.Idle, 1f, 0f, 1f, dir, exitPx, enterPx)

        /** 干净入场：从方案初值 +12dp / 0.994 起。 */
        fun enter(dir: Int, exitPx: Float, enterPx: Float) =
            TabSegment(TabPhase.Entering, 0f, dir * enterPx, ENTER_SCALE, dir, exitPx, enterPx)
    }
}
