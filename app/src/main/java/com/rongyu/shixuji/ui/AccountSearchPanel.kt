package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AccountSearch
import com.rongyu.shixuji.data.TagPalette
import com.rongyu.shixuji.theme.incomeGreen
import com.rongyu.shixuji.theme.isDarkTheme
import java.util.Calendar

/**
 * 账目搜索页（用户 2026-11 提出、并在建议报告里点名要做的那一条）。
 *
 * 设计口径：
 * - **占满整块内容区**，不是弹窗：搜索结果要能一直往下滚，弹窗高度不够；键盘弹起时列表也跟着缩。
 * - 输入即出结果（内存过滤，见 [AccountSearch]）；没有「搜索」按钮要点。
 * - 筛选三组：时间（全部/本月/上月/今年）、收支方向（收支/支出/收入）——这两组是并排的两个玻璃滑块，
 *   第三组标签单独一行。
 * - 结果按天分组，每条点开还是原来那个「账目详情」（修改 / 删除都在里面，不另做一套）。
 */
@Composable
internal fun AccountSearchPanel(
    vm: AccountViewModel,
    tags: List<AccTagEntity>,
    tagColorMap: Map<String, Color>,
    onOpenDetail: (Account) -> Unit,
    onClose: () -> Unit,
    /** 跨午夜刷新用：与其它页面同一个「今天」时间源，变了就重算「本月/上月/今年」 */
    todayTick: Long = 0L,
) {
    val dark = isDarkTheme
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(AccountSearch.Range.ALL) }
    var kind by remember { mutableStateOf(AccountSearch.Kind.BOTH) }
    var tagFilter by remember { mutableStateOf("") }

    // 搜索结果要跨全部时间，所以这里单独订阅整张表（不进账目页那条按月的 Flow）
    val allFlow = remember { vm.allAccounts() }
    val allAcc by allFlow.collectAsState(initial = emptyList())

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // ★ 用 todayTick 当 key：旧版是 `remember { }`，App 挂着跨过午夜后
    //   「本月/上月/今年」还按昨天算（搜索结果和计数都是旧窗口）。
    val todayMs = remember(todayTick) { DateUtils.todayCal().timeInMillis }
    val results = remember(allAcc, query, range, kind, tagFilter, todayMs) {
        AccountSearch.filter(allAcc, query, range, todayMs, kind, tagFilter)
    }
    val outSum = remember(results) { results.filter { it.type == 0 }.sumOf { it.amountCents } }
    val inSum = remember(results) { results.filter { it.type == 1 }.sumOf { it.amountCents } }
    val grouped = remember(results) {
        // 已按时间倒序，groupBy 保序 → 日期分组天然从新到旧
        results.groupBy { DateUtils.startOfDayMs(it.date) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 搜索行：输入框 + 取消 ──
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).height(44.dp)
                    .glassSurface(cardShape(CardRadius.SEARCH), dark = dark, elevation = 4.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchGlyph(size = 16.dp, stroke = 1.6.dp)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜备注、金额、标签", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 用 onSurface 而不是 onSurfaceVariant：这一格正压在右上角的桃花枝上，
            // 灰字叠在浅粉花瓣上只有 ~2.5:1，看着像没渲染完（用户 2026-11-08 复检提到）。
            // 朴素文字即可：搜索页打开时右上角的桃枝由 MainScreen 收起（见 onSearchingChange），
            // 这一格不再压着浅粉花瓣，不需要再自造一块底去救对比度 —— 早先试过套玻璃胶囊，
            // 结果和搜索框两块同色玻璃只隔 8dp，看起来像被裁了一刀。
            Text(
                "取消", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }

        // ── 一级：两个液态玻璃滑块并排 ──
        // 2026-11-11 用户：「收支支出和收入并到上一去，做两个液态玻璃滑块出来」——
        // 于是把独立的收支三态胶囊撤掉，跟时间范围合成一行：左边时间（4 格）、右边收支（3 格），
        // **两条槽、两颗各自会滑的玻璃药丸**，7 格等宽（weight 4 : 3 让两边的单格宽度一致）。
        // 两级仍是两级（左管时间、右管方向），但只占一行高度，标签行上移了约 40dp。
        val ranges = AccountSearch.Range.entries
        val kinds = AccountSearch.Kind.entries
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShixuSegmented(
                options = ranges.map { SegOption(it.label) },
                selected = ranges.indexOf(range),
                onSelect = { range = ranges[it] },
                modifier = Modifier.weight(4f)
            )
            Spacer(Modifier.width(8.dp))
            // 右条给药丸染色：两条白药丸并排会分不清手指落在哪条上（用户 2026-11-11）。
            // 颜色还带语义：支出=朱砂、收入=收入绿、收支=黛青。
            ShixuSegmented(
                options = kinds.map { SegOption(it.label) },
                selected = kinds.indexOf(kind),
                onSelect = { kind = kinds[it] },
                modifier = Modifier.weight(3f),
                pillTint = when (kind) {
                    AccountSearch.Kind.EXPENSE -> Color(0xFFB7473A)
                    AccountSearch.Kind.INCOME -> incomeGreen
                    AccountSearch.Kind.BOTH -> Color(0xFF5F6C86)
                }
            )
        }

        // ── 二级：标签（小胶囊，横向滚动）──
        // 形状同族、分量不同：一级是玻璃分段控件，二级退成小胶囊。
        //
        // 2026-11-05 三改（用户截图：「只看收入」那一块显示很不佳）。
        // 前两版都走「压在磨砂带上」：把标签行录进背景层 → 右端蒙一条磨砂带 →
        // 「只看收入」压在带上。深色下这层盖不住：被糊掉的标签轮廓仍透上来，
        // 胶囊描边与邻近标签的框交叉，字直接叠在字上（看着像两个胶囊叠在一起）。
        // 不再用叠加 —— 拆成两块并排：标签行自己**两端渐隐**（滑到边上就化掉，
        // 见 Modifier.horizontalEdgeFade），「只看收入」站在它右边，
        // 底下什么都不压，从根上不可能重叠。
        val tagRowState = rememberLazyListState()
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                Modifier.weight(1f).horizontalEdgeFade(tagRowState, fade = 20.dp),
                state = tagRowState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(tags, key = { it.id }) { t ->
                    ShixuTagChip(
                        selected = tagFilter == t.name,
                        onClick = { tagFilter = if (tagFilter == t.name) "" else t.name },
                        label = t.name,
                        dotColor = Color(t.color.toInt())
                    )
                }
            }
        }

        // ── 结果统计 ──
        if (results.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("共 ${results.size} 笔", fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (outSum > 0) {
                    Text("支出 ${formatCents(outSum)}", fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (outSum > 0 && inSum > 0) Spacer(Modifier.width(10.dp))
                if (inSum > 0) {
                    Text("收入 ${formatCents(inSum)}", fontSize = 11.5.sp,
                        color = incomeGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 2026-11-10：筛选条件一变就回到顶部。
        // 原来没有 state，列表会停在原来的滚动位置 —— 切「本月→全部」以后看到的是第 30 条，
        // 用户以为筛选没生效（用户反馈「自动回顶的功能在账目搜索界面没有做」）。
        val listState = rememberLazyListState()
        LaunchedEffect(range, tagFilter, kind, query) {
            // 用 scrollToItem（瞬时）：这里没有「从天上掉下来」的问题，
            // 因为它不是动画、只是把视口归零；animateScrollToItem 反而会让人以为列表在动。
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            // imePadding：edge-to-edge 之后窗口不会被输入法顶起来，结果列表会被键盘直接盖住
            // （实测最后两天的分组连标题一起压在键盘下面，还没法往上滑到底）。
            Modifier.fillMaxSize().imePadding(),
            state = listState,
            // 底部按「设置页」口径留白：搜索页没有 ＋ / 今 两个悬浮钮，只要不被玻璃底栏压住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = LIST_BOTTOM_PLAIN),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "没有匹配的账目", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            grouped.forEach { (dayMs, list) ->
                item(key = "h-$dayMs") {
                    DayHeader(dayMs, list)
                }
                items(list, key = { it.id }) { acc ->
                    AccountRow(
                        acc = acc,
                        tagColor = tagColorMap[acc.tag] ?: Color(TagPalette.UNLABELED),
                        onOpenDetail = { onOpenDetail(acc) }
                    )
                }
            }
        }
    }
}

/** 结果里的日期分隔行：日期 + 星期，右侧当天支出小计 */
@Composable
private fun DayHeader(dayMs: Long, list: List<Account>) {
    val cal = remember(dayMs) { DateUtils.calFrom(dayMs) }
    // ★ 7.5.3（审查 6.6）：`thisYear` 原来是 remember(dayMs) 里算的，只在 dayMs 变化时重算 ——
    //   App 挂着跨过元旦，标题就一直按旧年来判断（2026 的分组显示成「2026年1月1日」而不是「1月1日」）。
    //   现在把「今年」提出来当 key：跨年那一刻重组就会重算。
    val thisYear = DateUtils.todayCal().get(Calendar.YEAR)
    val title = remember(dayMs, thisYear) {
        val base = DateUtils.formatMonthDayCn(cal)
        "${if (cal.get(Calendar.YEAR) == thisYear) base else "${cal.get(Calendar.YEAR)}年$base"} ${DateUtils.weekdayCn(cal)}"
    }
    val dayOut = remember(list) { list.filter { it.type == 0 }.sumOf { it.amountCents } }
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (dayOut > 0) {
            Text(formatCents(dayOut), fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

