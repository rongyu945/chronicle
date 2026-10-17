package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import com.rongyu.shixuji.theme.isDarkTheme
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.QuickEntry
import com.rongyu.shixuji.theme.*
import com.rongyu.shixuji.ui.motion.UnifiedWeekDayNavigator
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import com.rongyu.shixuji.ui.motion.toLocalDate
import com.rongyu.shixuji.ui.motion.toStartMs
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private val FAB_BOTTOM = FAB_CLEARANCE   // 跟着底栏等比缩放（原来写死 92dp，底栏收缩后会悬空）
/** 「今」按钮：「＋」上方 64dp（原来 54dp 太近容易误触；改到 72dp 又太散，取中间） */
private val FAB_TODAY_OFFSET = 56.dp
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

    suspend fun budgetCents(): Long = prefDao.get("budget_month")?.toLongOrNull() ?: 0L

    fun add(title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(Account(title = title, amountCents = cents, type = if (isIncome) 1 else 0, tag = tag, date = dateMs))
        }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }

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
    vm: AccountViewModel = viewModel()
) {
    // 同待办：悬浮钮用**自己的**背景层（只录列表内容），不能借底栏那份（会采样到自己 → 递归崩溃）
    val fabLayer = rememberLiquidGlassBackdrop()
    var shownDayMs by rememberSaveable { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val shownDay = remember(shownDayMs) { DateUtils.calFrom(shownDayMs) }
    var showAdd by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<Account?>(null) }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    val undo = rememberUndoController()
    val scope = rememberCoroutineScope()

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

    /**
     * 所有「换一天」的入口都走这里：「今」、点周条日期、记一笔/改账目后跳过去。
     * 只改状态 —— 动画（走一页 / 滑红点 / 整体淡换）由统一周条组件自己按目标
     * 与「已落位日期」的差决定，调用方不再判断差几天、跨不跨周。
     */
    fun goDay(targetMs: Long) {
        shownDayMs = DateUtils.startOfDayMs(targetMs)
    }

    // 大标题折叠：列表滚动时标题 28sp → 17sp，并浮出半透明标题条
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapseA")

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
    val quickList by vm.quickEntries.collectAsState(initial = emptyList())

    var budgetCents by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { budgetCents = vm.budgetCents() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().recordBackdrop(fabLayer)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(start = 20.dp, end = 18.dp, top = if (collapseT > 0.5f) 8.dp else 14.dp,
                        bottom = if (collapseT > 0.5f) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("账目", fontSize = (28f - 11f * collapseT).sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                // 这里原来有一个「本周 / 回本周」灰色文字按钮，和右下角的「今」功能重复，已删
            }

            // 汇总卡：本月支出大字 + 今日/本周/收入 + 月度预算进度
            AccountHero(
                monthWord = heroMonthWord,
                monthOut = monthOut, dayOut = dayOut, weekOut = weekOut, monthIn = monthIn,
                budgetCents = budgetCents
            )

            // 常用记账：点一下记一笔
            if (quickList.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickList, key = { it.id }) { q ->
                        val dot = tagColorMap[q.tag] ?: Color(0xFFC7C7CC)
                        Row(
                            Modifier.glassSurface(RoundedCornerShape(20.dp), dark = isDarkTheme, elevation = 3.dp)
                                .clickable {
                                    val now = DateUtils.atCurrentTime(DateUtils.todayCal())
                                    // 新增不再弹撤回提示条（用户 2026-10-12：只保留删除的撤回）
                                    vm.add(q.title, q.amountCents, q.tag, q.type == 1, now)
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
                // 分组 + 排序同样要 remember：按标签分组要排序+建 Map，条目多时每帧都在算
                val grouped = remember(pDayAcc) {
                    pDayAcc.sortedByDescending { it.date }.groupBy { it.tag }
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
                LaunchedEffect(pageDayMs, pDayAcc.size, pDayAcc.firstOrNull()?.id) {
                    if (pageDayMs == anchorDayStartMs) slotState.animateScrollToItem(0)
                }
                // 只显示当天的账目，按标签分组（同标签吸附，不折叠）
                LazyColumn(
                    state = slotState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (pDayAcc.isEmpty()) {
                        // 空列表不再写提示字（右下角 ＋ 就在旁边）
                    } else {
                        grouped.forEach { (tag, list) ->
                            val tagColor = tagColorMap[tag] ?: Color(0xFFC7C7CC)
                            if (tag.isNotBlank()) {
                                item(key = "h-$tag") {
                                    Text(tag, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp))
                                }
                            }
                            items(list, key = { it.id }) { acc ->
                                SwipeRow(
                                    actions = listOf("编辑" to Color(0xFF8E8E93), "删除" to MaterialTheme.colorScheme.primary),
                                    onAction = { i ->
                                        if (i == 0) editTarget = acc
                                        else {
                                            vm.delete(acc.id)
                                            undo.show("已删除：${accountTitle(acc)}") { vm.restore(acc) }
                                        }
                                    },
                                    enabled = false,
                                    // 切天时同一批账目可能换序，位置动画会造成"乱窜"，只保留占位不带动画
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                                    )
                                ) {
                                    AccountRow(
                                        acc = acc, tagColor = tagColor,
                                        onOpenDetail = { detailTarget = acc }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

        // 同待办：磨砂玻璃圆（和底栏同一套液态玻璃），不用 FAB（避免阴影叠出多边形亮边）
        GlassCircleButton(
            label = "+",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
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
                onClick = { goDay(today.timeInMillis) },
                backdrop = fabLayer,
                modifier = Modifier.padding(20.dp).padding(bottom = FAB_BOTTOM + FAB_TODAY_OFFSET)
            )
        }

        // 提示条位置和待办页保持一致（记账页底部没有摘要条，132dp 就是干净的空档）
        UndoHost(undo, Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp))
    }

    if (showAdd) {
        AccountEditSheet(
            tags = tagList, editing = null,
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
            tags = tagList, editing = acc,
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
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { detailTarget = null },
            title = { Text("账目详情", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(accountTitle(acc), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        (if (acc.type == 1) "收入 " else "支出 ") + formatCents(acc.amountCents),
                        fontSize = 15.sp,
                        color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface
                    )
                    if (acc.tag.isNotBlank()) {
                        Text("标签：${acc.tag}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("时间：${DateUtils.formatDateCn(acc.date)} ${DateUtils.formatTime(acc.date)}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { detailTarget = null }) { Text("关闭") }
                    TextButton(onClick = { editTarget = acc; detailTarget = null }) { Text("修改") }
                    TextButton(
                        onClick = {
                            vm.delete(acc.id)
                            detailTarget = null
                            undo.show("已删除：${accountTitle(acc)}") { vm.restore(acc) }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                }
            }
        )
    }
}

/** 汇总卡：渐变玻璃 + 本月大字 + 三栏 + 预算进度 + 高光扫过 */
@Composable
private fun AccountHero(monthWord: String, monthOut: Long, dayOut: Long, weekOut: Long, monthIn: Long, budgetCents: Long) {
    val shape = RoundedCornerShape(24.dp)
    val infinite = rememberInfiniteTransition(label = "sweep")
    val sweep by infinite.animateFloat(
        initialValue = -0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepX"
    )
    val over = budgetCents > 0 && monthOut > budgetCents
    val frac = if (budgetCents > 0) (monthOut.toFloat() / budgetCents).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF6B61), Color(0xFFFF3B30), Color(0xFFE5342B))
                )
            )
            .border(glassBorder(), shape)
    ) {
        val w = maxWidth
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("${monthWord}支出", fontSize = 12.sp, color = Color.White.copy(alpha = 0.88f))
            Text(formatCents(monthOut), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                color = Color.White)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 「本日」= 正在看的那一天的支出（原写「今日」，翻到别的日期时标签与数字对不上）
                HeroCell("本日", formatCents(dayOut), Modifier.weight(1f))
                HeroCell("本周", formatCents(weekOut), Modifier.weight(1f))
                HeroCell("${monthWord}收入", formatCents(monthIn), Modifier.weight(1f))
            }
            if (budgetCents > 0) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("本月预算 ${formatCents(budgetCents)}", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (over) "超支 ${formatCents(monthOut - budgetCents)}" else "已用 ${(frac * 100).toInt()}%",
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.28f))) {
                    Box(Modifier.fillMaxWidth(frac).height(7.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (over) Color(0xFFFFE07A) else Color.White))
                }
            }
        }
        // 高光扫过：必须用 matchParentSize 包住——直接 fillMaxHeight 会把卡片撑成满屏高，
        // 周条和当日列表会被挤出屏幕（曾经真的踩过）
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.width(110.dp).fillMaxHeight()
                    .offset(x = w * sweep)
                    .graphicsLayer { rotationZ = 14f }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.26f), Color.Transparent)
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
private fun HeroCell(label: String, value: String, mod: Modifier) {
    val emWidth = value.fold(0) { acc, ch ->
        acc + when (ch) { '¥' -> 919; '.', ',' -> 291; else -> 623 }
    } / 1000.0
    val numSize = (13.0 * 4.95 / emWidth).coerceIn(9.0, 13.0).sp
    Column(
        mod.clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(glassBorder(0.8.dp), RoundedCornerShape(14.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.86f))
        Text(value, fontSize = numSize, fontWeight = FontWeight.SemiBold, color = Color.White,
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
    // 用 TextFieldValue：获得焦点时能把光标设到末尾、视图一起滚到最后
    var amountV by remember(key) { mutableStateOf(TextFieldValue(editing?.let { centsToYuanString(it.amountCents) } ?: "")) }
    var titleV by remember(key) { mutableStateOf(TextFieldValue(editing?.title ?: "")) }
    val amount = amountV.text
    val title = titleV.text
    // 备注默认收起；已有备注自动展开
    var showNote by remember(key) { mutableStateOf(!(editing?.title ?: "").isBlank()) }
    var isIncome by remember(key) { mutableStateOf(editing?.type == 1) }
    var tag by remember(key) { mutableStateOf(editing?.tag ?: "") }
    var dateMs by remember(key) { mutableLongStateOf(defaultDateMs) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    val dateCal = remember(dateMs) { DateUtils.calFrom(dateMs) }

    LaunchedEffect(key) { amountFocus.requestFocus() }

    // 每次打开弹窗都把金额光标放到末尾（弹窗是自动聚焦的，这样一进来就能接着改）
    LaunchedEffect(key) {
        withFrameNanos { }
        withFrameNanos { }
        amountV = amountV.copy(selection = TextRange(amountV.text.length))
    }

    /**
     * 点输入框 → 光标和视图一起落到整条文本最后。
     *
     * 不用"延两帧再改"那种写法：那是靠时间赌，TextField 的定位可能在更晚的帧才处理完，
     * 结果把光标又拽回手指点的地方（模拟器上通过、真机上失效）。
     * 现在统一走 jumpCursorToEnd（定义在 CursorEnd.kt）—— 它挂在 Final 通道，
     * 确定性地在 TextField 那次定位之后、同一帧内生效，不依赖帧数。
     */

    ShixuDialog(
        title = if (editing == null) "记一笔" else "修改账目",
        onDismissRequest = onDismiss,
        content = {
            OutlinedTextField(
                value = amountV, onValueChange = { amountV = it; error = null },
                label = { Text("金额") }, singleLine = true,
                isError = error != null,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // 千万别给这个输入框指定高度：M3 带标签的 OutlinedTextField 按字体度量留字身空间，
                // 本 App 的 songti_sc 字体 ascent=1.06em / descent=0.435em，自然高度是 64dp（非规范的 56dp）。
                // 压得比自然高度小，文本行就会在字段内部被裁掉下半截（2026-10-16 实测：52dp 只剩 20px 笔画、
                // 56dp 剩 31px、60dp 剩 42px，而 "ABpgqy" 完整字形是 43px）。
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(amountFocus)
                    .jumpCursorToEnd({ amountV }) { amountV = it })
            // 错误提示单独占一行（自带 6dp 行距），不走 TextField 的 supportingText：
            // supportingText 会常驻给字段再加一段固定高度，而单独一行只在出错时才出现、弹窗自然长高。
            // （另外，早前输入框高度被压到 52dp 时 supportingText 也曾被裁掉下半截，
            //   实测只剩 13px 笔画、正常是 28px —— 同一个"压高度"坑，现输入框已不指定高度。）
            error?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShixuChip(selected = !isIncome, onClick = { isIncome = false }, compact = true,
                    label = { Text("支出") })
                ShixuChip(selected = isIncome, onClick = { isIncome = true }, compact = true,
                    label = { Text("收入") })
            }
            if (showNote) {
                OutlinedTextField(
                    value = titleV, onValueChange = { titleV = it },
                    label = { Text("备注") }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    // 同上：不要指定高度，否则文字下半截被裁
                    modifier = Modifier.fillMaxWidth()
                        .jumpCursorToEnd({ titleV }) { titleV = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("收起 ▴", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showNote = false }
                            .padding(horizontal = 6.dp))
                }
            } else {
                TextButton(onClick = { showNote = true }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(26.dp)) {
                    Text("＋ 备注", fontSize = 13.sp)
                }
            }
            if (tags.isNotEmpty()) {
                Text("标签", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Text("日期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dateMs = DateUtils.addDays(dateCal, -1).timeInMillis },
                    contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(DateUtils.formatMonthDayCn(dateCal), fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 6.dp))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dateMs = DateUtils.addDays(dateCal, 1).timeInMillis },
                    contentAlignment = Alignment.Center) {
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            ShixuDialogButton("保存") {
                val cents = parseYuanToCents(amount)
                if (cents == null) {
                    error = "请输入有效金额（最多两位小数）"
                } else {
                    onSave(title.trim(), cents, tag, isIncome, dateMs)
                    onDismiss()
                }
            }
        },
        dismissButton = { ShixuDialogTextButton("取消", onDismiss) }
    )
}
