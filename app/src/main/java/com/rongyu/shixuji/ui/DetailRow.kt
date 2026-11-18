package com.rongyu.shixuji.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 详情弹窗里的「标签　值」一行。
 *
 * 为什么不用「备注：逾期了」这种连写（2026-11-08 改）：
 *   · 中文冒号 + 同色同号的字，标签和值糊成一句，扫读时分不出哪是字段名；
 *   · 各行长度不一，右边参差不齐；标签固定一列之后，值全部左对齐成一条竖线。
 *
 * 标签列宽默认 46dp = 两个汉字 + 一点余量（「备注」「日期」「标签」「时间」都是两字）；
 * 四字标签（「下次提醒」）必须传 [labelWidth] = 58.dp，否则 46dp 会把它折成两行
 * ——2026-11-09 循环详情弹窗实测就是这样（「下次提」/「醒」）。
 * 值为空时调用方自己决定要不要画这一行。
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelWidth: androidx.compose.ui.unit.Dp = 46.dp,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Text(
            label,
            fontSize = DialogText.Body,
            // 13sp 的行盒如果沿用 bodyLarge 的 24sp，两行之间会空出一大截；
            // 这里压到 19sp，与 [DialogText.Body] 的行距口径一致。
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            value,
            fontSize = DialogText.Body,
            lineHeight = 19.sp,
            color = valueColor
        )
    }
}
