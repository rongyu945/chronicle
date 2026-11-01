package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 底部提示条控制器。
 *
 * 两种形态：
 *  - [show]     可撤回（删除 / 修改 / 新增…），右侧固定给「撤回」
 *  - [showInfo] 纯提示（记了一笔、无期限新建…），不显示按钮
 *
 * 5 秒后自动消失，期间再触发一次会重置计时。
 */
class UndoController {
    var message by mutableStateOf<String?>(null)
        private set

    /** null = 纯提示（不画撤回）；非空 = 画「撤回」并执行它 */
    var action by mutableStateOf<(() -> Unit)?>(null)
        private set

    /**
     * 每次弹出都自增的计数。
     *
     * 计时不能只用 [message] 当 key：连着两次弹出**同一句话**（比如删掉两条同名的待办），
     * 文案没变、LaunchedEffect 就不会重启，第二条还接着第一条剩下的零点几秒，
     * 提示条会毫无预兆地突然消失（用户看到的「有时一下就没了」）。用计数当 key 每次都重新计时。
     */
    var token by mutableIntStateOf(0)
        private set

    fun show(msg: String, onUndo: () -> Unit) {
        message = msg
        action = onUndo
        token++
    }

    /** 纯提示：没有可撤回的动作，右侧不显示按钮 */
    fun showInfo(msg: String) {
        message = msg
        action = null
        token++
    }

    fun undo() {
        val a = action
        message = null
        action = null
        token++
        a?.invoke()
    }

    fun clear() {
        message = null
        action = null
        token++
    }
}

@Composable
fun rememberUndoController(): UndoController = remember { UndoController() }

/**
 * 底部提示条（5 秒后自动消失）。
 *
 * 2026-10-25 用户要求：「撤回的条目的左右长度收窄，黑色的透明度增加，不要黑色要灰色」——
 * 原来是一条 90% 黑、左右只留 16dp 边距、几乎占满屏宽的硬黑条，压在浅色界面上很像系统报错。
 * 现在：
 *  - **宽度跟着内容走**（最大 300dp，居中），短文案就是一小颗，不再横贯全屏；
 *  - 底色改成中性灰 #515157（不再是纯黑），透明度 0.85 —— 比原来的 0.90 更透一点，
 *    同时保证白字在这层灰上仍有 ~5:1 对比度（再透就看不清了）。
 */
@Composable
fun UndoHost(ctrl: UndoController, modifier: Modifier = Modifier) {
    // 用 token 当 key（不是 message）：同一句话连续弹两次也要重新计时
    LaunchedEffect(ctrl.token) {
        if (ctrl.message != null) {
            delay(5000)
            ctrl.clear()
        }
    }
    AnimatedVisibility(
        visible = ctrl.message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF50616D).copy(alpha = 0.85f))
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文字**限宽 + 单行省略号**，不用 weight：weight 会让整行占满 max 宽度，
                // 条子又变成一条横贯屏幕的长条（正是这次要收窄掉的东西）。
                // 限到 210dp 既保证右侧「撤回」永远完整可见，又让整条跟着文案长短伸缩。
                Text(
                    ctrl.message ?: "", color = Color.White, fontSize = 13.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 210.dp)
                )
                // 有动作才画「撤回」，画了就一定完整可见（不参与宽度争夺）
                if (ctrl.action != null) {
                    Text(
                        "撤回", color = Color(0xFFFFB3A7), fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier
                            .clickable { ctrl.undo() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                } else {
                    // 纯提示：右侧留一点内边距，别让文字贴着圆角
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}
