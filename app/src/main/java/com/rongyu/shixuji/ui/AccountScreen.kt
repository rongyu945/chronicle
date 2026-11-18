package com.rongyu.shixuji.ui

import com.rongyu.shixuji.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import com.rongyu.shixuji.theme.songtiFamily
import com.rongyu.shixuji.theme.isDarkTheme
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AmountExpr
import com.rongyu.shixuji.data.QuickEntry
import com.rongyu.shixuji.data.TagPalette
import com.rongyu.shixuji.data.orderTagsByUsage
import com.rongyu.shixuji.theme.*
import com.rongyu.shixuji.ui.motion.UnifiedWeekDayNavigator
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import com.rongyu.shixuji.ui.motion.toLocalDate
import com.rongyu.shixuji.ui.motion.toStartMs
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug

// 跟着底栏等比缩放（原来写死 92dp，底栏收缩后会悬空）；
// -32dp 与待办页 FAB_PLUS_BOTTOM 同一落点 —— 两页的 ＋/今 必须齐平，否则切页时球会跳一截
private val FAB_BOTTOM = FAB_CLEARANCE - 32.dp
/**
 * 「今」按钮的中心比「＋」高多少（与待办页同一口径：圆钮 55dp、边缘净间距 10dp）。
 * 两页必须一致，否则切 Tab 时两个悬浮键会跳一下。
 */
private val FAB_TODAY_OFFSET = 52.dp
// 列表底部留白 LIST_BOTTOM 与待办页共用同一份（见 BottomTabBar.kt），不要在这里再写一个

class AccountViewModel : ViewModel() {
    private val dao = AppContainer.database.accountDao()
    private val tagDao = AppContainer.database.accTagDao()
    private val quickDao = AppContainer.database.quickEntryDao()
    private val prefDao = AppContainer.database.appPrefDao()

    val tags = tagDao.observeAll()
    val quickEntries = quickDao.observeAll()

    /** 只取某个日期区间的账目（下推到 SQL） */
    fun accountsBetween(start: Long, end: Long) = dao.observeBetween(start, end)

    /** 搜索要跨全部时间（不受当前显示月份限制），所以单独订阅整张表 —— 只在搜索页开着时才收 */
    fun allAccounts() = dao.observeAll()

    suspend fun budgetCents(): Long = prefDao.get("budget_month")?.toLongOrNull() ?: 0L

    /**
     * 最近 30 天每个标签被用了多少次 —— 记账弹窗里标签按这个从多到少排
     * （用户 2026-10-25：「记账界面的标签按照从最常用到最不常用排序」，口径=最近 30 天滚动窗口，
     * 不用自然月：月初头一天不会因为"本月还没记"把顺序全打回原形）。
     */
    suspend fun tagUsage30d(): Map<String, Int> {
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return dao.countByTagSince(since).associate { it.tag to it.cnt }
    }

    fun add(title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(Account(title = title, amountCents = cents, type = if (isIncome) 1 else 0, tag = tag, date = dateMs))
        }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }

    /**
     * 演完离场动画再删库（记账页删除用）。
     *
     * 挂在 viewModelScope 而不是页面的 rememberCoroutineScope：用户删完立刻退出这一页 /
     * 切到别的日期，删除照样要落地。返回的 Job 由 [cancelPendingDelete] 掐掉（撤销路径）。
     */
    private val pendingDeletes = java.util.concurrent.ConcurrentHashMap<Long, Job>()
    fun deleteAfter(id: Long, delayMs: Long) {
        pendingDeletes.remove(id)?.cancel()
        pendingDeletes[id] = viewModelScope.launch {
            delay(delayMs)
            withContext(Dispatchers.IO) { dao.delete(id) }
            pendingDeletes.remove(id)
        }
    }

    fun cancelPendingDelete(id: Long) {
        pendingDeletes.remove(id)?.cancel()
    }

    /** 撤销删除：按原 id 插回（upsert：万一 id 已被新条占用也不会崩） */
    fun restore(a: Account) = viewModelScope.launch(Dispatchers.IO) {
        dao.upsert(a.copy(id = a.id))
    }

    /**
     * 新增账目。用 Room `@Insert` 返回的真实 rowId（原来先 `MAX(id)+1` 猜一个：
     * 与插入非原子、且删过最大 id 后会复用 —— 复用时按 id 撤回会覆盖另一条）。
     */
    suspend fun addReturningId(title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long): Long =
        dao.insert(Account(title = title, amountCents = cents, type = if (isIncome) 1 else 0, tag = tag, date = dateMs))

    /**
     * 修改并回传「改之前」的完整快照（提示条的「撤回」要整条还原）。
     * 同时也回传改之前所在日期 —— 撤回时界面要跳回那条原来的那天，否则看着像没还原。
     */
    suspend fun updateReturningOld(
        id: Long, title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long
    ): Account? {
        val old = dao.getById(id) ?: return null
        dao.update(id, title, cents, if (isIncome) 1 else 0, tag, dateMs)
        return old
    }

    /** 整条覆盖（撤回修改用，id 保留原样；upsert：那条还在库里，直插会撞主键） */
    fun restoreFull(a: Account) = viewModelScope.launch(Dispatchers.IO) {
        dao.upsert(a.copy(id = a.id))
    }

    fun update(id: Long, title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(id, title, cents, if (isIncome) 1 else 0, tag, dateMs)
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    todayTick: Long = 0L,
    /** 长按图标「记一笔」：进来直接把录入弹窗顶起来（只消费一次） */
    openAdd: Boolean = false,
    onOpenAddHandled: () -> Unit = {},
    /** 撤回球停留时长（毫秒），来自设置页「撤回停留时间」 */
    undoMs: Int = UNDO_LIFE_DEFAULT_MS,
    /** 搜索页开合回报给上层：搜索页是专注模式，右上角装饰性桃枝要一起收起来 */
    onSearchingChange: (Boolean) -> Unit = {},
    vm: AccountViewModel = viewModel()
) {
    // 同待办：悬浮钮用**自己的**背景层（只录列表内容），不能借底栏那份（会采样到自己 → 递归崩溃）
    val fabLayer = rememberLiquidGlassBackdrop()
    var shownDayMs by rememberSaveable { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val shownDay = remember(shownDayMs) { DateUtils.calFrom(shownDayMs) }
    var showAdd by remember { mutableStateOf(false) }
    // 搜索页：普通 remember（不是 rememberSaveable）—— 切走 Tab 时自动收起，回来还是原来的日子
    var searching by remember { mutableStateOf(false) }
    // 搜索页是一块「专注模式」：右上角的桃枝装饰在这层要收起来（见 MainScreen 的 PlumBranch）。
    LaunchedEffect(searching) { onSearchingChange(searching) }
    // 快捷方式「记一笔」：先把搜索页收掉（否则弹窗关掉后底下还是搜索页），再弹录入框
    LaunchedEffect(openAdd) {
        if (openAdd) {
            searching = false
            showAdd = true
            onOpenAddHandled()
        }
    }
    var detailTarget by remember { mutableStateOf<Account?>(null) }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    val undo = rememberUndoController()
    val scope = rememberCoroutineScope()

    // ── 删除 / 撤回的动效 ──────────────────────────────────────────────────────
    //
    // 删：`RowExitShell`（划掉线 → 化掉 → 收高度），下面的行被真实高度推上来。
    // 撤回（撤销删除）：**完全相反的同一套时钟** —— 行从 0 高度长回自然高度，
    // 下面的行被真实高度**一点点压下去**。2026-11-18 用户原话：
    // 「一撤回之后条目直接闪现，然后下面的条目自动往下沉 —— 能不能让它慢慢出现，
    //   配合下面一个条目逐渐下降；撤回的条目图层应该在下一个条目的下面，才像慢慢浮上来」。
    //
    // 所以这里和「待办页那三条铁则」正好相反，是**有意为之**：
    // ① 被撤回的行必须做动画（`RowRestoreShell` 的高度 + 淡入两条时钟）；
    // ② 位移交给这一行自己变高，邻居的 `placementSpec` 在这期间**必须关掉**（否则弹簧追着高度跑，互相压住）；
    // ③ 它画在邻居**下面**（`zIndex = −1f`），起步那 35% 高度藏在下面那张卡背后，看得到「钻出来」。
    var restoringAccId by remember { mutableStateOf<Long?>(null) }
    var settleUntil by remember { mutableLongStateOf(0L) }
    var placementToken by remember { mutableIntStateOf(0) }
    fun pulsePlacement(ms: Long = 900L) {
        settleUntil = System.currentTimeMillis() + ms
        val mine = ++placementToken
        scope.launch { delay(ms + 80L); if (placementToken == mine) settleUntil = 0L }
    }
    LaunchedEffect(restoringAccId) {
        val id = restoringAccId ?: return@LaunchedEffect
        // 正常情况由 RowRestoreShell 的 onFinished 收尾；这里只兜底：
        // 行被 LazyColumn 回收（LaunchedEffect 被取消）时，别让它永远停在半高。
        delay(MotionDebug.ms(ROW_RESTORE_MS) * 2L + 600L)
        if (restoringAccId == id) restoringAccId = null
    }

    // ── 删除的离场（2026-11-18 用户要求与待办页同步）────────────────────────────
    // 与待办页同一条时间轴：划掉线 240ms → 站住 120ms 后化掉 200ms → 位置从 240ms 收到 440ms。
    // **唯一差别**：账目行没有勾选框，所以没有「按钮变成红色勾子」那一步，只保留划掉那道线。
    //
    // 行的删库被推迟到演完（见下方「删除」按钮）：这 440ms 里它仍然在库里 → 仍然在列表里，
    // 于是「这一行自己变矮、下面的行被真实高度推上来」这一条时钟才成立
    //（旧写法是立刻删库 + animateItem 的 240ms 淡出，占位第一帧就没了，
    //  下面的行再用另一套弹簧补位 —— 两套时钟）。
    var exitingAcc by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var settledAcc by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val exitingAccIds: Set<Long> = exitingAcc.keys
    val exitSpecs = rememberExitSpecs()
    LaunchedEffect(exitingAcc) {
        if (exitingAcc.isEmpty()) return@LaunchedEffect
        // 兜底：行被 LazyColumn 回收、页面销毁、或删除没落地时，别让它永远停在「已收起」。
        // 时长跟着 MotionDebug 的慢放倍率走（调参时会放到 4×）。
        delay(MotionDebug.ms(ROW_EXIT_MS) * 2L + 520L)
        exitingAcc = emptyMap()
        settledAcc = emptySet()
    }

    val today = remember(todayTick) { DateUtils.todayCal() }

    // ★ 每个「显示日」一个独立的 LazyListState（普通 Map 当缓存，不是 Compose 状态）。
    //
    // 旧写法是「一个共享 state + 切天时 scrollToItem(0) 纠回来」：PeriodSwipe 两侧那两页
    // 用的是各自的 state（停在第一条），滑动过程中看到的确实是新一天的开头；
    // 可 onCommit 那一刻中心页换成共享 state，它记的还是**上一天**滚到的位置 ——
    // 松手后先显示新一天中段的条目（分组标题「类别」还在视口上方，看着像没有类别），
    // 过一会才被纠回顶部。按天缓存 state 后，侧页与落位后的中心页是同一个实例，
    // 滑进来看到什么就留什么，不需要任何纠正。（离开的日子会被清掉，再翻回来仍是顶部。）
    val dayStates = remember { mutableMapOf<Long, LazyListState>() }
    val shownDayStartMs = DateUtils.startOfDayMs(shownDayMs)
    fun dayListState(dayMs: Long) = dayStates.getOrPut(dayMs) { LazyListState() }
    // 统一周条组件报回来的「视觉上已落位」的那一天（不是已请求但还没滑到的目标日）：
    // 列表滚动状态与标题折叠按它取，否则表头/汇总会先变、列表后到。
    var anchorDayMs by remember { mutableLongStateOf(shownDayStartMs) }
    val anchorDayStartMs = DateUtils.startOfDayMs(anchorDayMs)
    val listState = dayListState(anchorDayStartMs)
    LaunchedEffect(shownDayStartMs) {
        val c = DateUtils.calFrom(shownDayStartMs)
        val keep = (-1..1).map { DateUtils.startOfDayMs(DateUtils.addDays(c, it).timeInMillis) }.toHashSet()
        dayStates.keys.retainAll(keep)
    }

    // ★ 周条 + 日列表 = 统一周条组件 ui/motion/UnifiedWeekDayNavigator（2026-10-16 重构，
    //   与待办页共用同一套实现）。旧版这里是 dayNav / stripNav / dayFrac 三套运动互相补同步：
    //   差一天走一页、更远淡换、跨周还要在 onCommit 里补一条周条动画 —— 既脆又必然两段式。
    //   现在只有一个 offsetFraction，输入、运动、反馈同源。
    val todayDate = remember(todayTick) { DateUtils.startOfDayMs(DateUtils.todayCal().timeInMillis).toLocalDate() }
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current

    /**
     * 所有「换一天」的入口都走这里：「今」、点周条日期、记一笔/改账目后跳过去。
     * 只改状态 —— 动画（走一页 / 滑红点 / 整体淡换）由统一周条组件自己按目标
     * 与「已落位日期」的差决定，调用方不再判断差几天、跨不跨周。
     */
    fun goDay(targetMs: Long) {
        shownDayMs = DateUtils.startOfDayMs(targetMs)
    }

    // 大标题折叠量（0 = 展开 28sp，1 = 收起 17sp）。跟手 + 吸附见 ui/TitleFold.kt。
    val fold = rememberTitleFold(listState)

    // 切天从顶部开始显示由「按天缓存 LazyListState」天然保证（见上），
    // 不再需要 scrollToItem(0) 事后纠正 —— 那正是「先显示旧位置、过一会才刷新」的来源。

    val weekStart = DateUtils.startOfWeek(shownDay)
    val weekEnd = DateUtils.addDays(weekStart, 6)
    val weekStartMs = weekStart.timeInMillis
    val weekEndMs = DateUtils.endOfDayMs(weekEnd.timeInMillis)

    // 汇总卡的月份 = 正在看的那一天所在的月份（原来固定取当前自然月，翻到别的月份就对不上了）
    val heroCal = DateUtils.calFrom(shownDayMs)
    val heroYear = heroCal.get(Calendar.YEAR)
    val heroMonth0 = heroCal.get(Calendar.MONTH)
    val heroIsThisMonth = heroYear == today.get(Calendar.YEAR) && heroMonth0 == today.get(Calendar.MONTH)
    val heroMonthWord = if (heroIsThisMonth) "本月" else "${heroMonth0 + 1}月"
    val monthStartMs = DateUtils.month(heroYear, heroMonth0).timeInMillis
    val monthEndMs = DateUtils.endOfDayMs(
        DateUtils.month(heroYear, heroMonth0)
            .apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(heroYear, heroMonth0)) }
            .timeInMillis
    )

    // 数据窗口按「月」而**不是按周**给。
    //
    // 原因：窗口原来是从 weekStart/weekEnd 推出来的（本周 ±1 周），于是**跨周那一刻**
    // 窗口值变了 → `remember(swipeFromMs, swipeToMs)` 换 key → 重建 Room Flow、重跑一次
    // SQL；用户实测"一天一天滑，只有周日滑到下周一那一下会卡"，正是这个只在跨周发生的动作。
    //
    // 改成按当前显示月份取，且**留足两侧余量**：列表区要 ±1 天，周条区要能看到 ±1 周
    // （±1 周最多跨到相邻月初月末各 6 天）。所以往前后各放宽 14 天 —— 逐日滑动时月份不变
    // → 窗口不变 → 不重查；只有跨月才换一次查询（一年 12 次 vs 原来 52 次），
    // 且跨月只会发生在月末/月初，不会同时又是跨周边界。
    // 用「保留上一次结果」而不是 collectAsState(initial = 空)：窗口万一刚换、新查询还没回来时，
    // 中心页显示上一窗口的数据（仍含当前这一周），所以不会闪空。
    val swipeFromMs = DateUtils.addDays(DateUtils.calFrom(monthStartMs), -14).timeInMillis
    val swipeToMs = DateUtils.endOfDayMs(DateUtils.addDays(DateUtils.calFrom(monthEndMs), 14).timeInMillis)
    val swipeFlow = remember(swipeFromMs, swipeToMs) { vm.accountsBetween(swipeFromMs, swipeToMs) }
    var swipeAcc by remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(swipeFlow) { swipeFlow.collect { swipeAcc = it } }
    val monthFlow = remember(monthStartMs, monthEndMs) { vm.accountsBetween(monthStartMs, monthEndMs) }
    val monthAcc by monthFlow.collectAsState(initial = emptyList())
    // ⚠️ 这些过滤**必须 remember**（和待办页 DayTodoList 同一个坑，这里一直漏了）：
    // 账目页在滚动/拖动时每帧都重组，不缓存的话每帧都要把 swipeAcc 整个扫几遍
    // （weekAcc、dayAcc、day/week/month 的 sumOf，以及周条页的 pWeekAcc），
    // 窗口现在按月算、条目更多，逐日滑动时这一笔开销每帧都付。
    // 跨周那一刻这些过滤的输入（weekStartMs 等）全变，正好是最重的一帧。
    val weekAcc = remember(swipeAcc, weekStartMs, weekEndMs) {
        swipeAcc.filter { it.date in weekStartMs..weekEndMs }
    }
    // 事件日（哪些天有账目）：交给统一周条组件画小圆点
    val eventDates: Set<LocalDate> = remember(swipeAcc) {
        swipeAcc.map { DateUtils.startOfDayMs(it.date).toLocalDate() }.toHashSet()
    }

    val dayEndMs = DateUtils.endOfDayMs(shownDayMs)
    val dayAcc = remember(weekAcc, shownDayMs, dayEndMs) {
        weekAcc.filter { it.date in shownDayMs..dayEndMs }
    }
    val dayOut = remember(dayAcc) { dayAcc.filter { it.type == 0 }.sumOf { it.amountCents } }
    val weekOut = remember(weekAcc) { weekAcc.filter { it.type == 0 }.sumOf { it.amountCents } }
    val monthOut = remember(monthAcc) { monthAcc.filter { it.type == 0 }.sumOf { it.amountCents } }
    val monthIn = remember(monthAcc) { monthAcc.filter { it.type == 1 }.sumOf { it.amountCents } }

    val tagList by vm.tags.collectAsState(initial = emptyList())
    val tagColorMap = remember(tagList) { tagList.associate { it.name to Color(it.color.toInt()) } }
    // 记账弹窗里标签的排序依据（最近 30 天用量）：只在弹窗打开时查一次，
    // 不挂在常驻 Flow 上 —— 每记一笔都要重查整张 accounts 表没必要。
    var tagUsage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(showAdd, editTarget?.id) {
        if (showAdd || editTarget != null) tagUsage = vm.tagUsage30d()
    }
    val tagsForSheet = remember(tagList, tagUsage) { orderTagsByUsage(tagList, tagUsage) }
    val quickList by vm.quickEntries.collectAsState(initial = emptyList())

    var budgetCents by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { budgetCents = vm.budgetCents() }

    // 搜索页开着时，返回键先收掉它（否则单 Activity 会直接退出 App）
    BackHandler(enabled = searching) { searching = false }

    Box(Modifier.fillMaxSize()) {
        // 2026-11-11「Motion P0」：搜索页原先是一整块硬切（if/else 直接换子树，零过渡）。
        // 这里按项目既有铁律做「先出后进」，**不做交叉淡变** —— 账目页和搜索页都是透明纸底，
        // 两层文字同框会叠印（问候页换段踩过同一个坑）。旧内容 110ms 淡出走完，
        // 新内容才开始 150ms 淡入 + 8dp 上浮，真正同框 0ms。
        AnimatedContent(
            targetState = searching,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    // 7.4.0（方案 §22「搜索 进入/退出」）：入场抬到 240ms、延后 160ms ——
                    // 旧版 150ms/延后 110 与退出的 160ms 重叠，两页文字会同时在场约 50ms（纸底上会叠印）。
                    (fadeIn(Motion.enterDelayed(240, 160), initialAlpha = 0f) +
                        slideInVertically(Motion.enterDelayed(240, 160)) { it / 40 })
                        .togetherWith(fadeOut(Motion.exit(160)))
                }
            },
            label = "accountSearchSwitch"
        ) { isSearch ->
        if (isSearch) {
            // 搜索页整块占掉内容区：结果要能一直往下滚，叠在列表上也容易两套滚动打架
            AccountSearchPanel(
                vm = vm,
                tags = tagList,
                tagColorMap = tagColorMap,
                onOpenDetail = { detailTarget = it },
                onClose = { searching = false },
                todayTick = todayTick
            )
        } else {
        // ★ AnimatedContent 的 content lambda 给的是 AnimatedContentScope，不是 BoxScope。
        //   里面的 `.align(Alignment.BottomEnd)` 会静默解析到**外层**那个 Box 的 BoxScope 上 ——
        //   编译能过，但本节点的父布局是 AnimatedContent 内部那个 Box，align 数据没人读，
        //   两个悬浮钮就掉到左上角去了（实测：红 + 压在「账目」标题上）。
        //   所以普通账目这一支自己再套一层 fillMaxSize 的 Box，给悬浮钮一个真正的 BoxScope。
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().recordBackdrop(fabLayer)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(start = 20.dp, end = 12.dp, top = lerp(14.dp, 8.dp, fold),
                        bottom = lerp(6.dp, 8.dp, fold)),
                verticalAlignment = Alignment.CenterVertically) {
                // 2026-11-07 用户：「把这个搜索图标也移动到账目这个字样的右边」。
                // 原来标题 weight(1f) 吃满整行、放大镜被顶到最右缘，正好压在桃花枝上，
                // 几乎看不见。现在标题只占自身宽度，放大镜紧跟其后（间距 10dp），
                // 剩余空间交给行尾的弹性空白。
                Text("账目", fontSize = titleSizeFor(fold).sp, fontWeight = FontWeight.Bold,
                    letterSpacing = titleTrackingFor(fold).sp,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(10.dp))
                // 搜索入口：标题右侧一个放大镜（自己画的，不用 emoji/图片资源）
                val searchPress = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(34.dp)
                        .pressScale(searchPress, pressed = Motion.PRESS_ICON)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = searchPress,
                            indication = null
                        ) { searching = true }
                        .semantics { contentDescription = "搜索账目" },
                    contentAlignment = Alignment.Center
                ) {
                    SearchGlyph(size = 19.dp, stroke = 1.8.dp)
                }
                Spacer(Modifier.weight(1f))
            }

            // 汇总卡（2026-11-06 用户定案：默认只留一条，点开才是完整红卡）
            //
            // 原来整张红卡常驻顶部：只读的摘要占了 1/4 屏、用了全 App 最饱和的主色；
            // 而它要说的只有两件事——今天花了多少、月度预算到哪了。用户给的解法比"调淡"好：
            // 平时收成一条「本日支出 + 预算环」，想看三项拆分和预算数额再点开。
            // 主色因此只在主动索取时出现，日常状态是安静的（Apple：色彩要挣来，不是白给）。
            var heroOpen by rememberSaveable { mutableStateOf(false) }
            val heroOver = budgetCents > 0 && monthOut > budgetCents
            // 不夹到 1：环里要显示真实的百分比（超支时 137% 就是 137%，用户 2026-11-17）。
            val heroFrac = if (budgetCents > 0) (monthOut.toFloat() / budgetCents).coerceAtLeast(0f) else 0f
            Column(
                Modifier.fillMaxWidth().animateContentSize(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f)
                )
            ) {
                if (heroOpen) {
                    AccountHero(
                        monthWord = heroMonthWord,
                        monthOut = monthOut, dayOut = dayOut, weekOut = weekOut, monthIn = monthIn,
                        budgetCents = budgetCents,
                        // 2026-11-07 用户：「大卡变大的那个也改成本日支出」。主数字改成当日，
                        // 本月支出挪到下面第一小格；跟着胶囊一起按「今天/别的日期」改称呼。
                        dayLabel = if (DateUtils.calFrom(shownDayMs).let {
                                it.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    it.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                            }) "本日支出" else "当日支出",
                        onClick = { heroOpen = false }
                    )
                } else {
                    HeroSummaryPill(
                        dayOut = dayOut,
                        frac = heroFrac,
                        over = heroOver,
                        hasBudget = budgetCents > 0,
                        isToday = DateUtils.calFrom(shownDayMs).let {
                            it.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                it.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                        },
                        expanded = false,
                        onClick = { heroOpen = true }
                    )
                }
            }

            // 常用记账：点一下记一笔
            if (quickList.isNotEmpty()) {
                // 常用记账是横向滚动的一行：条目多到放不下时右端会露出一半 ——
                // 不给渐隐的话那一半就被硬切在屏幕边上（像渲染出错）。两端擦淡，
                // 和记账弹窗的标签行用同一套（EdgeFade）。
                val quickRowState = remember { LazyListState() }
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                        .horizontalEdgeFade(quickRowState, 26.dp),
                    state = quickRowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickList, key = { it.id }) { q ->
                        val dot = tagColorMap[q.tag] ?: Color(TagPalette.UNLABELED)
                        Row(
                            Modifier.paperSurface(cardShape(CardRadius.ACTION), dark = isDarkTheme, elevation = 2.dp, strokeInset = 0.dp)
                                .clickable {
                                    val now = DateUtils.atCurrentTime(DateUtils.todayCal())
                                    // 新增不再弹撤回提示条（用户 2026-10-12：只保留删除的撤回）
                                    vm.add(q.title, q.amountCents, q.tag, q.type == 1, now)
                                    // 规则 13：记账是「提交」类动作，给一记轻触感（和底栏切 Tab 同一种）
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // 快捷记账固定记在「今天」：当前若看着别的日期就跳过去，
                                    // 否则记完在当前页看不到这笔（像没记上）
                                    goDay(now)
                                    // 不再在这里 scrollToItem(0)：那是和写库并行的协程，会抢在
                                    // 数据到达之前滚，反而把列表锚在原位（详见列表区那段注释）。
                                    // 回顶统一由「当天数据变化」那个 LaunchedEffect 负责。
                                }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).background(dot, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("${q.title} ${formatCents(q.amountCents)}", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ★ 统一周条组件（与待办页共用同一个实现）：
            //   一条运动状态同时驱动 周条 + 那枚红点 + 账目列表。跨周拖动时日期带在
            //   拖动第一帧就跟着手指走；红点整条只画一枚。旧版「日列表滑完周条才补滑」
            //   的两段式、以及跨周第一帧红圈换格，在这个组件里都不存在。
            UnifiedWeekDayNavigator(
                selectedDate = shownDayMs.toLocalDate(),
                onSelectedDateChange = { d -> shownDayMs = d.toStartMs() },
                onAnchorDateChange = { d -> anchorDayMs = d.toStartMs() },
                eventDates = eventDates,
                today = todayDate,
                reducedMotion = reducedMotion,
                modifier = Modifier.weight(1f)
            ) { date ->
                val pageDayMs = DateUtils.startOfDayMs(date.toStartMs())
                val slotState = dayListState(pageDayMs)
                val isCenter = pageDayMs == anchorDayStartMs
                // 必须 remember：组件同时画三页，不缓存的话每帧过滤三遍
                val pDayAcc = remember(swipeAcc, pageDayMs) {
                    swipeAcc.filter { it.date in pageDayMs..DateUtils.endOfDayMs(pageDayMs) }
                }
                // 分组 + 排序同样要 remember：按标签分组要排序+建 Map，条目多时每帧都在算。
                //
                // ★ 无标签条目（tag 为空）必须**单独成组、放在最后**：
                //   旧写法直接 groupBy { it.tag }，空标签那一组的标题被 if (isNotBlank()) 跳过 ——
                //   于是这批条目的行就死死贴在上一个标签的条目下面，看起来完全像是被归进了
                //   那个标签的板块（用户报的「没有标签的账目被莫名分类到别的板块」）。
                //   现在给它们一个「未分类」标题，并且永远排在所有标签组之后。
                val grouped = remember(pDayAcc) {
                    val order = ArrayList<String>()
                    val buckets = HashMap<String, MutableList<Account>>()
                    pDayAcc.sortedByDescending { it.date }.forEach { a ->
                        val k = a.tag
                        if (k !in buckets) { buckets[k] = mutableListOf(); order += k }
                        buckets.getValue(k).add(a)
                    }
                    // 有标签的组保持「最近记录在前」的出现顺序，未分类固定垫底
                    order.filter { it.isNotBlank() }.map { it to buckets.getValue(it) } +
                        listOfNotNull(if (order.any { it.isBlank() }) "" to buckets.getValue("") else null)
                }
                // 侧页（不是当前已落位的那一天）先归零：跟着手指滑进来的那一页
                // 永远从第一条开始；落位后中心页用的就是这个 state，松手瞬间不跳。
                LaunchedEffect(pageDayMs, isCenter) {
                    if (!isCenter) slotState.scrollToItem(0)
                }
                // ★ 回顶只在「当天数据真的变了」时触发：旧写法在保存回调里直接滚，和写库
                //   并行 —— 滚动先执行、数据还没到，等 Flow 推来新条目时 LazyColumn 会按 key
                //   把原可见那条锚在原位，新条目插在它上面看不见。用 animateScrollToItem
                //   （平滑）而不是瞬移版：与待办页同一口径（用户 2026-10-14：「跳起来很难看」）。
                // ★ 回顶只在「当天条目变多」时做（新记一笔 / 把别的账目改到这一天），目的是让新条目
                //   出现在眼前。删除、改金额、改标签都是「没变多」→ 停在用户当前看的位置。
                //   待办页早就是这套判据；账目页原来是「变量一变就回顶」，删掉列表中间一条会突然跳到
                //   顶部，长列表里很打断（用户 2026-10-19 一并提出）。
                val prevCount = remember(pageDayMs) { mutableIntStateOf(-1) }
                LaunchedEffect(pageDayMs, pDayAcc.size) {
                    val prev = prevCount.intValue
                    prevCount.intValue = pDayAcc.size
                    if (prev < 0) return@LaunchedEffect                      // 首帧只记基线
                    // 撤回也会让条数 +1，但那不该动视口 —— 被撤回的卡片就在原槽位补回来
                    if (pDayAcc.size > prev && pageDayMs == anchorDayStartMs && restoringAccId == null) {
                        slotState.animateScrollToItem(0)
                    }
                }
                // 位置动画规格：只在「刚删除 / 刚撤回」的窗口里给弹簧，其余时候 null
                // 有行正在演「收起」时一律不给弹簧：那时它每一帧都在变矮，
                // 邻居是被真实高度推着走的；再叠一层 placement 弹簧会让它们追不上、互相压住。
                // 等它收完（settledAcc）才重新开 —— 那是最后几 dp 的行距合上。
                val runningExit = exitingAccIds.any { it !in settledAcc }
                // 撤回（长回来）期间同样不给弹簧：这一行每一帧都在变高，邻居被真实高度推着走。
                val settleSpec: FiniteAnimationSpec<IntOffset>? =
                    if (settleUntil > 0L && !runningExit && restoringAccId == null)
                        spring(stiffness = Spring.StiffnessMediumLow) else null

                // 只显示当天的账目，按标签分组（同标签吸附，不折叠）
                LazyColumn(
                    state = slotState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (pDayAcc.isEmpty()) {
                        item(key = "empty-day") {
                            // fillParentMaxSize 让这一项占满内容区，插画与文字因此能纵向居中
                            // （原来 padding(top = 34) 顶着上方，下面一大片空白）
                            EmptyLedger(
                                // 视口下沿被底栏与列表留白吃掉一大截，直接按视口居中会偏高；
                                // 实测（1080×2400）需要把整块往下推 ~200px，即顶部让出 64dp
                                //（fillParentMaxSize 下加顶部内边距，内容中心只下移一半）。
                                modifier = Modifier.fillParentMaxSize().padding(top = 110.dp),
                                isToday = pageDayMs.toLocalDate() == LocalDate.now()
                            )
                        }
                    } else {
                        grouped.forEach { (tag, list) ->
                            val untagged = tag.isBlank()
                            val tagColor = if (untagged) Color(TagPalette.UNLABELED) else tagColorMap[tag] ?: Color(TagPalette.UNLABELED)
                            // 每个组都有标题（含「未分类」）：没有标题的那批条目会看起来属于上一组
                            item(
                                key = if (untagged) "h-\u0000blank" else "h-$tag",
                                contentType = "head"
                            ) {
                                // 2026-11-05 与待办页的小节头统一：一枚短竖条 + 加粗标题。
                                // 原来这里是 12sp 半粗灰字，比条目里的正文还轻，翻到列表中部时
                                // 根本读不出「这是新的一组」。竖条用该标签自己的颜色 —— 组色即标签色，
                                // 与左边那颗圆点同色，一眼能对上。
                                Row(
                                    modifier = Modifier
                                        .animateItem(
                                            // ★ 组标题要跟着淡入淡出：账目的分组会整组消失/重现
                                            //   （删掉某组最后一条、撤回让它回来），null 就是硬切——
                                            //   条目淡出 240ms 而标题凭空消失，看着像 bug。
                                            //   待办页的小节头是一天固定的四节，不会消失，那边才是 null。
                                            fadeInSpec = tween(240),
                                            placementSpec = settleSpec,
                                            fadeOutSpec = tween(240)
                                        )
                                        .padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(width = 2.5.dp, height = 12.dp)
                                            .background(tagColor, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (untagged) "未分类" else tag,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            items(list, key = { it.id }) { acc ->
                                // 左右滑操作已删（SwipeRow 在这里也是 enabled=false 的死代码）；
                                // 编辑/删除都在点击后的「账目详情」弹窗里。
                                val leavingNow = acc.id in exitingAccIds
                                val restoringNow = restoringAccId == acc.id
                                if (restoringNow) {
                                    // 撤销删除：从 0 长回自然高度 + 淡入，画在邻居**下面**（zIndex −1f），
                                    // 起步那截藏在下面那张卡背后 → 「慢慢浮上来」。
                                    RowRestoreShell(
                                        id = acc.id,
                                        spec = exitSpecs.restore,
                                        fadeSpec = exitSpecs.restoreFade,
                                        onFinished = { id -> if (restoringAccId == id) restoringAccId = null },
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = null,
                                                placementSpec = null,
                                                fadeOutSpec = null
                                            )
                                            .zIndex(-1f),
                                        spacing = 0.dp
                                    ) {
                                        AccountRow(
                                            acc = acc, tagColor = tagColor,
                                            onOpenDetail = { detailTarget = acc },
                                        )
                                    }
                                } else if (leavingNow) {
                                    // 删除离场：与待办页同一个外壳（划掉 → 化掉 → 收高度）。
                                    // spacing = 0.dp：这一节的行距来自上面 LazyColumn 的 spacedBy(5.dp)，
                                    // 行内再垫一份就会收不完。
                                    RowExitShell(
                                        id = acc.id,
                                        spec = exitSpecs.height,
                                        fadeSpec = exitSpecs.fade,
                                        strikeSpec = exitSpecs.strike,
                                        onSettled = { id ->
                                            settledAcc = settledAcc + id
                                            // 收完把「让位」窗口续上：剩下的是那 5dp 行距合上
                                            pulsePlacement()
                                        },
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = null,
                                                placementSpec = null,
                                                fadeOutSpec = null
                                            )
                                            .zIndex(1f),
                                        spacing = 0.dp
                                    ) {
                                        AccountRow(
                                            acc = acc, tagColor = tagColor,
                                            onOpenDetail = { },
                                        )
                                    }
                                } else {
                                    AccountRow(
                                        acc = acc, tagColor = tagColor,
                                        onOpenDetail = { detailTarget = acc },
                                        // 让位：只在「刚删完」的窗口里用弹簧（切天时同一批账目会换序，
                                        // 常开会让它们乱窜）。撤回走的是另一条路：由 RowRestoreShell
                                        // 自己变高把下面的行压下去，这里不能再叠弹簧。
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = null,
                                                placementSpec = settleSpec,
                                                // 删除离场不走这里（那条走 RowExitShell，末尾自己已经化到 0），
                                                // 这里的淡出只管「改日期把它挪到别的天」这种直接消失的路径。
                                                fadeOutSpec = tween(240)
                                            )
                                            .zIndex(0f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

        // 底部纸色压层：与待办页同一份（ui/BottomFade.kt）。搜索页不画 —— 那页整块是结果列表，
        // 底下再压一层纸反而把结果糊掉。
        // 用 isSearch（本分支的身份）而不是 searching：退出搜索时 searching 已经翻成 false，
        // 但这一支还在 110ms 的淡出窗口里，用 searching 会让底部纸压在淡出中途突然冒出来。
        if (!isSearch) BottomPaperFade()

        // 同待办：磨砂玻璃圆（和底栏同一套液态玻璃），不用 FAB（避免阴影叠出多边形亮边）
        GlassCircleButton(
            label = "+",
            // 同待办页：实心主题色（参考图的红圆 + 白十字）
            tint = MaterialTheme.colorScheme.primary,
            fallbackColor = MaterialTheme.colorScheme.primary,
            labelColor = Color.White,
            contentDescription = "记一笔",
            onClick = { showAdd = true },
            backdrop = fabLayer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).padding(bottom = FAB_BOTTOM)
        )

        // “今”按钮：不在今天时出现，一键回今天（和待办页一致）
        AnimatedVisibility(
            visible = !DateUtils.isSameDay(shownDay, today),
            // 7.4.1（方案 P0-5）：减弱动效时「今」按钮直接出现/消失，不做小七成的弹出。
            enter = if (reducedMotion) EnterTransition.None
            else fadeIn(Motion.enter(180)) + scaleIn(initialScale = 0.7f, animationSpec = Motion.enter(200)),
            exit = if (reducedMotion) ExitTransition.None
            else fadeOut(Motion.exit(140)) + scaleOut(targetScale = 0.7f, animationSpec = Motion.exit(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val dark = isDarkTheme
            GlassCircleButton(
                label = "今",
                tint = if (dark) Color(0xFF3A3430) else Color.White,
                fallbackColor = if (dark) Color(0xFF3A3430) else Color.White,
                labelColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                // 参考图「今」墨迹 68px / 球径 186px = 36.6%；本机 songti_sc 墨迹≈1.0em，
                // 所以 44dp 的球要 16sp（原来 22sp 顶到 50%，把球塞满了）
                labelSize = 16.sp, labelBold = true,
                border = if (dark) Color(0x33FFFFFF) else Color(0xFFEAE0D2),
                contentDescription = "回到今天",
                onClick = { goDay(today.timeInMillis) },
                backdrop = fabLayer,
                modifier = Modifier.padding(20.dp).padding(bottom = FAB_BOTTOM + FAB_TODAY_OFFSET)
            )
        }
        }   // ← 普通账目分支自己的 Box 结束（给悬浮钮 BoxScope）
        }   // ← searching 的 else 结束：内容 + 两个悬浮钮只在普通账目页出现。
        }   // ← AnimatedContent 的 content lambda 结束
            //   悬浮钮必须一起收起来：它们要采样 fabLayer（由上面那层 Column 录制），
            //   而搜索页里那层 Column 已经不在了 —— 玻璃去取一个已脱离的节点坐标会直接崩
            //   （IllegalStateException: LayoutCoordinate operations are only valid when isAttached is true）。

        // 撤回提示：和待办页同一个「悬浮球」，位置也对齐 —— 挨在 ＋ 左边 10dp
        UndoHost(
            undo,
            // ★ 搜索页里 fabLayer 的录制者不组合（在 else 分支里），传进来就是一个
            // detached 状态：撤回球一采样就抛
            // IllegalStateException: LayoutCoordinate operations are only valid when isAttached is true。
            // 搜索 → 点结果 → 详情 → 删除，正好是这条路径（撤回球不传 backdrop 只是不折射，功能照常）。
            backdrop = if (searching) null else fabLayer,
            lifeMs = undoMs,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(end = 61.dp)
                .padding(bottom = FAB_BOTTOM)
        )
    }

    if (showAdd) {
        AccountEditSheet(
            tags = tagsForSheet, editing = null,
            defaultDateMs = DateUtils.atCurrentTime(shownDay),
            onSave = { t, cents, tag, isIncome, dateMs ->
                // 新增不再弹撤回提示条（用户 2026-10-12：只保留删除的撤回），直接落库即可
                vm.add(t, cents, tag, isIncome, dateMs)
                // 记完一笔要「跳到这一笔所在的那天」——记的是别的日期时，不跳过去当前这天
                // 就看不到它，像没记上。日期相同则 goDay 里直接返回，页面原地不动。
                goDay(dateMs)
            },
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { acc ->
        AccountEditSheet(
            tags = tagsForSheet, editing = acc,
            defaultDateMs = acc.date,
            onSave = { t, cents, tag, isIncome, dateMs ->
                scope.launch {
                    val old = vm.updateReturningOld(acc.id, t, cents, tag, isIncome, dateMs)
                    if (old != null) {
                        undo.show("已保存：${if (t.isBlank()) formatCents(cents) else t}") {
                            vm.restoreFull(old)
                            // 撤回修改：整条还原 + 跳回它原来所在的那一天（不然看着像没还原）
                            goDay(old.date)
                        }
                    }
                }
                // 改完也一样：跳到改后的日期（改到别的日期时当前页就看不到它了）；
                // 回顶同样交给「当天数据变化」统一处理。
                goDay(dateMs)
            },
            onDismiss = { editTarget = null }
        )
    }

    // 点击/长按条目 → 详情弹窗：完整显示备注、金额、标签、时间，可直接修改/删除
    detailTarget?.let { acc ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { detailTarget = null },
            // 同待办详情：条目名即标题，金额紧随其后唱主角，下面两栏是属性。
            title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ★ 7.6.9（用户）：「修改」从底部操作行提到标题行右上角的圆形 i —
                    //   底部只留「关闭 / 删除」两颗小胶囊，挤右下角。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            accountTitle(acc),
                            fontSize = DialogText.Title,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        DialogInfoButton { editTarget = acc; detailTarget = null }
                    }
                    // 详情页用「支出 12.34 元 / 收入 12.34 元」：列表里已经不靠负号表意，
                    // 这里把方向写成文字、单位写全，比一个「¥」前缀更好读。
                    Text(
                        (if (acc.type == 1) "收入 " else "支出 ") +
                            formatCents(acc.amountCents).removePrefix("¥") + " 元",
                        fontSize = DialogText.Main,
                        color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface
                    )
                    if (acc.tag.isNotBlank()) DetailRow("标签", acc.tag)
                    DetailRow("时间", "${DateUtils.formatDateCn(acc.date)} ${DateUtils.formatTime(acc.date)}")
                }
            },
            // 7.6.9：两颗小胶囊、右对齐（「修改」已上移成右上角圆形 i）
            compactButtons = true,
            dismissButton = {
                ShixuDialogTextButton(stringResource(R.string.action_close)) { detailTarget = null }
            },
            confirmButton = {
                ShixuDialogButton(
                    stringResource(R.string.action_delete),
                ) {
                    detailTarget = null
                    // 先登记离场：这一行原地演「划掉 → 化掉 → 收高度」，演完才从库里删。
                    exitingAcc = exitingAcc + (acc.id to System.currentTimeMillis())
                    pulsePlacement()
                    vm.deleteAfter(acc.id, ROW_EXIT_MS + 60L)
                    undo.show("已删除：${accountTitle(acc)}") {
                        // 撤销：把还没执行的删除掐掉，并撤掉离场状态（否则这一行会停在收起态）
                        vm.cancelPendingDelete(acc.id)
                        exitingAcc = exitingAcc - acc.id
                        settledAcc = settledAcc - acc.id
                        // 先立「正在长回来」的身份，再插库：这样这条行第一次进入组合就是 0 高度，
                        // 不会有一帧以自然高度闪现。
                        restoringAccId = acc.id
                        vm.restore(acc)
                    }
                }
            }
        )
    }
}

/**
 * 折叠态的账目汇总条（2026-11-06，用户定案）。
 *
 * 只回答两个问题：今天花了多少、月度预算走到哪了。圆环沿用红卡里那枚的语言，
 * 但轨道和字色换成主题的中性色——它现在站在纸色玻璃上，白环白字会整个消失。
 * 右侧小箭头随展开状态转 90°，是「这里能点」的唯一提示，所以要看得见但不抢戏。
 */
@Composable
private fun HeroSummaryPill(
    dayOut: Long,
    frac: Float,
    over: Boolean,
    hasBudget: Boolean,
    isToday: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val dark = isDarkTheme
    val shape = cardShape(CardRadius.ROW)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val full = formatCents(dayOut).removePrefix("¥")
    val dot = full.lastIndexOf('.')
    val intPart = if (dot >= 0) full.substring(0, dot) else full
    val decPart = if (dot >= 0) full.substring(dot) else ""

    val pillPress = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            // pressScale 一定要在 background/paperSurface 之前：graphicsLayer 只作用于
            // 它后面的内容，放在它们之后会「只缩字不缩卡」，看着像没反应。
            .pressScale(pillPress, pressed = 0.985f)
            .paperSurface(shape, dark = dark, elevation = 3.dp, strokeInset = 0.dp)
            // 方案 A：折叠条本身也染一层极淡的朱（5.5%），和展开卡同族。
            // 原来是纯白纸，点开瞬间跳到满饱和红，明度差太大。
            .background(
                if (dark) Color(0xFFF05654).copy(alpha = 0.08f)
                else Color(0xFFED5736).copy(alpha = 0.055f),
                shape
            )
            .clickable(
                interactionSource = pillPress,
                indication = null,
                onClick = onClick
            )
            // 规则 1：按下即有反馈（原先 indication = null，按下去毫无动静）
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isToday) "本日支出" else "当日支出",
                fontSize = 10.5.sp, fontFamily = songtiFamily, color = muted
            )
            // 「本日支出」已被压到字形高（宋体 ascent+descent ≈ 1.5em，Trim 裁不动），
            // 剩下的缝全在 ¥ 那行上沿：23sp 数字的 ascent 比墨迹高出一截。
            // 直接让金额行上提 2.5dp —— 实测标签→金额 15.2dp → 12.6dp。
            Row(verticalAlignment = Alignment.Bottom,
                modifier = Modifier.offset(y = (-2.5).dp)) {
                // 方案 A：整串金额都用朱色（原先是「¥ 朱 / 数字墨黑」两色，白条上才成立；
                // 染了朱底之后墨黑数字会显得发灰，统一成朱色更像一枚印）。
                // 2026-11-07 深色改白：朱字压在这张暗朱底（#F05654@8%）上，
                // 两者色相太近、明度又拉不开，糊成一团脏红（用户原话「非常丑陋」）。
                // 深色下金额一律暖白；超支仍保留暖橙，那是要提示的信息，不能一起漂白。
                val amountInk = if (over) (if (dark) Color(0xFFFF9E7A) else Color(0xFF9E2A0C))
                                else (if (dark) Color(0xFFF7F1EA) else Color(0xFFC9431F))
                Text(
                    "¥", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    color = amountInk.copy(alpha = 0.78f),
                    fontFamily = songtiFamily,
                    modifier = Modifier.alignByBaseline().padding(end = 1.dp)
                )
                Text(
                    intPart, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    color = amountInk,
                    fontFamily = songtiFamily, letterSpacing = (-0.5).sp,
                    modifier = Modifier.alignByBaseline()
                )
                if (decPart.isNotEmpty()) {
                    Text(
                        decPart, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = amountInk.copy(alpha = 0.78f), fontFamily = songtiFamily,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
        if (hasBudget) {
            MiniBudgetRing(frac = frac, over = over)
            Spacer(Modifier.width(6.dp))
        }
        // 2026-11-07 用户：「我不需要你做这个向下打开的箭头」—— 整条本身可点，箭头是多余提示，删掉。
    }
}

/** 收起条里的小号预算环：42dp、环宽 3.6dp。红卡里那枚 58dp 在这条上会顶到上沿。 */
@Composable
private fun MiniBudgetRing(frac: Float, over: Boolean) {
    val dark = isDarkTheme
    // 轨道改成朱色淡调（原来是中性墨色淡调，放在朱染底上会显脏）
    val track = if (dark) Color(0xFFF05654).copy(alpha = 0.20f) else Color(0xFFED5736).copy(alpha = 0.16f)
    val fill = if (over) (if (dark) Color(0xFFFF9E7A) else Color(0xFF9E2A0C))
               else (if (dark) Color(0xFFF05654) else Color(0xFFED5736))
    val arc = ringArcFrac(frac)
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 3.6.dp.toPx()
            val r = (size.minDimension - sw) / 2f
            val tl = Offset((size.width - 2 * r) / 2f, (size.height - 2 * r) / 2f)
            drawCircle(track, radius = r, style = Stroke(sw))
            if (arc * 360f >= 8f) {
                drawArc(
                    color = fill, startAngle = -90f, sweepAngle = 360f * arc,
                    useCenter = false, topLeft = tl, size = Size(2 * r, 2 * r),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        val label = "${pctText(frac)}%"
        Text(label, fontSize = ringPctSize(label.length, big = false), fontWeight = FontWeight.Bold,
            fontFamily = songtiFamily, color = fill)
    }
}

/** 首页大金额：分档排版（¥ 16sp / 整数 34sp / 小数 16sp），小数与符号都压在基线上。 */
@Composable
private fun HeroAmount(cents: Long, numColor: Color, availWidth: Dp = 999.dp,
                       modifier: Modifier = Modifier) {
    val full = formatCents(cents)
    val neg = full.startsWith("-")
    val body = (if (neg) full.substring(1) else full).removePrefix("¥")
    val dot = body.lastIndexOf('.')
    val intPart = if (dot >= 0) body.substring(0, dot) else body
    val decPart = if (dot >= 0) body.substring(dot) else ""
    // 2026-11-07 Apple Design 复核（规则 15 两条一起修）：
    // ① 字距必须随字号变 —— 大字号该收紧，之前 34sp 的主金额字距是 0，
    //    而同卡里 23sp 的折叠条反而写了 -0.5sp，关系是反的。现在整数 -1.0、¥/小数 -0.4。
    // ② 字号要按可用宽度收 —— 系统把字体调到 1.3 倍时 34sp 会变成 44sp，
    //    原有的固定字号会把整串顶出卡片（5.8.6 踩过一次）。这里按「这串字实际占几个 em」
    //    反推能放多大，最少落到 20sp，强制单行。
    val decEm = if (decPart.isNotEmpty()) 0.291 + (decPart.length - 1) * 0.623 else 0.0
    val emWidth = (if (neg) 0.60 else 0.0) + 0.919 + intPart.length * 0.623 + decEm
    val fontScale = LocalDensity.current.fontScale
    val intSize = (availWidth.value / (emWidth * fontScale).toFloat())
        .coerceIn(20f, HERO_INT_SP)
    val fracSize = (intSize * (HERO_FRAC_SP / HERO_INT_SP)).coerceAtLeast(9f)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (neg) {
            Text("−", fontSize = fracSize.sp, fontWeight = FontWeight.Bold, color = numColor,
                fontFamily = songtiFamily, letterSpacing = (-0.4).sp,
                modifier = Modifier.alignByBaseline())
        }
        Text("¥", fontSize = fracSize.sp, fontWeight = FontWeight.SemiBold,
            color = numColor.copy(alpha = 0.78f), fontFamily = songtiFamily,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.alignByBaseline().padding(end = 1.dp))
        Text(intPart, fontSize = intSize.sp, fontWeight = FontWeight.Bold, color = numColor,
            fontFamily = songtiFamily, letterSpacing = (-1.0).sp,
            modifier = Modifier.alignByBaseline())
        if (decPart.isNotEmpty()) {
            Text(decPart, fontSize = fracSize.sp, fontWeight = FontWeight.SemiBold,
                color = numColor.copy(alpha = 0.80f), fontFamily = songtiFamily,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * 月度预算环（5.8.0）：替代原来那条 7dp 横条。
 * 横条要单独占一行（标签 + 条 + 6dp 间距），环可以直接站在金额右边，
 * 整张卡因此矮一行；超支时弧线与数字转黄。
 */
@Composable
private fun BudgetRing(frac: Float, over: Boolean) {
    val dark = isDarkTheme
    val track = if (dark) Color(0xFFF05654).copy(alpha = 0.20f) else Color(0xFFEFDCCD)
    val fill = if (over) (if (dark) Color(0xFFFF9E7A) else Color(0xFF9E2A0C))
               else (if (dark) Color(0xFFF05654) else Color(0xFFED5736))
    val arc = ringArcFrac(frac)
    Box(Modifier.size(BUDGET_RING_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 5.dp.toPx()
            val r = (size.minDimension - sw) / 2f
            val tl = Offset((size.width - 2 * r) / 2f, (size.height - 2 * r) / 2f)
            drawCircle(track, radius = r, style = Stroke(sw))
            if (arc * 360f >= 8f) {
                drawArc(
                    color = fill, startAngle = -90f, sweepAngle = 360f * arc,
                    useCenter = false, topLeft = tl, size = Size(2 * r, 2 * r),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        // 百分比回到环里（用户 2026-11-07 定案）。不足 1% 写「<1%」——
        // 取整成 0% 会被读成「一分钱没花」。
        // 2026-11-17：超支时写真实数字（「137%」），弧线画满一圈，不再夹成 100%。
        val label = "${pctText(frac)}%"
        Text(label, fontSize = ringPctSize(label.length, big = true), fontWeight = FontWeight.Bold,
            fontFamily = songtiFamily, color = fill)
    }
}

/**
 * 空状态：选了没有账目的一天。
 * 原来这里只留一片空白（右下角 ＋ 在旁边），既没说「这天没有」也没给下一步。
 * 5.8.1：整块在内容区里真正居中（横向靠 Column 居中、纵向靠 fillParentMaxSize），
 * 2026-11-05 换成方印风「叠页方印」——原来那枚是细线青色文档 + 书签，
 * 跟底栏的中式方印 icon 不是同一套图形语言。
 */
@Composable
private fun EmptyLedger(modifier: Modifier = Modifier, isToday: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 画布裁到「墨迹本身」的大小。48 单位网格 → 62dp，笔宽 3 单位
            // （与底栏三个方印图标同一套笔宽比例：3/48）。
            // 图形 = 用户 2026-11-05 选定的方案 3「叠页方印」：
            //   两本错开叠放，前面那本写了两行账，右下角用朱红画一个直角当印角。
            // 2026-11-05 用户从六版里选了 E6「极简：页边 + 三行 + 一点红」：
            // 不再画书本外框——一个 62dp 的方框在这块留白里显得又空又板。
            // 现在只画三样：一条账页的左边界、三行金额线、右下角一枚朱红小圆点（当印章用）。
            // 48 单位网格 → 62dp，笔宽 3 单位（与底栏方印图标同一套比例）。
            Canvas(Modifier.size(width = 62.dp, height = 62.dp)) {
                val u = size.width / 48f
                val ink = muted.copy(alpha = 0.68f)
                val sw = 3f * u
                // ① 账页左边界：从上到下贯通，圆头收口
                drawLine(ink, Offset(11f * u, 8f * u), Offset(11f * u, 40f * u), sw, StrokeCap.Round)
                // ② 三行账：越往下越短，读起来像逐行递减的账目
                val rows = ink.copy(alpha = 0.54f)   // 0.68 × 0.8，与墨线同一支笔、稍弱一级
                drawLine(rows, Offset(18f * u, 14f * u), Offset(40f * u, 14f * u), sw, StrokeCap.Round)
                drawLine(rows, Offset(18f * u, 24f * u), Offset(34f * u, 24f * u), sw, StrokeCap.Round)
                drawLine(rows, Offset(18f * u, 34f * u), Offset(29f * u, 34f * u), sw, StrokeCap.Round)
                // ③ 朱红小圆点：全图唯一一处上色，落在第三行末尾的右下方
                drawCircle(
                    color = accent.copy(alpha = 0.94f),
                    radius = 3.2f * u,
                    center = Offset(39f * u, 35f * u)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (isToday) "今天还没有账目" else "这天还没有账目",
                fontSize = 13.sp, color = muted, textAlign = TextAlign.Center
            )
        }
    }
}

/** 汇总卡：渐变玻璃 + 本月大字 + 三栏 + 预算进度 + 高光扫过 */
/**
 * 红色看板卡的版式常量（5.8.5 定稿）。
 *
 * 用户 2026-11-05 从「高度五档 + 圆角三案」里选了方案 A，并指出真正的痛点是
 * **小格里标签与数字的竖直间距过大**（不是卡片高度）。
 *
 * 同心圆角的硬约束：外卡圆角 R、内格圆角 r、水平内边距 dx、垂直内边距 dy，
 * 必须 dx == dy 且 r == R - dx。所以下面的 PAD_H 与 PAD_V **必须相等**（都是 14dp），
 * 小格半径取 R - 14 = 10dp。任何一边单独改，两条弧线就会重新交叉。
 */
private val HERO_R get() = CardRadius.HERO   // 外卡圆角，统一由 CardRadius 控制
private val HERO_PAD = 12.dp         // 卡内边距（2026-11-07：14→12，卡片整体收矮一档）（上下左右必须一致，否则不同心）
private val HERO_CELL_R get() = HERO_R - HERO_PAD   // 与外卡同心
private val HERO_GAP = 7.dp          // 主金额与三小格之间
private val HERO_CELL_V = 8.dp       // 小格自身上下留白（2026-11-07：9→8）
private val BUDGET_RING_SIZE = 58.dp  // 预算环直径（主金额算可用宽度时要扣掉它）
// 字号保持 5.8.4 原值 —— 用户 2026-11-05：「这下怎么字体数字都变了」。
// 只调间距/圆角，字号一个都不动。
private const val HERO_INT_SP = 34f
private const val HERO_FRAC_SP = 16f
private const val HERO_CELL_LABEL_SP = 11f
private const val HERO_CELL_NUM_MAX = 12.5f   // 13 → 12.5：连同 4.75em 基准一起，给右侧留出余量

/**
 * 环里的百分比文字（用户 2026-11-17：「超过 100% 之后，在圆环中显示百分之多少，
 * 这个数字需要超过一百」）。所以 [frac] 传**未夹取**的真实比值：
 * 137% 就写「137%」，不要 `coerceIn(0f,1f)` 之后再取整成 100%（旧行为，
 * 超支时环里永远显示 100%，看不出超了多少）。
 * 环的弧线仍然只画到 360°（满环），由 [ringArcFrac] 负责夹取。
 */
internal fun pctText(frac: Float): String {
    val v = frac * 100f
    return when {
        v <= 0f -> "0"
        v < 1f -> "<1"
        else -> v.toInt().toString()
    }
}

/** 环弧用的比例：真实比值一律夹到 [0,1] —— 超过 100% 时画满一整圈。 */
private fun ringArcFrac(frac: Float): Float = frac.coerceIn(0f, 1f)

/** 环内文字的字号：位数多了要缩，否则 5 位以上会顶到环上。 */
private fun ringPctSize(textLength: Int, big: Boolean): TextUnit {
    val ladder = if (big) listOf(11.5f, 10.5f, 9f) else listOf(9.5f, 8.5f, 7.5f)
    val step = when {
        textLength <= 4 -> 0
        textLength == 5 -> 1
        else -> 2
    }
    return ladder[step].sp
}

/** 带千分位的金额（仅大卡预算行用；全局 formatCents 不加逗号，账目行与 CSV 导出依赖它）。 */
private fun withThousands(cents: Long): String {
    val full = formatCents(cents)
    val neg = full.startsWith("-")
    val body = full.removePrefix("-")
    val dot = body.lastIndexOf('.')
    val intPart = if (dot >= 0) body.substring(0, dot) else body
    val rest = if (dot >= 0) body.substring(dot) else ""
    val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-" else "") + grouped + rest
}

@Composable
private fun AccountHero(
    monthWord: String, monthOut: Long, dayOut: Long, weekOut: Long, monthIn: Long, budgetCents: Long,
    dayLabel: String,
    onClick: () -> Unit = {},
) {
    // 方案 A「朱印」（2026-11-06 用户从五版里选定 A）：
    // 卡片本体永远是一张浅朱纸，红色只留给金额与预算环。折叠条和展开卡用同一个底色族，
    // 展开时只有面积在变 —— 不再像原来那样「纯白条 → 满饱和红卡」，那一步明度差 60 级，
    // 眼睛读起来是「换了一层皮」而不是「展开了」。
    val dark = isDarkTheme
    // 2026-11-07 深色改白：朱字 + 暗朱底（#34241E）色相太近，糊成一团（用户：「非常丑陋」）
    val inkStrong = if (dark) Color(0xFFF7F1EA) else Color(0xFFC9431F)   // 主金额
    val inkBody = if (dark) Color(0xFFF2E8E2) else Color(0xFF392F41)     // 小格数字
    val inkLabel = if (dark) Color(0xFFC9A99B) else Color(0xFFA07C6E)    // 标签 / 预算行
    val overInk = if (dark) Color(0xFFFF9E7A) else Color(0xFF9E2A0C)     // 超支
    val incomeInk = if (dark) Color(0xFF4FBF8F) else Color(0xFF057748)   // 收入
    val shape = cardShape(CardRadius.HERO)
    // 2026-11-07 Apple Design 复核：原来是 rememberInfiniteTransition + 6 秒一轮的
    // 永久循环高光 —— 正好落在「避免接近 0.2Hz 慢速循环振荡（约每 5 秒一轮）」的禁区里，
    // 深色卡上还能看见一条白斜带一直挂在中间。改成**只在卡片刚展开时扫一次**（用户选的方案 B）：
    // 展开是一次「materialize」动作，光扫过去像材质刚被点亮；扫完就停，卡面安静下来。
    // 减弱动效开着时不扫（这里不做「快速版」，因为纯装饰，直接省掉最合适）。
    val reducedMotion = rememberReducedMotion()
    val sweep = remember { Animatable(-0.6f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            sweep.animateTo(1.6f, tween(durationMillis = 950, easing = LinearEasing))
        }
    }
    val over = budgetCents > 0 && monthOut > budgetCents
    // 同上：环里显示真实百分比，弧线在 ringArcFrac 里夹到满环。
    val frac = if (budgetCents > 0) (monthOut.toFloat() / budgetCents).coerceAtLeast(0f) else 0f

    val haptics = LocalHapticFeedback.current
    // 2026-11-07 Apple Design 复核（规则 1）：按下那一刻就要有反馈，
    // 之前 indication = null 等于「按下去屏幕毫无变化，松手才啪地展开」，手感是死的。
    // 不用波纹（和纸的质感不搭），改用 pressScale：大卡面积大，缩 1.5% 足够被察觉又不晃眼。
    val heroPress = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .pressScale(heroPress, pressed = 0.985f)
            .clip(shape)
            .clickable(
                interactionSource = heroPress,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .background(
                // 只是「纸」的极微渐变（上亮下暗一点点），不再是桃红→妃色→樱桃红的高饱和渐变
                if (dark) Brush.linearGradient(listOf(Color(0xFF34241E), Color(0xFF2C1D18)))
                else Brush.linearGradient(listOf(Color(0xFFFDEFE9), Color(0xFFFBE8E0)))
            )
            .border(glassBorder(), shape)
    ) {
        val w = maxWidth
        // 金额分档（¥ 小 / 整数大 / 小数小）+ 右侧预算环。
        // 上下左右统一 HERO_PAD，小格圆角才能与外卡同心（见 HERO_PAD 的注释）。
        Column(Modifier.padding(horizontal = HERO_PAD, vertical = HERO_PAD)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // 2026-11-07 用户：「大卡也改成本日支出，把本日和本月支出换个位置」。
                    // 主数字改当日之后，右上那枚预算环画的仍是「本月用了多少」——
                    // 它紧挨着本日数字会被读成「本日占了预算 N%」，是错的。
                    // 解决（用户选定方案 A）：环不动，靠这行小字把语义钉在月度上。
                    // 同 HeroCell 那条教训：只给 fontSize、lineHeight 仍沿用 bodyLarge 的 24sp，
                    // 11.5sp 的「本日支出」被塞进 24sp 的行盒里，下面是空的 —— 它与 ¥ 数字之间
                    // 无端多出约 12px 的缝（2026-11-08 与待办备注同一个病根）。
                    Text(
                        dayLabel,
                        style = TextStyle(
                            fontFamily = songtiFamily, fontSize = 11.5.sp, lineHeight = 14.sp,
                            lineHeightStyle = LineHeightStyle(
                                LineHeightStyle.Alignment.Center,
                                LineHeightStyle.Trim.Both
                            )
                        ),
                        color = inkLabel
                    )
                    // 标签本身已经压到字形高（宋体 ascent+descent ≈ 1.5em，Trim 也裁不动），
                    // 剩下的那截缝全在 ¥ 这一行的上沿（大字号 ascent 比数字墨迹高出一截）。
                    // 所以这里不再动 Spacer，直接让整行金额上提 2.5dp —— 实测标签→金额 15.2dp → 12.4dp。
                    HeroAmount(
                        dayOut, inkStrong,
                        availWidth = w - HERO_PAD * 2 -
                            (if (budgetCents > 0) BUDGET_RING_SIZE + 10.dp else 0.dp),
                        modifier = Modifier.offset(y = (-2.5).dp)
                    )
                    // 2026-11-07 用户：「直接删去那一行 本月已用 <1% 预算 20,000 这种，
                    // 还是一样在这个圆环里面加百分号」。预算语义回到环里，
                    // 主金额下面不再挂一行小字 —— 卡片也因此矮了一行。
                }
                if (budgetCents > 0) {
                    Spacer(Modifier.width(10.dp))
                    BudgetRing(frac = frac, over = over)
                }
            }
            Spacer(Modifier.height(HERO_GAP))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 2026-11-07：本月支出从主区挪下来，占第一格；本周、本月收入不动。
                HeroCell("${monthWord}支出", formatCents(monthOut), Modifier.weight(1f))
                HeroCell("本周", formatCents(weekOut), Modifier.weight(1f))
                HeroCell("${monthWord}收入", formatCents(monthIn), Modifier.weight(1f), valueColor = incomeInk)
            }
        }
        // 高光扫过：必须用 matchParentSize 包住——直接 fillMaxHeight 会把卡片撑成满屏高，
        // 周条和当日列表会被挤出屏幕（曾经真的踩过）
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.width(110.dp).fillMaxHeight()
                    .offset(x = w * sweep.value)
                    .graphicsLayer { rotationZ = 14f }
                    .background(
                        Brush.horizontalGradient(
                            // 浅色底上白色高光会像一道脏痕，压到 0.20；深色保持原值
                            listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.26f else 0.20f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
/**
 * 大看板里的三个小格（今日 / 本周 / 本月收入）。
 *
 * 数字字号按「这串金额实际占几个字宽」反推，因为这套打包字体的数字被拉高处理过、
 * 字宽也一起变宽了：数字每个 0.623em、¥ 0.919em、小数点 0.291em —— "¥0000.00" 整串 4.95em。
 * 固定 15sp 时，360dp 小屏 + 字号放大档（1.15）正好顶出格子、折成两行；
 * 现在以 4.95em 为基准（最多 13sp），金额更长就按比例再收，最少 9sp，并强制单行。
 */
private fun HeroCell(label: String, value: String, mod: Modifier, valueColor: Color? = null) {
    val emWidth = value.fold(0) { acc, ch ->
        acc + when (ch) { '¥' -> 919; '.', ',' -> 291; else -> 623 }
    } / 1000.0
    val numSize = (HERO_CELL_NUM_MAX.toDouble() * 4.75 / emWidth)
        .coerceIn(9.0, HERO_CELL_NUM_MAX.toDouble()).sp
    // 行高压到字号本身：Text 只改 fontSize 时 lineHeight 还沿用 LocalTextStyle（M3 bodyLarge = 24sp），
    // 11sp 的标签被塞进 24sp 的行盒，标签与数字之间就空出一大截（用户：「竖直方向上的数字和文字之间距离过大」）。
    val labelLh = (HERO_CELL_LABEL_SP * 1.06f).sp
    val numLh = (numSize.value * 1.04f).sp
    // 只压 lineHeight 还不够：Compose 默认 LineHeightStyle.Trim.None，行盒上下各留半份行距，
    // 两个 Text 叠起来就多出一整份（用户说的「竖直方向上的数字和文字之间距离过大」）。
    // Trim.Both 把「首行上半」和「末行下半」都裁掉，两段文字之间就只剩真实字形间距。
    val cellText = TextStyle(
        fontFamily = songtiFamily,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    val cellShape = cardShape(HERO_CELL_R)
    Column(
        mod.clip(cellShape)
            // 浅朱底上用 7.5% 妃色（不是白），小格才不会在纸面上「浮成一块白斑」
            // 2026-11-07 Apple Design 复核（规则 12）：原来是「朱色 7.5%（深色 13%）压在
            // 浅朱／深朱的卡面上」—— 一层浅半透明叠另一层浅半透明，可读性靠 1px 描边硬撑，
            // 深色下格子和卡底几乎同色。改成实色底，深浅两态都有明确层次。
            .background(
                if (isDarkTheme) Color(0xFF3B2A22)
                else Color(0xFFFFF6F1)
            )
            .border(glassBorder(0.8.dp), cellShape)
            // 左右内边距 7 → 10dp（用户 2026-11-05：右边那格的 ¥12000.00 几乎贴着格子边）。
            .padding(horizontal = 10.dp, vertical = HERO_CELL_V)
    ) {
        Text(label, fontSize = HERO_CELL_LABEL_SP.sp, lineHeight = labelLh, style = cellText,
            color = if (isDarkTheme) Color(0xFFC9A99B) else Color(0xFFA07C6E))
        Text(value, fontSize = numSize, lineHeight = numLh, style = cellText, fontWeight = FontWeight.SemiBold,
            color = valueColor ?: (if (isDarkTheme) Color(0xFFF2E8E2) else Color(0xFF392F41)),
            maxLines = 1, softWrap = false,
            textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AccountEditSheet(
    tags: List<AccTagEntity>,
    editing: Account?,
    defaultDateMs: Long,
    onSave: (title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val key = editing?.id
    // ★ 7.5.3（审查 6.1）：金额改成**自绘小键盘**驱动，不再走系统输入法 ——
    //   所以这里没有 TextFieldValue / 焦点那一套。
    // ★ 7.7.2（复核报告 §4.3）：「只有末尾追加」改成带光标 —— 点金额框可以把光标挪到中间，
    //   插入与退格都按光标走（纯函数在 ui/AmountCaret.kt，单测 AmountCaretTest）。
    var amountField by remember(key) {
        mutableStateOf(AmountField.of(editing?.let { centsToYuanString(it.amountCents) } ?: ""))
    }
    val amount = amountField.text
    var titleV by remember(key) { mutableStateOf(TextFieldValue(editing?.title ?: "")) }
    var noteFocused by remember(key) { mutableStateOf(false) }
    val title = titleV.text
    // 备注默认收起；已有备注自动展开
    var showNote by remember(key) { mutableStateOf(!(editing?.title ?: "").isBlank()) }
    var isIncome by remember(key) { mutableStateOf(editing?.type == 1) }
    var tag by remember(key) { mutableStateOf(editing?.tag ?: "") }
    var dateMs by remember(key) { mutableLongStateOf(defaultDateMs) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val dateCal = remember(dateMs) { DateUtils.calFrom(dateMs) }

    // ★ 7.7.9（用户给的重构方案）：自绘键盘与系统输入法的交接不再靠两个布尔值互相猜。
    //   原因：`keypadVisible` 表达的是「面板显不显示」、`noteFocused` 表达的是「焦点在哪」，
    //   没有一个是「用户此刻想用哪种输入」。于是「备注失焦」被当成了「用户要输金额」——
    //   点标签、点日期、按系统返回键收 IME，都会莫名其妙把数字键盘弹回来。
    //   现在只有一个 [AccountInputMode]：
    //     Amount             自绘键盘（打开弹窗时的默认态）
    //     Note               备注在用系统输入法
    //     SwitchingToAmount  已经请求收起 IME，等 inset 真的归零再让键盘上来
    //     None               当前没有任何键盘（只在交接过程中短暂出现）
    //   `noteFocused` 保留，但只负责焦点表现（橙边、光标），**不再决定键盘**。
    var inputMode by remember(key) { mutableStateOf(AccountInputMode.Amount) }
    // 读一次导航条高度，同时喂给面板和让位量：两边各读一次一旦不同步，
    // 弹窗底就会被键盘顶掉几十像素（多出来的正是手势条那一条）。
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // ★ 7.7.3：注意 focusManager / keyboardController 必须在 **content lambda 内**读
    //   （见下面的 fm / kb）：备注文本框住在 ShixuDialog 的 Dialog window 里，而这里的
    //   函数体是在 Dialog **之外**组合的 —— 在这里取 LocalFocusManager 拿到的是 Activity
    //   window 的那一个，clearFocus() 清了个寂寞（实测点金额框后备注仍是聚焦橙边）。

    // 自绘键盘的三个动作：追加 / 退格 / 清空。都过一遍 sanitize（长度上限、非法字符）。
    fun push(k: String) {
        amountField = AmountCaret.insert(amountField, k); error = null
    }
    fun backspace() {
        amountField = AmountCaret.backspace(amountField); error = null
    }

    ShixuDialog(
        title = if (editing == null) "记一笔" else "修改账目",
        onDismissRequest = onDismiss,
        // ★ 7.7.5（用户：「恢复这个弹窗的宽度，和其他的弹窗统一」）：
        //   不再传 width，用 ShixuDialog 默认的 276dp，与待办/设置里其他弹窗同一档。
        //   380dp 是 7.6.4 为「20 键塞在弹窗正文里」特意放宽的 —— 键盘要够宽才好点。
        //   7.7.3 起键盘已搬到底部独立面板、键宽由屏宽决定，弹窗再宽已没有用处，
        //   反而比别的弹窗胖一圈。所以回到默认档。
        shadowPadH = 15.dp,
        shadowPadV = 56.dp,
        // ★ 7.7.9：让位量跟随输入模式，不再永久留出 287dp。
        //   Amount 档：自绘面板实际高 + 导航条 + 12dp 缝（弹窗坐在面板上方，键盘升降它都不动）；
        //   其余档（Note / Switching / None）：交给 [Modifier.dialogPlacement] 按真实 IME inset
        //   让位 —— 传 0 时它按「无键盘 → 垂直居中」处理，与其它弹窗同一套位置。
        //   实测这两档的落点几乎重合（居中底 1550px vs 面板档 1553px），所以切模式不会看到弹窗跳。
        bottomClearance = if (inputMode == AccountInputMode.Amount)
            KEYPAD_PANEL_HEIGHT + navInset + 12.dp else 0.dp,
        bottomOverlay = {
            BottomKeypadPanel(
                visible = inputMode == AccountInputMode.Amount,
                navInset = navInset,
                onInsert = { push(it) },
                onBackspace = { backspace() },
                onClear = { amountField = AmountCaret.clear(); error = null },
            )
        },
        content = {
            // 在 Dialog 内部读（这里才是 Dialog window 的 focus owner / IME 控制器）
            val fm = LocalFocusManager.current
            val kb = LocalSoftwareKeyboardController.current
            val density = LocalDensity.current
            // 组合期取出 inset 对象本身（`WindowInsets.ime` 是 @Composable 访问器，
            // 不能写进 snapshotFlow 的 block 里）；之后在 snapshotFlow 里读它的 getBottom()，
            // 对象随 inset 变化更新，所以读到的是最新值。
            val imeInsets = WindowInsets.ime
            // ★ 7.7.9：安全交接。`kb.hide()` 只是**请求**，不代表系统键盘这一帧就没了；
            //   以前是「hide() 完立刻把自绘键盘放上来」，于是两条键盘会短暂重叠、弹窗也会被
            //   顶两次。现在点金额框只把模式置成 SwitchingToAmount，这里盯着 Dialog window 的
            //   真实 IME inset，归零了才真正切到 Amount。
            //   超时也要放行：外接实体键盘、或某些输入法不响应 hide() 时 inset 永远不会归零，
            //   不能让用户卡在「一个键盘都没有」的状态里。
            LaunchedEffect(inputMode) {
                if (inputMode != AccountInputMode.SwitchingToAmount) return@LaunchedEffect
                withTimeoutOrNull(700) {
                    snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
                }
                // 期间用户可能又点了备注（模式已改成 Note）—— 只在仍是 Switching 时才落地，
                // 这就是 latest-wins：最后一次明确操作决定最终状态。
                if (inputMode == AccountInputMode.SwitchingToAmount) {
                    inputMode = AccountInputMode.Amount
                }
            }
            // ★ 7.5.3：只读显示框 + 自绘小键盘（乘除显示 × ÷）。点这里不再弹系统输入法。
            // 7.7.2：光标位置由 AmountDisplay 画出来，点文字任意一处就挪过去
            AmountDisplay(
                text = amount, label = "金额", isError = error != null,
                isActive = inputMode == AccountInputMode.Amount,
                caret = amountField.caret,
                // 7.7.9：点金额框 = 用户明确要「用自绘键盘输金额」，这是唯一能进入 Amount 的入口之一
                onCaretChange = {
                    // 先记光标位置（和键盘交接拆开：光标永不受交接结果影响）
                    amountField = AmountCaret.moveTo(amountField, it)
                    if (inputMode != AccountInputMode.Amount) {
                        inputMode = AccountInputMode.SwitchingToAmount
                        fm.clearFocus(force = true)
                        kb?.hide()
                    }
                }
            )
            // 算式的实时结果：只有写了运算符才出现（纯数字时不打扰），保存时也按它入账
            // ★ 7.5.5（复核报告 8.2）：算不出来时也**就地说明原因**（除零 / 括号 / 超上限…），
            //   只有「算式还没写完」在输入过程中按下不表 —— 打到 `128÷` 的瞬间报错等于骂人。
            val expr = if (AmountExpr.isExpression(amount)) AmountExpr.evaluate(amount) else null
            when (expr) {
                is AmountExpr.Eval.Ok -> Text(
                    "= ${formatCents(expr.cents)}", fontSize = DialogText.Hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
                )
                is AmountExpr.Eval.Bad -> if (expr.kind != AmountExpr.ErrorKind.INCOMPLETE) Text(
                    expr.kind.message, fontSize = DialogText.Hint,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
                )
                null -> Unit
            }
            // 错误提示单独占一行（自带 6dp 行距），不走 TextField 的 supportingText：
            // supportingText 会常驻给字段再加一段固定高度，而单独一行只在出错时才出现、弹窗自然长高。
            // （另外，早前输入框高度被压到 52dp 时 supportingText 也曾被裁掉下半截，
            //   实测只剩 13px 笔画、正常是 28px —— 同一个"压高度"坑，现输入框已不指定高度。）
            error?.let {
                Text(it, fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShixuChip(selected = !isIncome, onClick = { isIncome = false }, compact = true,
                    label = { Text(stringResource(R.string.acc_expense)) })
                ShixuChip(selected = isIncome, onClick = { isIncome = true }, compact = true,
                    label = { Text(stringResource(R.string.acc_income)) })
            }
            if (showNote) {
                ShixuField(
                    value = titleV, onValueChange = { titleV = it },
                    label = "备注", singleLine = true,
                    innerModifier = Modifier
                        // ★ 7.7.9：备注获焦 = 用户要用系统输入法 → 进 Note 模式（自绘键盘退场）。
                        //   **失焦分支只更新焦点事实**：以前这里写的是「失焦 → kb.hide() + 面板升回来」，
                        //   于是点标签、点日期、按系统返回键收 IME 都会把数字键盘弹回来 ——
                        //   用户从没表达过那个意图。现在只有「点金额框 / 点收起」才回 Amount 模式。
                        .onFocusChanged {
                            noteFocused = it.isFocused
                            if (it.isFocused) inputMode = AccountInputMode.Note
                        }
                        .firstTapCursorToEnd({ noteFocused }, { titleV }) { titleV = it })
                // 与待办弹窗同一个口径：收起/备注开关左对齐，和下面的「标签」「日期」标签同一竖线
                Text(
                    "收起 ▴", fontSize = DialogText.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 32.dp)
                        .clickable {
                            // 「收起」是明确动作：焦点交还给金额，所以这里可以回 Amount。
                            // 光 clearFocus 不会自己收 IME，主动 hide 一次；模式直接落 Amount，
                            // 不等 inset —— 否则面板要等 260ms 才回来，看着像点漏了。
                            fm.clearFocus(force = true)
                            kb?.hide()
                            showNote = false
                            inputMode = AccountInputMode.Amount
                        }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            } else {
                TextButton(onClick = { showNote = true }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(26.dp)) {
                    // 2026-11-06：原来是主色红，和弹窗里的「保存」抢主色。
                    // 它只是个「还有别的可以填」的次要展开，改成中性色，主色只留给主按钮。
                    Text("＋ 备注", fontSize = DialogText.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tags.isNotEmpty()) {
                Text("标签", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 标签行每次打开弹窗都从第一个标签开始（用户 2026-11-03：有时一打开显示的是中间那几个）。
                // rememberLazyListState() 是 saveable 的：关弹窗时它把手势留下的偏移一起存了，
                // 下次再点开就停在上次的位置 —— 尤其是「滑到一半就点取消」时最明显。
                // 这里用普通 remember（每次重建都是新的 0 位），再补一次 scrollToItem(0) 兜住
                // 「边甩边关」残留的待处理滚动。
                val tagRowState = remember { LazyListState() }
                LaunchedEffect(tagRowState) {
                    withFrameNanos { }            // 等首帧布局落定再归零，免得被入场动画/焦点滚动带偏
                    tagRowState.scrollToItem(0)
                }
                // 两端渐隐：标签多到能横向滚动时，左右各 22dp 把内容"擦"淡
                // （贴到某一端时那一侧自动不渐隐，见 EdgeFade.kt）。
                LazyRow(
                    state = tagRowState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalEdgeFade(tagRowState, 22.dp)
                ) {
                    items(tags, key = { it.id }) { t ->
                        ShixuChip(
                            selected = tag == t.name,
                            onClick = { tag = if (tag == t.name) "" else t.name },
                            compact = true,
                            label = { Text(t.name, fontSize = 13.sp) },
                            leadingIcon = {
                                Box(Modifier.size(10.dp).background(Color(t.color.toInt()), CircleShape))
                            }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(38.dp)) {
                Text("日期", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                StepperArrow(ChevronDir.Left) { dateMs = DateUtils.addDays(dateCal, -1).timeInMillis }
                Text(DateUtils.formatMonthDayCn(dateCal), fontSize = DialogText.Value,
                    modifier = Modifier.padding(horizontal = 1.dp))
                StepperArrow(ChevronDir.Right) { dateMs = DateUtils.addDays(dateCal, 1).timeInMillis }
            }
            // 7.7.3：20 键已经搬到 Dialog 底部的面板里（见上面的 bottomOverlay），
            //   弹窗正文到这里就结束了 —— 表单因此只有 ~270dp 高，不再被键盘撑满屏。
        },
        confirmButton = {
            ShixuDialogButton(stringResource(R.string.action_save)) {
                val cents = amountToCents(amount)
                if (cents == null) {
                    // 7.5.5：算式类错误按原因说明（见 AmountExpr.ErrorKind），纯数字仍用原话
                    error = if (AmountExpr.isExpression(amount))
                        (AmountExpr.evaluate(amount) as? AmountExpr.Eval.Bad)?.kind?.message
                            ?: "算式看不懂"
                    else "金额写错了"
                } else {
                    onSave(title.trim(), cents, tag, isIncome, dateMs)
                    onDismiss()
                }
            }
        },
        dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) }
    )
}
