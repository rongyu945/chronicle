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
fun AccountRow(acc: Account, tagColor: Color, onOpenDetail: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = LIST_ROW_MIN_HEIGHT)
            .glassSurface(shape, dark = isDarkTheme, elevation = 4.dp, strokeInset = 2.dp)
            .clickable { onOpenDetail() }
            .padding(horizontal = 14.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(tagColor, CircleShape))
        Text(accountTitle(acc), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(
            formatCents(if (acc.type == 1) acc.amountCents else -acc.amountCents),
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, maxLines = 1,
            color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 96.dp)
        )
    }
}
