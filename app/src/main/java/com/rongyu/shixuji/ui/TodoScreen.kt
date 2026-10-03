package com.rongyu.shixuji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.data.LoopTodo
import com.rongyu.shixuji.data.Todo
import com.rongyu.shixuji.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** 玻璃底栏 + 悬浮按钮占用的底部高度，列表底部留白要够 */
private val FAB_PLUS_BOTTOM = 92.dp
private val FAB_TODAY_BOTTOM = 146.dp
private val LIST_BOTTOM = 180.dp

class TodoViewModel : ViewModel() {
    private val dao = AppContainer.database.todoDao()
    private val accDao = AppContainer.database.accountDao()
    private val loopDao = AppContainer.database.loopTodoDao()
    val todos = dao.observeAll()
    val loops = loopDao.observeAll()

    /** 只取某一天的账目（下推到 SQL） */
    fun accountsBetween(start: Long, end: Long) = accDao.observeBetween(start, end)

    fun add(title: String, detail: String, dueDate: Long, priority: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(Todo(title = title, detail = detail, dueDate = dueDate, priority = priority))
        }

    fun update(id: Long, title: String, detail: String, dueDate: Long, priority: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(id, title, detail, dueDate)
            dao.setPriority(id, priority)
        }

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch(Dispatchers.IO) { dao.setDone(id, done) }
    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }

    /** 撤销删除：按原 id 原字段插回 */
    fun restore(t: Todo) = viewModelScope.launch(Dispatchers.IO) {
        dao.insert(t.copy(id = t.id))
        dao.setSortOrder(t.id, t.sortOrder)
    }

    /** 手动拖动排序：按当前顺序整体重编号 */
    fun reorder(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            ids.forEachIndexed { i, id -> dao.setSortOrder(id, i + 1) }
        }
    }

    fun toggleLoop(id: Long, done: Boolean) = viewModelScope.launch(Dispatchers.IO) { loopDao.setDone(id, done) }
    fun deleteLoop(id: Long) = viewModelScope.launch(Dispatchers.IO) { loopDao.delete(id) }
}

// 三级导航：年 -> 月 -> 周(含日详情)，默认周
enum class TodoLevel { YEAR, MONTH, WEEK }

@Composable
fun TodoScreen(
    onOpenAccount: () -> Unit = {},
    todayTick: Long = 0L,
    vm: TodoViewModel = viewModel()
) {
    val todos by vm.todos.collectAsState(initial = emptyList())
    val loops by vm.loops.collectAsState(initial = emptyList())

    // 界面状态用 rememberSaveable：切换底部 Tab 再回来不会被重置
    var levelName by rememberSaveable { mutableStateOf(TodoLevel.WEEK.name) }
    val level = TodoLevel.valueOf(levelName)
    var shownYear by rememberSaveable { mutableIntStateOf(DateUtils.todayCal().get(Calendar.YEAR)) }
    var shownMonth by rememberSaveable { mutableIntStateOf(DateUtils.todayCal().get(Calendar.MONTH)) }
    var shownDayMs by rememberSaveable { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val shownDay = remember(shownDayMs) { DateUtils.calFrom(shownDayMs) }

    // 大标题折叠：列表一滚动，标题从 28sp 收到 17sp，并浮出半透明标题条
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapse")

    // 保存后把当天列表滚回顶部，保证刚加的那条一定在眼前
    val scrollScope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Todo?>(null) }
    var editTarget by remember { mutableStateOf<Todo?>(null) }
    val undo = rememberUndoController()

    val today = remember(todayTick) { DateUtils.todayCal() }
    val isToday = DateUtils.isSameDay(shownDay, today)

    val dayStartMs = DateUtils.startOfDayMs(shownDayMs)
    val dayEndMs = DateUtils.endOfDayMs(shownDayMs)
    val dayAccFlow = remember(dayStartMs, dayEndMs) { vm.accountsBetween(dayStartMs, dayEndMs) }
    val dayAcc by dayAccFlow.collectAsState(initial = emptyList())
    val dayOut = dayAcc.filter { it.type == 0 }.sumOf { it.amountCents }
    val dayIn = dayAcc.filter { it.type == 1 }.sumOf { it.amountCents }

    // 当月账目（按展示的月份）→ 底栏显示"本月盈余"
    val mStart = DateUtils.monthStartMs(shownYear, shownMonth)
    val mEnd = DateUtils.monthEndMs(shownYear, shownMonth)
    val monthAccFlow = remember(mStart, mEnd) { vm.accountsBetween(mStart, mEnd) }
    val monthAcc by monthAccFlow.collectAsState(initial = emptyList())
    val monthSurplus = monthAcc.sumOf {
        if (it.type == 0) -it.amountCents else it.amountCents
    }

    // 每天的事项数量：年视图热力图用（一次算好，格子 O(1) 查）
    val evCountOfMonth: Map<Int, Map<Int, Int>> = remember(todos) {
        val m = HashMap<Int, HashMap<Int, Int>>()
        todos.forEach { t ->
            if (t.dueDate != 0L) {
                val c = DateUtils.calAt(t.dueDate)
                val k = DateUtils.monthKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
                val d = c.get(Calendar.DAY_OF_MONTH)
                m.getOrPut(k) { HashMap() }.let { it[d] = (it[d] ?: 0) + 1 }
            }
        }
        m
    }
    val evDays: Set<Long> = remember(todos) {
        todos.asSequence().filter { it.dueDate != 0L }
            .map { DateUtils.startOfDayMs(it.dueDate) }.toHashSet()
    }

    // 日期范围：2008 ~ 2100（用户要求）
    fun clampYear(y: Int) = y.coerceIn(2008, 2100)

    fun shiftMonth(delta: Int) {
        val (y, m) = DateUtils.addMonths(shownYear, shownMonth, delta)
        shownYear = clampYear(y); shownMonth = m
    }

    fun goToday() {
        shownDayMs = today.timeInMillis
        shownYear = today.get(Calendar.YEAR)
        shownMonth = today.get(Calendar.MONTH)
        levelName = TodoLevel.WEEK.name
    }

    /**
     * 新增/修改保存后，直接跳到那条待办所在的日期（周视图）。
     * 否则：在月/年视图里加、或者当前停在别的日期时，回到原视图看不到刚加的东西 ——
     * 用户反馈的「添加了东西之后却不显示」就是这么来的。
     */
    fun jumpToDay(dueMs: Long) {
        val target = if (dueMs == 0L) today.timeInMillis else DateUtils.startOfDayMs(dueMs)
        shownDayMs = target
        val c = DateUtils.calFrom(target)
        shownYear = c.get(Calendar.YEAR)
        shownMonth = c.get(Calendar.MONTH)
        levelName = TodoLevel.WEEK.name
    }

    // 返回键：年/月视图按返回降级到周视图，周视图不拦截（交给系统）
    BackHandler(enabled = level != TodoLevel.WEEK) {
        if (level == TodoLevel.YEAR) { shownYear = shownDay.get(Calendar.YEAR); shownMonth = shownDay.get(Calendar.MONTH) }
        levelName = TodoLevel.WEEK.name
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TodoHeader(
                level, shownYear, shownMonth, shownDay,
                titleSize = (28f - 11f * collapseT).sp,
                collapsed = collapseT > 0.5f,
                onPrev = {
                    when (level) {
                        TodoLevel.YEAR -> shownYear = clampYear(shownYear - 1)
                        TodoLevel.MONTH -> shiftMonth(-1)
                        else -> shownDayMs = DateUtils.addDays(shownDay, -1).timeInMillis
                    }
                },
                onNext = {
                    when (level) {
                        TodoLevel.YEAR -> shownYear = clampYear(shownYear + 1)
                        TodoLevel.MONTH -> shiftMonth(1)
                        else -> shownDayMs = DateUtils.addDays(shownDay, 1).timeInMillis
                    }
                },
                onTitleClick = {
                    when (level) {
                        TodoLevel.WEEK -> {
                            shownYear = shownDay.get(Calendar.YEAR)
                            shownMonth = shownDay.get(Calendar.MONTH)
                            levelName = TodoLevel.MONTH.name
                        }
                        TodoLevel.MONTH -> levelName = TodoLevel.YEAR.name
                        TodoLevel.YEAR -> {}
                    }
                })
            // 跟手翻页：内容跟着手指横向走，左右两侧各渲染一个相邻周期。
            // weight(1f) 只吃标题之外的剩余高度（用 fillMaxSize 会撑出屏幕、底部被切）
            // 层级切换（年⇄月⇄周）淡入淡出过渡，别硬切
            Crossfade(
                targetState = level,
                modifier = Modifier.weight(1f),
                animationSpec = tween(200),
                label = "levelFade"
            ) { lv ->
            if (lv == TodoLevel.WEEK) {
                // 周视图拆成两块区域、各管各的横滑：
                //   上 = 周条区（在条上左右滑 → 切周），下 = 待办列表区（在列表上左右滑 → 切天）
                // 列表区把「已滑几格」报进 dayFrac，周条上那枚红圆据此跟着手指走
                val dayFrac = remember { mutableFloatStateOf(0f) }
                Column(Modifier.fillMaxSize()) {
                    // —— 周条区：左右滑动切换一周 ——
                    // 注意必须给固定高度：PeriodSwipe 内部是 fillMaxSize，
                    // 不给高度它会在 Column 里吃光剩余空间，下面的日列表就被挤成 0 高度，
                    // 结果"在列表上滑"实际还在切周的滑动层里（用户报的核心 bug）
                    // 注意必须给固定高度：PeriodSwipe 内部是 fillMaxSize，
                    // 不给高度它会在 Column 里吃光剩余空间，下面的日列表就被挤成 0 高度，
                    // 结果"在列表上滑"实际还在切周的滑动层里（用户报过的核心 bug）。
                    // 68dp = 上下内边距 8 + 星期行 15 + 圆 32 + 点(5+4) 9，留一点余量给字号放大档
                    PeriodSwipe(
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        onCommit = { dir ->
                            // 周条换周维持原方向：手往左 = 下一周（用户明确要求只改切天、不动换周）
                            shownDayMs = DateUtils.addDays(DateUtils.calFrom(shownDayMs), 7 * dir).timeInMillis
                        }
                    ) { off ->
                        val pageDay = remember(shownDayMs, off) {
                            DateUtils.addDays(DateUtils.calFrom(shownDayMs), 7 * off)
                        }
                        // 从周日切到下周一 = 跨周：整条周条的 7 格会同时换掉，硬切会出现
                        // "卡一下"。这里按「周」做一次快速淡换，跨周就不再是硬跳。
                        val weekKey = remember(pageDay) { DateUtils.startOfWeek(pageDay).timeInMillis }
                        Crossfade(targetState = weekKey, animationSpec = tween(150), label = "weekFade") {
                            WeekNavView(
                                evDays, pageDay,
                                onSelectDay = { shownDayMs = it.timeInMillis },
                                dragFrac = { dayFrac.floatValue }
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // —— 待办列表区：左右滑动切换一天 ——
                    // 方向和换周/年/月一致：手往左 = 下一天（内容从右边进来）。
                    // 周条上那枚红圆因此是朝「新进来的那一天」滑（位置 = 选中格 - 手指进度）。
                    PeriodSwipe(
                        modifier = Modifier.weight(1f),
                        onProgress = { f -> dayFrac.floatValue = f },
                        onCommit = { dir ->
                            shownDayMs = DateUtils.addDays(DateUtils.calFrom(shownDayMs), dir).timeInMillis
                        }
                    ) { off ->
                        // 中心页沿用共享 listState（大标题折叠跟着它走），两侧各自独立
                        val slotState = if (off == 0) listState else remember(off) { LazyListState() }
                        val pageDay = remember(shownDayMs, off) {
                            DateUtils.addDays(DateUtils.calFrom(shownDayMs), off)
                        }
                        DayTodoList(
                            todos = todos, loops = loops, shownDay = pageDay,
                            onToggle = vm::toggle,
                            onLongPress = { deleteTarget = it },
                            onToggleLoop = vm::toggleLoop,
                            onDeleteWithUndo = { t ->
                                vm.delete(t.id)
                                undo.show("已删除：${t.title}") { vm.restore(t) }
                            },
                            onDoneWithUndo = { t ->
                                vm.toggle(t.id, true)
                                undo.show("已完成：${t.title}") { vm.toggle(t.id, false) }
                            },
                            onReorder = { ids -> vm.reorder(ids) },
                            onDeleteLoop = vm::deleteLoop,
                            state = slotState,
                            listModifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // 年视图/月视图：整页横滑切年/切月
                PeriodSwipe(modifier = Modifier.fillMaxSize(), onCommit = { dir ->
                    when (lv) {
                        TodoLevel.YEAR -> shownYear = clampYear(shownYear + dir)
                        else -> shiftMonth(dir)
                    }
                }) { off ->
                    val slotState = if (off == 0) listState else remember(off) { LazyListState() }
                    when (lv) {
                        TodoLevel.YEAR -> {
                            val pageYear = clampYear(shownYear + off)
                            YearNavView(evCountOfMonth, pageYear, slotState,
                                { y, m -> shownYear = y; shownMonth = m; levelName = TodoLevel.MONTH.name })
                        }
                        TodoLevel.MONTH -> {
                            val (py, pm) = DateUtils.addMonths(shownYear, shownMonth, off)
                            val pageYear = clampYear(py)
                            MonthNavView(evCountOfMonth, pageYear, pm,
                                { d ->
                                    shownDayMs = DateUtils.month(pageYear, pm)
                                        .apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
                                    levelName = TodoLevel.WEEK.name
                                })
                        }
                        else -> {}
                    }
                }
            }
            }
        }
        // 日历底部记账摘要：放在玻璃底栏上方
        AccSummaryBar(monthSurplus, { onOpenAccount() },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp))
        // 悬浮按钮直接用实心圆画，不用 FloatingActionButton（FAB 的阴影会浮出多边形亮边）。
        // 涟漪被全局关掉了，所以这里用按压缩放补上点下去的反馈
        val addPress = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.BottomEnd)
                .padding(20.dp).padding(bottom = FAB_PLUS_BOTTOM).size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = addPress, indication = null, role = Role.Button) { showAdd = true }
                .pressScale(addPress)
                .semantics { contentDescription = "新建待办" },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 22.sp, color = Color.White) }
        // “今”按钮：不在今天或不在周视图时出现（月/年视图翻远了也能一键回来）
        // "今"按钮：进出用缩放+淡入淡出，不要突然出现/消失
        AnimatedVisibility(
            visible = !isToday || level != TodoLevel.WEEK,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val dark = isDarkTheme
            val todayPress = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .padding(20.dp).padding(bottom = FAB_TODAY_BOTTOM).size(44.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF3A3A3C) else Color.White)
                    .border(0.8.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFFE5E5EA), CircleShape)
                    .clickable(interactionSource = todayPress, indication = null, role = Role.Button) { goToday() }
                    .pressScale(todayPress)
                    .semantics { contentDescription = "回到今天" },
                contentAlignment = Alignment.Center
            ) { Text("今", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
        // 上移到两个悬浮按钮上方：原来 84dp 会盖住 bottom=74dp 的记账摘要小字
        UndoHost(undo, Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp))
    }

    // 新增 / 修改 共用一个弹窗，点一下卡片不再直接删除
    if (showAdd) {
        TodoEditDialog(vm = vm, editing = null, defaultDueMs = dayStartMs,
            onSaved = { due, t ->
                jumpToDay(due)
                scrollScope.launch { listState.scrollToItem(0) }
                // 明确回执：告诉他加到了哪一天（也能一眼看出日期有没有跑偏）
                // 原来这里会弹一条"已添加：xxx"的黑色提示条（当初排查"加了不显示"用的），
                // 用户觉得又丑又多余 —— 现在保存后直接关闭弹窗并把列表滚到顶，反馈已经足够
            },
            onDismiss = { showAdd = false })
    }
    editTarget?.let { t ->
        TodoEditDialog(vm = vm, editing = t,
            onSaved = { due, t2 ->
                jumpToDay(due)
                scrollScope.launch { listState.scrollToItem(0) }
                undo.show("已保存：$t2 · ${DateUtils.formatDateCn(due)}") { /* 长按可修改/删除 */ }
            },
            onDismiss = { editTarget = null })
    }

    deleteTarget?.let { t ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("这条待办", fontWeight = FontWeight.Bold) },
            text = {
                Text(buildString {
                    append("「${t.title}」")
                    if (t.dueDate != 0L) append(" · ${DateUtils.formatDateCn(t.dueDate)}")
                })
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                    TextButton(onClick = { editTarget = t; deleteTarget = null }) { Text("修改") }
                    TextButton(
                        onClick = {
                            vm.delete(t.id)
                            deleteTarget = null
                            undo.show("已删除：${t.title}") { vm.restore(t) }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                }
            }
        )
    }
}

/** 日历底部记账摘要：一行小字，点击跳转记账页 */
@Composable
private fun AccSummaryBar(surplus: Long, onClick: () -> Unit, mod: Modifier) {
    val dot = if (surplus >= 0) "＋" else "−"
    Text(
        "本月盈余 $dot${formatCents(if (surplus < 0) -surplus else surplus)}",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = mod.fillMaxWidth().clickable { onClick() }.padding(vertical = 3.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TodoHeader(
    level: TodoLevel, shownYear: Int, shownMonth: Int, shownDay: Calendar,
    onPrev: () -> Unit, onNext: () -> Unit, onTitleClick: () -> Unit,
    titleSize: TextUnit = 28.sp, collapsed: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth()
            // 不再加底色：列表在自己的区域里滚动，不会钻到标题下面；加一层白底反而很丑
            .background(Color.Transparent)
            .padding(start = 20.dp, end = 18.dp, top = if (collapsed) 8.dp else 14.dp, bottom = if (collapsed) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val title: String = when (level) {
            TodoLevel.WEEK -> "${shownDay.get(Calendar.MONTH) + 1}月"
            TodoLevel.MONTH -> "${shownYear}年"
            TodoLevel.YEAR -> "${shownYear}年"
        }
        val clickable = level != TodoLevel.YEAR
        Text(title, fontSize = titleSize, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = if (clickable) Modifier.weight(1f).clickable { onTitleClick() } else Modifier.weight(1f))

        val dayLabel = when (level) {
            TodoLevel.WEEK -> "${shownDay.get(Calendar.MONTH) + 1}月${shownDay.get(Calendar.DAY_OF_MONTH)}日"
            TodoLevel.MONTH -> "${shownYear}年${shownMonth + 1}月"
            TodoLevel.YEAR -> ""
        }
        val clickSize = if (level == TodoLevel.WEEK) 26.dp else 30.dp
        val arrowSp = if (level == TodoLevel.WEEK) 20.sp else 24.sp
        Box(Modifier.size(clickSize).clip(RoundedCornerShape(8.dp)).clickable { onPrev() }
            .semantics { contentDescription = "上一个" }, contentAlignment = Alignment.Center) {
            Text("‹", fontSize = arrowSp, color = MaterialTheme.colorScheme.primary)
        }
        if (dayLabel.isNotBlank()) {
            Text(dayLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        Box(Modifier.size(clickSize).clip(RoundedCornerShape(8.dp)).clickable { onNext() }
            .semantics { contentDescription = "下一个" }, contentAlignment = Alignment.Center) {
            Text("›", fontSize = arrowSp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

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

/** 一级：年视图（日期格按当天事项数量着色 = 热力图） */
@Composable
private fun YearNavView(
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

private fun rowsNeeded(year: Int, m0: Int): Int {
    val cells = DateUtils.leadingBlanks(year, m0) + DateUtils.daysInMonth(year, m0)
    return (cells + 6) / 7          // 不足一行也算一行
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
private fun MonthNavView(evCountOfMonth: Map<Int, Map<Int, Int>>, year: Int, month: Int, onSelectDay: (Int) -> Unit) {
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

/** 三级：周视图 */
@Composable
private fun WeekNavView(
    evDays: Set<Long>, shownDay: Calendar,
    onSelectDay: (Calendar) -> Unit,
    dragFrac: () -> Float = { 0f }
) {
    val start = DateUtils.startOfWeek(shownDay)
    val days = (0..6).map { DateUtils.addDays(start, it) }
    val today = DateUtils.todayCal()
    val selIdx = days.indexOfFirst { DateUtils.isSameDay(it, shownDay) }.coerceAtLeast(0)
    val circle = 32.dp
    // 千万不要写 fillMaxSize：周条下面还有日列表，占满整页会让列表拿到 0 高度（列表什么都画不出来）
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        // 星期几单独一行：显式行高（不给会继承主题 24sp），下面红圆的纵坐标也因此是确定的
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(DateUtils.weekdayShortCn(d), fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val colPx = constraints.maxWidth / 7f
            val circlePx = with(LocalDensity.current) { circle.toPx() }
            // 选中圆是整条共用的一枚：横向位置 =（选中格 - 手指进度）。
            // 减号是因为「手往左 = 下一天」，而下一天在右边 —— 圆要朝新进来的那一天滑。
            // 进度在布局阶段读 → 拖动时只重排版、不触发重组，和底栏那枚圆是同一套做法。
            Box(
                Modifier
                    .offset {
                        val x = (selIdx - dragFrac()) * colPx + (colPx - circlePx) / 2f
                        IntOffset(x.roundToInt(), 0)
                    }
                    .size(circle)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, d ->
                    val isToday = DateUtils.isSameDay(d, today)
                    val hasEv = evDays.contains(d.timeInMillis)
                    Column(Modifier.weight(1f).clickable { onSelectDay(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(circle).background(
                            // 今天给一层淡红底；选中那格由共用的红圆负责，别叠两层
                            if (isToday && i != selIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                            // 数字画两层：常态色 + 白字。白字层按「被红圆盖住的程度」淡入，
                            // 所以红圆滑到一半时两个日期各白一半，不会出现"圆走了字还是白的"穿帮。
                            val num = "${d.get(Calendar.DAY_OF_MONTH)}"
                            val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            Text(num, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = weight,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(num, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = weight,
                                color = Color.White,
                                modifier = Modifier.graphicsLayer {
                                    alpha = (1f - abs(i - (selIdx - dragFrac()))).coerceIn(0f, 1f)
                                })
                        }
                        Box(Modifier.padding(top = 5.dp).size(4.dp).background(
                            if (hasEv) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                    }
                }
            }
        }
    }
}

/** 日列表：当日待办 + 循环待办 + 过往未完成 + 无期限 */
@Composable
private fun DayTodoList(
    todos: List<Todo>, loops: List<LoopTodo>, shownDay: Calendar,
    onToggle: (Long, Boolean) -> Unit, onLongPress: (Todo) -> Unit, onToggleLoop: (Long, Boolean) -> Unit,
    onDeleteWithUndo: (Todo) -> Unit, onDoneWithUndo: (Todo) -> Unit, onReorder: (List<Long>) -> Unit,
    onDeleteLoop: (Long) -> Unit, state: LazyListState, listModifier: Modifier = Modifier
) {
    // 归一化到当天零点：即使 shownDayMs 万一带了时刻，过滤区间也仍然是整天
    val startMs = DateUtils.startOfDayMs(shownDay.timeInMillis)
    val endMs = DateUtils.endOfDayMs(startMs)
    val isToday = DateUtils.isToday(shownDay)
    val dayTodos = todos.filter { it.dueDate in startMs..endMs }
    val overdueTodos = todos.filter { it.dueDate != 0L && it.dueDate < startMs && !it.done }
    val noDueTodos = if (isToday) todos.filter { it.dueDate == 0L && !it.done } else emptyList()
    val dayLoops = loops.filter { it.dayOfWeek == DateUtils.isoDayOfWeek(shownDay) }

    // 手动拖动排序：本地顺序（id 列表），拖动结束后整体落库
    var localOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(dayTodos.map { it.id }) {
        localOrder = dayTodos.map { it.id }
    }
    val ordered = remember(localOrder, dayTodos) {
        val byId = dayTodos.associateBy { it.id }
        // 没被手动排过序的（sortOrder=0，即刚加进来的）排在最前 —— 否则新加的会落到列表最底下，
        // 长一点的列表里用户根本看不到，看起来就像"加了没反应"
        val rest = dayTodos.filter { t -> localOrder.none { it == t.id } }
        val arranged = localOrder.mapNotNull { byId[it] }
        rest + arranged
    }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 62.dp.toPx() }

    fun onDragStart(id: Long) { dragId = id; dragDy = 0f }
    fun onDragBy(delta: Float) {
        val id = dragId ?: return
        dragDy += delta
        val cur = localOrder.indexOf(id)
        if (cur < 0) return
        if (dragDy > rowPx / 2 && cur < localOrder.size - 1) {
            localOrder = localOrder.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
            dragDy -= rowPx
        } else if (dragDy < -rowPx / 2 && cur > 0) {
            localOrder = localOrder.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
            dragDy += rowPx
        }
    }
    fun onDragFinish() {
        val id = dragId
        dragId = null; dragDy = 0f
        if (id != null) onReorder(localOrder)
    }

    LazyColumn(
        state = state,
        // 和周条同处一个 Column：必须用 weight 吃剩余高度，用 fillMaxSize 会让列表底部伸出页面
        modifier = listModifier,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(ordered, key = { _, t -> t.id }) { index, t ->
            val isDragging = dragId == t.id
            SwipeRow(
                actions = listOf("完成" to Green, "删除" to Accent),
                onAction = { i -> if (i == 0) onDoneWithUndo(t) else onDeleteWithUndo(t) },
                enabled = false,
                modifier = Modifier
                    // 位置动画只在拖动排序时开：切天时"过往未完成/无期限/每日固定"这些条目的 key
                    // 跨天是一样的、只是顺序变了，位置动画会把它们从旧位置动到新位置 →
                    // 看起来就是"条目先窜到顶格、再回落"。平时一律关掉。
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = if (isDragging) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = null
                    )
                    .zIndex(if (isDragging) 2f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragDy else 0f }
            ) {
                TodoCard(
                    t = t,
                    onToggle = onToggle,
                    onLongPress = onLongPress,
                    showHandle = true,
                    reorderHandleModifier = Modifier.pointerInput(t.id, localOrder) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(t.id) },
                            onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                            onDragEnd = { onDragFinish() },
                            onDragCancel = { onDragFinish() }
                        )
                    }
                )
            }
        }
        if (dayLoops.isNotEmpty()) {
            item {
                Text("每日固定", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            items(dayLoops, key = { "loop-" + it.id }) { loop ->
                SwipeRow(
                    actions = listOf("完成" to Green, "删除" to Accent),
                    onAction = { i ->
                        if (i == 0) onToggleLoop(loop.id, true) else onDeleteLoop(loop.id)
                    },
                    enabled = false,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    )
                ) { LoopTodoCard(loop, onToggleLoop) }
            }
        }
        if (overdueTodos.isNotEmpty()) {
            item {
                Text("过往未完成", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            }
            items(overdueTodos, key = { it.id }) { t ->
                SwipeRow(
                    actions = listOf("完成" to Green, "删除" to Accent),
                    onAction = { i -> if (i == 0) onDoneWithUndo(t) else onDeleteWithUndo(t) },
                    enabled = false,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    )
                ) { TodoCard(t, onToggle, onLongPress, Modifier) }
            }
        }
        if (noDueTodos.isNotEmpty()) {
            item {
                Text("无期限", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            }
            items(noDueTodos, key = { it.id }) { t ->
                SwipeRow(
                    actions = listOf("完成" to Green, "删除" to Accent),
                    onAction = { i -> if (i == 0) onDoneWithUndo(t) else onDeleteWithUndo(t) },
                    enabled = false,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    )
                ) { TodoCard(t, onToggle, onLongPress, Modifier) }
            }
        }
    }
}

@Composable
private fun LoopTodoCard(loop: LoopTodo, onToggle: (Long, Boolean) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Card(modifier = Modifier.fillMaxWidth()
            .glassSurface(shape, dark = isDarkTheme, elevation = 5.dp, strokeInset = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = loop.doneThisWeek, onCheckedChange = { onToggle(loop.id, it) })
            Column(Modifier.weight(1f)) {
                Text(loop.title, fontSize = 15.sp,
                    color = if (loop.doneThisWeek) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (loop.doneThisWeek) TextDecoration.LineThrough else null)
                Text("循环 · ${DateUtils.WEEK_CN[loop.dayOfWeek - 1]}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("🔁", fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoCard(
    t: Todo, onToggle: (Long, Boolean) -> Unit, onLongPress: (Todo) -> Unit,
    reorderHandleModifier: Modifier = Modifier, showHandle: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)
    val dark = isDarkTheme
    val cardPress = remember { MutableInteractionSource() }
    Card(modifier = Modifier.fillMaxWidth()
            .glassSurface(shape, dark = isDarkTheme, elevation = 5.dp, strokeInset = 2.dp)
            .pressScale(cardPress, pressed = 0.97f),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHandle) {
                // 拖动把手：单独吃掉长按手势，避免和卡片"长按弹菜单"同时触发
                Box(reorderHandleModifier.size(width = 22.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                    Text("≡", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 其余区域：长按弹「取消 / 修改 / 删除」
            Row(
                Modifier.weight(1f).combinedClickable(
                    interactionSource = cardPress, indication = null,
                    onClick = {}, onLongClick = { onLongPress(t) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pc = priorityColor(t.priority, dark)
                Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(pc))
                Spacer(Modifier.width(8.dp))
                Checkbox(checked = t.done, onCheckedChange = { onToggle(t.id, it) })
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 15.sp,
                        color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null)
                    val sub = buildList {
                        if (t.detail.isNotBlank()) add(t.detail)
                        if (priorityLabel(t.priority).isNotBlank()) add("${priorityLabel(t.priority)}优先")
                        if (t.dueDate != 0L && !DateUtils.isToday(DateUtils.calFrom(t.dueDate))) add(DateUtils.formatDateCn(t.dueDate))
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 新增 / 修改待办（editing == null 表示新增） */
@Composable
private fun TodoEditDialog(
    vm: TodoViewModel, editing: Todo?, defaultDueMs: Long = 0L,
    onSaved: (dueMs: Long, title: String) -> Unit = { _, _ -> }, onDismiss: () -> Unit
) {
    val key = editing?.id
    var title by remember(key) { mutableStateOf(editing?.title ?: "") }
    var detail by remember(key) { mutableStateOf(editing?.detail ?: "") }
    // 备注默认收起（用户很少填）：已有备注时自动展开，否则点「＋ 备注」才出现
    var showDetail by remember(key) { mutableStateOf(!(editing?.detail ?: "").isBlank()) }
    var priority by remember(key) { mutableIntStateOf(editing?.priority ?: PRIORITY_NONE) }
    var dueMs by remember(key) {
        mutableLongStateOf(editing?.dueDate?.takeIf { it != 0L } ?: defaultDueMs)
    }
    val dueCal = remember(dueMs) { DateUtils.calFrom(dueMs) }

    AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新增待办" else "修改待办", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("项目") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (showDetail) {
                    OutlinedTextField(value = detail, onValueChange = { detail = it },
                        label = { Text("备注（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text("收起", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { showDetail = false })
                        })
                } else {
                    TextButton(onClick = { showDetail = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("＋ 备注", fontSize = 13.sp)
                    }
                }
                Text("优先级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PRIORITY_NONE to "无", PRIORITY_LOW to "低",
                        PRIORITY_MID to "中", PRIORITY_HIGH to "高"
                    ).forEach { (p, label) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label, fontSize = 13.sp) },
                            leadingIcon = if (p == PRIORITY_NONE) null else {
                                { Box(Modifier.size(9.dp).background(priorityColor(p, isDarkTheme), CircleShape)) }
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { dueMs = DateUtils.addDays(dueCal, -1).timeInMillis },
                        contentAlignment = Alignment.Center) {
                        Text("‹", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(DateUtils.formatMonthDayCn(dueCal), fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 6.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { dueMs = DateUtils.addDays(dueCal, 1).timeInMillis },
                        contentAlignment = Alignment.Center) {
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        if (editing == null) vm.add(title, detail, dueMs, priority)
                        else vm.update(editing.id, title, detail, dueMs, priority)
                        onSaved(dueMs, title)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp)
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
