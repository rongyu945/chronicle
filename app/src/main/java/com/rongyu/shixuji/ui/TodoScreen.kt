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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
/** 「今」按钮：「＋」上方 56dp（2026-10-15 用户：再近一点；原 64dp 两钮之间空 20dp，现在空 12dp） */
private val FAB_TODAY_OFFSET = 56.dp
private val FAB_TODAY_BOTTOM = FAB_PLUS_BOTTOM + FAB_TODAY_OFFSET
private val LIST_BOTTOM = 180.dp

/** 列表条目统一最小高度：待办 / 循环 / 账目三种行完全一致（介于旧「无时间待办」与旧「带时间账目」之间） */
internal val LIST_ROW_MIN_HEIGHT = 48.dp

class TodoViewModel : ViewModel() {
    private val dao = AppContainer.database.todoDao()
    private val accDao = AppContainer.database.accountDao()
    private val loopDao = AppContainer.database.loopTodoDao()
    val todos = dao.observeAll()
    val loops = loopDao.observeAll()

    /** 只取某一天的账目（下推到 SQL） */
    fun accountsBetween(start: Long, end: Long) = accDao.observeBetween(start, end)

    /**
     * 新增待办：排到最上面（用户 2026-10-14 定稿）。
     *
     * 做法是给它一个「比全表最小 sortOrder 再小 1」的手动顺序值：
     *  - 列表按库顺序渲染（openOrder 由 dayOpen 初始化），所以它在当天列表里恒是第一行；
     *  - 越新的条目值越小 → 新建的永远压在上一条新建的上面；
     *  - 与 drag 重排兼容：拖动会给参与拖动的条目重编号为正数，新条目仍是负数、仍在最上面；
     *  - 重建 App 后位置不变（以前只改内存里的顺序，重启就掉回当天列表最底下）。
     */
    fun add(title: String, detail: String, dueDate: Long, priority: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(Todo(title = title, detail = detail, dueDate = dueDate, priority = priority,
                sortOrder = dao.minSortOrder() - 1))
        }

    /**
     * 新增并回传新条的 id（提示条的「撤回」要按 id 删）。
     * 先算出 id 再插入 —— 不用等 insert 返回，界面仍是「保存即关弹窗」。
     */
    suspend fun addReturningId(title: String, detail: String, dueDate: Long, priority: Int): Long {
        val nextId = dao.nextId()
        dao.insert(Todo(id = nextId, title = title, detail = detail, dueDate = dueDate, priority = priority))
        return nextId
    }

    fun update(id: Long, title: String, detail: String, dueDate: Long, priority: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(id, title, detail, dueDate)
            dao.setPriority(id, priority)
        }

    /** 修改并回传「改之前」的完整快照 + 改之前的手动顺序（提示条的「撤回」要整条还原） */
    suspend fun updateReturningOld(
        id: Long, title: String, detail: String, dueDate: Long, priority: Int
    ): Todo? {
        val old = dao.getById(id) ?: return null
        dao.update(id, title, detail, dueDate)
        dao.setPriority(id, priority)
        return old
    }

    /** 整条覆盖（撤回修改用；id / sortOrder 保留原样） */
    fun restoreFull(t: Todo) = viewModelScope.launch(Dispatchers.IO) {
        dao.upsert(t.copy(id = t.id))
        dao.setSortOrder(t.id, t.sortOrder)
    }

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch(Dispatchers.IO) { dao.setDone(id, done) }

    /**
     * 勾选 / 取消勾选待办：同时写「完成时刻」。
     * 完成 → 立刻沉到当天列表最下方的「已完成」分区（用户要的自动收缩），
     * doneAt = 完成那一刻，已完成分区按它倒序（最近完成的排最前）。
     */
    fun toggleFull(t: Todo, done: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        dao.setDoneFull(t.id, done, if (done) System.currentTimeMillis() else t.doneAt)
    }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }

    /** 撤销删除：按原 id 原字段插回（upsert：万一 id 已被新条占用也不会崩） */
    fun restore(t: Todo) = viewModelScope.launch(Dispatchers.IO) {
        dao.upsert(t.copy(id = t.id))
        dao.setSortOrder(t.id, t.sortOrder)
    }

    /**
     * 手动拖动排序：整批一次性编号（顺序表里的 id 各自拿到自己的下标）。
     * 一次落库（不逐条 update 触发 Flow 抖动），且**不覆盖**未参与拖动的条目原有顺序 ——
     * 老实现给所有条目重编号、顺序表里又没有已完成那些，会把它们的顺序抹成同一档。
     *
     * 编号从 **0** 起（原来从 1 起）：新条目用的是负数（见 add），若是 1..n
     * 就会把「没拖过 = sortOrder 0」的老条目全顶到拖动过的条目前面 ——
     * 重建 App（openOrder 重置、改按库顺序渲染）时顺序就乱了。0 起则库顺序与手动顺序一致。
     */
    fun reorder(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            ids.forEachIndexed { i, id -> dao.setSortOrder(id, i) }
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
    // 悬浮玻璃钮（＋「今」）自己的背景层：只录**列表内容那一层**，不含按钮本身。
    // ⚠️ 绝不能借底栏那份 backdrop —— 那份把按钮自己也录进去了，玻璃采样自己 = 渲染树递归，
    //    实测直接 native 栈溢出崩溃（libhwui RenderNode::prepareTreeImpl 递归 500+ 层）。
    val fabLayer = rememberLiquidGlassBackdrop()
    val todos by vm.todos.collectAsState(initial = emptyList())
    val loops by vm.loops.collectAsState(initial = emptyList())

    // 界面状态用 rememberSaveable：切换底部 Tab 再回来不会被重置
    var levelName by rememberSaveable { mutableStateOf(TodoLevel.WEEK.name) }
    val level = TodoLevel.valueOf(levelName)
    var shownYear by rememberSaveable { mutableIntStateOf(DateUtils.todayCal().get(Calendar.YEAR)) }
    var shownMonth by rememberSaveable { mutableIntStateOf(DateUtils.todayCal().get(Calendar.MONTH)) }
    var shownDayMs by rememberSaveable { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val shownDay = remember(shownDayMs) { DateUtils.calFrom(shownDayMs) }

    // ★ 每个「显示日」一个独立的 LazyListState（普通 Map 当缓存，不是 Compose 状态）。
    //
    // 为什么不继续用「一个共享 state + 切天时 scrollToItem(0) 纠回来」：
    // PeriodSwipe 左右两侧那两页用的是各自的 state（天生停在第 0 条），所以手指拖动时
    // 看到的确实是新一天的第一条；可 onCommit 那一刻中心页换成共享 state，
    // 而它记的还是**上一天**滚到的位置（比如滚在「过往未完成」那一段）——
    // 松手后画面先显示新一天的中段、再被纠回顶部，观感就是
    // 「先显示过往待办（或没有类别的条目），过一会才刷新出当天的内容」。
    // 按天缓存 state 后，侧页和落位后的中心页是**同一个实例**：滑进来看到什么就留什么，
    // 一帧都不用纠。（离开的日子会被清掉，所以下次再翻回来仍然是从顶部开始。）
    val dayStates = remember { mutableMapOf<Long, LazyListState>() }
    val shownDayStartMs = DateUtils.startOfDayMs(shownDayMs)
    fun dayListState(dayMs: Long) = dayStates.getOrPut(dayMs) { LazyListState() }
    val listState = dayListState(shownDayStartMs)
    LaunchedEffect(shownDayStartMs) {
        val c = DateUtils.calFrom(shownDayStartMs)
        val keep = (-1..1).map { DateUtils.startOfDayMs(DateUtils.addDays(c, it).timeInMillis) }.toHashSet()
        dayStates.keys.retainAll(keep)
    }

    // 大标题折叠：列表一滚动，标题从 28sp 收到 17sp，并浮出半透明标题条
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapse")

    // 切换日期后列表从最顶部开始显示由「按天缓存 LazyListState」天然保证（见上），
    // 不需要再在切天时 scrollToItem(0) 去纠 —— 那种「先显示旧位置、再纠回来」
    // 正是用户看到的「先显示中段、过一会才刷新」。

    // 保存后把当天列表滚回顶部，保证刚加的那条一定在眼前
    val scrollScope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<Todo?>(null) }
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

    /**
     * 保存后只有「那条的日期」和当前显示日**不是同一天**才跳过去。
     * 同一天时原地不动（用户 2026-10-12 明确：新建后不要自动跳到那条置顶展示的位置，
     * 保持当前页面；回顶由「当天数据变化」那个 LaunchedEffect 统一负责）。
     */
    fun jumpToDayIfNeeded(dueMs: Long) {
        val target = if (dueMs == 0L) DateUtils.startOfDayMs(today.timeInMillis) else DateUtils.startOfDayMs(dueMs)
        if (target == dayStartMs) return
        jumpToDay(dueMs)
    }

    // 返回键：年/月视图按返回降级到周视图，周视图不拦截（交给系统）
    BackHandler(enabled = level != TodoLevel.WEEK) {
        if (level == TodoLevel.YEAR) { shownYear = shownDay.get(Calendar.YEAR); shownMonth = shownDay.get(Calendar.MONTH) }
        levelName = TodoLevel.WEEK.name
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().recordBackdrop(fabLayer)) {
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
                        // 跨周时整条周条的 7 格会同时换掉。原来这里套了一层 Crossfade(150ms)：
                        // 它只在跨周时命中，会把整条周条**同时合成两份**并各建离屏图层——
                        // 实测跨周最差帧 ≈1095ms（周内 ≈323ms），正是"跨周卡一下"的元凶之一。
                        // 去掉后数字瞬换，反而顺。
                        WeekNavView(
                            evDays, pageDay,
                            onSelectDay = { shownDayMs = it.timeInMillis },
                            dragFrac = { dayFrac.floatValue }
                        )
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
                        val pageDay = remember(shownDayMs, off) {
                            DateUtils.addDays(DateUtils.calFrom(shownDayMs), off)
                        }
                        // 按「这一天」取 state：中心页和侧页拿到的是同一个实例，
                        // 所以滑进来时看到的位置就是落位后保留的位置（不再跳回上一天的偏移）
                        val slotState = dayListState(DateUtils.startOfDayMs(pageDay.timeInMillis))
                        // 侧页（不是当前显示日）只要它那天变了，就先归零：
                        // 保证「跟着手指滑进来的那一页」永远是从第一条开始的，
                        // 而且落位后中心页用的就是这个 state —— 松手瞬间不会跳到上一天的偏移。
                        // 归零用 scrollToItem（瞬时）而不是动画：侧页此刻在屏幕外，没有人会看到这一下，
                        // 但用动画的话落位后可能还在滚，正是「先显示中段、过一会才刷新」的来源。
                        LaunchedEffect(off, DateUtils.startOfDayMs(pageDay.timeInMillis)) {
                            if (off != 0) slotState.scrollToItem(0)
                        }
                        // ★ 回顶同账目页：挂在「当天待办集合」上，等数据真的变了再滚。
                        // 旧写法是保存时 scrollScope.launch { listState.scrollToItem(0) }，
                        // 和写库并行 —— 滚动常先执行、数据还没到，等 Flow 推来新条目时
                        // LazyColumn 按 key 把原先可见那一条锚在原位，新条目插到它上面看不见。
                        // 这里仍然只由「数据变化」触发（不在保存回调里滚），但用
                        // animateScrollToItem 而不是 requestScrollToItem：后者是**瞬间**跳位，
                        // 停在底部时整页从尾瞬移到头，用户 2026-10-14 反馈「跳起来很难看」；
                        // 动画版是平滑滑上去，落点同样是第 0 条（新的那条就在最上面）。
                        val pageStartMs = DateUtils.startOfDayMs(pageDay.timeInMillis)
                        val pageEndMs = DateUtils.endOfDayMs(pageStartMs)
                        val pageKey = remember(todos, pageStartMs, pageEndMs) {
                            val inDay = todos.count { it.dueDate in pageStartMs..pageEndMs }
                            val overdue = todos.count { it.dueDate != 0L && it.dueDate < pageStartMs && !it.done }
                            val noDue = if (DateUtils.isSameDay(pageDay, today)) todos.count { it.dueDate == 0L && !it.done } else 0
                            inDay to (overdue + noDue)
                        }
                        LaunchedEffect(off, pageStartMs, pageKey) {
                            if (off == 0) slotState.animateScrollToItem(0)
                        }
                        DayTodoList(
                            todos = todos, loops = loops, shownDay = pageDay,
                            // 循环待办：完成状态是「本周」维度、不是分区，照旧直接切
                            onToggle = vm::toggle,
                            // 待办勾选：完成即沉底（自动收缩到「已完成」分区）
                            onToggleTodo = vm::toggleFull,
                            onOpenDetail = { detailTarget = it },
                            onToggleLoop = vm::toggleLoop,
                            onDeleteWithUndo = { t ->
                                vm.delete(t.id)
                                undo.show("已删除：${t.title}") { vm.restore(t) }
                            },
                            // 取消完成（并给撤回）：整条还原 + 完成时刻一起还原，
                            // 否则「撤回」再勾上时 doneAt 会是老时间，条目被顶到已完成区最前面
                            onRestoreTodo = { t ->
                                vm.restoreFull(t)
                                undo.show("已取消完成：${t.title}") { vm.toggleFull(t, true) }
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
        // 悬浮按钮：磨砂玻璃版（和底栏同一套液态玻璃），仍自己画圆不用 FloatingActionButton
        //（FAB 的阴影会浮出多边形亮边）。涟漪被全局关掉了，按压缩放提供点下去的反馈。
        GlassCircleButton(
            label = "+",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
            fallbackColor = MaterialTheme.colorScheme.primary,
            labelColor = Color.White,
            contentDescription = "新建待办",
            onClick = { showAdd = true },
            backdrop = fabLayer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).padding(bottom = FAB_PLUS_BOTTOM)
        )
        // “今”按钮：不在今天或不在周视图时出现（月/年视图翻远了也能一键回来）
        // "今"按钮：进出用缩放+淡入淡出，不要突然出现/消失
        AnimatedVisibility(
            visible = !isToday || level != TodoLevel.WEEK,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val dark = isDarkTheme
            GlassCircleButton(
                label = "今",
                tint = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.34f),
                fallbackColor = if (dark) Color(0xFF3A3A3C) else Color.White,
                labelColor = MaterialTheme.colorScheme.primary,
                labelSize = 14.sp, labelBold = true,
                contentDescription = "回到今天",
                onClick = { goToday() },
                backdrop = fabLayer,
                modifier = Modifier.padding(20.dp).padding(bottom = FAB_TODAY_BOTTOM)
            )
        }
        // 上移到两个悬浮按钮上方：原来 84dp 会盖住 bottom=74dp 的记账摘要小字
        UndoHost(undo, Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp))
    }

    // 新增 / 修改 共用一个弹窗，点一下卡片不再直接删除
    //
    // 保存（含提示条）**必须在这个页面的作用域里跑**：弹窗自己的 rememberCoroutineScope
    // 会随弹窗关闭一起被取消 —— 之前把落库写在弹窗里，就会出现「界面弹了已添加、
    // 库里其实没这条」和「撤回按钮点了没反应」（撤回要先把新条写进库拿到 id）。
    if (showAdd) {
        TodoEditDialog(editing = null, defaultDueMs = dayStartMs,
            onSave = { t, d, due, p ->
                // 新增不再弹撤回提示条（用户 2026-10-12：只保留删除的撤回），直接落库即可
                vm.add(t, d, due, p)
                jumpToDayIfNeeded(due)
                // 回顶不在这里做（会和写库赛跑，见列表区那段注释）
            },
            onDismiss = { showAdd = false })
    }
    editTarget?.let { t ->
        TodoEditDialog(editing = t,
            onSave = { nt, nd, due, p ->
                scrollScope.launch {
                    val old = vm.updateReturningOld(t.id, nt, nd, due, p)
                    if (old != null) undo.show("已保存：$nt") { vm.restoreFull(old) }
                }
                jumpToDayIfNeeded(due)
                // 回顶同上：交给「当天数据变化」统一处理
            },
            onDismiss = { editTarget = null })
    }

    // 点击/长按条目 → 详情弹窗：完整显示标题、备注、优先级、日期，可直接修改/删除
    detailTarget?.let { t ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { detailTarget = null },
            title = { Text("待办详情", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (t.detail.isNotBlank()) {
                        Text("备注：${t.detail}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (priorityLabel(t.priority).isNotBlank()) {
                        Text("优先级：${priorityLabel(t.priority)}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (t.dueDate != 0L) "日期：${DateUtils.formatDateCn(t.dueDate)}" else "日期：无期限",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (t.done) {
                        Text("已完成", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { detailTarget = null }) { Text("关闭") }
                    TextButton(onClick = { editTarget = t; detailTarget = null }) { Text("修改") }
                    TextButton(
                        onClick = {
                            vm.delete(t.id)
                            detailTarget = null
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

/** 三级：周视图 */
@Composable
private fun WeekNavView(
    evDays: Set<Long>, shownDay: Calendar,
    onSelectDay: (Calendar) -> Unit,
    dragFrac: () -> Float = { 0f }
) {
    // remember：跨周时这一页的 7 天全变，不记住会每帧重算 7 次 Calendar 拷贝（周条很贵）
    val start = remember(shownDay) { DateUtils.startOfWeek(shownDay) }
    val days = remember(start) { (0..6).map { DateUtils.addDays(start, it) } }
    val today = remember(start) { DateUtils.todayCal() }
    val selIdx = remember(days, shownDay) {
        days.indexOfFirst { DateUtils.isSameDay(it, shownDay) }.coerceAtLeast(0)
    }
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
                            // 淡出必须更快（×1.7）：白字在灰底/浅底上对比度低，拖远一点还留 2 成白
                            // 就整格发糊，看不清是几号。
                            val num = "${d.get(Calendar.DAY_OF_MONTH)}"
                            val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            Text(num, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = weight,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(num, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = weight,
                                color = Color.White,
                                modifier = Modifier.graphicsLayer {
                                    val near = (1f - abs(i - (selIdx - dragFrac()))).coerceIn(0f, 1f)
                                    alpha = (near * 1.7f).coerceIn(0f, 1f)
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

/** 日列表：当日待办（未完成） + 每日固定 + 过往未完成 + 无期限 + 已完成（沉底、默认折叠） */
@Composable
private fun DayTodoList(
    todos: List<Todo>, loops: List<LoopTodo>, shownDay: Calendar,
    onToggle: (Long, Boolean) -> Unit,
    onToggleTodo: (Todo, Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    onToggleLoop: (Long, Boolean) -> Unit,
    onDeleteWithUndo: (Todo) -> Unit, onRestoreTodo: (Todo) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onDeleteLoop: (Long) -> Unit, state: LazyListState, listModifier: Modifier = Modifier
) {
    // 归一化到当天零点：即使 shownDayMs 万一带了时刻，过滤区间也仍然是整天
    val startMs = DateUtils.startOfDayMs(shownDay.timeInMillis)
    val endMs = DateUtils.endOfDayMs(startMs)
    val isToday = DateUtils.isToday(shownDay)
    // ⚠️ 这些过滤**必须 remember**：本组件被 PeriodSwipe 同时画三份（前天/今天/后天），
    // 不 remember 的话每帧重组都把整个 todos 表过滤 3×5 遍、循环的列表还要 O(n²) 找位，
    // 跨周时数据全变 → 一次重组要做 15 遍全表扫描 → 掉帧（实测跨周 100% jank、单帧 200ms，
    // 周内只有 46ms）。改成按输入 remember 后只在数据/日期真的变了才算一次。
    val dayAll = remember(todos, startMs, endMs) { todos.filter { it.dueDate in startMs..endMs } }
    // 未完成在上、已完成（自动）沉到最下面那个分区
    val dayOpen = remember(dayAll) { dayAll.filter { !it.done } }
    val dayDone = remember(dayAll) { dayAll.filter { it.done }.sortedByDescending { it.doneAt } }
    val overdueTodos = remember(todos, startMs) {
        todos.filter { it.dueDate != 0L && it.dueDate < startMs && !it.done }
    }
    val noDueTodos = remember(todos, isToday) {
        if (isToday) todos.filter { it.dueDate == 0L && !it.done } else emptyList()
    }
    val shownIsoDow = remember(shownDay, startMs) { DateUtils.isoDayOfWeek(shownDay) }
    val dayLoops = remember(loops, shownIsoDow) { loops.filter { it.dayOfWeek == shownIsoDow } }
    // 「已完成」默认折叠（跨天/重启都回到收起）；「过往未完成」默认展开、折叠按钮保留
    //（4.5.0 曾把它默认收起，用户 2026-10-09 要求改回默认展开）
    var doneExpanded by rememberSaveable(startMs) { mutableStateOf(false) }
    var overdueExpanded by rememberSaveable(startMs) { mutableStateOf(true) }

    // 手动拖动排序：本地顺序（id 列表），拖动结束后整体落库
    var openOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(dayOpen.map { it.id }) {
        openOrder = dayOpen.map { it.id }
    }
    // 显示顺序就按这份顺序表来 —— 拖动中「显示」和「排序依据」是同一份数据，才不会对不上号
    // remember：不然每帧重组都重算（旧写法 openOrder.none{} 嵌在 filter 里还是 O(n²)）
    val orderedOpen = remember(openOrder, dayOpen) {
        val byId = dayOpen.associateBy { it.id }
        val inOrder = openOrder.toHashSet()
        // 不在顺序表里的（刚加进来的）排在最前，否则新加的会落到列表最底下、用户看不到
        dayOpen.filter { t -> t.id !in inOrder } + openOrder.mapNotNull { byId[it] }
    }

    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    // 行高必须实测：原来写死 48dp 而行高是 56dp（+8dp 间距 = 64dp），
    // 每格差 16dp 会一路累积，手指越往下拖越对不上（用户反馈的「体验不好」）。
    // 取「没被拖走的那条」量（拖起的那条加了缩放，量它会偏大）。
    // 初值 0 = 还没量到，用到时候再按 64dp 兜底（别写 64f —— 那是像素、不是 dp）。
    var rowPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dragScope = rememberCoroutineScope()

    // 记录内容实际滚到哪了。自动滚动发生在协程里（下面 dragScope.launch），
    // 而 onDragBy 是同步的 —— 不把滚动量减回来，每帧都会重复判断、
    // 被拖那条会被一次性推过好几格（用户看到的就是「一拖就跳」）。
    var edgeAccum by remember { mutableFloatStateOf(0f) }

    fun onDragStart(id: Long) { dragId = id; dragDy = 0f; edgeAccum = 0f }

    fun onDragBy(delta: Float) {
        val id = dragId ?: return
        dragDy += delta

        // 拖到列表上下边缘 → 自动滚动
        // 补偿方式：内容往上滚 dyEdge，行的基准位置也跟着上移，所以 dragDy 要同步 +dyEdge，
        // 让被拖的那条始终贴在手指底下（不补偿的话行会从手指下滑走）。
        // edgeAccum 记着「已经滚掉多少、还没从 dragDy 里扣掉的量」，每帧最多扣 rowH/2 ——
        // 不扣的话一次滚动会被反复计入，被拖那条瞬间窜过好几格。
        val vh = state.layoutInfo.viewportSize.height.toFloat()
        val step = 14f
        val dyEdge = when {
            dragDy < -vh * 0.35f -> -step
            dragDy > vh * 0.35f -> step
            else -> 0f
        }
        if (dyEdge != 0f) {
            dragScope.launch {
                state.scrollBy(dyEdge)
                edgeAccum += dyEdge
            }
        }
        // 一次性从 dragDy 里减掉累积的滚动量（最多减一行，避免一帧跨多格）
        if (edgeAccum != 0f) {
            val rowH0 = if (rowPx > 1f) rowPx else with(density) { 64.dp.toPx() }
            val take = edgeAccum.coerceIn(-rowH0, rowH0)
            dragDy += take
            edgeAccum -= take
        }

        var cur = openOrder.indexOf(id)
        if (cur < 0) return
        // 换位阈值 = 实测行高（含间距）的一半；循环写法应付「一帧里拖过好几格」
        // rowPx 兜底：万一没量到（回调没触发），退到 64dp 对应的像素，别用 64f 这种"像素值"
        val rowH = if (rowPx > 1f) rowPx else with(density) { 64.dp.toPx() }
        var list = openOrder
        while (dragDy > rowH / 2f && cur < list.size - 1) {
            list = list.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
            dragDy -= rowH; cur++
        }
        while (dragDy < -rowH / 2f && cur > 0) {
            list = list.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
            dragDy += rowH; cur--
        }
        if (list !== openOrder) openOrder = list
    }

    fun onDragFinish() {
        val id = dragId
        dragId = null; dragDy = 0f; edgeAccum = 0f
        if (id != null) onReorder(openOrder)
    }

    LazyColumn(
        state = state,
        // 和周条同处一个 Column：必须用 weight 吃剩余高度，用 fillMaxSize 会让列表底部伸出页面
        modifier = listModifier,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(orderedOpen, key = { _, t -> t.id }) { _, t ->
            val isDragging = dragId == t.id
            TodoRow(
                t = t,
                onToggleTodo = onToggleTodo, onOpenDetail = onOpenDetail,
                onDeleteWithUndo = onDeleteWithUndo,
                onMeasured = { h ->
                    if (dragId != t.id && h > 0) rowPx = h + with(density) { 8.dp.toPx() }
                },
                dragModifier = Modifier.pointerInput(t.id, openOrder.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(t.id) },
                        onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                        onDragEnd = { onDragFinish() },
                        onDragCancel = { onDragFinish() }
                    )
                },
                modifier = Modifier
                    // 位置动画：让「让位」的那几条平滑滑到新位置。
                    // 被拖起的那条**必须关掉** —— 它的位置由手指的 translationY 直接控制，
                    // 再叠一层弹簧动画会两边打架、视觉上就是「画面突然闪一下」。
                    // （原来是反的：拖动条开动画、让位条关动画，正好写颠倒了。）
                    // 平时（非拖动）也一律关：切天时「过往未完成/无期限/每日固定」的 key
                    // 跨天不变、只是顺序变了，位置动画会把它们从旧位置动到新位置 →
                    // 看起来就是「条目先窜到顶格、再回落」。
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = if (dragId != null && !isDragging) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = null
                    )
                    .zIndex(if (isDragging) 2f else 0f)
                    // 拖起时放大 2% + 加投影（抬起来的反馈），其余卡片靠弹簧动画让位
                    .graphicsLayer {
                        translationY = if (isDragging) dragDy else 0f
                        val sc = if (isDragging) 1.02f else 1f
                        scaleX = sc; scaleY = sc
                    }
            )
        }
        if (dayLoops.isNotEmpty()) {
            item(key = "loopHead") {
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
            item(key = "overdueHead") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { overdueExpanded = !overdueExpanded }
                        .padding(top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("过往未完成 (${overdueTodos.size})", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(if (overdueExpanded) "收起 ▴" else "展开 ▾", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (overdueExpanded) {
                items(overdueTodos, key = { "over-" + it.id }) { t ->
                    TodoRow(
                        t = t,
                        onToggleTodo = onToggleTodo, onOpenDetail = onOpenDetail,
                        onDeleteWithUndo = onDeleteWithUndo, onMeasured = {},
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        )
                    )
                }
            }
        }
        if (noDueTodos.isNotEmpty()) {
            item(key = "noDueHead") {
                Text("无期限", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            }
            items(noDueTodos, key = { "nodue-" + it.id }) { t ->
                TodoRow(
                    t = t,
                    onToggleTodo = onToggleTodo, onOpenDetail = onOpenDetail,
                    onDeleteWithUndo = onDeleteWithUndo, onMeasured = {},
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    )
                )
            }
        }
        // 已完成：沉到列表最下方，默认折叠成一行
        if (dayDone.isNotEmpty()) {
            item(key = "doneHead") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { doneExpanded = !doneExpanded }
                        .padding(top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已完成 (${dayDone.size})", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(if (doneExpanded) "收起 ▴" else "展开 ▾", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (doneExpanded) {
                items(dayDone, key = { "done-" + it.id }) { t ->
                    TodoRow(
                        t = t,
                        onToggleTodo = onToggleTodo, onOpenDetail = onOpenDetail,
                        onDeleteWithUndo = onDeleteWithUndo, onMeasured = {},
                        restore = onRestoreTodo,
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        )
                    )
                }
            }
        }
    }
}

/**
 * 一条待办（列表行 + 左右滑操作）。
 *
 * [restore] 非空 = 这条在「已完成」分区里：左滑第一下是「取消完成」，
 * 勾选框取消勾选时走整条还原（连完成时刻一起还原，否则刚取消的又会被顶到分区最前面）。
 */
@Composable
private fun TodoRow(
    t: Todo, onToggleTodo: (Todo, Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    onDeleteWithUndo: (Todo) -> Unit, onMeasured: (Int) -> Unit,
    restore: ((Todo) -> Unit)? = null,
    modifier: Modifier = Modifier, dragModifier: Modifier = Modifier
) {
    SwipeRow(
        // 已完成分区里的行，左滑第一下是「取消完成」（原来只有「完成」，点了没意义）
        actions = if (restore != null) listOf("取消完成" to Color(0xFF8E8E93), "删除" to Accent)
        else listOf("完成" to Green, "删除" to Accent),
        onAction = { i ->
            if (i == 0) { if (restore != null) restore(t) else onToggleTodo(t, true) }
            else onDeleteWithUndo(t)
        },
        enabled = false,
        modifier = modifier
    ) {
        TodoCard(
            t = t,
            onToggle = { done ->
                // 取消已完成 → 走「带撤回的还原」；其余（勾上、未完成区取消）直接写库
                if (!done && restore != null) restore(t) else onToggleTodo(t, done)
            },
            onOpenDetail = onOpenDetail,
            showHandle = restore == null,
            reorderHandleModifier = dragModifier,
            measureModifier = Modifier.onGloballyPositioned { onMeasured(it.size.height) }
        )
    }
}

@Composable
private fun LoopTodoCard(loop: LoopTodo, onToggle: (Long, Boolean) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Card(modifier = Modifier.fillMaxWidth()
            .glassSurface(shape, dark = isDarkTheme, elevation = 5.dp, strokeInset = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        Row(Modifier.heightIn(min = LIST_ROW_MIN_HEIGHT).padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
                Checkbox(checked = loop.doneThisWeek, onCheckedChange = { onToggle(loop.id, it) })
            }
            Column(Modifier.weight(1f)) {
                Text(loop.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (loop.doneThisWeek) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (loop.doneThisWeek) TextDecoration.LineThrough else null)
                Text("循环 · ${DateUtils.WEEK_CN[loop.dayOfWeek - 1]}", fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("🔁", fontSize = 14.sp)
        }
    }
}

/**
 * 一条待办卡片。
 *
 * [showHandle] 拖动把手只在「未完成」分区出现（已完成分区不给把手，不给拖动）。
 * [measureModifier] 让外层量到这张卡的真实高度，拖动换位阈值按它算（写死会导致越拖越偏）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoCard(
    t: Todo, onToggle: (Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    reorderHandleModifier: Modifier = Modifier, showHandle: Boolean = false,
    measureModifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val dark = isDarkTheme
    val cardPress = remember { MutableInteractionSource() }
    Card(modifier = Modifier.fillMaxWidth()
            .glassSurface(shape, dark = isDarkTheme, elevation = 5.dp, strokeInset = 2.dp)
            .pressScale(cardPress, pressed = 0.97f)
            .then(measureModifier),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        Row(Modifier.heightIn(min = LIST_ROW_MIN_HEIGHT).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHandle) {
                // 拖动把手：单独吃掉长按手势，不打扰卡片点击。
                // 触摸区 24×36dp（够大好拖），**不画底色** —— 那层极淡灰底用户说很丑已去掉。
                // 宽度 26→24dp 是为了把勾选框整体左移、给标题腾地方（用户 2026-10-15 要求）。
                Box(
                    reorderHandleModifier.size(width = 24.dp, height = 36.dp),
                    contentAlignment = Alignment.Center
                ) { Text("≡", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // 其余区域：点击打开详情（标题一行放不下时截断，完整内容在弹窗里看）
            Row(
                Modifier.weight(1f).clickable(
                    interactionSource = cardPress, indication = null
                ) { onOpenDetail(t) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pc = priorityColor(t.priority, dark)
                Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(pc))
                // 勾选框整体左移、占宽收窄：色条↔勾选框间距 8→4dp，勾选框交互区 34→30dp。
                // 两项合计给标题多腾出约 12dp（15sp 下一个汉字约 15dp，接近一个字）。
                // Checkbox 的方块（20dp）在交互区里居中，所以交互区变小 → 方块也自动往左挪。
                Spacer(Modifier.width(4.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 30.dp) {
                    Checkbox(checked = t.done, onCheckedChange = { onToggle(it) })
                }
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null)
                    // 列表条目只显示备注与优先级，**绝不显示日期**。
                    // 用户明确要求过多次：条目本来就按天分区展示，日期是冗余信息；
                    // 未来日期的待办一旦带上日期，看起来就像"给条目加了时间"（被批评过）。
                    val sub = buildList {
                        if (t.detail.isNotBlank()) add(t.detail)
                        if (priorityLabel(t.priority).isNotBlank()) add("${priorityLabel(t.priority)}优先")
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * 新增 / 修改待办（editing == null 表示新增）。
 *
 * 自己不落库、也不弹提示条：只把结果交给 [onSave]，由调用方在**页面的**作用域里执行。
 * 弹窗这个作用域在关闭时会被取消，落库写在这里会丢（见调用处的说明）。
 */
@Composable
private fun TodoEditDialog(
    editing: Todo?, defaultDueMs: Long = 0L,
    onSave: (title: String, detail: String, dueMs: Long, priority: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val key = editing?.id
    // 输入框用 TextFieldValue 而不是纯 String：这样才能把光标位置设到末尾，
    // 长备注获得焦点时视图跟着滚到最后（用户要的「点进去直接看到末尾」）。
    var titleV by remember(key) { mutableStateOf(TextFieldValue(editing?.title ?: "")) }
    var detailV by remember(key) { mutableStateOf(TextFieldValue(editing?.detail ?: "")) }
    val title = titleV.text
    val detail = detailV.text
    // 备注默认收起（用户很少填）：已有备注时自动展开，否则点「＋ 备注」才出现
    var showDetail by remember(key) { mutableStateOf(!(editing?.detail ?: "").isBlank()) }
    var priority by remember(key) { mutableIntStateOf(editing?.priority ?: PRIORITY_NONE) }
    var dueMs by remember(key) {
        mutableLongStateOf(editing?.dueDate?.takeIf { it != 0L } ?: defaultDueMs)
    }
    val dueCal = remember(dueMs) { DateUtils.calFrom(dueMs) }

    /**
     * 点输入框 → 光标和视图一起落到整条文本最后。
     *
     * 两个坑：
     *  1. 不能在 onFocusChanged 里直接改 —— 点击时 TextField 自己会把光标放到**手指点的位置**，
     *     那一手在焦点回调之后，直接改会被覆盖。
     *  2. 只用「获得焦点」当触发不够 —— 输入框已经处于聚焦状态时再点一下不会触发焦点变化，
     *     光标就停在手指点的位置（实测：先点一下、再点中间，字会插在中间）。
     * 所以改成监听 InteractionSource 的「按下抬起」，每次点击都延一帧后强制设到末尾。
     */
    fun toEnd(v: TextFieldValue) = v.copy(selection = TextRange(v.text.length))

    // 光标定位走 jumpCursorToEnd（定义在 CursorEnd.kt）：
    // 它挂在 Final 通道上，确定性地在 TextField 自己那次定位之后生效，不靠"延两帧"赌时序
    // —— 原来那版在模拟器上通过、在真机上失效（用户反馈"点了还是没跳过去"）。
    // 顺便保留原有的自动聚焦：弹窗一打开就把光标放到末尾，接着就能改。
    LaunchedEffect(key) {
        withFrameNanos { }
        withFrameNanos { }
        titleV = toEnd(titleV)
    }

    AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新增待办" else "修改待办", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = titleV, onValueChange = { titleV = it }, label = { Text("项目") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().jumpCursorToEnd({ titleV }) { titleV = it })
                if (showDetail) {
                    OutlinedTextField(value = detailV, onValueChange = { detailV = it },
                        label = { Text("备注（可选）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().jumpCursorToEnd({ detailV }) { detailV = it })
                    // 备注区收尾：一条 1px 分隔线 + 线下方右侧的「收起」
                    //（原来「收起」嵌在输入框右边，和内容挤在一起，看不清是哪一层）
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("收起 ▴", fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showDetail = false }
                                .padding(horizontal = 6.dp, vertical = 4.dp))
                    }
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
                        onSave(title.trim(), detail.trim(), dueMs, priority)
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
