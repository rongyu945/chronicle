package com.rongyu.shixuji.ui

import com.rongyu.shixuji.R
import com.rongyu.shixuji.theme.AccentDeep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.Todo
import com.rongyu.shixuji.theme.isDarkTheme
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import com.rongyu.shixuji.theme.BgLight
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import com.rongyu.shixuji.ui.motion.Motion

/** 整理页的筛选档 */
private enum class BatchFilter(val label: String) {
    ALL("全部"), OPEN("未完成"), DONE("已完成")
}

/** 整理页的行距：与待办日列表的 DAY_ROW_SPACING 取同一个值（6dp） */
private val BATCH_ROW_SPACING = 6.dp

// ── 底部三颗胶囊的刻度 ──
// 2026-11-10 用户：「整理页既然没有底栏，就把这三颗放到原底栏的位置」。
// 于是直接对齐底栏几何：宽 = 3×108×0.9 = 291.6dp、高 56dp（底栏 64×0.9 = 57.6dp）、
// 底边距 = 导航栏之上 10dp —— 与底栏的 `.navigationBarsPadding().padding(bottom = 10.dp)` 同款。
private val ACTION_H = 56.dp
private val ACTION_FONT = 13.5.sp
private val ACTION_BAR_W = 291.6.dp
private val ACTION_BOTTOM_GAP = 10.dp
/** 列表底部留白 / 毛玻璃带总高：导航栏 ≈24 + 底距 10 + 胶囊 56 + 呼吸 8 */
private val ACTION_BAND_H = 154.dp
private const val ACTION_WRAP = true

/**
 * 待办「整理」页（用户 2026-11-03 指正：批量整理本来就该做在待办上）。
 *
 * 口径：
 * - 整块占掉内容区（和搜索页同一套），不做弹窗：条目多的时候弹窗里两套滚动会打架。
 * - 列表是**全部待办**（未完成在上、已完成沉底），不跟当前显示的日子走 —— 整理本来就是为了收拾攒下来的东西。
 * - 三件事：改日期 / 改优先级 / 删除。删除不弹二次确认，改走「撤回」，与单条删除同一个交互。
 * - 顶部「全选」芯片，底部操作区；没选中时按钮是灰的、点不动。
 * - 长按任意一条待办也能直接进这里，并且把那条预先选上（[initialSelected]）。
 *
 * 2026-11-04 用户反馈「和原本界面非常有割裂感，风格一点也不统一」——
 * 原来条目是扁平半透明色块、按钮是自绘胶囊，跟全 App 的玻璃质感不搭。
 * 现在条目一律走 [glassSurface]（和 TodoCard 同参数），选中态是「玻璃 + 主色淡染 + 描边」，
 * 底部按钮也换成玻璃体，跟搜索页/摘要条一个家族。
 */
@Composable
internal fun TodoBatchPanel(
    vm: TodoViewModel,
    initialSelected: Set<Long>,
    onClose: () -> Unit,
    onDeleted: (List<Todo>) -> Unit,
    /** 改日期 / 改优先级之后给一条可撤回的提示：面板把「改之前那几条」整条交给上层还原 */
    onUndoable: (String, () -> Unit) -> Unit,
) {
    val dark = isDarkTheme
    val all by vm.todos.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(initialSelected) }

    // 顺序：**和待办页封面同口径**（用户 2026-11-10：整理页顺序要和封面一致）。
    // 封面的分节是「当天 → 循环待办 → 无期限 → 过往未完成 → 已完成」，整理页看不到循环，
    // 所以按同一套分区排：今天及以后（按到期日）→ 无期限 → 已逾期（离今天越近越前）→ 已完成。
    // 分区内部沿用封面那套键：拖动过的 sortOrder 优先，其次优先级，最后到期日/id 兜底
    // （2026-11-17 起已逾期组内改「新的在上」，与封面的「过往未完成」同口径）。
    val ordered = remember(all) {
        val todayStart = DateUtils.startOfDayMs(System.currentTimeMillis())
        all.sortedWith(
            compareBy<Todo> { t ->
                when {
                    t.done -> 3
                    t.dueDate == 0L -> 1
                    t.dueDate < todayStart -> 2
                    else -> 0
                }
            }
                .thenBy { if (it.done || it.dueDate == 0L) 0 else it.sortOrder }
                .thenByDescending { if (it.done) 0 else it.priority }
                // 已逾期组：到期日越晚（离今天越近）越前 = 取负号；今天及以后组仍按日期从近到远。
                .thenBy { if (it.done || it.dueDate == 0L) 0L else if (it.dueDate < todayStart) -it.dueDate else it.dueDate }
                .thenByDescending { if (it.done) it.doneAt else 0L }
                .thenBy { it.id }
        )
    }
    val picked = remember(ordered, selected) { ordered.filter { it.id in selected } }

    var duePicker by remember { mutableStateOf(false) }
    var priorityPicker by remember { mutableStateOf(false) }

    // 筛选：全部 / 未完成 / 已完成（2026-11 用户要求「多选里也要能筛已完成」）
    var filter by remember { mutableStateOf(BatchFilter.ALL) }
    // 切筛选回顶用（见下面 LazyColumn 的 state）
    val listState = rememberLazyListState()
    LaunchedEffect(filter) { listState.scrollToItem(0) }
    val visible = remember(ordered, filter) {
        when (filter) {
            BatchFilter.ALL -> ordered
            BatchFilter.OPEN -> ordered.filter { !it.done }
            BatchFilter.DONE -> ordered.filter { it.done }
        }
    }
    // 全选只作用于**当前筛出来的那些**（筛「已完成」时不会把未完成的也勾上）
    val allSelected = visible.isNotEmpty() && visible.all { it.id in selected }
    val enabled = selected.isNotEmpty()

    // 底栏式浮层：列表整块滚到胶囊下面，胶囊用自己的背景采样 + 模糊压在上面
    val backdrop = rememberLiquidGlassBackdrop()

    Box(Modifier.fillMaxSize()) {
        // ── 可滚动的一整块（标题 / 筛选 / 列表）──
        // 这一层会被录一份给玻璃采样；胶囊写在它**外面**，否则会把自己录进去 → 递归。
        Column(Modifier.fillMaxSize().recordBackdrop(backdrop)) {
            // ── 标题行（与其它页同一个字号/留白）──
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "整理", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
                )
                // 同搜索页：这颗「取消」压在右上角花枝上，灰字对比度不够（2026-11-08 复检）
                Text(
                    "取消", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }

            // ── 一级：完成状态（玻璃分段控件，数量写在选项里）──
            ShixuSegmented(
                options = BatchFilter.entries.map { f ->
                    SegOption(
                        f.label,
                        when (f) {
                            BatchFilter.ALL -> ordered.size
                            BatchFilter.OPEN -> ordered.count { !it.done }
                            BatchFilter.DONE -> ordered.count { it.done }
                        }
                    )
                },
                selected = filter.ordinal,
                onSelect = { filter = BatchFilter.entries[it] },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )

            // ── 二级：全选（小胶囊）+ 计数 ──
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShixuTagChip(
                    selected = allSelected,
                    onClick = {
                        val ids = visible.map { it.id }.toSet()
                        selected = if (allSelected) selected - ids else selected + ids
                    },
                    label = if (allSelected) "取消全选" else "全选"
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (selected.isEmpty()) "共 ${visible.size} 条" else "已选 ${selected.size} 条",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (filter) {
                            BatchFilter.DONE -> "没有已完成的待办"
                            BatchFilter.OPEN -> "没有未完成的待办"
                            BatchFilter.ALL -> "还没有待办"
                        },
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 与待办列表同一套排布：左右 16dp、上下 8dp、行距 6dp —— 从列表长按进来视觉不跳
                // 底部留出浮层的高度，最后一条能滚到胶囊上方，不会永远压在玻璃底下
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    // 切筛选回到顶部：旧版没有显式 state，筛「已完成」再筛「未完成」时
                    // 短列表会贴着底部显示（滚动位置跨筛选保留）
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = ACTION_BAND_H
                    ),
                    verticalArrangement = Arrangement.spacedBy(BATCH_ROW_SPACING)
                ) {
                    items(visible, key = { it.id }) { t ->
                        TodoBatchRow(
                            t = t, checked = t.id in selected, dark = dark,
                            onToggle = {
                                selected = if (t.id in selected) selected - t.id else selected + t.id
                            }
                        )
                    }
                }
            }
        }

        // ── 底部浮层：毛玻璃带 + 三颗玻璃胶囊 ──
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            // 底部纸色压层：整理页把原底栏位置让给了三颗胶囊，列表滚到底时需要在胶囊背后
            // 淡成纸色，否则条目文字会直接透在胶囊缝里（页面级的那层被本面板盖住了，见 TodoScreen）。
            BottomPaperFade(height = ACTION_BAND_H + 52.dp)
            // 条目在这条带子里被模糊、渐隐 —— 原来这里是一条硬切线
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .frostedBand(backdrop, dark, ACTION_BAND_H, fadeIn = 56.dp)
            )
            BatchActionBar(
                enabled = enabled, count = selected.size, dark = dark, backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = ACTION_BOTTOM_GAP),
                onDue = { duePicker = true }, onPriority = { priorityPicker = true },
                onDelete = { doDelete(picked, vm, { selected = emptySet() }, onDeleted, onClose) }
            )
        }
    }


    // ── 改日期 ──
    if (duePicker) {
        var dueMs by remember { mutableLongStateOf(DateUtils.startOfDayMs(DateUtils.todayCal().timeInMillis)) }
        val cal = remember(dueMs) { DateUtils.calFrom(dueMs) }
        ShixuDialog(
            title = "改日期",
            onDismissRequest = { duePicker = false },
            confirmButton = {
                ShixuDialogButton("应用 ${picked.size} 条") {
                    // 先留一份「改之前」的整条快照（含 dueDate / sortOrder），撤回时原样写回
                    val before = picked.toList()
                    vm.setDueMany(before.map { it.id }, dueMs)
                    duePicker = false
                    selected = emptySet()
                    onUndoable(
                        if (dueMs == 0L) "已改为无期限" else "已改到 ${DateUtils.formatMonthDayCn(cal)}"
                    ) { vm.restoreMany(before) }
                }
            },
            dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel)) { duePicker = false } }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // ★ dueMs == 0 表示「无期限」。旧版照旧把 0 交给 calFrom 渲染成「1月1日」，
                //   提示语还写「已改到 1月1日」—— 显示与真实语义互相矛盾（存的其实是 0）。
                if (dueMs != 0L) {
                    StepArrow("‹") { dueMs = DateUtils.startOfDayMs(DateUtils.addDays(cal, -1).timeInMillis) }
                    Text(
                        DateUtils.formatMonthDayCn(cal) + " " + DateUtils.weekdayShortCn(cal),
                        fontSize = DialogText.Main, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    StepArrow("›") { dueMs = DateUtils.startOfDayMs(DateUtils.addDays(cal, 1).timeInMillis) }
                } else {
                    Text(
                        "不设日期",
                        fontSize = DialogText.Main, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ShixuChip(
                    selected = dueMs == 0L,
                    // 再点一次能取消（否则选完「无期限」就没有箭头可点、回不到有日期的状态）
                    onClick = {
                        dueMs = if (dueMs == 0L) DateUtils.startOfDayMs(DateUtils.todayCal().timeInMillis) else 0L
                    },
                    compact = true,
                    label = { Text(stringResource(R.string.todo_no_due), fontSize = 13.sp) }
                )
            }
        }
    }

    // ── 改优先级 ──
    if (priorityPicker) {
        var prio by remember { mutableIntStateOf(PRIORITY_LOW) }   // 「无」档取消，默认低
        ShixuDialog(
            title = "改优先级",
            onDismissRequest = { priorityPicker = false },
            confirmButton = {
                ShixuDialogButton("应用 ${picked.size} 条") {
                    val before = picked.toList()
                    vm.setPriorityMany(before.map { it.id }, prio)
                    priorityPicker = false
                    selected = emptySet()
                    onUndoable("已设为${priorityLabel(prio)}优先") {
                        vm.restoreMany(before)
                    }
                }
            },
            dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel)) { priorityPicker = false } }
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PRIORITY_LOW to "低", PRIORITY_MID to "中", PRIORITY_HIGH to "高"
                ).forEach { (p, label) ->
                    ShixuChip(
                        selected = prio == p, onClick = { prio = p }, compact = true,
                        leadingIcon = {
                            Box(Modifier.size(8.dp).background(priorityColor(p, dark), CircleShape))
                        },
                        label = { Text(label, fontSize = 13.sp) }
                    )
                }
            }
        }
    }
}

private fun doDelete(
    picked: List<Todo>, vm: TodoViewModel, clear: () -> Unit,
    onDeleted: (List<Todo>) -> Unit, onClose: () -> Unit,
) {
    if (picked.isEmpty()) return
    vm.deleteMany(picked.map { it.id })
    clear()
    onDeleted(picked)
    onClose()
}

/**
 * 一行可选待办。
 *
 * **刻度和待办列表的 TodoCard 逐项对齐**（用户 2026-11-04：「条目的上下宽度要和原本的待办界面一致，
 * 这样才更有连续性」）——所以这里不是「长得像」，而是同一串数值：
 *   - 玻璃体 18dp / elevation 5dp / strokeInset 2dp
 *   - 内层 Row：`heightIn(min = LIST_ROW_MIN_HEIGHT)` + `padding(horizontal = 8, vertical = 3)`
 *   - 优先级色条 3×22dp → 4dp → 勾选框（交互区 30dp）→ 标题 15sp/21sp + 副标题 11.5sp/17sp
 * 差别只有两个：勾选框表示「选中」而不是「完成」，以及副标题里多一个日期（整理时要用）。
 */
@Composable
private fun TodoBatchRow(
    t: Todo,
    checked: Boolean,
    dark: Boolean,
    onToggle: () -> Unit,
) {
    val shape = cardShape(CardRadius.ROW)
    val press = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth()
            .paperSurface(shape, dark = dark, elevation = 4.dp, strokeInset = 2.dp)
            .pressScale(press, pressed = Motion.PRESS_CARD),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (checked) Modifier.background(accent.copy(alpha = if (dark) 0.14f else 0.07f)) else Modifier)
                .clickable(interactionSource = press, indication = null) { onToggle() }
                .heightIn(min = LIST_ROW_MIN_HEIGHT)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 列表那张卡最左边是 24dp 拖动把手；整理页没有拖动，但把这 24dp 空出来，
            // 这样从列表长按进来时色条 / 勾选框 / 标题的横向位置一格都不动。
            Spacer(Modifier.width(24.dp))
            Box(
                Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp))
                    .background(priorityColor(t.priority, dark))
            )
            Spacer(Modifier.width(4.dp))
            // 与列表同款圆环勾选框、同款交互区 30dp（5.8.0：整理页勾选=多选，
            // 但形状跟待办列表保持同一族，别在同一条条目上用两套勾选框）
            ShixuCheck(
                checked = checked,
                onToggle = onToggle,
                tint = accent,
                dark = dark,
                contentDescription = if (checked) "取消选择" else "选择"
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = RowType.rowTitle(), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                )
                // 这一页里日期是有用信息（正常列表不显示日期），所以放副标题；字号行高与列表副标题一致
                val sub = buildList {
                    add(if (t.dueDate == 0L) "无期限" else DateUtils.formatMonthDayCn(DateUtils.calFrom(t.dueDate)))
                    if (priorityLabel(t.priority).isNotBlank()) add("${priorityLabel(t.priority)}优先")
                    if (t.done) add("已完成")
                }.joinToString(" · ")
                Text(sub, style = RowType.rowSub(), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rowSubTight())
            }
        }
    }
}

/** 底部操作区：三颗独立的玻璃胶囊（改日期 / 优先级 / 删除 N 条），浮在列表之上。 */
@Composable
private fun BatchActionBar(
    enabled: Boolean,
    count: Int,
    dark: Boolean,
    backdrop: LiquidGlassBackdropState?,
    modifier: Modifier = Modifier,
    onDue: () -> Unit,
    onPriority: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteText = if (count > 0) "删除 $count 条" else "删除"
    // 宽度与底栏胶囊同宽（291.6dp），三颗等分 + 8dp 间隔 → 每颗 (291.6−16)/3 ≈ 91.9dp
    Row(
        modifier.width(ACTION_BAR_W).height(ACTION_H),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassAction("改日期", enabled, false, dark, backdrop, Modifier.weight(1f), onDue)
        GlassAction("优先级", enabled, false, dark, backdrop, Modifier.weight(1f), onPriority)
        GlassAction(deleteText, enabled, true, dark, backdrop, Modifier.weight(1f), onDelete)
    }
}

/**
 * 底部毛玻璃带：把**身后那层内容**（整理页的列表）按同样偏移采样一份、模糊掉，
 * 顶上用透明度渐变压进来 —— 条目滑到这一带就被化开、渐渐消失，没有硬切线。
 *
 * 它和胶囊各自独立采样：带子负责「整条过渡」，胶囊负责「一颗颗浮起来的玻璃」。
 * API < 33 没有 AGSL，回落到同色渐隐（[liquidGlass] 同理）。
 *
 * 试过但**不可用**的两条路，别再走：
 * ① 盖一层 `colorScheme.background` 渐变 —— 本 App 的 background 是 `Color.Transparent`，等于没盖；
 * ② 降采样（缩到 1/10 再放大）—— 录出来的离屏层是黑的，整条带子变成黑板。
 */
@Composable
private fun Modifier.frostedBand(
    backdrop: LiquidGlassBackdropState?,
    dark: Boolean,
    height: Dp,
    fadeIn: Dp,
    blurRadius: Dp = 26.dp,
): Modifier {
    val density = LocalDensity.current
    val fadePx = with(density) { fadeIn.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }
    val effect = remember(blurPx) { BlurEffect(blurPx, blurPx, TileMode.Clamp) }
    val off = remember { mutableStateOf(Offset.Zero) }
    val fallback = remember(dark) { if (dark) Color(0xFF0E0F12) else BgLight }
    val usable = backdrop != null && supportsLiquidGlass
    return this
        .height(height)
        .onGloballyPositioned { c ->
            backdrop?.coordinates?.let { off.value = offsetOf(it, c) }
        }
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = if (usable) effect else null
        }
        .drawWithContent {
            drawContent()
            if (size.height <= 0f) return@drawWithContent
            if (usable) {
                translate(-off.value.x, -off.value.y) { drawLayer(backdrop!!.layer) }
            }
            // 磨砂感：压一点点底色
            drawRect(color = fallback.copy(alpha = if (dark) 0.38f else 0.20f))
            // 顶部渐入：0 → 完全不透明。
            // ⚠️ 必须到 1.0：留一点透明，底下那份**没糊**的列表就透上来，
            // 看到的是「糊底 + 清晰残影」的鬼影，等于白做。
            val f = (fadePx / size.height).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0f),
                    f to Color.White.copy(alpha = 0f),
                    1f to Color.White
                ),
                blendMode = BlendMode.DstIn
            )
        }
}

/**
 * 一颗玻璃胶囊按钮：主色实心（删除）或玻璃体（其余），按下缩一点点。
 *
 * 玻璃体走 [liquidGlass]：真实采样**身后那层内容**（整理页的列表）再模糊 + 折射，
 * 所以条目从胶囊底下滑过时会被糊开 —— 这正是「浮在条目之上的玻璃」，
 * 而不是原来那种「按钮底下垫一块不透明底、跟列表硬切一刀」。
 * API < 33 没有 AGSL，[liquidGlass] 会自动变成空 Modifier，此时回落到 [glassSurface]。
 */
@Composable
private fun GlassAction(
    text: String,
    enabled: Boolean,
    danger: Boolean,
    dark: Boolean,
    backdrop: LiquidGlassBackdropState?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    // 整理页三颗要的是「胶囊」：半径吃满半高（56 / 2），两端即完整半圆。
    // 这里必须用 RoundedCornerShape 而不是 CapsuleShape —— liquidGlass 的 shader 只认圆角矩形，
    // 传超椭圆 Outline 会退回成直角矩形（2026-11-10 实测过）。
    val shape = remember { RoundedCornerShape(ACTION_H / 2) }
    val press = remember { MutableInteractionSource() }
    val glassable = enabled && !danger && backdrop != null && supportsLiquidGlass
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        danger -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier.height(ACTION_H)
            .then(
                if (enabled) Modifier.shadow(
                    7.dp, shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.13f)
                ) else Modifier
            )
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick)
            .pressScale(press, pressed = Motion.PRESS_BUTTON),
        contentAlignment = Alignment.Center
    ) {
        // ① 玻璃/底色层（独立一层，文字不能画进去，否则会被一起模糊）
        when {
            glassable -> Box(
                // 统一配方（见 ui/LiquidCapsule.kt）：折射=高度的 1/4、模糊=高度的 0.4。
                Modifier.matchParentSize().liquidCapsule(backdrop!!, shape, ACTION_H, dark)
            )
            !enabled -> Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f), shape)
            )
            // 2026-11-10 用户：删除按钮改成和弹窗统一的枣红色（AccentDeep = #C32136）
            danger -> Box(Modifier.matchParentSize().background(AccentDeep, shape))
            else -> Box(
                Modifier.matchParentSize().glassSurface(shape, dark = dark, elevation = 5.dp)
            )
        }
        // ② 文字
        Text(
            text, fontSize = ACTION_FONT, fontWeight = FontWeight.SemiBold, color = fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

/** 弹窗里的 ‹ › 步进箭头（与表头同款观感，字号放大一档便于点）。 */
@Composable
private fun StepArrow(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** 勾选圈：选中=实心主色 + 白勾；未选=一圈细描边。Canvas 画，不用图标资源。 */
@Composable
private fun BatchRing(checked: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Box(Modifier.size(21.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(21.dp)) {
            val r = size.minDimension / 2f
            if (checked) {
                drawCircle(accent, radius = r)
                val p = Path().apply {
                    moveTo(size.width * 0.29f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.72f, size.height * 0.35f)
                }
                drawPath(
                    p, Color.White,
                    style = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                drawCircle(
                    ring, radius = r - size.width * 0.055f,
                    style = Stroke(width = size.width * 0.075f),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}
