package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.Todo
import com.rongyu.shixuji.theme.Blue
import com.rongyu.shixuji.theme.BlueDark
import com.rongyu.shixuji.theme.Line
import com.rongyu.shixuji.theme.isDarkTheme
import java.util.Calendar

/*
 * 月视图 / 年视图（2026-11-18 改版：月＝「纸上年鉴」、年＝「格阵」，用户从三版 HTML 预览里点的组合）
 *
 * 月视图：不做卡片，只画细线。日期 17sp 大字，今天一枚细红圈（不填实心），
 *        数字下方最多 4 个彩点表示当天几件事，点的颜色 = 当天最高优先级；
 *        月历下面一行虚线小注（本月共几件 / 已完成 / 最忙哪天），补上「总量」这层信息。
 * 年视图：保留 3×4 卡阵，但卡内不再写日期数字 —— 13dp 圆里塞 8sp 字在真机上根本读不出来。
 *        改成纯色块热力 + 卡头「N 件」+ 卡底一条本月完成进度。信息更多，字反而更大。
 */

/** 某一天的事项概况：总数 / 已完成数 / 当天最高优先级（0＝无） */
data class DayBrief(val total: Int, val done: Int, val maxPrio: Int)

/**
 * 把待办列表聚合成「月键 → 日 → 概况」。
 *
 * 抽成纯函数是为了能单测（在 Composable 的 remember{} 里只能肉眼验）；
 * `dueDate == 0` 的「无期限」待办不进日历 —— 它们不属于任何一天。
 */
internal fun buildDayBriefs(todos: List<Todo>): Map<Int, Map<Int, DayBrief>> {
    val m = HashMap<Int, HashMap<Int, DayBrief>>()
    todos.forEach { t ->
        if (t.dueDate != 0L) {
            val c = DateUtils.calAt(t.dueDate)
            val k = DateUtils.monthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
            val d = c.get(Calendar.DAY_OF_MONTH)
            val dm = m.getOrPut(k) { HashMap() }
            val old = dm[d] ?: DayBrief(0, 0, 0)
            dm[d] = DayBrief(
                total = old.total + 1,
                done = old.done + if (t.done) 1 else 0,
                maxPrio = maxOf(old.maxPrio, t.priority)
            )
        }
    }
    return m
}

/** 热力等级：当天事项 0/1/2/3/4+ 对应 0..4 级 */
internal fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count == 2 -> 2
    count == 3 -> 3
    else -> 4
}

/**
 * 年视图色块的不透明度。
 * 比旧版圆点更实（0.14 / 0.34 / 0.60 / 1）：块里没有数字了，只能靠色块本身区分，
 * 太淡的话「1 件」和「没安排」会糊成一片。
 */
internal fun blockAlpha(level: Int): Float = when (level) {
    1 -> 0.14f
    2 -> 0.34f
    3 -> 0.60f
    else -> 1f
}

private fun rowsNeeded(year: Int, m0: Int): Int {
    val cells = DateUtils.leadingBlanks(year, m0) + DateUtils.daysInMonth(year, m0)
    return (cells + 6) / 7          // 不足一行也算一行
}

/* =====================================================================================
 * 年视图（格阵）
 * ===================================================================================== */

@Composable
fun YearNavView(
    evBriefOfMonth: Map<Int, Map<Int, DayBrief>>, year: Int,
    state: LazyListState, onSelectMonth: (Int, Int) -> Unit
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        // 240dp：滚到底时最后一排月卡能整体躲到右下角 ＋/今 两个悬浮按钮上方
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 240.dp),
        // 8dp：淡纸卡没有描边，卡与卡之间全靠这点缝分开（原来是 6dp，靠着白框才够看）
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(4) { row ->
            // 这一行 3 个月各自需要几行，取最大值 —— 一行内 3 张卡严格等高，同时避免白补空行
            val rowMin = (0..2).map { row * 3 + it }.filter { it < 12 }
                .maxOfOrNull { rowsNeeded(year, it) } ?: 6
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..2) {
                    val m = row * 3 + i
                    if (m < 12) MonthCard(evBriefOfMonth, year, m, rowMin, onSelectMonth, Modifier.weight(1f))
                }
            }
        }
        item {
            val primary = MaterialTheme.colorScheme.primary
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("少", fontSize = 10.sp, color = dim)
                for (a in listOf(0.14f, 0.34f, 0.60f, 1f)) {
                    Box(
                        Modifier.padding(horizontal = 2.dp).size(10.dp)
                            .clip(RoundedCornerShape(2.dp)).background(primary.copy(alpha = a))
                    )
                }
                Text("多 · 色块＝当天件数 · 细条＝本月完成", fontSize = 10.sp, color = dim)
            }
        }
    }
}

@Composable
private fun MonthCard(
    evBriefOfMonth: Map<Int, Map<Int, DayBrief>>, year: Int, m0: Int, minRows: Int,
    onClick: (Int, Int) -> Unit, mod: Modifier
) {
    val leading = DateUtils.leadingBlanks(year, m0)
    val days = DateUtils.daysInMonth(year, m0)
    val today = DateUtils.todayCal()
    val briefs = evBriefOfMonth[DateUtils.monthKey(year, m0)].orEmpty()
    val total = briefs.values.sumOf { it.total }
    val done = briefs.values.sumOf { it.done }
    val grid = List(leading) { 0 } + (1..days)
    // 补 0 到整行再切（保证每行固定 7 格、日期严格左对齐，月末不拉伸错列）；
    // 补多少行 = max(本月真正需要的行数, 同一行 3 个月里的最大值) —— 绝不无条件补到 6 行，
    // 否则只要不是 6 行的月份，卡片底下就白空一整行（用户报「月份和他的底差了那么多」）。
    val totalRows = maxOf(rowsNeeded(year, m0), minRows)
    val fixed = (grid + List(totalRows * 7 - grid.size) { 0 }).chunked(7)

    val primary = MaterialTheme.colorScheme.primary
    val blank = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val shape = cardShape(CardRadius.ROW)
    // 用户 2026-11-18 选的「C · 淡纸卡」：去掉白高光描边，只留一层极淡白底把月份托起来。
    // 12 张白框在纸底上像贴了 12 张便签，边框还跟格子线抢视线；淡底则「有块但没框」。
    val cardBg = Color.White.copy(alpha = if (isDarkTheme) 0.06f else 0.20f)

    Card(
        mod.clickable { onClick(year, m0) },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = null,
        shape = shape
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // lineHeight 必须显式给：主题的 24sp 行高比字号大得多，会把行框撑高
                Text("${m0 + 1}月", fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (total == 0) "—" else "$total 件",
                    fontSize = 9.5.sp, lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(5.dp))
            fixed.forEachIndexed { ri, rowCells ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    rowCells.forEach { day ->
                        val cellToday = day != 0 && day == today.get(Calendar.DAY_OF_MONTH) &&
                            m0 == today.get(Calendar.MONTH) && year == today.get(Calendar.YEAR)
                        val level = if (day == 0) 0 else heatLevel(briefs[day]?.total ?: 0)
                        val bg = when {
                            day == 0 -> Color.Transparent
                            cellToday -> primary
                            level == 0 -> blank
                            else -> primary.copy(alpha = blockAlpha(level))
                        }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(1.5.dp)).background(bg)
                        )
                    }
                }
                if (ri != fixed.lastIndex) Spacer(Modifier.height(1.5.dp))
            }
            // 本月完成进度：卡里没有数字之后，这条细线是唯一带「方向性」的信息
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)).background(primary.copy(alpha = 0.12f))
            ) {
                if (total > 0) {
                    Box(
                        Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight()
                            .background(primary.copy(alpha = 0.75f))
                    )
                }
            }
        }
    }
}

/* =====================================================================================
 * 月视图（纸上年鉴）
 * ===================================================================================== */

@Composable
fun MonthNavView(
    evBriefOfMonth: Map<Int, Map<Int, DayBrief>>, year: Int, month: Int, onSelectDay: (Int) -> Unit
) {
    val today = DateUtils.todayCal()
    val leading = DateUtils.leadingBlanks(year, month)
    val days = DateUtils.daysInMonth(year, month)
    val briefs = evBriefOfMonth[DateUtils.monthKey(year, month)].orEmpty()
    val total = briefs.values.sumOf { it.total }
    val done = briefs.values.sumOf { it.done }

    val dark = isDarkTheme
    // 细线：浅色用宣纸的暖线（缟），深色用 outlineVariant（20% 白）—— 浅米线在暗底上会发亮
    val rule = if (dark) MaterialTheme.colorScheme.outlineVariant else Line
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    val grid = List(leading) { 0 } + (1..days)
    // 只补到「本月真正需要的整行数」：旧版无条件补 42 格，5 行的月份底下会多出一整行空线，
    // 纸面风格里那行空线像漏排了一行。
    val padded = grid + List(rowsNeeded(year, month) * 7 - grid.size) { 0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it, fontSize = 10.sp, lineHeight = 14.sp, color = dim,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
        padded.chunked(7).forEach { rowCells ->
            Row(Modifier.fillMaxWidth()) {
                rowCells.forEach { day ->
                    MonthDayCell(day, year, month, leading, today, briefs, rule, primary, dim, dark, onSelectDay)
                }
            }
        }
        if (total > 0) {
            DashedRule(Modifier.padding(top = 12.dp))
            Text(
                monthSummary(briefs, total, done),
                fontSize = 10.5.sp, lineHeight = 16.sp, color = dim, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
            )
        }
    }
}

/**
 * 月视图的一格：上不封口、下带一条细线，整格可点（点按面积 ≈ 51×64dp）。
 * Column 里「数字容器固定 31dp + 点行固定 4dp」是为了让所有日期落在同一水平线上 ——
 * 今天要套 31dp 的红圈，若容器高度随内容变，有圈/无圈的日子数字会一高一低。
 */
@Composable
private fun RowScope.MonthDayCell(
    day: Int, year: Int, month: Int, leading: Int, today: Calendar,
    briefs: Map<Int, DayBrief>, rule: Color, primary: Color, dim: Color, dark: Boolean,
    onSelectDay: (Int) -> Unit
) {
    Box(
        Modifier.weight(1f).height(64.dp)
            .clickable(enabled = day != 0) { if (day != 0) onSelectDay(day) }
            .drawBehind {
                val y = size.height - 0.5f
                drawLine(color = rule, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        if (day == 0) return@Box
        val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
            month == today.get(Calendar.MONTH) && year == today.get(Calendar.YEAR)
        val dow = (day + leading - 1) % 7
        val isWeekend = dow == 5 || dow == 6
        val brief = briefs[day]
        val count = brief?.total ?: 0
        // 彩点颜色 = 当天最高优先级；全是「无优先级」的日子退回苍色，否则点会是透明的看不见
        val dotColor = if (brief == null || brief.maxPrio <= 0) dim else priorityColor(brief.maxPrio, dark)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 今天不用圈（用户 2026-11-18：红圈丑），改用靛青字 —— 色卡里的 --blue #177CB0。
            // 盒子仍是 34dp 方形，与 7.7.15 同尺寸同基线（盒心距格顶 28.5dp），只去掉圈和那个补偿位移。
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Text(
                    "$day", fontSize = 17.sp, lineHeight = 20.sp,
                    color = when {
                        isToday -> if (dark) BlueDark else Blue
                        isWeekend -> primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
            // 点行常驻（哪怕 0 枚）：不占位的话有事项和没事项的日子数字会一高一低
            Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                repeat(minOf(count, 4)) { i ->
                    Box(
                        Modifier.size(4.dp)
                            .background(dotColor.copy(alpha = 0.92f - i * 0.18f), CircleShape)
                    )
                }
            }
        }
    }
}

/** 「本月共 33 件 · 已完成 21 · 最忙 9 日（4 件）」—— 并列最大时取更早的那天 */
internal fun monthSummary(briefs: Map<Int, DayBrief>, total: Int, done: Int): String {
    val busiest = briefs.entries.maxWithOrNull(compareBy({ it.value.total }, { -it.key }))
    val head = "本月共 $total 件 · 已完成 $done"
    return if (busiest == null) head else "$head · 最忙 ${busiest.key} 日（${busiest.value.total} 件）"
}

@Composable
private fun DashedRule(mod: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.outlineVariant
    Canvas(mod.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = c, start = Offset(0f, 0.5f), end = Offset(size.width, 0.5f),
            strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
    }
}
