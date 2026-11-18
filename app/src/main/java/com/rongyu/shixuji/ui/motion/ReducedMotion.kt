package com.rongyu.shixuji.ui.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * `rememberReducedMotion()` 的组合局部版本（7.4.1，方案 P0-5）。
 *
 * 为什么要多这一层：原来只有 Glass / Segmented / 周条 / 待办分节自己在顶部读一次系统设置，
 * 页面切换、弹窗、撤回、搜索页这些**最显眼**的动效都没有响应「减弱动态效果」——
 * 用户在无障碍里关掉动画，翻页照样滑、弹窗照样弹。现在由 MainActivity 在根部
 * provide 一次，任何 composable 用 `LocalReducedMotion.current` 即可读到，不必各自注册
 * ContentObserver（十几处各注册一个观察者也是浪费）。
 */
val LocalReducedMotion = compositionLocalOf { false }

/** 减弱动效时把 spec 换成瞬时落位，否则原样返回。组合内直接用。 */
@Composable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

/** 组合外的版本（已经拿到布尔值时用，避免再多读一次 CompositionLocal）。 */
fun <T> specOrSnap(reduced: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> =
    if (reduced) snap() else spec

/**
 * 系统是否要求「减少动态效果」。
 *
 * 判据用「动画程序时长缩放 = 0」（开发者选项里那三档动画缩放），这是无障碍设置里
 * 「减弱动态效果」在系统层的落点。开启时统一周条组件会直接落位，不做过渡。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(animationsDisabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animationsDisabled(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animationsDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Throwable) {
    false
}
