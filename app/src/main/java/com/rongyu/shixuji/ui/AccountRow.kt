package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.theme.incomeGreen
import com.rongyu.shixuji.theme.isDarkTheme

/** 单条账目行（金额右对齐；单行截断显示，点击打开详情；具体时间只在详情弹窗里看） */
@Composable
fun AccountRow(acc: Account, tagColor: Color, onOpenDetail: () -> Unit, modifier: Modifier = Modifier) {
    val shape = cardShape(CardRadius.ROW)
    // 被删掉的那条（离场中）由外层 RowExitShell 广播「划掉」进度；平时是 null。
    // 账目行没有勾选框，所以这里只有那道线 —— 用户 2026-11-18：「按钮不用变红勾，划掉那道线保留」。
    val strike = LocalRowStrike.current
    Row(
        modifier.fillMaxWidth().heightIn(min = LIST_ROW_MIN_HEIGHT)
            .paperSurface(shape, dark = isDarkTheme, elevation = 3.dp, strokeInset = 2.dp)
            .clickable(onClickLabel = "查看详情") { onOpenDetail() }
            .padding(horizontal = 14.dp, vertical = 3.dp)
            // 线画在**内边距之内**（左右各让出 14dp），压过标题与金额，像笔划在纸面上
            .then(
                if (strike != null) Modifier.strikeLine(
                    progress = { strike },
                    color = MaterialTheme.colorScheme.onSurface
                ) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(tagColor, CircleShape))
        // 15sp：与待办卡的主行同一档。原来这里是 14sp，两个列表并排看时账目标题明显小一号
        // （2026-11-08 统一；金额仍是 15sp Medium，字重差把「标题 / 数字」分开）。
        Text(accountTitle(acc), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp))
        // 支出不再带「-」：收支靠颜色区分（支出＝正文色、收入＝绿），
        // 少一个符号，整列数字的小数点更容易对齐扫读（用户 2026-11-09：视觉更统一）。
        Text(
            formatCents(acc.amountCents),
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            // 等宽数字（tabular figures）：纵向扫列表时小数点、位数对齐，不会因为 1/8 窄宽跳动。
            // **必须从 LocalTextStyle 拷**：给 Text 传 style 参数会整个替掉默认的
            // `style = LocalTextStyle.current`，只写 fontFeatureSettings 的话字族也跟着没了 ——
            // 于是这一格的金额掉回系统无衬线，而同行标题还是宋体（用户 2026-11-05：
            // 「账目的条目里的数字不是宋体了」）。
            style = LocalTextStyle.current
                .copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End, maxLines = 1,
            color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 96.dp)
        )
    }
}
