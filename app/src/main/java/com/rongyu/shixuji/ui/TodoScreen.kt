package com.rongyu.shixuji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animate
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
import com.rongyu.shixuji.ui.motion.UnifiedWeekDayNavigator
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import com.rongyu.shixuji.ui.motion.toLocalDate
import com.rongyu.shixuji.ui.motion.toStartMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/** 玻璃底栏 + 悬浮按钮占用的底部高度，列表底部留白要够 */
private val FAB_PLUS_BOTTOM = FAB_CLEARANCE   // 跟着底栏等比缩放（原来写死 92dp，底栏收缩后会悬空）
/** 「今」按钮：「＋」上方 56dp（2026-10-15 用户：再近一点；原 64dp 两钮之间空 20dp，现在空 12dp） */
private val FAB_TODAY_OFFSET = 56.dp
private val FAB_TODAY_BOTTOM = FAB_PLUS_BOTTOM + FAB_TODAY_OFFSET

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
        viewModelScope.launch(Dispatchers.IO) { insertNew(title, detail, dueDate, priority) }

    /**
     * 落库并返回新条的 id。
     *
     * 用 Room `@Insert` **返回的真实 rowId**，不要像以前那样先 `SELECT MAX(id)+1` 猜一个：
     * ① 猜出来的 id 与插入不是原子的（并发/交错的两次新增会算出同一个 id，第二条撞主键报错）；
     * ② 删掉当前最大 id 的那条之后，下一个新增会**复用**这个 id —— 一旦复用，
     *    「按 id 删除的撤回」就会删掉/覆盖另一条（撤回删除时 upsert 会覆盖同 id 的新条目）。
     */
    private suspend fun insertNew(title: String, detail: String, dueDate: Long, priority: Int): Long =
        dao.insert(
            Todo(
                title = title, detail = detail, dueDate = dueDate, priority = priority,
                sortOrder = dao.minSortOrder() - 1
            )
        )

    fun update(id: Long, title: String, detail: String, dueDate: Long, priority: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            val old = dao.getById(id)
            dao.update(id, title, detail, dueDate)
            dao.setPriority(id, priority)
            if (old != null) pinTopIfDayChanged(id, old.dueDate, dueDate)
        }

    /** 修改并回传「改之前」的完整快照 + 改之前的手动顺序（提示条的「撤回」要整条还原） */
    suspend fun updateReturningOld(
        id: Long, title: String, detail: String, dueDate: Long, priority: Int
    ): Todo? {
        val old = dao.getById(id) ?: return null
        dao.update(id, title, detail, dueDate)
        dao.setPriority(id, priority)
        // 改了日子就置顶（撤回时 restoreFull 会把原 sortOrder 还回去，位置也一起回来）
        pinTopIfDayChanged(id, old.dueDate, dueDate)
        return old
    }

    /**
     * 日期改到「别的某一天」→ 把这条摆到那一天的最上面，与新建一致。
     *
     * 「置顶」在库里是靠 sortOrder 表达的（见 add：新建 = 全表最小 − 1，列表按它升序渲染）。
     * 改日期只写 dueDate、**不动 sortOrder**，于是那条按它在原来那天的位置落进新日期列表中间：
     * 保存后 App 明明跳到了新日期，用户却看到「我改的这条没在最上面」（2026-10-17 反馈）。
     * 同一天内只改标题/备注/优先级不重排 —— 免得「改个错字就被顶到最上面」。
     */
    private suspend fun pinTopIfDayChanged(id: Long, oldDueMs: Long, newDueMs: Long) {
        val oldKey = if (oldDueMs == 0L) 0L else DateUtils.startOfDayMs(oldDueMs)
        val newKey = if (newDueMs == 0L) 0L else DateUtils.startOfDayMs(newDueMs)
        if (oldKey == newKey) return
        dao.setSortOrder(id, dao.minSortOrder() - 1)
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
    // 统一周条组件报回来的「视觉上已落位」的那一天（不是「已请求但还没滑到位」的目标日）：
    // 列表滚动状态、大标题折叠都按它取，否则表头会先变、列表后到，看着割裂。
    var anchorDayMs by remember { mutableLongStateOf(shownDayStartMs) }
    val anchorDayStartMs = DateUtils.startOfDayMs(anchorDayMs)
    val listState = dayListState(anchorDayStartMs)
    LaunchedEffect(shownDayStartMs) {
        val c = DateUtils.calFrom(shownDayStartMs)
        val keep = (-1..1).map { DateUtils.startOfDayMs(DateUtils.addDays(c, it).timeInMillis) }.toHashSet()
        dayStates.keys.retainAll(keep)
    }

    // ★ 周视图 = 统一周条组件 ui/motion/UnifiedWeekDayNavigator（2026-10-16 重构）。
    //
    // 旧结构是「日列表 dayNav + 周条 stripNav + 红点进度 dayFrac」三套运动互相补同步：
    // 跨周要靠两个动画恰好同帧、手指滑完周条才补滑、被滑走的那一页还得用
    // stripOutDayMs 临时改成旧日期才不跳格，而红点是每一页各画一枚。
    // 现在只有一个 offsetFraction 同时驱动列表、周条和红点；红点整条只画一枚；
    // 跨周是「日期带连续平移」，手指拖到 1/3 处周条就已经在跟着走了 —— 输入、运动、
    // 反馈同源，不再有补同步这一步。
    //
    // 年/月视图整页横滑不变，仍然用 PeriodSwipeController。
    val bigNav = remember { PeriodSwipeController() }
    // 交给组件的目标日期（LocalDate）与事件日集合
    val selectedDate = remember(shownDayStartMs) { shownDayStartMs.toLocalDate() }
    val eventDates: Set<LocalDate> = remember(todos) {
        todos.asSequence().filter { it.dueDate != 0L }
            .map { DateUtils.startOfDayMs(it.dueDate).toLocalDate() }.toHashSet()
    }
    val todayDate = remember(todayTick) { DateUtils.startOfDayMs(DateUtils.todayCal().timeInMillis).toLocalDate() }
    val reducedMotion = rememberReducedMotion()

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
    // 日列表「刚少了一条」的短暂窗口：勾选完成 / 删除 / 取消完成的那一刻置 true，0.45s 后自动放下。
    // 窗口里打开位置动画（被去掉的那条淡出 + 其余行平滑补位），平时关着 ——
    // 切天时若开着，跨天不变的 key（过往未完成/无期限/每日固定）会被动画从旧位置拖到新位置，
    // 看起来像「条目先窜到顶格、再回落」（以前踩过）。
    // 必须是「点击那一刻就 true」（而不是等列表回调），因为点击 → 写库 → Flow 推送 中间还隔着几十毫秒。
    var holdPlacement by remember { mutableStateOf(false) }
    val placementScope = rememberCoroutineScope()
    fun pulsePlacement() {
        holdPlacement = true
        placementScope.launch { delay(450); holdPlacement = false }
    }

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
    // 日期范围：2008 ~ 2100（用户要求）
    fun clampYear(y: Int) = y.coerceIn(2008, 2100)

    fun shiftMonth(delta: Int) {
        val (y, m) = DateUtils.addMonths(shownYear, shownMonth, delta)
        shownYear = clampYear(y); shownMonth = m
    }

    /**
     * 只改「显示哪一天」这几个状态。**动画一律不在这里**：周视图的运动全部由
     * UnifiedWeekDayNavigator 依据 selectedDate 自己选路径（相邻→走一页；
     * 同周多日→单枚红点滑动 + 内容短淡换；跨周远跳→在不可见处交换）。
     */
    fun setShownDay(t: Long) {
        shownDayMs = t
        val c = DateUtils.calFrom(t)
        shownYear = clampYear(c.get(Calendar.YEAR))
        shownMonth = c.get(Calendar.MONTH)
        levelName = TodoLevel.WEEK.name
    }

    /**
     * 所有「换一天」的入口都走这里：表头 ‹ ›、「今」、月视图点某天、保存后跳过去。
     * 组件会拿这个目标日期和「当前已落位日期」比较，自己决定走哪条路径 —— 调用方
     * 不再需要判断差几天、跨不跨周（旧版就是靠这套判断 + 两套控制器补同步，很脆）。
     */
    fun goDay(targetMs: Long) {
        setShownDay(DateUtils.startOfDayMs(targetMs))
    }

    fun goToday() = goDay(today.timeInMillis)

    /**
     * 新增/修改保存后，直接跳到那条待办所在的日期（周视图）。
     * 否则：在月/年视图里加、或者当前停在别的日期时，回到原视图看不到刚加的东西 ——
     * 用户反馈的「添加了东西之后却不显示」就是这么来的。
     */
    fun jumpToDay(dueMs: Long) {
        goDay(if (dueMs == 0L) today.timeInMillis else dueMs)
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
            // 层级切换（年⇄月⇄周）淡入淡出过渡，别硬切。
            // 表头也搬进各分支：周视图的表头必须显示「视觉上已落位」的那一天
            //（统一周条组件通过 header 插槽把它报出来），否则点了 › 表头先变、
            // 列表 230ms 后才到，看着割裂。
            Crossfade(
                targetState = level,
                modifier = Modifier.weight(1f),
                animationSpec = tween(200),
                label = "levelFade"
            ) { lv ->
            if (lv == TodoLevel.WEEK) {
                // ★ 周视图 = 统一周条组件 ui/motion/UnifiedWeekDayNavigator：
                //   一条运动状态同时驱动 周条 + 那枚红点 + 日列表。
                //   · 上区（周条）横滑 = 换一周，下区（列表）横滑 = 换一天（与旧版一致）
                //   · 跨周拖动时日期带在拖动第一帧就跟着手指走，没有「滑完再补滑」
                //   · 红点整条只画一枚 —— 跨周过渡中也不会同时出现两个红圆
                //   · 点击 /「今」由 selectedDate 驱动，组件自己决定走一页还是淡换
                UnifiedWeekDayNavigator(
                    selectedDate = selectedDate,
                    onSelectedDateChange = { d -> setShownDay(d.toStartMs()) },
                    onAnchorDateChange = { d -> anchorDayMs = d.toStartMs() },
                    eventDates = eventDates,
                    today = todayDate,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.fillMaxSize(),
                    header = { visibleDate ->
                        // 表头显示「可见的那一天」（= 已落位），不是还没滑到的目标日
                        val vc = remember(visibleDate) { DateUtils.calFrom(visibleDate.toStartMs()) }
                        TodoHeader(
                            TodoLevel.WEEK, shownYear, shownMonth, vc,
                            titleSize = (28f - 11f * collapseT).sp,
                            collapsed = collapseT > 0.5f,
                            // ‹ › 从「已请求的目标日」累加，而不是从「可见日」：
                            // 连续快点几下时，每个点击都基于上一次请求 +1，不会被正在跑的
                            // 动画吃掉（表头文字仍显示可见日，见上）。
                            onPrev = { setShownDay(selectedDate.minusDays(1).toStartMs()) },
                            onNext = { setShownDay(selectedDate.plusDays(1).toStartMs()) },
                            onTitleClick = {
                                shownYear = vc.get(Calendar.YEAR)
                                shownMonth = vc.get(Calendar.MONTH)
                                levelName = TodoLevel.MONTH.name
                            })
                    }
                ) { date ->
                    // 一页 = 一天。组件同时渲染「落位日 ±1」三页，按同一条进度平移。
                    val pageDayMs = DateUtils.startOfDayMs(date.toStartMs())
                    val pageDay = remember(pageDayMs) { DateUtils.calFrom(pageDayMs) }
                    val slotState = dayListState(pageDayMs)
                    val isCenter = pageDayMs == anchorDayStartMs
                    // 侧页先归零（瞬时）：跟着手指滑进来的那一页永远从第一条开始；
                    // 落位后中心页用的就是这个 state，松手那一帧不会跳到上一天的偏移。
                    LaunchedEffect(pageDayMs, isCenter) {
                        if (!isCenter) slotState.scrollToItem(0)
                    }
                    // ★ 回顶：只在「这一天的数据真的变了」时滚（不在保存回调里滚）。
                    // 旧写法保存时直接 scrollToItem(0)，和写库并行 —— 滚动先执行、数据还没到，
                    // 等 Flow 推来新条目时 LazyColumn 按 key 把原可见那条锚在原位，新条目
                    // 插在它上面看不见。这里用 animateScrollToItem 而不是瞬时跳位
                    //（用户 2026-10-14：「跳起来很难看」），落点同样是第 0 条。
                    val pageStartMs = pageDayMs
                    val pageEndMs = DateUtils.endOfDayMs(pageStartMs)
                    // 当天「条目数」的三个分量：当天到期 / 无期限（只有今天这个页面才有）/ 过往未完成
                    val pageCounts = remember(todos, pageStartMs, pageEndMs) {
                        intArrayOf(
                            todos.count { it.dueDate in pageStartMs..pageEndMs },
                            if (DateUtils.isSameDay(pageDay, today)) todos.count { it.dueDate == 0L && !it.done } else 0,
                            todos.count { it.dueDate != 0L && it.dueDate < pageStartMs && !it.done }
                        )
                    }
                    // ★ 回顶只在「当天条目变多」时做（新增一条 / 把别条改期到这一天），为的是把
                    //   新条目送到用户眼前。勾选完成、删除、改走到别的日子都是「变少」→ 一律不动，
                    //   停在用户当前看的位置（用户 2026-10-17：勾完一条不要弹回最顶上）。
                    //   顺带解决了和写库的赛跑：只有数据已经到了（条数变多）才会滚。
                    val prevCounts = remember(pageStartMs) { intArrayOf(-1, -1, -1) }
                    LaunchedEffect(pageStartMs, pageCounts.contentHashCode()) {
                        val first = prevCounts[0] < 0
                        val grew = pageCounts[0] > prevCounts[0] || pageCounts[1] > prevCounts[1]
                        prevCounts[0] = pageCounts[0]
                        prevCounts[1] = pageCounts[1]
                        prevCounts[2] = pageCounts[2]
                        if (first) return@LaunchedEffect          // 首帧只记基线
                        if (grew && pageDayMs == anchorDayStartMs) slotState.animateScrollToItem(0)
                    }
                    DayTodoList(
                        todos = todos, loops = loops, shownDay = pageDay,
                        // 循环待办：完成状态是「本周」维度、不是分区，照旧直接切
                        onToggle = vm::toggle,
                        // 待办勾选：完成即沉底（自动收缩到「已完成」分区）；
                        // 同时给日列表一个脉冲，让它在这之后短时间打开「位置动画」（其余行平滑补位）
                        onToggleTodo = { t, done -> pulsePlacement(); vm.toggleFull(t, done) },
                        onOpenDetail = { detailTarget = it },
                        onToggleLoop = vm::toggleLoop,
                        onDeleteWithUndo = { t ->
                            pulsePlacement()
                            vm.delete(t.id)
                            undo.show("已删除：${t.title}") { vm.restore(t) }
                        },
                        // 取消完成（并给撤回）：整条还原 + 完成时刻一起还原，
                        // 否则「撤回」再勾上时 doneAt 会是老时间，条目被顶到已完成区最前面
                        onRestoreTodo = { t ->
                            pulsePlacement()
                            vm.restoreFull(t)
                            undo.show("已取消完成：${t.title}") { vm.toggleFull(t, true) }
                        },
                        onReorder = { ids -> vm.reorder(ids) },
                        onDeleteLoop = vm::deleteLoop,
                        state = slotState,
                        listModifier = Modifier.fillMaxSize(),
                        holdPlacement = holdPlacement
                    )
                }
            } else {
                // 年视图/月视图：表头 + 整页横滑切年/切月（这一套导航本轮不动）
                Column(Modifier.fillMaxSize()) {
                    TodoHeader(
                        lv, shownYear, shownMonth, shownDay,
                        titleSize = (28f - 11f * collapseT).sp,
                        collapsed = collapseT > 0.5f,
                        onPrev = { bigNav.step(-1) },
                        onNext = { bigNav.step(1) },
                        onTitleClick = { if (lv == TodoLevel.MONTH) levelName = TodoLevel.YEAR.name })
                PeriodSwipe(modifier = Modifier.weight(1f).fillMaxWidth(), controller = bigNav, onCommit = { dir ->
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
        }
        // 日历底部记账摘要：放在玻璃底栏上方
        // 74dp → 65dp：盈余小字到玻璃胶囊上沿的空档，实测 74dp 时 15.6dp、70dp 时 10.7dp，
        // 用户要求"再低一点"，再下移 5dp（约 6dp）。改这个数只需回读一次墨迹间隙即可定量。
        AccSummaryBar(monthSurplus, { onOpenAccount() },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 65.dp))
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

/** 日列表：当日待办（未完成） + 每日固定 + 过往未完成 + 无期限 + 已完成（沉底、默认折叠） */
@Composable
private fun DayTodoList(
    todos: List<Todo>, loops: List<LoopTodo>, shownDay: Calendar,
    onToggle: (Long, Boolean) -> Unit,
    onToggleTodo: (Todo, Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    onToggleLoop: (Long, Boolean) -> Unit,
    onDeleteWithUndo: (Todo) -> Unit, onRestoreTodo: (Todo) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onDeleteLoop: (Long) -> Unit, state: LazyListState, listModifier: Modifier = Modifier,
    /**
     * 「刚勾掉/删掉一条」的短暂窗口（由 TodoScreen 在点击那一刻置 true，约 0.45s 后自动放下）。
     * 为 true 时打开行的位置动画，让其余行平滑补位 —— 用参数而不是回调，是因为它必须在
     * 数据真的变化之前就已经是 true（点击 → 写库 → Flow 推送 中间隔了几十毫秒）。
     */
    holdPlacement: Boolean = false
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

    // 行的动效（就地展开，别包成 helper —— animateItem 是 LazyItemScope 的成员扩展）：
    //   · 被去掉的那条淡出 0.24s（勾选完成 / 删除 / 取消完成 都走这条）
    //   · 其余行平滑补位 = placementSpec，只在两处开：拖动让位 和 holdPlacement 窗口
    //   平时必须关着 —— 切天时跨天不变的 key（过往未完成/无期限/每日固定）会被位置动画
    //   从旧位置拖到新位置，看起来像「条目先窜到顶格、再回落」（以前踩过）。

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
                    // 平时（非拖动）只在「刚勾掉/删掉一条」的窗口里开（holdPlacement）：
                    // 切天时「过往未完成/无期限/每日固定」的 key 跨天不变、只是顺序变了，
                    // 位置动画会把它们从旧位置动到新位置 → 「条目先窜到顶格、再回落」。
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = if ((dragId != null && !isDragging) || holdPlacement)
                            spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = tween(240)
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
                        fadeInSpec = null,
                        placementSpec = if (holdPlacement) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = tween(240)
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
                        fadeInSpec = null,
                        placementSpec = if (holdPlacement) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = tween(240)
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
                        fadeInSpec = null,
                        placementSpec = if (holdPlacement) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = tween(240)
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
                        fadeInSpec = null,
                        placementSpec = if (holdPlacement) spring(stiffness = Spring.StiffnessMediumLow) else null,
                        fadeOutSpec = tween(240)
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
                    // 行高显式收紧：CJK 字体默认行高约 1.6em，标题+副标题两行各留一大截空白，
                    // 实测墨迹间隙 31px≈11.8dp（用户反馈"主标题和备注之间有点大"）。
                    // 21sp/17sp 落到约 7dp，行盒仍大于字形墨迹，不会切字。
                    Text(t.title, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                        Text(sub, fontSize = 11.5.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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

    ShixuDialog(
        title = if (editing == null) "新增待办" else "修改待办",
        onDismissRequest = onDismiss,
        content = {
            OutlinedTextField(
                value = titleV, onValueChange = { titleV = it }, label = { Text("项目") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                // 千万别给这个输入框指定高度：M3 带标签的 OutlinedTextField 是按字体度量留字身空间的，
                // 本 App 的 songti_sc 字体 ascent=1.06em / descent=0.435em，自然高度是 64dp（不是规范的 56dp）。
                // 一旦压得比自然高度小，文本行会在字段内部被裁掉下半截（2026-10-16 实测：52dp 只剩 20px 笔画、
                // 56dp 剩 31px、60dp 剩 42px，而 "ABpgqy" 完整字形是 43px；汉字下缘笔画同样被切）。
                modifier = Modifier.fillMaxWidth()
                    .jumpCursorToEnd({ titleV }) { titleV = it })
            if (showDetail) {
                OutlinedTextField(
                    value = detailV, onValueChange = { detailV = it },
                    label = { Text("备注（可选）") }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    // 同上：不要指定高度，否则文字下半截被裁
                    modifier = Modifier.fillMaxWidth()
                        .jumpCursorToEnd({ detailV }) { detailV = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("收起 ▴", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showDetail = false }
                            .padding(horizontal = 6.dp))
                }
            } else {
                TextButton(onClick = { showDetail = true }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(26.dp)) {
                    Text("＋ 备注", fontSize = 13.sp)
                }
            }
            Text("优先级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PRIORITY_NONE to "无", PRIORITY_LOW to "低",
                    PRIORITY_MID to "中", PRIORITY_HIGH to "高"
                ).forEach { (p, label) ->
                    ShixuChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        compact = true,
                        label = { Text(label, fontSize = 13.sp) },
                        leadingIcon = if (p == PRIORITY_NONE) null else {
                            { Box(Modifier.size(9.dp).background(priorityColor(p, isDarkTheme), CircleShape)) }
                        }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(38.dp)) {
                Text("日期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dueMs = DateUtils.addDays(dueCal, -1).timeInMillis },
                    contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(DateUtils.formatMonthDayCn(dueCal), fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 6.dp))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dueMs = DateUtils.addDays(dueCal, 1).timeInMillis },
                    contentAlignment = Alignment.Center) {
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            ShixuDialogButton("保存") {
                if (title.isNotBlank()) {
                    onSave(title.trim(), detail.trim(), dueMs, priority)
                    onDismiss()
                }
            }
        },
        dismissButton = { ShixuDialogTextButton("取消", onDismiss) }
    )
}
