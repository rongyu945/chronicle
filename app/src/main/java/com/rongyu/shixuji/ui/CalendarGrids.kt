package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** 热力等级：当天事项 0/1/2/3/4+ 对应 0..4 级 */
private fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count == 2 -> 2
    count == 3 -> 3
    else -> 4
}

private fun heatAlpha(level: Int): Float = when (level) {
    1 -> 0.18f
    2 -> 0.40f
    3 -> 0.66f
    else -> 1f
}

private fun rowsNeeded(year: Int, m0: Int): Int {
    val cells = DateUtils.leadingBlanks(year, m0) + DateUtils.daysInMonth(year, m0)
    return (cells + 6) / 7          // 不足一行也算一行
}

/** 一级：年视图（日期格按当天事项数量着色 = 热力图） */
@Composable
fun YearNavView(
    evCountOfMonth: Map<Int, Map<Int, Int>>, year: Int,
    state: LazyListState, onSelectMonth: (Int, Int) -> Unit
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        // 240dp：滚到底时最后一排月卡能整体躲到右下角 ＋/今 两个悬浮按钮上方
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 240.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(4) { row ->
            // B 方案：这一行 3 个月各自需要几行，取最大值 —— 一行内 3 张卡严格等高，同时避免白补空行
            val rowMin = (0..2).map { row * 3 + it }.filter { it < 12 }
                .maxOfOrNull { rowsNeeded(year, it) } ?: 6
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..2) {
                    val m = row * 3 + i
                    if (m < 12) MonthCard(evCountOfMonth, year, m, rowMin, onSelectMonth, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Text("少", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (a in listOf(0.18f, 0.40f, 0.66f, 1f)) {
                    Box(Modifier.padding(horizontal = 2.dp).size(12.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = a), CircleShape))
                }
                Text("多", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthCard(
    evCountOfMonth: Map<Int, Map<Int, Int>>, year: Int, m0: Int, minRows: Int,
    onClick: (Int, Int) -> Unit, mod: Modifier
) {
    val leading = DateUtils.leadingBlanks(year, m0)
    val days = DateUtils.daysInMonth(year, m0)
    val today = DateUtils.todayCal()
    val grid = List(leading) { 0 } + (1..days)
    // 补 0 到整行再切（保证每行固定 7 格、日期严格左对齐，月末不拉伸错列）；
    // 补多少行 = max(本月真正需要的行数, 同一行 3 个月里的最大值) —— 绝不无条件补到 6 行，
    // 否则只要不是 6 行的月份，卡片底下就白空一整行（用户报「月份和他的底差了那么多」）。
    val totalRows = maxOf(rowsNeeded(year, m0), minRows)
    val fixed = (grid + List(totalRows * 7 - grid.size) { 0 }).chunked(7)
    val counts = evCountOfMonth[DateUtils.monthKey(year, m0)].orEmpty()
    val shape = RoundedCornerShape(18.dp)
    Card(mod.clickable { onClick(year, m0) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = glassBorder(),
        shape = shape) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 6.dp)) {
            Text("${m0 + 1}月", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp))
            // 注意 lineHeight 必须给：主题里的 lineHeight(24sp) 比字号大得多，
            // 排版会把行框居中、把基线推到行框下半部分，小圆里的小字就会被裁掉下半截。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, fontSize = 8.sp, lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            fixed.forEach { rowCells ->
                Row(Modifier.height(16.dp).fillMaxWidth()) {
                    rowCells.forEach { day ->
                        var bg = Color.Transparent
                        var fg: Color = MaterialTheme.colorScheme.onSurface
                        var bold = false
                        val cellToday = day != 0 && day == today.get(Calendar.DAY_OF_MONTH) &&
                            m0 == today.get(Calendar.MONTH) && year == today.get(Calendar.YEAR)
                        val level = if (day == 0) 0 else heatLevel(counts[day] ?: 0)
                        if (cellToday) {
                            bg = MaterialTheme.colorScheme.primary; fg = Color.White; bold = true
                        } else if (day != 0) {
                            if (level > 0) {
                                bg = MaterialTheme.colorScheme.primary.copy(alpha = heatAlpha(level))
                                if (level >= 3) { fg = Color.White; bold = true }
                            } else {
                                val dow = (day + leading - 1) % 7
                                if (dow == 5 || dow == 6) fg = MaterialTheme.colorScheme.primary
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            // 尺寸要小于列宽（3 列时每列约 13~15dp），否则圆形被压成椭圆、相邻还会粘住
                            Box(Modifier.size(13.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                                // lineHeight 同①：不给的话数字下半截会被 13dp 的小圆裁掉
                                Text(if (day == 0) "" else "$day", fontSize = 8.sp, lineHeight = 10.sp,
                                    color = if (day == 0) Color.Transparent else fg,
                                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 二级：月视图 */
@Composable
fun MonthNavView(evCountOfMonth: Map<Int, Map<Int, Int>>, year: Int, month: Int, onSelectDay: (Int) -> Unit) {
    val today = DateUtils.todayCal()
    val leading = DateUtils.leadingBlanks(year, month)
    val days = DateUtils.daysInMonth(year, month)
    val counts = evCountOfMonth[DateUtils.monthKey(year, month)].orEmpty()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        val grid = List(leading) { 0 } + (1..days)
        val padded = grid + List(42 - grid.size) { 0 }
        padded.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    val level = if (day == 0) 0 else heatLevel(counts[day] ?: 0)
                    val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                        month == today.get(Calendar.MONTH) && year == today.get(Calendar.YEAR)
                    val dow = (day + leading - 1) % 7
                    val isWeekend = dow == 5 || dow == 6
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                            .background(
                                when {
                                    isToday -> MaterialTheme.colorScheme.primary
                                    level > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = heatAlpha(level))
                                    else -> Color.Transparent
                                }, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(enabled = day != 0) { if (day != 0) onSelectDay(day) }) {
                            // 同上：lineHeight 显式给 21sp，否则继承 24sp 行高把数字在圆里顶偏
                            Text(if (day == 0) "" else "$day", fontSize = 14.sp, lineHeight = 21.sp,
                                color = when {
                                    isToday -> Color.White
                                    level >= 3 -> Color.White
                                    isWeekend -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isToday || level >= 3) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}