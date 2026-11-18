package com.rongyu.shixuji.ui

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug

/**
 * 「一条从列表里离场」的全部时长与曲线 —— **全应用唯一一份**。
 *
 * 2026-11-18 用户要求把待办页勾掉的那套效果同步到记账页（「划掉那道线可以保留，按钮不用变红勾」）。
 * 同步的前提是同一个数：只要这两处各写一套 `tween(...)`，就一定会各自调参、早晚不一样
 * （8.5.8 之前「过往未完成」那一节就是这么偏掉的）。所以时长挪到这里，
 * 待办页与记账页都调 [rememberExitSpecs] 取同一个对象。
 *
 * 三条曲线之间的关系（逐帧验证过，别改成一条）：
 *  - [strike]：划掉那道线，240ms 从左扫到右，PenSweep（像人一笔划过去）。
 *  - [fade]：整行不透明度。先原地站住 120ms 让人看清线在走，再用 200ms 化掉（PaperInOut）。
 *  - [height]：高度。定住 240ms（等线划满），再用剩下的 200ms 收到 0（Glide）。
 *
 * 为什么要拆成三条：
 *  ① 只用一条时，下面那张卡的上边缘会从下往上扫过离场卡的脸 —— 用户原话
 *    「好像下面的把上面的吃掉了一样」（8.5.2）；淡出必须先完成。
 *  ② 淡出与划掉同起同落时，线还没划到一半字就淡没了（8.5.6，实测只扫到 60%），
 *    用户说「闪了一下就没了」；所以淡出要站住（8.5.7）。
 */

/** 「完成/删除一条」的整条时长：线划满 → 化掉 → 位置合上。 */
internal const val ROW_EXIT_MS = 440

/** 淡出总时长（含 [ROW_FADE_HOLD_MS] 的站住）。 */
internal const val ROW_EXIT_FADE_MS = 320

/** 淡出前先「原地站住」这么久，让线先划过去。0 = 淡出与划线同起同落（8.5.6 那版，会看不清划线）。 */
internal const val ROW_FADE_HOLD_MS = 120

/** 「划掉」那道线从无到满的时长。 */
internal const val ROW_STRIKE_MS = 240

/** 高度定住不动、等线划满的时长；之后到 [ROW_EXIT_MS] 为止收高度。 */
internal const val ROW_EXIT_HOLD_MS = 240

/**
 * 撤销删除时，「散掉的那条重新长回来」的时长（2026-11-18 用户要求）：
 * 行从 0 高度长回自然高度，下面的行被真实高度**一点点**压下去，不再一帧沉底。
 */
internal const val ROW_RESTORE_MS = 440

/**
 * 撤销删除时，「这一行自己」（描边 / 顶部受光 / 文字）从透明到实的那一段 ——
 * **纸背不在里面**：外壳（[RestoreRise]）一直铺着一层与卡片同色的不透明纸，所以
 * 半透明的那几帧只是「同色叠同色」，绝不会像以前那样把白纸混成米色（用户原话：
 * 「明明你可以只扣白色的地方，非要带上背景」）。
 *
 * 也正因为纸背常驻，这段时间可以放心地留白：卡片先长到 74% 高（一行字完整装得下的高度）
 * 再让字显形，中途就不会出现「字被横着切掉一半」。
 */
internal const val ROW_RESTORE_FADE_MS = 160

/** 离场的三条时钟 + 恢复的两条。由 [rememberExitSpecs] 一次性给出。 */
@Immutable
internal class ExitSpecs(
    val height: AnimationSpec<Float>,
    val fade: AnimationSpec<Float>,
    val strike: AnimationSpec<Float>,
    /** 撤销删除：高度 0→自然高度（推着下面的行往下走）。 */
    val restore: AnimationSpec<Float>,
    /** 撤销删除：透明度 0→1。 */
    val restoreFade: AnimationSpec<Float>,
)

/**
 * 取离场的三条时钟。系统「动画时长缩放 = 0」（或开发者选项关动画）时全部退化成 snap，
 * 不要在这里自作主张加最短时长 —— 那是无障碍设置，尊重它。
 */
@Composable
internal fun rememberExitSpecs(): ExitSpecs {
    val context = LocalContext.current
    val animationsOff = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    return remember(animationsOff) {
        ExitSpecs(
            height = if (animationsOff) snap() else keyframes {
                durationMillis = MotionDebug.ms(ROW_EXIT_MS)
                1f at 0
                1f at MotionDebug.ms(ROW_EXIT_HOLD_MS)
                0f at MotionDebug.ms(ROW_EXIT_MS) using Motion.Glide
            },
            fade = if (animationsOff) snap()
            else if (ROW_FADE_HOLD_MS <= 0) {
                tween(MotionDebug.ms(ROW_EXIT_FADE_MS), easing = Motion.PaperInOut)
            } else keyframes {
                durationMillis = MotionDebug.ms(ROW_EXIT_FADE_MS)
                1f at 0
                1f at MotionDebug.ms(ROW_FADE_HOLD_MS)
                0f at MotionDebug.ms(ROW_EXIT_FADE_MS) using Motion.PaperInOut
            },
            strike = if (animationsOff) snap()
            else tween(MotionDebug.ms(ROW_STRIKE_MS), easing = Motion.PenSweep),
            restore = if (animationsOff) snap()
            else tween(MotionDebug.ms(ROW_RESTORE_MS), easing = Motion.PaperInOut),
            restoreFade = if (animationsOff) snap() else keyframes {
                durationMillis = MotionDebug.ms(ROW_RESTORE_MS)
                0f at 0
                0f at MotionDebug.ms(ROW_RESTORE_MS - ROW_RESTORE_FADE_MS)
                1f at MotionDebug.ms(ROW_RESTORE_MS) using Motion.PaperInOut
            },
        )
    }
}
