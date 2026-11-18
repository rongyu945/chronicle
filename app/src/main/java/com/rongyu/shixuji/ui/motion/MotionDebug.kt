package com.rongyu.shixuji.ui.motion

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 动效调试开关（7.4.1，方案 P0-6「统一 debug slow-motion 开关与动画 session 日志」+ P3-6 覆盖层）。
 *
 * 为什么必须有：调手感时最常问的两句是「这段到底多长」和「它是不是太慢/太快」。
 * 前者靠日志，后者只能靠**慢放后逐帧看**——原速下 240ms 只有 14 帧，肉眼分不出
 * 「先动后停」和「匀速」。以前的土办法是临时把 tween 里那个数字改成 2400、
 * 看完再逐个改回来（改漏一次就会把慢放打进正式包，历史上真发生过）。
 *
 * 现在统一在 [Motion] 里乘倍率：`Motion.enter(240)` 实际时长 = 240 × [slow]。
 * 入口是设置页「版本」那一行的长按（见 SettingsScreen），不占界面。
 *
 * 值只落在 SharedPreferences（`motion_debug`），进程重启后保持，方便「杀掉重开继续看」。
 */
object MotionDebug {

    private const val TAG = "MotionTrace"
    private const val PREF = "motion_debug"

    private var prefs: SharedPreferences? = null

    /** 慢放倍率，1 = 正常。调参看 2×，看极端手感看 4×，核对「是不是本来就慢」用 0.5×。 */
    var slow by mutableFloatStateOf(1f)
        private set

    /** 是否把每个动效事件打到 logcat（tag = MotionTrace）并进覆盖层。 */
    var logging by mutableStateOf(false)
        private set

    /** 是否显示覆盖层（仅 logging 打开后可用：没有日志的覆盖层没有意义）。 */
    var overlay by mutableStateOf(false)
        private set

    /** 最近事件，覆盖层直接读（最多 8 条，新的在下）。 */
    val events = mutableStateListOf<String>()

    /** 是否处于任何非默认状态 —— 设置页用它决定要不要在版本号后面挂标记。 */
    fun active(): Boolean = slow != 1f || logging

    fun attach(context: Context) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        slow = p.getFloat("slow", 1f).coerceIn(0.25f, 4f)
        logging = p.getBoolean("logging", false)
        overlay = p.getBoolean("overlay", logging)
    }

    /** 注意：不能叫 `setSlow` —— `var slow` 的 setter 生成的就是 setSlow(F)V，会直接撞签名。 */
    fun applySlow(v: Float) {
        slow = v.coerceIn(0.25f, 4f)
        prefs?.edit()?.putFloat("slow", slow)?.apply()
        log("debug", "慢放 ${slow}×")
    }

    /** 设置页长按「版本」轮流切换：1 → 2 → 4 → 1。 */
    fun cycleSlow(): Float {
        val next = when {
            slow >= 4f -> 1f
            slow >= 2f -> 4f
            slow > 1f -> 2f
            else -> 2f
        }
        applySlow(next)
        if (next == 1f) {
            applyLogging(false)
        } else if (!logging) {
            applyLogging(true)
            overlay = true
            prefs?.edit()?.putBoolean("overlay", true)?.apply()
        }
        return slow
    }

    /** 同上：`var logging` 的 setter 已占用 setLogging(Z)V。 */
    fun applyLogging(on: Boolean) {
        logging = on
        if (!on) overlay = false
        prefs?.edit()?.putBoolean("logging", logging)?.putBoolean("overlay", overlay)?.apply()
        if (on) log("debug", "开始记录")
    }

    fun toggleOverlay() {
        overlay = !overlay
        prefs?.edit()?.putBoolean("overlay", overlay)?.apply()
    }

    /** 把慢放倍率乘进时长。所有经 [Motion] 造出来的 spec 都过这里。 */
    fun ms(base: Int): Int = (base * slow).toInt().coerceAtLeast(1)

    fun log(tag: String, msg: String) {
        if (!logging) return
        val sec = (SystemClock.uptimeMillis() % 1_000_000L) / 1000f
        val line = "%7.3fs  [%s] %s".format(sec, tag, msg)
        Log.d(TAG, line)
        events.add(line)
        while (events.size > 8) events.removeAt(0)
    }

    /** 记一小段代码的真实耗时（调参时量"这一下卡了多久"）。不能写成 inline：
     *  public inline 函数碰 private 的 [TAG] 会直接编译失败。 */
    fun timing(label: String, body: () -> Unit) {
        if (!logging) {
            body()
            return
        }
        val t0 = SystemClock.uptimeMillis()
        body()
        Log.d(TAG, "$label 提交于 ${SystemClock.uptimeMillis() - t0}ms")
    }
}

/**
 * 动效覆盖层：慢放倍率 + 最近事件 + 实时帧率。
 *
 * 帧率只在覆盖层打开时测量（`withFrameNanos` 本身几乎不花时间，但没必要常开）。
 * 判据：60Hz 屏稳定 58–60 才算满帧；掉到 50 上下说明这一帧贵了 3ms 以上。
 */
@Composable
fun MotionDebugOverlay(modifier: Modifier = Modifier) {
    if (!MotionDebug.overlay) return
    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var frames = 0
        var t0 = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                frames++
                if (now - t0 >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    t0 = now
                }
            }
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier
                .padding(start = 10.dp, top = 64.dp)
                .background(Color(0xE6231E1A), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                "慢放 ${MotionDebug.slow}× · ${fps}fps",
                fontSize = 10.sp,
                color = Color(0xFFFFE0A3),
                fontFamily = FontFamily.Monospace
            )
            MotionDebug.events.forEach { e ->
                Text(e, fontSize = 9.sp, color = Color(0xFFE8E2D6), fontFamily = FontFamily.Monospace)
            }
        }
    }
}
