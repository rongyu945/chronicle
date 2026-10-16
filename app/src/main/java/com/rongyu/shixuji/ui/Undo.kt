package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * 底部黑色提示条控制器。
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

/** 底部黑色提示条（5 秒后自动消失） */
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE61C1C1E))
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文字用 weight 吃「扣掉按钮之后」的剩余宽度 + 单行省略号：
            // 长文案再也不会把右侧按钮挤出屏幕（用户报的「撤回有时有、有时没有」就是这么来的：
            // 原来的文字没限宽，长标题会占满整条，按钮只剩 0 宽度被裁掉）。
            Text(
                ctrl.message ?: "", color = Color.White, fontSize = 13.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 有动作才画「撤回」，画了就一定完整可见（不参与宽度争夺）
            if (ctrl.action != null) {
                Text(
                    "撤回", color = Color(0xFFFF8A80), fontSize = 13.5.sp,
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
