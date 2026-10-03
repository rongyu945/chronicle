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
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private val FAB_BOTTOM = 92.dp
private val LIST_BOTTOM = 180.dp

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

    /** 撤销删除：按原 id 插回 */
    fun restore(a: Account) = viewModelScope.launch(Dispatchers.IO) {
        dao.insert(a.copy(id = a.id))
    }

    fun update(id: Long, title: String, cents: Long, tag: String, isIncome: Boolean, dateMs: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(id, title, cents, if (isIncome) 1 else 0, tag, dateMs)
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(todayTick: Long = 0L, vm: AccountViewModel = viewModel()) {
    var shownDayMs by rememberSaveable { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val shownDay = remember(shownDayMs) { DateUtils.calFrom(shownDayMs) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    val undo = rememberUndoController()

    val today = remember(todayTick) { DateUtils.todayCal() }
    val isThisWeek = DateUtils.isSameDay(DateUtils.startOfWeek(shownDay), DateUtils.startOfWeek(today))

    // 大标题折叠：列表滚动时标题 28sp → 17sp，并浮出半透明标题条
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36 }
    }
    val collapseT by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "navCollapseA")

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

    // 一条查询管三周（本周 ±1 周）：翻页不用重查，本周数据也在这里筛出来（省掉单独查一次本周）。
    // 用「保留上一次结果」而不是 collectAsState(initial = 空)：翻页落定那一帧窗口刚换、
    // 新查询还没回来时，中心页显示上一窗口的数据（仍含当前这一周），所以不会闪空。
    val swipeFromMs = DateUtils.addDays(weekStart, -7).timeInMillis
    val swipeToMs = DateUtils.endOfDayMs(DateUtils.addDays(weekEnd, 7).timeInMillis)
    val swipeFlow = remember(swipeFromMs, swipeToMs) { vm.accountsBetween(swipeFromMs, swipeToMs) }
    var swipeAcc by remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(swipeFlow) { swipeFlow.collect { swipeAcc = it } }
    val weekAcc = swipeAcc.filter { it.date in weekStartMs..weekEndMs }
    val monthFlow = remember(monthStartMs, monthEndMs) { vm.accountsBetween(monthStartMs, monthEndMs) }
    val monthAcc by monthFlow.collectAsState(initial = emptyList())

    val dayAcc = weekAcc.filter { it.date in shownDayMs..DateUtils.endOfDayMs(shownDayMs) }
    val dayOut = dayAcc.filter { it.type == 0 }.sumOf { it.amountCents }
    val weekOut = weekAcc.filter { it.type == 0 }.sumOf { it.amountCents }
    val monthOut = monthAcc.filter { it.type == 0 }.sumOf { it.amountCents }
    val monthIn = monthAcc.filter { it.type == 1 }.sumOf { it.amountCents }

    val tagList by vm.tags.collectAsState(initial = emptyList())
    val tagColorMap = remember(tagList) { tagList.associate { it.name to Color(it.color.toInt()) } }
    val quickList by vm.quickEntries.collectAsState(initial = emptyList())

    var budgetCents by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { budgetCents = vm.budgetCents() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                                    vm.add(q.title, q.amountCents, q.tag, q.type == 1, now)
                                    undo.show("已记一笔：${q.title} ${formatCents(q.amountCents)}") { /* 手动删除即可 */ }
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

            // 周视图拆成两块区域、各管各的横滑：
            //   上 = 周条区（在条上左右滑 → 切周），下 = 账目列表区（在列表上左右滑 → 切天）
            val dayFrac = remember { mutableFloatStateOf(0f) }
            Column(Modifier.weight(1f)) {
                // —— 周条区：左右滑动切换一周 ——
                // 必须给固定高度：PeriodSwipe 内部 fillMaxSize，不给高度会吃光剩余空间，
                // 下方账目列表 weight(1f) 被挤成 0 → "在列表上滑"其实还在切周。
                // 70dp = 上下内边距 8 + 星期行 15 + 圆 32 + 点(5+4) 9，留余量给字号放大档
                PeriodSwipe(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    onCommit = { dir ->
                        // 周条换周维持原方向（手往左 = 下一周）；用户明确只要改"切天"的方向
                        shownDayMs = DateUtils.addDays(DateUtils.calFrom(shownDayMs), 7 * dir).timeInMillis
                    }
                ) { off ->
                    val pageDay = remember(shownDayMs, off) {
                        DateUtils.addDays(DateUtils.calFrom(shownDayMs), 7 * off)
                    }
                    val pageWeekStart = DateUtils.startOfWeek(pageDay)
                    val pStartMs = pageWeekStart.timeInMillis
                    val pEndMs = DateUtils.endOfDayMs(DateUtils.addDays(pageWeekStart, 6).timeInMillis)
                    val pWeekAcc = swipeAcc.filter { it.date in pStartMs..pEndMs }
                    // 从周日切到下周一 = 跨周，整条周条 7 格同时换 → 硬切会"卡一下"，
                    // 按「周」做一次快速淡换解决
                    val accWeekKey = remember(pageDay) { DateUtils.startOfWeek(pageDay).timeInMillis }
                    Crossfade(targetState = accWeekKey, animationSpec = tween(150), label = "accWeekFade") {
                        AccountWeekStrip(pageDay, today, pWeekAcc,
                            onSelectDay = { shownDayMs = it.timeInMillis },
                            dragFrac = { dayFrac.floatValue })
                    }
                }
                Spacer(Modifier.height(2.dp))
                // —— 账目列表区：左右滑动切换一天 ——
                // 方向和换周/年/月一致：手往左 = 下一天（内容从右边进来）。
                // 周条上那枚红圆因此是朝「新进来的那一天」滑（位置 = 选中格 - 手指进度）。
                PeriodSwipe(
                    modifier = Modifier.weight(1f),
                    onProgress = { f -> dayFrac.floatValue = f },
                    onCommit = { dir ->
                        shownDayMs = DateUtils.addDays(DateUtils.calFrom(shownDayMs), dir).timeInMillis
                    }
                ) { off ->
                    val slotState = if (off == 0) listState else remember(off) { LazyListState() }
                    val pageDay = remember(shownDayMs, off) {
                        DateUtils.addDays(DateUtils.calFrom(shownDayMs), off)
                    }
                    val pageDayMs = pageDay.timeInMillis
                    val pDayAcc = swipeAcc.filter { it.date in pageDayMs..DateUtils.endOfDayMs(pageDayMs) }

                    Column(Modifier.fillMaxSize()) {

                    // 只显示当天的账目，按标签分组（同标签吸附，不折叠）
                    LazyColumn(
                        state = slotState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (pDayAcc.isEmpty()) {
                            // 空列表不再写提示字（右下角 ＋ 就在旁边）
                        } else {
                            val grouped = pDayAcc.sortedByDescending { it.date }.groupBy { it.tag }
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
                                            onLongPress = { deleteTarget = acc }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                }
            }

        // 同待办：实心圆，不用 FAB（避免阴影叠出多边形亮边）+ 按压反馈
        val addPress = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.BottomEnd)
                .padding(20.dp).padding(bottom = FAB_BOTTOM).size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = addPress, indication = null, role = Role.Button) { showAdd = true }
                .pressScale(addPress)
                .semantics { contentDescription = "记一笔" },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 22.sp, color = Color.White) }

        // “今”按钮：不在今天时出现，一键回今天（和待办页一致）
        AnimatedVisibility(
            visible = !DateUtils.isSameDay(shownDay, today),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val dark = isDarkTheme
            val todayPress = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .padding(20.dp).padding(bottom = FAB_BOTTOM + 54.dp).size(44.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF3A3A3C) else Color.White)
                    .border(0.8.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFFE5E5EA), CircleShape)
                    .clickable(interactionSource = todayPress, indication = null, role = Role.Button) {
                        shownDayMs = today.timeInMillis
                    }
                    .pressScale(todayPress)
                    .semantics { contentDescription = "回到今天" },
                contentAlignment = Alignment.Center
            ) { Text("今", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }

        UndoHost(undo, Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp))
    }

    if (showAdd) {
        AccountEditSheet(
            vm = vm, tags = tagList, editing = null,
            defaultDateMs = DateUtils.atCurrentTime(shownDay),
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { acc ->
        AccountEditSheet(
            vm = vm, tags = tagList, editing = acc,
            defaultDateMs = acc.date,
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { acc ->
        AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("这笔账目", fontWeight = FontWeight.Bold) },
            text = { Text("「${accountTitle(acc)}」 ${formatCents(if (acc.type == 1) acc.amountCents else -acc.amountCents)}") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                    TextButton(onClick = { editTarget = acc; deleteTarget = null }) { Text("修改") }
                    TextButton(
                        onClick = {
                            vm.delete(acc.id)
                            deleteTarget = null
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
                HeroCell("今日", formatCents(dayOut), Modifier.weight(1f))
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

/** 单条账目行（金额等宽右对齐） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(acc: Account, tagColor: Color, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().glassSurface(shape, dark = isDarkTheme, elevation = 4.dp, strokeInset = 2.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(tagColor, CircleShape))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(accountTitle(acc), fontSize = 14.sp)
            Text(DateUtils.formatTime(acc.date), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatCents(if (acc.type == 1) acc.amountCents else -acc.amountCents),
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 96.dp)
        )
    }
}

/** 记账星期条：本周7天，选中高亮，点选切换（类似待办周条） */
@Composable
private fun AccountWeekStrip(
    shownDay: Calendar, today: Calendar, weekAcc: List<Account>, onSelectDay: (Calendar) -> Unit,
    dragFrac: () -> Float = { 0f }
) {
    val start = DateUtils.startOfWeek(shownDay)
    val days = (0..6).map { DateUtils.addDays(start, it) }
    val selIdx = days.indexOfFirst { DateUtils.isSameDay(it, shownDay) }.coerceAtLeast(0)
    val circle = 32.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // 星期几单独一行（显式行高，红圆纵坐标才是确定的）
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
            // 选中圆整条共用一枚，横向位置 =（选中格 - 手指进度）——「手往左 = 下一天」，
            // 下一天在右边，所以圆朝新进来的那天滑。进度在布局阶段读，拖动时不重组。
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
                    val dayM = d.timeInMillis
                    val dayMd = DateUtils.endOfDayMs(dayM)
                    val hasAcc = weekAcc.any { it.date in dayM..dayMd }
                    Column(Modifier.weight(1f).clickable { onSelectDay(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(circle).background(
                            if (isToday && i != selIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                            // 数字画两层：常态色 + 白字（白字层按被红圆盖住的程度淡入，避免穿帮）。
                            // lineHeight 显式给 21sp（= 字号 14 × 字体自身行高 1.48）：
                            // 不给会继承主题 24sp 行高，多出的空白把数字在圆里顶低约 2dp、看着不居中。
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
                        // 和待办周条一致：小红点与日期圆之间留 5dp
                        Box(Modifier.padding(top = 5.dp).size(4.dp).background(
                            if (hasAcc) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                    }
                }
            }
        }
    }
}

/** 记账 / 修改账目共用的弹窗：金额 → 收支 → 备注 → 标签 → 日期 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditSheet(
    vm: AccountViewModel,
    tags: List<AccTagEntity>,
    editing: Account?,
    defaultDateMs: Long,
    onDismiss: () -> Unit
) {
    val key = editing?.id
    var amount by remember(key) { mutableStateOf(editing?.let { centsToYuanString(it.amountCents) } ?: "") }
    var title by remember(key) { mutableStateOf(editing?.title ?: "") }
    // 备注默认收起；已有备注自动展开
    var showNote by remember(key) { mutableStateOf(!(editing?.title ?: "").isBlank()) }
    var isIncome by remember(key) { mutableStateOf(editing?.type == 1) }
    var tag by remember(key) { mutableStateOf(editing?.tag ?: "") }
    var dateMs by remember(key) { mutableLongStateOf(defaultDateMs) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    val dateCal = remember(dateMs) { DateUtils.calFrom(dateMs) }

    LaunchedEffect(key) { amountFocus.requestFocus() }

    AlertDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            modifier = glassDialogModifier(),
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "记一笔" else "修改账目", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it; error = null },
                    label = { Text("金额") }, singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, fontSize = 11.sp) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("支出") })
                    FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("收入") })
                }
                if (showNote) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        label = { Text("备注") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text("收起", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { showNote = false })
                        })
                } else {
                    TextButton(onClick = { showNote = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("＋ 备注", fontSize = 13.sp)
                    }
                }
                if (tags.isNotEmpty()) {
                    Text("标签", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tags, key = { it.id }) { t ->
                            FilterChip(
                                selected = tag == t.name,
                                onClick = { tag = if (tag == t.name) "" else t.name },
                                label = { Text(t.name) },
                                leadingIcon = {
                                    Box(Modifier.size(10.dp).background(Color(t.color.toInt()), CircleShape))
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { dateMs = DateUtils.addDays(dateCal, -1).timeInMillis },
                        contentAlignment = Alignment.Center) {
                        Text("‹", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(DateUtils.formatMonthDayCn(dateCal), fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 6.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { dateMs = DateUtils.addDays(dateCal, 1).timeInMillis },
                        contentAlignment = Alignment.Center) {
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = parseYuanToCents(amount)
                    if (cents == null) {
                        error = "请输入有效金额（最多两位小数）"
                    } else {
                        if (editing == null) vm.add(title.trim(), cents, tag, isIncome, dateMs)
                        else vm.update(editing.id, title.trim(), cents, tag, isIncome, dateMs)
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
