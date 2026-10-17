package com.rongyu.shixuji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 紧凑玻璃弹窗（替代 M3 AlertDialog 做「新增/修改」这类表单弹窗）。
 *
 * 用户 2026-10-16：「添加的弹窗各项目之间的间距都有点太大了，希望更紧凑一点、更小一点」。
 * AlertDialog 的留白是写死的、改不动：外圈上下各 24dp、标题→内容 16dp、内容→按钮 24dp，
 * 按钮和芯片还要各占 48dp 触摸高度（图案只有 32~40dp）——四五段叠起来就多出 100dp 空白。
 * 这里把这些间距全部收进一个地方，玻璃观感仍走 glassDialogModifier()/GlassDialogShape，
 * 和其余 AlertDialog（详情、设置里那些）保持一致。
 *
 * 尺寸不写死（原来是 `.width(296.dp)` 一锤子买卖）：
 *  - 宽度取「可用宽度的 92%」与 [width] 的较小者 —— 窄屏（320dp）上 296dp 的固定宽会顶满、
 *    左右一点余量都不剩；宽屏上也不会超过 296dp（视觉不变）。
 *  - 内容可以纵向滚动：系统字号调到最大（设置里还有「大」档 1.15×）叠上「＋备注」「标签」
 *    两种展开态时，弹窗会高过屏幕 —— 不可滚动的话底部按钮会被顶出去、弹窗关不掉。
 *
 * 用法：内容用 content 传（自动 6dp 行距），按钮用 ShixuDialogButton / ShixuDialogTextButton。
 * 注意：内容里**不要**用 ColumnScope 的 weight（滚动容器给子项的高度是无界的，weight 会崩）。
 */
@Composable
fun ShixuDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    width: Dp = 296.dp,
    gap: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = GlassDialogShape,
            color = glassDialogContainer(),
            modifier = glassDialogModifier()
                .fillMaxWidth(0.92f)
                .widthIn(max = width),
        ) {
            Column(
                Modifier
                    .padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 11.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(gap), content = content)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    Spacer(Modifier.width(4.dp))
                    confirmButton()
                }
            }
        }
    }
}

/** 弹窗主按钮（保存）。比默认矮 8dp、窄一点，配紧凑弹窗。 */
@Composable
fun ShixuDialogButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/** 弹窗次按钮（取消）。 */
@Composable
fun ShixuDialogTextButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/*
 * 弹窗里输入框的高度：**不要指定**。
 *
 * M3 带标签的 OutlinedTextField 会按字体度量算自然高度。本 App 用的 songti_sc 字体度量很特殊
 * （ascent = 1.06em、descent = 0.435em，普通字体约 0.75/0.2），自然高度算出来是 64dp ——
 * 不是 M3 规范里的 56dp。所以任何"为了紧凑"写的 .height(52.dp) 之类都会踩这个坑：
 * 字段内部给文本行的高度不够，字身下半截被就地裁掉（p/g/q/y 的下伸部、汉字的下缘笔画全没了，
 * 而框内下方还留着几十 px 空白）。2026-10-16 逐像素实测（模拟器 1080×2400，密度 2.625）：
 *   'ABpgqy' 完整字形墨迹高 43px；52dp 框只画出 20px，56dp 剩 31px，60dp 剩 42px（临界），
 *   不指定高度（自然 64dp）与 80dp 基准一致、画出完整 43px；汉字 34px 同样在 60dp 起才完整。
 * 想更紧凑请从弹窗的其它间距里省（ShixuDialog 的 gap/内边距），别动输入框高度。
 */
