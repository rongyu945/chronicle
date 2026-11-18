package com.rongyu.shixuji.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 列表行「主行 + 副行」的文字节奏。
 *
 * 为什么要有这个文件（2026-11-08）：
 *   Compose 的 Text 默认行高约是字号的 1.6 倍，而 CJK 字体本身的 ascent/descent 就比拉丁文大，
 *   两行叠起来时行盒上下各留一截空气 —— 实测「标题墨迹 → 备注墨迹」的间隙 31px≈11.8dp，
 *   读起来像两段不相干的信息（用户原话：「备注和主行之间的间距可以缩小」）。
 *
 * 做法：行高压到接近字面高度，再用 [LineHeightStyle] 的 `Trim.Both` 把首行上方、末行下方的
 *   多余行距裁掉 —— 行高于是只作用于「多行文本的行间」，单行文本不再撑出一圈空气。
 *   实测同一个循环待办卡：主行→副行 12.19dp → 6.86dp；有备注的待办卡 11.4dp → 6.1dp。
 *
 * **必须用 [rowTitle]/[rowSub]，不要直接把 [TitleStyle]/[SubStyle] 传给 `Text(style = …)`**：
 *   `style` 参数会整个替掉 `LocalTextStyle.current`（宋体字族在里面），只写字号行高的话
 *   这一格会掉回系统无衬线体 —— 账目金额那格踩过一次（见 AccountRow 的注释）。
 *   这里用 `LocalTextStyle.current.merge(...)`，字族沿用主题。
 *
 * 弹窗、标题栏的字号节奏另有一套，见 [DialogText]。
 */
object RowType {

    private val Trim = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )

    /** 主行：15sp。行高 20sp 只留给「万一换行」的场合，单行时会被 trim 裁到字面高。 */
    val TitleStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, lineHeightStyle = Trim)

    /** 副行（备注 / 循环说明）：11.5sp，比主行再压一档。 */
    val SubStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, lineHeightStyle = Trim)

    /**
     * 副行贴紧用的一点点负位移（-1.5dp）。
     *
     * 为什么不能再靠 lineHeight 压：宋体的 ascent+descent 本身约 1.5em（15sp 的行盒实测 22.5dp、
     * 11.5sp 的 17.1dp），行盒已经等于字形高、Trim.Both 也只能裁掉 lineHeight 多加的那一点点 ——
     * 想让两行更贴，只能让副行整体往上挪 1.5dp。实测墨迹间隙 7.24dp → 5.71dp，
     * 既读成一块、又没糊在一起。
     */
    val SubTighten: Dp = (-1.5).dp

    @Composable
    @ReadOnlyComposable
    fun rowTitle(): TextStyle = LocalTextStyle.current.merge(TitleStyle)

    @Composable
    @ReadOnlyComposable
    fun rowSub(): TextStyle = LocalTextStyle.current.merge(SubStyle)
}

/** 副行专用：`Modifier.offset` 的薄封装，避免各处各写一个数字。 */
fun Modifier.rowSubTight(): Modifier = offset(y = RowType.SubTighten)
