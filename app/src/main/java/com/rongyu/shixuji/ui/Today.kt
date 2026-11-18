package com.rongyu.shixuji.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.rongyu.shixuji.data.LoopSchedule
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * 「今天」的本地零点毫秒值，**跨午夜会自动刷新**。
 *
 * ★ 7.5.2（审查 5.1 / 5.2）：以前页面里写的是
 * `val today = remember { LoopSchedule.dayStart(System.currentTimeMillis()) }`。
 * `remember {}` 只在首次组合时算一次，父组件重组也不会重算 —— 用户 23:50 打开
 * 设置 → 循环待办，停到 00:10，页面仍然按**昨天**判断「今天该不该提醒 / 是否已完成」，
 * 点勾选还会把昨天的日期写进 doneDates。
 *
 * 这里改成：
 * 1. 只在**前台**跑（[repeatOnLifecycle] STARTED）—— 关屏/切后台时协程挂起，不耗电；
 * 2. 睡到**下一个本地午夜**再醒，不是每分钟轮询；
 * 3. 回前台立刻重算一次，后台熬夜那段时间的跨天不会漏。
 *
 * 日历加减用 [Calendar.add] + 再抹零，夏令时那天（23/25 小时）也不会算错午夜。
 */
@Composable
fun rememberTodayMs(): Long {
    var today by remember { mutableLongStateOf(LoopSchedule.dayStart(System.currentTimeMillis())) }
    val owner = LocalLifecycleOwner.current
    val clock = rememberClockChangeToken()
    // ★ 7.5.5（复核报告 3.1）：sleep 到「下一个本地午夜」只对「时间自然流逝」有效。
    //   用户在前台改系统时间 / 改时区 / 系统自动校时之后，原来算好的那个目标时刻可能已经过期
    //   （例如 10:00 把钟改成 23:59，得再睡 24 小时才醒）。这里监听三个广播，一变就重启循环、立刻重算。
    LaunchedEffect(owner, clock) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LoopSchedule.dayStart(System.currentTimeMillis())
                if (now != today) today = now
                delay(msUntilNextMidnight())
            }
        }
    }
    return today
}

/**
 * 「系统时间/日期/时区变了」的令牌：每收到一次广播 +1。
 *
 * 为什么要单独一个令牌：`delay(msUntilNextMidnight())` 是**一次性**的睡眠目标，
 * 睡眠期间用户改时区，那个目标时刻就作废了，而协程还在原地睡（最长 25 小时）。
 * 把它当 `LaunchedEffect` 的 key 之一，令牌一变循环就重启，立刻按新的时钟重算。
 *
 * 三个广播缺一不可：
 * - `TIME_SET`（`ACTION_TIME_CHANGED`）：手动改时间、自动校时；
 * - `TIMEZONE_CHANGED`：换时区（含出差的自动切换）；
 * - `DATE_CHANGED`：跨零点、手动改日期。
 */
@Composable
fun rememberClockChangeToken(): Int {
    val context = LocalContext.current
    var token by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { token++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // RECEIVER_NOT_EXPORTED：这些是系统广播、只发给本应用，不需要导出（API 33+ 的硬要求）
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return token
}

/**
 * 距离**下一个本地午夜**还有多少毫秒（给 [rememberTodayMs] 当 sleep 时长）。
 *
 * 先 `add(DAY_OF_MONTH, 1)` 再抹零：夏令时当天这样得到的是「明天 00:00」而不是
 * 「今天 00:00 + 24h」。返回值夹在 [1s, 25h]，避免时钟被改动后算出 0 或负数导致忙等。
 */
internal fun msUntilNextMidnight(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (cal.timeInMillis - now).coerceIn(1_000L, 25L * 60 * 60 * 1000)
}
