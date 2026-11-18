package com.rongyu.shixuji.ui


import com.rongyu.shixuji.R
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import android.provider.Settings
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.data.LoopSchedule
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
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug

/**
 * 玻璃底栏 + 悬浮按钮占用的底部高度，列表底部留白要够。
 *
 * 2026-11-05 用户：「＋ 的位置有点偏上，带着『今』一起往下一点」，之后又让再往下一点。
 * 改前实测 ＋ 的下沿到玻璃底栏上沿 38.5dp（截图量化），下移 24dp 后 13.4dp，仍显飘；
 * 再下移 8dp → 实测空档约 5.5dp，球的下沿正好压在底栏的高光边上，读感是"按在底栏上"。
 * 别再往下：底栏上沿再吃掉一点就会切进球体的投影。
 * 「今」跟着同一个基准走（见 [FAB_TODAY_BOTTOM]），两枚之间的距离不变。
 */
private val FAB_PLUS_BOTTOM = FAB_CLEARANCE - 32.dp
/**
 * 「今」按钮的中心比「＋」高多少。
 *
 * 2026-11-01 用户：两个键都放大约 25%、两者间距再小一点 —— 圆钮 44 → 55dp（+25%），
 * 中心距 56 → 65dp，于是**边缘净间距 12dp → 10dp**（再小就碰在一起了）。
 */
private val FAB_TODAY_OFFSET = 52.dp
private val FAB_TODAY_BOTTOM = FAB_PLUS_BOTTOM + FAB_TODAY_OFFSET

/** 列表条目统一最小高度：待办 / 循环 / 账目三种行完全一致（介于旧「无时间待办」与旧「带时间账目」之间） */
internal val LIST_ROW_MIN_HEIGHT = 48.dp

/**
 * 日列表行间距。**拖动换位阈值必须用它**：原来阈值写死「行高 + 8dp」，而实际行距是 6dp，
 * 每换一行就多算 2dp，越往下拖越对不上（用户反馈的「拖到下面就乱了」）。
 */
private val DAY_ROW_SPACING = 6.dp





/**
 * 勾上之后标题「墨色 → 灰」的过渡时长。
 *
 * 旧版颜色是**硬切**的：`if (t.done) onSurfaceVariant else onSurface` 在点击那一帧
 * 直接从墨黑跳到灰，肉眼就是一帧变灰（用户 2026-11-18：「变灰的速度有点快了，
 * 希望有过渡动画，颜色一点点变灰」）。现在走 animateColorAsState，与卡片离场的
 * 淡出用同一条曲线（[Motion.Glide]，先快后软），所以「变灰」和「化掉」是同一节奏，
 * 不会出现「先灰一下、再慢慢消失」两段式。
 */
private const val DONE_INK_MS = 300

/**
 * 离场行的「墨色 → 灰」进度（0 = 还是墨，1 = 全灰）；非离场路径是 null。
 *
 * 为什么不能只靠卡片里的 animateColorAsState：勾上那一下这一行会被换进 [RowExitShell]
 * （树的位置变了），整棵子树重建，`animateColorAsState` 首次组合直接取目标值 ——
 * 也就是又变成一帧跳水。这里让 [RevealRow] 把离场那条淡出时钟的补数（1 − fade）
 * 广播下来，卡片照它上色，重建也不会丢起点。
 */
private val LocalRowDoneInk = compositionLocalOf<Float?> { null }

/**
 * 离场行的「划掉」进度（0 = 没划，1 = 划满）；非离场路径是 null。
 *
 * 与 [LocalRowDoneInk] 同一个原因：勾上那一帧这一行会被换进 [RowExitShell]，
 * 子树重建，卡片自己 remember 的 Animatable 会从目标值重新开始。
 * 进度必须由离场外壳持有再广播下来。
 */
internal val LocalRowStrike = compositionLocalOf<Float?> { null }

/**
 * 划掉那道线：progress 0→1 时从左往右扫出来。
 *
 * 画在文字**上面**（先 drawContent 再画线），像笔压过字面；两端各出头 3dp，
 * 右端比左端高 0.6dp —— 手写的线不可能绝对水平，纯水平反而像删除线。
 */
internal fun Modifier.strikeLine(
    progress: () -> Float,
    color: Color,
    thickness: Dp = 1.8.dp
): Modifier = drawWithContent {
    drawContent()
    val f = progress().coerceIn(0f, 1f)
    if (f <= 0.001f) return@drawWithContent
    val over = 3.dp.toPx()
    val total = size.width + over * 2f
    val y = size.height * 0.55f
    val tilt = 0.3.dp.toPx()
    drawLine(
        color = color,
        start = Offset(-over, y + tilt),
        end = Offset(-over + total * f, y - tilt),
        strokeWidth = thickness.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * 拖动排序用的编号区间：三个分区各占一段，互不相交。
 *
 * 为什么不能都用 0..n−1：sortOrder 是**全表**一个序列，三节共用的话，同一条待办换个分区
 * （到期日过了 → 进「过往未完成」，或改成无期限）就会掉进别处的中间去。
 * 用互不相交的大区间，各节的相对顺序就互不打扰；备份里存的就是 sortOrder，顺序也能跟着备份走。
 */
private const val BAND_OVERDUE = 10_000_000
// 分节收起/展开的错峰（2026-11-10）：
// 原来是 N 条同时做高度动画 —— 每一帧都是「一排被压扁的卡片壳、彼此还留着行距」，
// 用户截图反馈「条目收起的动画没做好」。错峰后同一时刻只有 2~3 条在折，
// 视觉上是从下往上「卷起来」，下面的内容连续上移；行距也已随条目一起收（见 per-row padding）。
// 收起与展开**共用同一个时长**（2026-11-10 用户：展开的观感很好，收起要和展开一样快）。
// 之前收 130ms / 开 150ms，加上淡出 90ms / 淡入 120ms 的差异，收起来就是「更快更硬」。
// 2026-11-10：用户反馈「速度曲线生硬、走得也太快」——
// ①时长放软：150 → 220 → 320ms（用户两次「太快了」；收起展开仍共用同一个值，两向等速这条铁律不变）；
// ②曲线换掉 FastOutSlowInEasing：它是「一上来就全速、尾巴慢慢停」，观感就是硬拽一下。
//   换成 cubic-bezier(0.4, 0, 0.2, 1)：起步有加速、中段有速度、收尾柔和，两端都不硬。
// ── 分节展开/收起：单一进度模型（6.9.31）─────────────────────────────────
// 一个分节只有一个 Animatable 进度 p∈[0,1]，条目的高度/透明度/箭头/间距全部由它派生：
// ①只有一个主时钟（旧做法每条一套 AnimatedVisibility + delay，各演各的）；
// ②总时长恒为 SECTION_DURATION_MS，与条目数无关（旧做法条数一多就拖成长尾）；
// ③Animatable 被新目标打断时从当前值继续 —— 连续快速点击天然连续反向，
//   不需要冷却、定时器或「动画期间禁止点击」。
private const val SECTION_DURATION_MS = 480
// 错峰不是「每条各自 delayN 毫秒」，而是进度轴上的起跑偏移：0.28 × 420ms ≈ 118ms 总跨度，
// 只做轻微阅读层次，不形成排队感；展开自上而下、收起自下而上由同一个映射天然给出。
private const val SECTION_STAGGER_SPAN = 0.24f
// 内容透明度窗口（按条目局部进度）：0.08 之前全透明（卡片还太矮、字会被裁），0.72 到 1。
private const val SECTION_FADE_START = 0.08f
private const val SECTION_FADE_RANGE = 0.64f
private const val BAND_NODUE = 20_000_000

/**
 * 分节收起/展开的缓动 = CSS 的 easeInOutCubic。
 * 注意：Compose 的 FastOutSlowInEasing 就是 cubic-bezier(0.4, 0, 0.2, 1)（已从
 * animation-core 的 EasingKt 字节码核对过），拿它当「换条曲线」是自我欺骗 —— 它起步
 * 已经有相当速度，高度动画上读起来就是「被拽了一下」。easeInOutCubic 是
 * cubic-bezier(0.645, 0.045, 0.355, 1)：起步更缓、中段更有速度、收尾同样柔和。
 */
private val SectionEase = EaseInOutCubic

/**
 * 某条待办在它所在分区里的「拖动名次」。
 *
 * 0 = 没被拖动过（或原本就是别的分区的编号）→ 排在该分区最前，彼此之间仍按优先级排；
 * 拖过一次之后整节都会被重编号到 base+1..base+n，于是名次就是用户摆的那个顺序。
 */
private fun bandRank(sortOrder: Int, base: Int): Int =
    if (sortOrder > base) sortOrder - base else 0

/**
 * 「过往未完成」一节的行序（用户 2026-11-17 定稿）：
 *
 *   ① 拖动编号（bandRank）——拖过的整节按用户亲手摆的顺序；
 *   ② 重要性 高 → 中 → 低 → 无 —— **优先级在时间之前**，同色的勾选框环挨在一起；
 *   ③ 到期日 **从新到旧** —— 同优先级下，最近才到期的排上面，久拖的老条目往下沉
 *      （旧版是从旧到新，越久没做的越顶在最前）；
 *   ④ id 兜底 —— 排序键必须唯一且稳定，否则每次 Flow 推送重排、行会乱跳。
 */
internal val overdueTodosOrder: Comparator<Todo> =
    compareBy<Todo> { bandRank(it.sortOrder, BAND_OVERDUE) }
        .thenByDescending { it.priority }
        .thenByDescending { it.dueDate }
        .thenBy { it.id }

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
        // 一个事务里写完：upsert + setSortOrder 分两次写会让 Room 连推两次 Flow，
        // 列表在一两帧内按两种顺序各排一次，看起来就是「撤回/恢复时画面抖一下」。
        AppContainer.database.withTransaction {
            dao.upsert(t.copy(id = t.id))
            dao.setSortOrder(t.id, t.sortOrder)
        }
    }

    /**
     * 勾选 / 取消勾选待办：同时写「完成时刻」。
     * 完成 → 立刻沉到当天列表最下方的「已完成」分区（用户要的自动收缩），
     * doneAt = 完成那一刻，已完成分区按它倒序（最近完成的排最前）。
     */
    fun toggleFull(t: Todo, done: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        // 取消勾选要把 doneAt 清零（实体注释也是这么写的）：留着老时刻的话，
        // 以后重新勾上时若走了不清 doneAt 的路径，会被顶到已完成区最前面。
        dao.setDoneFull(t.id, done, if (done) System.currentTimeMillis() else 0L)
    }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }

    /** 撤销删除：按原 id 原字段插回（upsert：万一 id 已被新条占用也不会崩） */
    fun restore(t: Todo) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            dao.upsert(t.copy(id = t.id))
            dao.setSortOrder(t.id, t.sortOrder)
        }
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
    fun reorder(ids: List<Long>, base: Int = 0) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            // base 让三个分区各用一段互不相交的编号（见 DayTodoList 里的 BAND_*）：
            // 都用 0..n-1 的话，同一条待办换个分区（到期了 / 改成无期限）就会掉进别处的中间。
            ids.forEachIndexed { i, id -> dao.setSortOrder(id, base + i) }
        }
    }

    /**
     * 勾选某一天的循环待办。按「哪一天」记（[com.rongyu.shixuji.data.LoopSchedule.withDone]）：
     * 间隔可以是 3 天也可以是 20 天，没有「这一周」这种概念，一个布尔标记既会串天也没法回溯。
     */
    fun toggleLoop(id: Long, dayMs: Long, done: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val loop = loopDao.getById(id) ?: return@launch
        loopDao.setDoneDates(id, LoopSchedule.withDone(loop, dayMs, done))
    }
    fun deleteLoop(id: Long) = viewModelScope.launch(Dispatchers.IO) { loopDao.delete(id) }

    /**
     * 循环待办拖动换位（2026-11-09）。与 [reorder] 同一个口径，但编号从 0 起、写 loop_todos 自己的列 ——
     * 两张表的顺序互不相干：一条循环待办不会因为某天变成普通待办就掉进 todos 的编号段里。
     */
    /** 删循环待办后撤回（详情弹窗里的删除） */
    fun restoreLoop(t: LoopTodo) = viewModelScope.launch(Dispatchers.IO) { loopDao.upsert(t) }

    fun reorderLoops(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            ids.forEachIndexed { i, id -> loopDao.setSortOrder(id, i) }
        }
    }

    // ── 批量整理（5.7.1）──────────────────────────────────────────────
    // 一次 SQL 处理一批；撤回按原 id + 原 sortOrder 整条还原，位置也跟着回来。

    fun deleteMany(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao.deleteAll(ids)
    }

    fun restoreMany(items: List<Todo>) = viewModelScope.launch(Dispatchers.IO) {
        // 整批一个事务：N 条 × 2 次写 = 2N 次推送，撤回时列表会连续重排十几遍
        AppContainer.database.withTransaction {
            items.forEach {
                dao.upsert(it.copy(id = it.id))
                dao.setSortOrder(it.id, it.sortOrder)
            }
        }
    }

    /**
     * 批量改日期。
     *
     * 顺带把这一批摆到目标那天的最上面（与「新建」「单条改日期」同一个口径，见 pinTopIfDayChanged）：
     * 倒着逐条取「全表最小 − 1」，后取的那条更小 —— 这样整批顶到最前，且它们之间的相对顺序不变。
     */
    fun setDueMany(ids: List<Long>, dueDate: Long) = viewModelScope.launch(Dispatchers.IO) {
        if (ids.isEmpty()) return@launch
        dao.setDueAll(ids, dueDate)
        ids.reversed().forEach { dao.setSortOrder(it, dao.minSortOrder() - 1) }
    }

    fun setPriorityMany(ids: List<Long>, priority: Int) = viewModelScope.launch(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao.setPriorityAll(ids, priority)
    }
}

// 三级导航：年 -> 月 -> 周(含日详情)，默认周
enum class TodoLevel { YEAR, MONTH, WEEK }

@Composable
fun TodoScreen(
    onOpenAccount: () -> Unit = {},
    todayTick: Long = 0L,
    /** 长按图标「记待办」：进来直接把新增弹窗顶起来（只消费一次） */
    openAdd: Boolean = false,
    onOpenAddHandled: () -> Unit = {},
    /** 撤回球停留时长（毫秒），来自设置页「撤回停留时间」 */
    undoMs: Int = UNDO_LIFE_DEFAULT_MS,
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

    // 顶部月份行要显示的那一节（用户选的 ③「收进周条」）。由落位那页的 DayTodoList 上报。
    var dockHead by remember { mutableStateOf<DockHead?>(null) }
    // 换天就把上一节的残留清掉：新的一天还没滚，标题都还在屏内
    LaunchedEffect(anchorDayStartMs) { dockHead = null }

    // 大标题折叠量（0 = 展开 28sp，1 = 收起 17sp）。
    // 跟手 + 松手吸附的实现与踩坑记录都在 ui/TitleFold.kt，待办/账目/设置三页共用。
    val fold = rememberTitleFold(listState)

    // 切换日期后列表从最顶部开始显示由「按天缓存 LazyListState」天然保证（见上），
    // 不需要再在切天时 scrollToItem(0) 去纠 —— 那种「先显示旧位置、再纠回来」
    // 正是用户看到的「先显示中段、过一会才刷新」。

    // 保存后把当天列表滚回顶部，保证刚加的那条一定在眼前
    val scrollScope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(openAdd) {
        if (openAdd) { showAdd = true; onOpenAddHandled() }
    }
    var detailTarget by remember { mutableStateOf<Todo?>(null) }
    // 循环条目的详情（2026-11-09）：条目只留标题+备注，循环间隔与下次提醒搬到这里
    var loopDetailTarget by remember { mutableStateOf<LoopTodo?>(null) }
    var editTarget by remember { mutableStateOf<Todo?>(null) }
    // 整理页（批量改日期 / 改优先级 / 删除）。普通 remember：切 Tab 回来自动收，和账目那边的 searching 一个口径。
    var batching by remember { mutableStateOf(false) }
    // 「已完成」分组顶部那颗「清空」原来是**点了就删**（只有事后撤回条兜底）。
    // 用户 2026-11-06 点名：清空已完成必须有确认弹窗。这里存待清的条目，null = 不弹。
    var clearDoneTarget by remember { mutableStateOf<List<Todo>?>(null) }
    // 长按某条进来时，把那条预先选上
    var batchSeed by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val undo = rememberUndoController()
    // 整理页占台期间把底栏收掉：它自己有「取消」，底栏是第二条退出路径，
    // 而且会压住底部操作条。
    //
    // ⚠️ 必须按 token 登记，不能靠 `if (batching) hold()` + `onDispose { if (batching) release() }`：
    //    `batching` 是 `by remember` 的委托属性，onDispose 里读到的永远是**当前值**而不是
    //    这一次 effect 的快照 —— batching true→false 时 onDispose 读到 false、不 release，
    //    进出一次计数净 +1 永不归零，底栏就永久停在 alpha=0（2026-11-10 用户报的
    //    「从整理界面回退时底栏彻底消失」）。token 登记两个方向都幂等，见 ChromeState。
    val chromeToken = remember { Any() }
    DisposableEffect(batching) {
        if (batching) ChromeState.hold(chromeToken)
        onDispose { ChromeState.release(chromeToken) }
    }
    // 日列表「刚少了一条」的短暂窗口：勾选完成 / 删除 / 取消完成的那一刻置 true，0.45s 后自动放下。
    // 窗口里打开位置动画（被去掉的那条淡出 + 其余行平滑补位），平时关着 ——
    // 切天时若开着，跨天不变的 key（过往未完成/无期限/每日固定）会被动画从旧位置拖到新位置，
    // 看起来像「条目先窜到顶格、再回落」（以前踩过）。
    // 必须是「点击那一刻就 true」（而不是等列表回调），因为点击 → 写库 → Flow 推送 中间还隔着几十毫秒。
    var holdPlacement by remember { mutableStateOf(false) }
    val placementScope = rememberCoroutineScope()
    // 每一次「开窗口」都领一个号；到点只允许**最后那一次**关窗。
    // 原来是无条件 delay(450) 后置 false：删完 1.5 秒再撤回时，两次脉冲的定时器会叠在一起，
    // 旧的那颗子弹在半路把新窗口关掉 —— 撤回就有一半概率不播位置动画（行一帧瞬移）。
    // 2026-11-09 逐帧实测就是这么随机（同样操作两次，一次平滑一次瞬跳）。
    var placementToken by remember { mutableIntStateOf(0) }
    fun pulsePlacement(ms: Long = 800L) {
        holdPlacement = true
        val mine = ++placementToken
        placementScope.launch { delay(ms); if (placementToken == mine) holdPlacement = false }
    }

    /**
     * 撤回时用：除了打开位置动画，还要让「刚回来的那条」淡入 0.2s。
     *
     * 只开位置动画是不够的 —— 被撤回的行是**重新插回列表**的，不淡入就是一帧「啪」地出现，
     * 用户 2026-11-09 报的「撤回之后画面突然跳一下」就是它（配合下面把写库并成一个事务，
     * 行不会因为两次数据推送先后落位、闪两下）。
     */
    var restoredId by remember { mutableStateOf<Long?>(null) }

    /**
     * 改期保存后「要露出来」的那一条 + 它所在的日（零点毫秒）。
     * 见 [jumpToDayIfNeeded]：跳过去的当天列表不会自己把新插进来的条目滚进视野。
     */
    var focusTodoId by remember { mutableStateOf<Long?>(null) }
    var focusDayMs by remember { mutableStateOf(0L) }
    fun pulseRestore(id: Long?) {
        pulsePlacement()
        restoredId = id
        placementScope.launch {
            delay(800)
            if (restoredId == id) restoredId = null
        }
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

    // 每天的事项概况（件数 / 已完成 / 当天最高优先级）：月视图画彩点和脚注、年视图画色块和完成条都读它。
    // 一次遍历算好，格子 O(1) 查；不在两个 Composable 里各扫一遍 todos（几百条时每次重组都要重扫）。
    // 聚合逻辑在 CalendarGrids.kt 里（纯函数，可单测）。
    val evBriefOfMonth = remember(todos) { buildDayBriefs(todos) }
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
    fun jumpToDayIfNeeded(dueMs: Long, focusId: Long? = null) {
        val target = if (dueMs == 0L) DateUtils.startOfDayMs(today.timeInMillis) else DateUtils.startOfDayMs(dueMs)
        if (target == dayStartMs) return
        // 跳过去还不够：目标日那张列表的 LazyColumn 会把滚动锚点钉在**原先可见的第一条**上，
        // 被改期进来的这条插在它上面 → 落在视口外，顶边只露一截。用户 2026-11-18：
        // 「跳转到的那个日期的界面不会显示这个条目，需要左右划拉一下才会刷新出来」。
        // 记下 id 交给目标页的 DayTodoList，由它在数据到齐后瞬时对位（scrollToItem）。
        if (focusId != null) { focusTodoId = focusId; focusDayMs = target }
        jumpToDay(dueMs)
    }

    // 返回键：整理页 → 先退回列表；年/月视图 → 降级到周视图
    BackHandler(enabled = batching) { batching = false }
    BackHandler(enabled = !batching && level != TodoLevel.WEEK) {
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
                animationSpec = tween(270, easing = Motion.PaperEnter),
                label = "levelFade"
            ) { lv ->
            if (lv == TodoLevel.WEEK) {
                // ★ 周视图 = 统一周条组件 ui/motion/UnifiedWeekDayNavigator：
                //   一条运动状态同时驱动 周条 + 那枚红点 + 日列表。
                //   · 上区（周条）横滑 = 换一周，下区（列表）横滑 = 换一天（与旧版一致）
                //   · 跨周拖动时日期带在拖动第一帧就跟着手指走，没有「滑完再补滑」
                //   · 红点整条只画一枚 —— 跨周过渡中也不会同时出现两个红圆
                //   · 点击 /「今」由 selectedDate 驱动，组件自己决定走一页还是淡换
                // 整理页整块顶替内容区（连 ＋/今 两枚玻璃钮一起收掉，见下面的 if (!batching)）
                // 长按进整理页 / 退出：不要「啪」地整块换掉（用户 2026-11-04：「这个画面的切换
                // 非常的僵硬」）。进去 = 从下方轻轻推上来 + 淡入 + 微放大；退出 = 反向推出。
                // 240ms 量级，跟勾掉条目的滑出（220ms）同一个节奏，界面像是同一次动作的两面。
                AnimatedContent(
                    targetState = batching,
                    transitionSpec = {
                        // 7.4.0（方案 P2）：进 350ms / 出 280ms。原来进 260、出 220，
                        // 两个方向都不足以让人看清「这是换了一层界面」，只剩一下位移的残影。
                        if (targetState) {
                            (
                                fadeIn(tween(240, easing = Motion.PaperEnter)) +
                                slideInVertically(tween(350, easing = Motion.PaperEnter)) { it / 20 } +
                                scaleIn(tween(350, easing = Motion.PaperEnter), initialScale = 0.975f)
                            ).togetherWith(fadeOut(tween(140, easing = Motion.PaperExit)))
                        } else {
                            (
                                fadeIn(tween(200, easing = Motion.PaperEnter)) +
                                slideInVertically(tween(280, easing = Motion.PaperEnter)) { -it / 24 }
                            ).togetherWith(
                                fadeOut(tween(180, easing = Motion.PaperExit)) + slideOutVertically(tween(280, easing = Motion.PaperExit)) { it / 20 }
                            )
                        }
                    },
                    label = "batchMode",
                    modifier = Modifier.fillMaxSize()
                ) { inBatch ->
                // 「清空已完成」二次确认。放在这里而不是 DayTodoList 里面：Dialog 是浮层、
                // 不参与测量，塞在 AnimatedContent 的 content 里不会改变任何布局。
                clearDoneTarget?.let { pending ->
                    ShixuDialog(
                        title = "确认清空？",
                        onDismissRequest = { clearDoneTarget = null },
                        compactButtons = true,
                        confirmButton = {
                            ShixuDialogButton("清空 ${pending.size} 条") {
                                clearDoneTarget = null
                                pulsePlacement()
                                vm.deleteMany(pending.map { it.id })
                                undo.show("已删除 ${pending.size} 条") { pulseRestore(pending.firstOrNull()?.id) ; vm.restoreMany(pending) }
                            }
                        },
                        dismissButton = {
                            ShixuDialogTextButton(stringResource(R.string.action_cancel)) { clearDoneTarget = null }
                        }
                    ) {}
                }
                if (inBatch) {
                    TodoBatchPanel(
                        vm = vm,
                        initialSelected = batchSeed,
                        onClose = { batching = false; batchSeed = emptySet() },
                        onDeleted = { items ->
                            pulsePlacement()
                            undo.show("已删除 ${items.size} 条") { pulseRestore(items.firstOrNull()?.id); vm.restoreMany(items) }
                        },
                        onUndoable = { msg, revert -> undo.show(msg) { pulseRestore(null); revert() } }
                    )
                } else {
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
                            titleSize = titleSizeFor(fold).sp,
                            fold = fold,
                            surplus = monthSurplus,
                            dock = dockHead,
                            onSurplusClick = { onOpenAccount() },
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
                        // 撤回（restoredId != null）也是一次「变多」，但它不该动视口：
                        // 一滚就把整块内容带上去，撤回的那条从顶边滑进来 —— 用户 2026-11-09
                        // 说的「从天上掉下来」。撤回的补位交给 animateItem / 行自己的动画。
                        if (grew && pageDayMs == anchorDayStartMs && restoredId == null) {
                            slotState.animateScrollToItem(0)
                        }
                    }
                    DayTodoList(
                        todos = todos, loops = loops, shownDay = pageDay,
                        // 循环待办：完成状态是「本周」维度、不是分区，照旧直接切
                        // 待办勾选：完成即沉底（自动收缩到「已完成」分区）；
                        // 同时给日列表一个脉冲，让它在这之后短时间打开「位置动画」（其余行平滑补位）。
                        // 勾完给一条可撤回的提示条 —— 「已完成」分区默认折叠，没有提示的话条目
                        // 沉下去、或被移出「过往未完成」时看起来就像凭空消失（删除本来就有一条）。
                        onToggleTodo = { t, done ->
                            pulsePlacement()
                            vm.toggleFull(t, done)
                            if (done) undo.show("已完成：${t.title}") { pulseRestore(t.id); vm.toggleFull(t, false) }
                        },
                        onOpenDetail = { detailTarget = it },
                        onToggleLoop = vm::toggleLoop,
                        // 取消完成（并给撤回）：整条还原 + 完成时刻一起还原，
                        // 否则「撤回」再勾上时 doneAt 会是老时间，条目被顶到已完成区最前面。
                        //
                        // ⚠️ 传进来的 t 是「已完成」那一行本尊（done = true）：
                        //    直接 restoreFull(t) 等于把 done = true 原样写回库 —— 界面弹了
                        //    「已取消完成」的黑条，可那条还在已完成里（用户报的
                        //    「取消完成卡住、只有黑框、没真的取消」就是这个）。
                        //    所以先显式翻成未完成并清掉 doneAt 再落库；撤回用原值整条还原。
                        onRestoreTodo = { t ->
                            pulsePlacement()
                            vm.restoreFull(t.copy(done = false, doneAt = 0L))
                            undo.show("已取消完成：${t.title}") { pulseRestore(t.id); vm.restoreFull(t) }
                        },
                        onReorder = { ids, base -> vm.reorder(ids, base) },
                        onReorderLoops = vm::reorderLoops,
                        onOpenLoopDetail = { loopDetailTarget = it },
                        state = slotState,
                        listModifier = Modifier.fillMaxSize(),
                        holdPlacement = holdPlacement,
                        restoredId = restoredId,
                        focusId = if (focusDayMs == pageStartMs) focusTodoId else null,
                        onFocusHandled = { focusTodoId = null },
                        reportDock = isCenter,
                        onDock = { dockHead = it },
                        // 长按某条 → 进整理页并预选它（用户 2026-11-03：批量整理该做在待办上）
                        onLongPressTodo = { t -> batchSeed = setOf(t.id); batching = true },
                        // 「已完成」分组顶部的「清空」：一样可撤回
                        // 不再直接删：先落到 clearDoneTarget，由下面的确认弹窗真正执行
                        onClearDone = { items -> clearDoneTarget = items }
                    )
                }
                }
                }
            } else {
                // 年视图/月视图：表头 + 整页横滑切年/切月（这一套导航本轮不动）
                Column(Modifier.fillMaxSize()) {
                    TodoHeader(
                        lv, shownYear, shownMonth, shownDay,
                        titleSize = titleSizeFor(fold).sp,
                        fold = fold,
                        surplus = monthSurplus,
                        onSurplusClick = { onOpenAccount() },
                        onPrev = { bigNav.step(-1) },
                        onNext = { bigNav.step(1) },
                        onTitleClick = { if (lv == TodoLevel.MONTH) levelName = TodoLevel.YEAR.name })
                PeriodSwipe(modifier = Modifier.weight(1f).fillMaxWidth(), controller = bigNav, onCommit = { dir ->
                    when (lv) {
                        TodoLevel.YEAR -> shownYear = clampYear(shownYear + dir)
                        else -> shiftMonth(dir)
                    }
                }) { off ->
                    // ★ 年视图必须用自己的滚动状态。
                    //   原来 `off == 0` 时直接用 listState —— 那是**日列表**的滚动状态，
                    //   于是「日列表滚到第 8 条 → 点标题进月 → 再进年」会继承 index≈8，
                    //   年视图一进来就停在第 9 个月；而且 fold 也是按它算的，标题呈折叠态。
                    //   反向一样：从年视图退回周视图，日列表被拽到中段。
                    val slotState = if (off == 0 && lv != TodoLevel.YEAR) listState
                    else remember(off, lv) { LazyListState() }
                    when (lv) {
                        TodoLevel.YEAR -> {
                            val pageYear = clampYear(shownYear + off)
                            YearNavView(evBriefOfMonth, pageYear, slotState,
                                { y, m -> shownYear = y; shownMonth = m; levelName = TodoLevel.MONTH.name })
                        }
                        TodoLevel.MONTH -> {
                            val (py, pm) = DateUtils.addMonths(shownYear, shownMonth, off)
                            val pageYear = clampYear(py)
                            MonthNavView(evBriefOfMonth, pageYear, pm,
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
        // 底部纸色压层（三个页面共用，见 ui/BottomFade.kt）
        // 必须在悬浮按钮之前调用：＋/今/撤 下沿贴着底栏上沿，正好落在压层的实心段里，
        // 放到按钮之后会把它们一起糊掉。
        // 整理页：面板自己带毛玻璃带，纸色压层会盖在它上面把底部胶囊糊成半透明 → 整页不画
        if (!batching) BottomPaperFade()
        // 本月盈余已移到顶部表头（日期右侧）：底部这块只保留压层，不再有多余的一层"摘要条"。
        // 悬浮按钮：磨砂玻璃版（和底栏同一套液态玻璃），仍自己画圆不用 FloatingActionButton
        //（FAB 的阴影会浮出多边形亮边）。涟漪被全局关掉了，按压缩放提供点下去的反馈。
        // 「＋」一样要淡出/缩回：整理页淡入时它不能瞬间消失（否则像画面被硬切）
        AnimatedVisibility(
            visible = !batching,
            enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.7f),
            exit = fadeOut(tween(150)) + scaleOut(tween(170), targetScale = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            GlassCircleButton(
                label = "+",
                // 2026-11-06 实心版：整颗球就是主题妃色，不再用半透明去模拟玻璃体色。
                tint = MaterialTheme.colorScheme.primary,
                fallbackColor = MaterialTheme.colorScheme.primary,
                labelColor = Color.White,
                contentDescription = "新建待办",
                onClick = { showAdd = true },
                backdrop = fabLayer,
                modifier = Modifier.padding(20.dp).padding(bottom = FAB_PLUS_BOTTOM)
            )
        }
        // “今”按钮：不在今天或不在周视图时出现（月/年视图翻远了也能一键回来）
        // "今"按钮：进出用缩放+淡入淡出，不要突然出现/消失
        AnimatedVisibility(
            visible = !batching && (!isToday || level != TodoLevel.WEEK),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val dark = isDarkTheme
            GlassCircleButton(
                label = "今",
                // 参考图：白球比红球小一圈（186 vs 150 px），两颗**左缘对齐**、
                // 而不是右缘 —— 所以这里靠 padding 右移 6dp 把左边缘调齐。
                tint = if (dark) Color(0xFF3A3430) else Color.White,
                fallbackColor = if (dark) Color(0xFF3A3430) else Color.White,
                labelColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                // 参考图「今」墨迹 68px / 球径 186px = 36.6%；本机 songti_sc 墨迹≈1.0em，
                // 所以 44dp 的球要 16sp（原来 22sp 顶到 50%，把球塞满了）
                labelSize = 16.sp, labelBold = true,
                border = if (dark) Color(0x33FFFFFF) else Color(0xFFEAE0D2),
                contentDescription = "回到今天",
                onClick = { goToday() },
                backdrop = fabLayer,
                modifier = Modifier.padding(20.dp).padding(bottom = FAB_TODAY_BOTTOM)
            )
        }
        // 撤回提示：2026-11-05 用户选定「悬浮球」——和 ＋/今 同一颗球、同尺寸（55dp），
        // 挨在 ＋ 左边 10dp、同一水平线上。整理页时 ＋ 收起来了、底部是那三颗按钮，
        // 球就退到按钮上方 12dp，别压住它们。
        UndoHost(
            undo,
            backdrop = fabLayer,
            lifeMs = undoMs,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = if (batching) 148.dp else FAB_PLUS_BOTTOM)
                .padding(end = if (batching) 0.dp else 61.dp)
        )
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
                jumpToDayIfNeeded(due, focusId = t.id)
                // 回顶同上：交给「当天数据变化」统一处理
            },
            onDismiss = { editTarget = null })
    }

    // 点击/长按条目 → 详情弹窗：完整显示标题、备注、优先级、日期，可直接修改/删除
    detailTarget?.let { t ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { detailTarget = null },
            // 不再挂一行「待办详情」：这一层是详情，条目名本身就是标题。
            // 多一行固定标签只会把真正的信息压小（原来标题 17 粗 vs 条目名 16 中，几乎同大，
            // 读起来是两行平级的信息，谁也不突出）。
            title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ★ 7.6.9（用户）：「修改」提到标题行右上角的圆形 i，底部只留两颗小胶囊。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.title,
                            fontSize = DialogText.Title,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        DialogInfoButton { editTarget = t; detailTarget = null }
                    }
                    if (t.detail.isNotBlank()) {
                        DetailRow("备注", t.detail)
                    }
                    // 2026-11-07 用户：「红点/勾选框的颜色已经很明显能看出哪个更重要，
                    // 不需要再标出来高中低」—— 详情弹窗里这行「优先级：高」删掉。
                    // 列表条目本来就没有文字标签，优先级一律靠左侧色条表达；
                    // 保留的只有「编辑弹窗里的三档选择器」（那是输入，不是展示）。
                    DetailRow(
                        "日期",
                        if (t.dueDate != 0L) DateUtils.formatDateCn(t.dueDate) else "无期限"
                    )
                    if (t.done) {
                        DetailRow("状态", "已完成", valueColor = MaterialTheme.colorScheme.primary)
                    }
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
                    // 详情弹窗里的删除原来漏了 pulsePlacement()：只有勾选/批量删除/清空
                    // 会开位置动画，单条删除走这里 → 剩下的行**一帧瞬移**顶上，
                    // 用户看到的就是「删掉一条，下面整个跳上去」。
                    pulsePlacement()
                    vm.delete(t.id)
                    detailTarget = null
                    undo.show("已删除：${t.title}") { pulseRestore(t.id); vm.restore(t) }
                }
            }
        )
    }

    // 循环条目详情（2026-11-09 用户要求）：条目上只留「标题 + 备注」两行，
    // 「循环 · 每 N 天」和「下次提醒」挪到这里。版式与待办详情同源（条目名当标题、DetailRow 两栏）。
    loopDetailTarget?.let { loop ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { loopDetailTarget = null },
            title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(loop.title, fontSize = DialogText.Title, fontWeight = FontWeight.Bold)
                    if (loop.detail.isNotBlank()) DetailRow("备注", loop.detail)
                    DetailRow(
                        "循环",
                        if (loop.intervalDays <= 1) "每天" else "每 ${loop.intervalDays} 天"
                    )
                    DetailRow(
                        "下次提醒",
                        if (loop.nextDate == dayStartMs) "今天" else DateUtils.formatDateCn(loop.nextDate),
                        labelWidth = 58.dp
                    )
                    if (LoopSchedule.isDoneOn(loop, loop.nextDate)) {
                        DetailRow("状态", "本轮已完成", valueColor = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
                        ShixuDialogTextButton(stringResource(R.string.action_close)) { loopDetailTarget = null }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
                        ShixuDialogButton(
                            stringResource(R.string.action_delete),
                                ) {
                            val t = loop
                            vm.deleteLoop(t.id)
                            loopDetailTarget = null
                            undo.show("已删除：${t.title}") { vm.restoreLoop(t) }
                        }
                    }
                }
            }
        )
    }
}

/** 日历底部记账摘要：一行小字，点击跳转记账页 */
/**
 * 日历页底部的「本月盈余」。
 *
 * 用户 2026-11-05 在看过的六个方案里选了 ④「底部渐隐（不加底板）」：
 * 不再是一行裸字压在条目上，而是在它身后铺一条**从透明渐到纸色的竖向渐变**，
 * 列表滚上来时先淡掉再被字压住 —— 比加一块底板轻，又比裸字可读。
 *
 * 渐变的底边贴着玻璃底栏上沿（所以 padding 用底栏高度 + 间距），
 * 上沿要够高（88dp）才有"慢慢化开"的感觉，太短会看出条纹。
 */
@Composable
private fun TodoHeader(
    level: TodoLevel, shownYear: Int, shownMonth: Int, shownDay: Calendar,
    onPrev: () -> Unit, onNext: () -> Unit, onTitleClick: () -> Unit,
    titleSize: TextUnit = 28.sp, fold: Float = 0f,
    /** 本月盈余（分）。null = 不显示 */
    surplus: Long? = null,
    onSurplusClick: () -> Unit = {},
    /**
     * 非空 = 某一节的小节标题已经滚出列表顶部：把「11月」换成这一节的名字（用户选的 ③「收进周条」）。
     * 只在周视图生效 —— 月/年视图的列表结构里没有这些小节。
     */
    dock: DockHead? = null
) {
    // 本月盈余默认不显示：点表头的空白处才翻出来（再点一下收起）。
    // 用 rememberSaveable 是有意的 —— 翻月/切层级会重组表头，用户既然翻出来了就别在他眼皮底下又消失。
    var showSurplus by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            // 不再加底色：列表在自己的区域里滚动，不会钻到标题下面；加一层白底反而很丑
            .background(Color.Transparent)
            // 整行可点 = 点空白处切换盈余。标题、‹ › 自己是 clickable，
            // 子节点会先把点击吃掉，所以点它们不会误触发这里。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showSurplus = !showSurplus }
            // 上下 padding 也走 fold 连续插值：旧版是 `if (collapsed)` 布尔切档，
            // 在折叠过半那一帧高度瞬少 4dp，列表会往上跳一下。
            // 2026-11-07 用户：「整体再往右去」→ 起边 28dp（与周条、卡片左缘对齐）；
            // 「还可以缩」→ 收的是右边这段空，把盈余顶到行尾，不是继续缩起边。
            .padding(start = 28.dp, end = 10.dp,
                top = lerp(14.dp, 8.dp, fold), bottom = lerp(6.dp, 8.dp, fold)),
        // 2026-11-07 用户选 C：日期不另起一行，压到「11月」基线下方 ——
        // 靠底部对齐做出「右下角」的感觉，表头高度不变、列表起点不挪。
        verticalAlignment = Alignment.Bottom
    ) {
        val title: String = when (level) {
            TodoLevel.WEEK -> "${shownDay.get(Calendar.MONTH) + 1}月"
            TodoLevel.MONTH -> "${shownYear}年"
            TodoLevel.YEAR -> "${shownYear}年"
        }
        val clickable = level != TodoLevel.YEAR
        // 「11月」⇄「▍过往未完成 · 12」共用一个槽位：
        // 两个都常驻在同一个 Box 里、只动 alpha —— 若用 AnimatedContent 会连高度一起动画，
        // 周条会被顶得上下跳（高度一变，Column 里的周条就跟着挪）。
        val dockTarget = if (level == TodoLevel.WEEK) dock else null
        val dockA by animateFloatAsState(
            targetValue = if (dockTarget != null) 1f else 0f,
            animationSpec = tween(150),
            label = "dockLabel"
        )
        Box(
            modifier = if (clickable) Modifier.clickable { onTitleClick() } else Modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Text(title, fontSize = titleSize, fontWeight = FontWeight.Bold,
                letterSpacing = titleTrackingFor(fold).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = 1f - dockA })
            if (dockTarget != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = dockA }
                ) {
                    // 和列表里的小节标题同一枚朱红竖条，一眼能对上「这是同一节」
                    Box(
                        Modifier
                            .size(width = 2.5.dp, height = 12.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        buildString {
                            append(dockTarget.title)
                            dockTarget.count?.let { append(" · ").append(it) }
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                }
            }
        }
        // 2026-11-07 用户：「这个具体日期的控件不是跟花重叠了吗？移到 11月 的右边」。
        // 原来这里是 weight(1f)，把日期/箭头一路顶到屏幕最右，正好落在花枝上。
        // 现在只留一个固定小间距，日期紧跟月份；弹性空白挪到整行末尾。
        // 2026-11-07 用户两次：「7日还可以再往左」—— 间距由 10dp → 4dp → 0，
        // 再给日期组吃 5dp 负偏移，把箭头点击区的内边距也抵掉。


        // 周视图大标题已经是「10月」，右边只要「19日」；月视图大标题是「2026年」，右边写「10月」。
        // 原来两处都把月份/年份写了两遍（大标题 + 右边日期各一次），看着像重复。
        val dayLabel = when (level) {
            TodoLevel.WEEK -> "${shownDay.get(Calendar.DAY_OF_MONTH)}日"
            TodoLevel.MONTH -> "${shownMonth + 1}月"
            TodoLevel.YEAR -> ""
        }
        // 2026-11-01 箭尾改平头、横向只剩半高宽，点击区跟着收窄，箭头与日期的间距才不会显空
        val clickSize = if (level == TodoLevel.WEEK) 25.dp else 27.dp
        val arrowSize = if (level == TodoLevel.WEEK) 12.5.dp else 14.5.dp
        // 2026-11-17 用户：「去掉年视图界面上年份旁边的那个左右箭头」。
        // 年视图里 dayLabel 是空的，这组箭头就紧贴在「2026年」右边、孤零零两颗，
        // 而年视图本来就能整页横滑切年（下面的 PeriodSwipe），点月格也能进月视图，
        // 所以整个导航组都不画。月视图的「‹ 10月 ›」保留 —— 那里箭头夹着月份，是有语义的。
        if (level != TodoLevel.YEAR) {
            // 整行底部对齐之后，日期组要自己抬一点，才不会被大字的行盒底边顶到肉
            Row(
                modifier = Modifier.padding(bottom = 4.dp).offset(x = (-5).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左箭头热区往左多伸 8dp（墨迹不动），点「月」字右侧的空白也能退一天。
                ChevronButton(ChevronDir.Left, onClick = onPrev, box = clickSize, chevron = arrowSize,
                    stroke = 1.1.dp, corner = 8.dp, shiftX = 4.dp, growStart = 7.dp,
                    contentDescription = "上一个")
                if (dayLabel.isNotBlank()) {
                    Text(dayLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 1.dp))
                }
                ChevronButton(ChevronDir.Right, onClick = onNext, box = clickSize, chevron = arrowSize,
                    stroke = 1.1.dp, corner = 8.dp, shiftX = (-5).dp, contentDescription = "下一个")
            }
        }
        // 本月盈余：2026-11-07 用户「放到这个日期的右边去」—— 原来贴在日期左边，
        // 和「‹ 7日 ›」挤在一起像同一组控件；挪到右箭头之后，日期组的导航语义就干净了。
        // （更早它还是屏幕底部的一条独立横带，压着透明底栏，接缝明显。）
        AnimatedVisibility(
            visible = showSurplus && surplus != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) {
            if (surplus != null) {
                val dot = if (surplus >= 0) "＋" else "−"
                // 用户 2026-11-05：「本月盈余改成文字和数字两行」。
                // 挤成一行时 11.5sp 的「本月盈余 ＋¥11,679.82」跟右边的日期、箭头连成一片，
                // 看不出是两件事；拆成上下两行后，标签退成 10sp 的淡字、数字放大到 14sp，
                // 一块清楚的「小标题 + 数值」，宽度也只多占一点（右侧日期与箭头仍不动）。
                // 2026-11-07 用户：「本月盈余改成左对齐」。原来是 End（标签和金额都右收尾），
                // 跟左边月份的起始方向相反，一眼看过去是两块对顶。改 Start 后整行只有一个起点方向。
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .clickable { onSurplusClick() }
                        // 2026-11-07 用户：「本月盈余再往左」—— 与日期组的间距 14 → 6dp。
                        // 2026-11-13 用户：「左移一个字的距离」—— 标签 10sp，一个汉字约 10dp，
                        //   挪 10dp 即整块「本月盈余 / −¥579.00」的左边线正好让出一格字宽。
                        //   用 offset 而不是减小 start padding：padding 已经只剩 6dp，不够让。
                        //
                        // ★ 2026-11-17 年视图删掉箭头后，这 10dp 负偏移把它压进「2026年」的字身上
                        //   （实测标题右缘 x=322、「本月盈余」左缘 x=312，重叠 10px）——
                        //   年视图里箭头那 54dp 的间隔没了，负偏移就不再是「让字形对齐」而是「撞字」。
                        //   所以年视图改为 0 偏移 + 16dp 起始间距（保证字与字之间有肉眼可见的空当）。
                        .offset(x = if (level == TodoLevel.YEAR) 0.dp else (-10).dp)
                        .padding(start = if (level == TodoLevel.YEAR) 16.dp else 6.dp, bottom = 2.dp)
                ) {
                    Text(
                        "本月盈余",
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                    Text(
                        "$dot${formatCents(if (surplus < 0) -surplus else surplus)}",
                        fontSize = 13.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                }
            }
        }
        // 弹性空白搬到末尾：整行仍然点得到（切本月盈余），但日期组靠左排、不再压花枝。
    }
}

/**
 * 拖动进行中才用：把「顺序表」和「库里最新的列表」合成一份显示顺序。
 * 不在顺序表里的（拖动期间别处新增/撤回补回来的）按 sortOrder 落位，
 * 免得它们被扔到列表最前、和手指下那条错位。
 */
private fun <T : Any> mergeByOrder(
    base: List<T>,
    order: List<Long>,
    idOf: (T) -> Long,
    sortOf: (T) -> Int
): List<T> {
    val byId = base.associateBy(idOf)
    val inOrder = order.toHashSet()
    val known = order.mapNotNull { byId[it] }.toMutableList()
    base.filter { idOf(it) !in inOrder }.forEach { t ->
        val at = known.indexOfFirst { sortOf(it) > sortOf(t) }
        if (at < 0) known.add(t) else known.add(at, t)
    }
    return known
}

/** 日列表：当日待办（未完成） + 每日固定 + 过往未完成 + 无期限 + 已完成（沉底、默认折叠） */
// 分组标题用 stickyHeader 吸顶（实验 API）
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayTodoList(
    todos: List<Todo>, loops: List<LoopTodo>, shownDay: Calendar,
    onToggleTodo: (Todo, Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    onToggleLoop: (Long, Long, Boolean) -> Unit,
    onRestoreTodo: (Todo) -> Unit,
    /** 拖动结束落库：ids = 新的顺序表，base = 该分区的编号起点（当天 0，另两节用 BAND_*） */
    onReorder: (List<Long>, Int) -> Unit,
    /** 循环待办那一节拖完落库（写 loop_todos 自己的 sortOrder，编号 0..n-1） */
    onReorderLoops: (List<Long>) -> Unit = {},
    /** 点循环条目 → 详情弹窗（「循环 · 每 N 天」现在是详情里的信息） */
    onOpenLoopDetail: (LoopTodo) -> Unit = {},
    /** 长按任意一条 → 进整理页并预选这条 */
    onLongPressTodo: (Todo) -> Unit = {},
    /** 「已完成」分组顶部的「清空」：交给上层删库并给撤回 */
    onClearDone: (List<Todo>) -> Unit = {},
    state: LazyListState, listModifier: Modifier = Modifier,
    /**
     * 「刚勾掉/删掉一条」的短暂窗口（由 TodoScreen 在点击那一刻置 true，约 0.45s 后自动放下）。
     * 为 true 时打开行的位置动画，让其余行平滑补位 —— 用参数而不是回调，是因为它必须在
     * 数据真的变化之前就已经是 true（点击 → 写库 → Flow 推送 中间隔了几十毫秒）。
     */
    holdPlacement: Boolean = false,
    /** 刚被撤回的那一条：进来的这次淡入 0.2s（其余行仍是秒现，别让切天/切 Tab 也淡入） */
    restoredId: Long? = null,
    /** 改期保存后要「露出来」的那一条（只有它所在那一页会收到，见 TodoScreen.focusTodoId） */
    focusId: Long? = null,
    /** 对位完成（或等超时）后回调，让上层把 focusId 清掉，免得下次切回来又滚一次 */
    onFocusHandled: () -> Unit = {},
    /**
     * 只有「落位那一页」上报小节名，侧页（±1 天）不上报：
     * 三页共用一套变量，谁都报的话顶部那一行会随手指乱跳。
     */
    reportDock: Boolean = false,
    /** 上报「当前滚到了哪一节」给顶部的月份行；null = 标题仍在屏内，不用显示 */
    onDock: (DockHead?) -> Unit = {}
) {
    // 归一化到当天零点：即使 shownDayMs 万一带了时刻，过滤区间也仍然是整天
    val startMs = DateUtils.startOfDayMs(shownDay.timeInMillis)
    val endMs = DateUtils.endOfDayMs(startMs)
    // item key 一律带日期前缀。原因：位置动画（animateItem 的 placement）现在**常开**，
    // 而「过往未完成 / 无期限 / 每日固定」这些条目在切天时 key 不变、只是位置变了 ——
    // 常开的 placement 会把它们从旧位置一路拖到新位置（以前踩过：条目先窜到顶格再回落）。
    // 把日期编进 key 后，切天 = 全新 key = 不做位置动画；同一天内增删则始终有动画。
    val dayTag = "$startMs-"
    val isToday = DateUtils.isToday(shownDay)

    // ── 「完成」的离场：先原地收起，收完再摘掉（2026-11-18）───────────────────────
    //
    // 旧做法（8.4.0 及以前）：勾上即从数据里消失。`animateItem` 对**被删掉**的条目只能演淡出
    // （fadeOutSpec 280ms），而它占的那一格在第一帧就没了 —— 下面各行用 150ms 的 placement
    // 立刻补上来。两件事同时开跑、时长差一倍，观感就是用户说的
    // 「上面的还没动，下面的就急着向上走了」（逐帧截图实测：下面那张已经顶到顶格，
    // 上面那张的 ghost 还在原地慢慢淡，两张卡叠在一起）。
    //
    // 现在：数据库照旧**点击即写**（正确性、撤回都不变，见 TodoCard 里那段说明），
    // 只是渲染层把这一行多留 ROW_EXIT_MS，期间高度由一条时钟从自身高度收到 0、末尾淡出，
    // 下面的行是被真实高度推着走的（AnimatedReveal + placementSpec = null）——
    // 与分节展开/收起同一套模型，全列表只有一个时钟在驱动位移，不会出现两个速度。
    var exiting by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    val exitingIds: Set<Long> = exiting.keys
    fun beginExit(id: Long) { exiting = exiting + (id to System.currentTimeMillis()) }
    fun settleExit(id: Long) { exiting = exiting - id }
    // 兜底：行被 LazyColumn 回收 / 中途切天时它的动画会被取消，靠这条定时清掉，
    // 否则那一条会永远以「半截高度」留在列表里。
    //
    // 2026-11-18：这里的时长必须跟着 MotionDebug 的慢放倍率走 —— 否则 4× 慢放时
    // 动画要 1.44s、兜底却按 1.32s 就到点，行会在演到一半时被摘掉（LaunchedEffect 被取消，
    // `onSettled` 永远不执行，量时长时看到的就是「只有开始、没有结束」）。
    LaunchedEffect(exiting) {
        if (exiting.isEmpty()) return@LaunchedEffect
        delay(MotionDebug.ms(ROW_EXIT_MS) * 2L + 600L)
        val now = System.currentTimeMillis()
        exiting = exiting.filterValues { now - it < MotionDebug.ms(ROW_EXIT_MS) + 500L }
    }
    // 勾上 = 先登记离场，再写库（写库不能等动画：动画期间这一行可能被回收）。
    // 反过来勾掉（含撤回）要立刻取消离场，否则那一行会先收成 0 再"长"回来。
    val toggleWithExit: (Todo, Boolean) -> Unit = { tt, done ->
        if (done) beginExit(tt.id) else settleExit(tt.id)
        onToggleTodo(tt, done)
    }
    // 撤回是父层直接改数据的（不走 toggleWithExit），这里补一次清场
    LaunchedEffect(restoredId) {
        if (restoredId != null) exiting = exiting - restoredId
    }

    // ⚠️ 这些过滤**必须 remember**：本组件被 PeriodSwipe 同时画三份（前天/今天/后天），
    // 不 remember 的话每帧重组都把整个 todos 表过滤 3×5 遍、循环的列表还要 O(n²) 找位，
    // 跨周时数据全变 → 一次重组要做 15 遍全表扫描 → 掉帧（实测跨周 100% jank、单帧 200ms，
    // 周内只有 46ms）。改成按输入 remember 后只在数据/日期真的变了才算一次。
    val dayAll = remember(todos, startMs, endMs) { todos.filter { it.dueDate in startMs..endMs } }
    // 未完成在上、已完成（自动）沉到最下面那个分区。
    // 正在离场的那一条 done 已经是 true 了，但**还要留在原位**（正在收起），所以也算进本节。
    val dayOpen = remember(dayAll, exitingIds) {
        dayAll.filter { !it.done || it.id in exitingIds }
    }
    /**
     * 「已完成」分区 = ① 这天到期的已完成 + ② **今天完成的**（含「过往未完成」「无期限」里刚勾掉的）。
     *
     * 只收 ① 的话，勾掉一条过期／无期限的待办后它既不在未完成区、也不在已完成区 ——
     * 直接从页面上消失，用户以为数据被删了，得翻回它原来那天的日期才找得到。
     * 收 ② 之后：勾掉即沉到这个分区（还能取消完成），和当天条目行为一致。
     */
    val dayDone = remember(dayAll, todos, startMs, endMs, exitingIds) {
        val byId = LinkedHashMap<Long, Todo>()
        // 正在收起的那一条**先不进这一节**：它同时还留在上面原位，两边都渲染会出现重复 key
        // （LazyColumn 直接崩），而且它其实还没「落」到已完成里来。
        dayAll.filter { it.done && it.id !in exitingIds }.forEach { byId[it.id] = it }
        todos.filter { it.done && it.doneAt in startMs..endMs && it.id !in exitingIds }
            .forEach { byId[it.id] = it }
        byId.values.sortedByDescending { it.doneAt }
    }
    val overdueTodos = remember(todos, startMs, exitingIds) {
        todos.filter { it.dueDate != 0L && it.dueDate < startMs && (!it.done || it.id in exitingIds) }
            // 排序规则见 overdueTodosOrder：重要性（高→中→低→无）在前，同级按到期日**从新到旧**
            // （用户 2026-11-17：重要的在上面，其次时间新的在上面）。
            // ★ 2026-11-08：这一节现在可以拖动排序。拖过之后整节的 sortOrder 被重编号到
            //   BAND_OVERDUE+1 起，于是「编号」成为第一键；没拖过的条目排出的 rank 是 0，
            //   仍然按优先级排在按名次摆过的条目前面。
            .sortedWith(overdueTodosOrder)
    }
    val noDueTodos = remember(todos, isToday, exitingIds) {
        if (isToday) todos.filter { it.dueDate == 0L && (!it.done || it.id in exitingIds) }
            .sortedWith(
                compareBy<Todo> { bandRank(it.sortOrder, BAND_NODUE) }
                    .thenByDescending { it.priority }
            )
        else emptyList()
    }
    // 循环待办按「开始日期 + 下一次提醒 → 间隔天数」算哪天出现（见 LoopSchedule.isDueOn）
    val dayLoops = remember(loops, startMs) { loops.filter { LoopSchedule.isDueOn(it, startMs) } }
    // 「已完成」默认折叠（跨天/重启都回到收起）；「过往未完成」默认展开、折叠按钮保留
    //（4.5.0 曾把它默认收起，用户 2026-10-09 要求改回默认展开）
    var doneExpanded by rememberSaveable(startMs) { mutableStateOf(false) }
    var overdueExpanded by rememberSaveable(startMs) { mutableStateOf(true) }

    // 分节收起/展开的高度动画状态。放这里（而不是 LazyListScope 里）是因为 remember/LaunchedEffect
    // 只能在 @Composable 上下文调用；清单本身用这两个 state 决定「要不要把 0 高度条目留在列表里」。
    //
    // 初值必须是「当前真实状态」，不能写死 false。
    // 原因：底栏切 Tab 走的是 AnimatedContent(targetState = 选中的 Tab)，切走时 TodoScreen 被整个
    // 销毁、切回来时重新创建 —— `remember { MutableTransitionState(false) }` 每次都从「收起」起步，
    // 而 overdueExpanded 默认是 true，于是每回到待办页都会从 0 高度播一次展开动画
    // （用户 2026-11-09 真机发现：每次切回待办，过往未完成都「弹」一下）。
    // 直接把初值给成 overdueExpanded，首帧 currentState == targetState，AnimatedVisibility 不播入场。
    // 分节展开/收起的**唯一主时钟**：0 = 完全收起，1 = 完全展开。
    // 初值 = 当前真实状态（不能是 0f），否则页面重建会从 0 播一次入场
    // （用户 2026-11-09 真机发现过「每次切回待办，过往未完成都弹一下」）。
    // key 用 startMs：跨天切日期时分节状态重置，进度必须跟着回到目标值，不能残留上一周期的中间量。
    val overdueProgress = remember(startMs) { Animatable(if (overdueExpanded) 1f else 0f) }
    val doneProgress = remember(startMs) { Animatable(if (doneExpanded) 1f else 0f) }
    // 系统「移除动画 / 减少动态效果」打开时直接落位（布局终态仍然正确），不播长动画。
    val context = LocalContext.current
    val animationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 7.4.1：时长过一遍 MotionDebug.ms —— 分节是调参时最想慢放看清的一处（480ms 有 20 多条
    // 条目在错峰，原速下根本看不出「谁先谁后」，2× 才看得出 0.24 的错峰跨度）。
    val sectionSpec: AnimationSpec<Float> =
        if (animationsOff) snap() else tween(MotionDebug.ms(SECTION_DURATION_MS), easing = SectionEase)
    // 「完成一条」的离场：**两条独立的速率曲线**。
    //
    //  - `exitFadeSpec`：整行（纸面 + 字）的不透明度。先站住 ROW_FADE_HOLD_MS（120）让人看清线在走，
    //    再用 200ms 化掉（PaperInOut：两头慢，收得干净）。
    //  - `exitSpec`：高度。先按 ROW_EXIT_HOLD_MS 定住不动，再在剩下的时间里收到 0。
    //
    // 为什么要拆：同一个进度既管高度又管透明度时，下面那张卡的上边缘会从下往上扫过
    // 离场卡的脸 —— 用户的原话是「好像下面的把上面的吃掉了一样」。
    // 现在淡出先完成，等下面开始扫的时候那块已经看不见了，观感是「卡片化掉 → 位置合上」。
    // 三条离场时钟（线 / 淡出 / 高度）统一由 ExitSpecs.kt 提供 —— 记账页用同一份，
    // 两处不可能再各调各的。
    val exitSpecs = rememberExitSpecs()
    val exitFadeSpec: AnimationSpec<Float> = exitSpecs.fade
    val exitStrikeSpec: AnimationSpec<Float> = exitSpecs.strike
    val exitSpec: AnimationSpec<Float> = exitSpecs.height

    // 点击只写目标状态；动画由 effect 驱动。快速连点时新目标打断上一次 animateTo，
    // Animatable 从**当前值**继续走 —— 不重置到 0/1，也不会出现旧动画结束回写新状态。
    LaunchedEffect(overdueExpanded, sectionSpec) {
        overdueProgress.animateTo(if (overdueExpanded) 1f else 0f, sectionSpec)
    }
    LaunchedEffect(doneExpanded, sectionSpec) {
        doneProgress.animateTo(if (doneExpanded) 1f else 0f, sectionSpec)
    }


    // 「收尾补位」窗口不再需要（2026-11-10）：6.9.13~6.9.22 的 settleUntil 是为兜住
    // 「收完那一帧，0 高度条目被摘掉、连带 6dp 行距一起消失 → 上面内容往上跳一下」；
    // 6.9.23 把行距搬进每条自己的 padding 后，条目收到 0 高度时行距也已是 0，摘掉不再产生位移。
    // 这段补位反而把收起的尾巴多拖了 240ms（用户要求收起与展开同速）。现在只留两条规矩：
    //   ① 分节高度动画期间不叠位置动画（否则下面的行追不上正在长出来的卡片，卡片会互相压住）；
    //   ② 平时用弹簧（拖动排序 / 删除让位）。
    val sectionAnimating = overdueProgress.isRunning || doneProgress.isRunning
    // 2026-11-10 第二次修正（用户明确：展开保持 6.9.23 的样子，收起对齐展开的速度）：
    // 6.9.24 为了让收起别比展开多拖一截，在动画结束后又压了 150ms 的 placementCooling，
    // 那 150ms 里 placementSpec = null ⇒ 高度动画一结束，下面那些行「啪」地直接到位，
    // 弹簧没了 —— 展开本来是弹簧收尾的，这么一压等于把两个方向一起改快了。
    // 现在恢复 6.9.23 的收尾：动画期间不叠位置动画（否则下面的行追不上正在长出来的卡片），
    // 动画一结束立刻交回弹簧；收起用与展开同一组时长（150ms 高度 + 120ms 淡入淡出、错峰同向），
    // 所以两个方向的起止时刻一致 —— 既不加速展开，也不给收起留尾巴。
    // 只有「收起」路径需要一段冷却：收起来的最后一步是把 0 高度条目从列表里摘掉，
    // 那一刻列表重排，弹簧会接手再慢慢往上追 ~0.4s（×20 慢放实测：收起 13.07s、
    // 展开 6.00s，差的就是这截尾巴）。展开没有这一步，本来就没有位移要追。
    // 所以冷却只挂在收起上 —— 展开仍走 6.9.23 的弹簧收尾（用户要的原样），
    // 收起也不再被弹簧拖长，两个方向都落在 6s 上下。
    // 条目不再在收起结束时被摘掉，也就没有那一下布局跳变 —— 弹簧不必再追，
    // 收起与展开的起止时刻自然一致。placement 只留一条规矩：
    // 高度动画期间关掉（否则下面的行追不上正在长出来的卡片），其余时候弹簧（拖动/删除让位）。
    // 位置动画规格（2026-11-10 定稿）：
    //  · 分节高度动画期间 → null（下面的行由高度动画逐帧推动，不能再叠一层）；
    //  · 撤回刚发生（restoredId 非空）→ 弹簧，保持「你做得很好」的那版手感；
    //  · 其余 → 140ms 缓动。收起结束时那 ~46px 的收尾若交给弹簧要追 0.45s，
    //    收起就比展开长一倍（×20 实测 12.9s vs 5.9s）；换成 140ms tween 后
    //    两个方向都落在 6s 上下，收尾依然是「滑」过去而不是一帧跳过去。
    //    展开本来就没有残余位移，这个规格对展开的表现没有任何影响 —— 展开仍是 6.9.23 的样子。
    val rowPlacement: FiniteAnimationSpec<IntOffset>? = when {
        sectionAnimating -> null
        // 「完成一条」的原地收起期间也必须关掉（2026-11-18）：
        // 那一行的真实高度会在 ~277ms 里 1→0，下面的行本来就由布局逐帧推上来；
        // 再叠一条 150ms 的 placement tween，它们会**提前**跑完全程，
        // 于是又回到「上面还没收完、下面已经窜到位」——用户报的就是这个双时钟。
        exitingIds.isNotEmpty() -> null
        // 撤回期间同理（2026-11-18 第二轮）：那一行的高度 0→自然高要 440ms，
        // 邻居由真实高度逐帧压下。原来这里给的是弹簧（「你做得很好」那版手感），
        // 但弹簧 0.45s 跑完时卡片才长到一半 —— 用户看到的就是「条目闪现、下面自己沉下去」。
        restoredId != null -> null
        // 收尾这段只能给 150ms：给 200ms 会让收起比展开多出 0.06s（×20 实测 8.83s vs 7.06s），
        // 而两个方向等速是用户明确要求过的硬指标。
        else -> tween(150, easing = SectionEase)
    }
    // ── 手动拖动排序：当天 / 过往未完成 / 无期限 三节各自独立 ──────────────
    //
    // 三节共用「被拖的是谁、拖了多远」这套状态，但**各持一份顺序表**：
    // 拖哪一节就只在该节内部换位 —— 跨分区拖动没有语义（那件事叫「改日期」）。
    //
    // 为什么另两节要独立：以前它们画着拖动把手 ≡ 却根本没接手势（长按毫无反应），
    // 而 TODO 行只在当天那节传 dragModifier —— 把手是「可拖动」的承诺，说话要算数。
    var openOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var overOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var noDueOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    /** 循环待办那一节自己的顺序表（2026-11-09：它也带把手了） */
    var loopOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    /** 正在拖哪一节：0 = 当天，1 = 过往未完成，2 = 无期限，3 = 循环待办 */
    var dragSection by remember { mutableIntStateOf(0) }
    // 被拖着的那条 id（null = 没在拖）。声明放在 openOrder 同步之前 —— 下面就靠它判断
    // 「现在别覆盖顺序表」，Kotlin 局部变量必须先声明后使用。
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    // 长按起拖时给一次轻触感（与切 Tab、切日期同一档）：把手就是「可拖动」的承诺，
    // 手指按住 0.4s 起拖这一下如果没有反馈，很容易以为没抓住又松手。
    val haptics = LocalHapticFeedback.current

    fun orderOf(sec: Int): List<Long> = when (sec) {
        1 -> overOrder
        2 -> noDueOrder
        3 -> loopOrder
        else -> openOrder
    }
    fun setOrder(sec: Int, list: List<Long>) {
        when (sec) {
            1 -> overOrder = list
            2 -> noDueOrder = list
            3 -> loopOrder = list
            else -> openOrder = list
        }
    }
    LaunchedEffect(dayOpen.map { it.id }) {
        // ★ 拖动进行中不要覆盖：库里推来新顺序（另一条的勾选/删除/撤回）时整体重置，
        //   正被拖的那行会在 openOrder 里找不到原位、dragDy 与显示错位，手指下那条「跳」回去。
        if (dragId == null || dragSection != 0) openOrder = dayOpen.map { it.id }
    }
    // 另两节同理：拖这一节的时候，别让库推来的新顺序把顺序表重置掉
    LaunchedEffect(overdueTodos.map { it.id }) {
        if (dragId == null || dragSection != 1) overOrder = overdueTodos.map { it.id }
    }
    LaunchedEffect(noDueTodos.map { it.id }) {
        if (dragId == null || dragSection != 2) noDueOrder = noDueTodos.map { it.id }
    }
    LaunchedEffect(dayLoops.map { it.id }) {
        if (dragId == null || dragSection != 3) loopOrder = dayLoops.map { it.id }
    }
    // 显示顺序就按这份顺序表来 —— 拖动中「显示」和「排序依据」是同一份数据，才不会对不上号
    // remember：不然每帧重组都重算（旧写法 openOrder.none{} 嵌在 filter 里还是 O(n²)）
    // ⚠️ 显示顺序铁则：**没在拖动时就按库顺序（dayOpen）原样渲染**。
    // 以前一律用 openOrder 去拼，而 openOrder 是 LaunchedEffect 在**组合之后**才刷新的：
    // 撤回/新增那一刻顺序表里还没这条 → 被当成「新加的」扔到第 0 位画一帧、下一帧再弹回原位。
    // 屏幕上就是「卡片先冒到顶上（甚至露在顶边外半张）→ 下面的行一帧跳一格」。
    // 库顺序本来就是权威（sortOrder 由拖动/新增写入），只有拖动中才需要用 openOrder 现拼。
    val draggingOpen = dragId != null && dragSection == 0
    val orderedOpen = remember(openOrder, dayOpen, draggingOpen) {
        if (!draggingOpen || openOrder.isEmpty()) dayOpen
        else mergeByOrder(dayOpen, openOrder, { it.id }, { it.sortOrder })
    }
    // 另两节用同一个「拖动中现拼」的口径
    fun orderedWith(base: List<Todo>, order: List<Long>): List<Todo> =
        if (order.isEmpty()) base else mergeByOrder(base, order, { it.id }, { it.sortOrder })
    val draggingLoop = dragId != null && dragSection == 3
    val draggingOverdue = dragId != null && dragSection == 1
    val draggingNoDue = dragId != null && dragSection == 2
    val orderedLoops = remember(dayLoops, loopOrder, draggingLoop) {
        if (!draggingLoop || loopOrder.isEmpty()) dayLoops else mergeByOrder(dayLoops, loopOrder, { it.id }, { it.sortOrder })
    }
    val orderedOverdue = remember(overdueTodos, overOrder, draggingOverdue) {
        if (!draggingOverdue || overOrder.isEmpty()) overdueTodos else orderedWith(overdueTodos, overOrder)
    }
    val orderedNoDue = remember(noDueTodos, noDueOrder, draggingNoDue) {
        if (!draggingNoDue || noDueOrder.isEmpty()) noDueTodos else orderedWith(noDueTodos, noDueOrder)
    }

    /**
     * 撤回一条「在视口上方」的条目时，把它滚回可见范围。
     *
     * LazyColumn 的滚动是锚定在**原来那个第一条**上的：在它上面插回一行，列表不会跟着滚，
     * 被插回来的这条就落在视口外，只在顶边露出一条边 —— 用户 2026-11-09 报的
     * 「撤回后第二条待办显示在第一条、原本的第一条只剩一条底线」就是这个。
     *
     * 写库是异步的（撤回回调 → Room → Flow），所以这里轮询等它真的进了列表再判断一次：
     * 只有在「它排在当前第一个可见条目之前」时才滚动，其余情况保持原样，不打扰用户。
     */
    // 同一帧对位：撤回到第一位时，LazyColumn 会把滚动锚在原来那条上，
    // 补回来的那一行就落在视口上方（只露半张 → 用户说的「显示问题」）。
    // 在 SideEffect 里发滚动请求 = 发生在「composition 之后、measure 之前」，
    // 插入和滚动会进同一帧的布局：新行完整出现，下面的行拿到一次真实位移，
    // 由 animateItem 的 placement 弹簧平滑让位（不是瞬跳、也不会被上边界裁掉）。
    SideEffect {
        val rid = restoredId
        // 注意：这里读到的是**上一帧**的布局信息（composition 在 measure 之前），
        // 所以判断「本来就在顶部」要用 == 0，而不是插入后锚点挪过去的 1。
        if (rid != null && state.firstVisibleItemIndex == 0 &&
            state.firstVisibleItemScrollOffset == 0
        ) {
            // 只有「补回到第一位」才需要对位：其它位置的插入不会动锚点。
            if (orderedOpen.firstOrNull()?.id == rid) state.requestScrollToItem(0, 0)
        }
    }

    /**
     * 某条在这张列表里的行下标；不在 = -1。
     *
     * 每个小节**标题**各占一行、折叠的小节高度 0 但下标照样计入（否则拖动重排会算错位）。
     * 撤回对位和改期定位共用这一份算法 —— 之前只有撤回有，改期那条就漏了。
     */
    fun rowIndexOf(id: Long): Int {
        var idx = 0
        var found = -1
        val emptyDay = orderedOpen.isEmpty() && orderedOverdue.isEmpty() &&
            orderedNoDue.isEmpty() && dayLoops.isEmpty() && dayDone.isEmpty()
        if (emptyDay) idx++
        orderedOpen.forEach { if (it.id == id) found = idx; idx++ }
        if (dayLoops.isNotEmpty()) {
            idx++
            orderedLoops.forEach { if (it.id == id) found = idx; idx++ }
        }
        if (noDueTodos.isNotEmpty()) {
            idx++
            orderedNoDue.forEach { if (it.id == id) found = idx; idx++ }
        }
        if (overdueTodos.isNotEmpty()) {
            idx++
            orderedOverdue.forEach { if (it.id == id) found = idx; idx++ }
        }
        if (dayDone.isNotEmpty()) {
            idx++
            dayDone.forEach { if (it.id == id) found = idx; idx++ }
        }
        return found
    }

    LaunchedEffect(restoredId) {
        val id = restoredId ?: return@LaunchedEffect
        repeat(30) {
            val found = rowIndexOf(id)
            if (found >= 0) {
                // 只做「瞬时对位」，绝不做带动画的滚动：
                //   · animateScrollToItem = 整块内容被滚上去、被撤回的那条从屏幕顶边滑进来
                //     （用户 2026-11-09：「直接从天上掉下来一个，而且这速度还很诡异」）；
                //   · requestScrollToItem 也行，但锚点重排有时比这条 effect 晚一帧，
                //     那就会先出现「被撤回那条只剩一条底线」的一帧，然后再归位。
                // scrollToItem 是瞬时的，而且这里连修几帧：只要发现它被挤到第一个可见条目之上，
                // 立刻把视口对回去。位置一旦对上就不会再动（okFrames 计到 3 就收工）。
                if (found < state.firstVisibleItemIndex && state.firstVisibleItemIndex <= 3) {
                    state.scrollToItem(found)
                }
            }
            delay(8)
        }
    }

    /**
     * 改期保存 → 跳到目标日 → **把改的那一条滚进视野**。
     *
     * 为什么必须显式滚：LazyColumn 的滚动锚点是「当前第一条可见」的那一行，数据晚到，
     * 被改期进来的这条（往往插在当天小节最前面）就落到视口上方 —— 用户看到的就是
     * 「这一天的界面里没有它」，随手横滑去别的天再回来（侧页会 scrollToItem(0)）才「刷新出来」。
     *
     * 写库是异步的（保存 → Room → Flow），所以轮询等它真的进了这一页再定位；
     * 用 scrollToItem 而不是 animateScrollToItem：这是一次「跳过去看它」，跟着动反而晕。
     */
    LaunchedEffect(focusId) {
        val id = focusId ?: return@LaunchedEffect
        repeat(60) {                       // ~0.5s：够 Room 往返 + 一次重组
            val found = rowIndexOf(id)
            if (found >= 0) {
                state.scrollToItem(found)
                onFocusHandled()
                return@LaunchedEffect
            }
            delay(8)
        }
        onFocusHandled()                   // 等不到（改到别的天了/被删了）就放手，别一直挂着
    }


    // 行的动效（就地展开，别包成 helper —— animateItem 是 LazyItemScope 的成员扩展）：
    //   · 被去掉的那条淡出 0.24s（勾选完成 / 删除 / 取消完成 都走这条）
    //   · 其余行平滑补位 = placementSpec（常开；切天不会误动，因为 item key 带了日期前缀）
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

    fun onDragStart(id: Long, sec: Int) {
        dragId = id; dragSection = sec; dragDy = 0f; edgeAccum = 0f
    }

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

        val list0 = orderOf(dragSection)
        var cur = list0.indexOf(id)
        if (cur < 0) return
        // 换位阈值 = 实测行高（含间距）的一半；循环写法应付「一帧里拖过好几格」
        // rowPx 兜底：万一没量到（回调没触发），退到 64dp 对应的像素，别用 64f 这种"像素值"
        val rowH = if (rowPx > 1f) rowPx else with(density) { 64.dp.toPx() }
        var list = list0
        while (dragDy > rowH / 2f && cur < list.size - 1) {
            list = list.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
            dragDy -= rowH; cur++
        }
        while (dragDy < -rowH / 2f && cur > 0) {
            list = list.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
            dragDy += rowH; cur--
        }
        if (list !== list0) setOrder(dragSection, list)
    }

    fun onDragFinish() {
        val id = dragId
        val sec = dragSection
        dragId = null; dragDy = 0f; edgeAccum = 0f
        if (id != null) {
            if (sec == 3) onReorderLoops(orderOf(3))
            else onReorder(orderOf(sec), when (sec) {
                1 -> BAND_OVERDUE
                2 -> BAND_NODUE
                else -> 0
            })
        }
    }


    // 标题行的位置动画必须在 item 作用域里写（Modifier.animateItem 是 LazyItemScope 的扩展），
    // 所以下面四处各自就地写；抽成变量会编译不过（Unresolved reference animateItem）。

    // 「现在看的是哪一节」＝列表最顶上那一条所属的分区。
    // 一开始用几何法（表头窗口 y vs 列表顶），但顶部大标题会随滚动折叠（28sp→17sp），
    // 列表顶边自己就往上移了 50 多 px，基准在动 → 判定抖、偶尔不触发（实测 listTop 495→442）。
    // 改用 LazyList 自己的 layoutInfo：**第一个可见条目是谁**、它滚掉了多少。
    // 条目 key 自带分区前缀（loop-/over-/nodue-/done-），不用再维护一张索引表。
    //
    // ★ 但 layoutInfo / firstVisibleItemIndex 是「每帧滚动都变」的 State，直接写在 composition
    //   里读（旧写法）＝ 每滚动一帧就重组整个 DayTodoList（四个分节、上百个 Compose 节点全量重建），
    //   这正是长列表滑动不跟手的主因（lint: FrequentlyChangedStateReadInComposition 抓到两处）。
    //   现在只把「顶上是哪一条的 key」做成 derivedStateOf：滚动过程中 key 不变 → 不重组，
    //   只有真的翻过一条时才重组（≈ 每条一次，而不是每帧一次）。
    fun dockOf(key: Any?): DockHead? = when {
        key == "loopHead" || (key is String && key.startsWith("loop-")) ->
            DockHead("循环待办", dayLoops.size)
        key == "overdueHead" || (key is String && key.startsWith("over-")) ->
            DockHead("过往未完成", overdueTodos.size)
        key == "noDueHead" || (key is String && key.startsWith("nodue-")) ->
            DockHead("无期限", null)
        key == "doneHead" || (key is String && key.startsWith("done-")) ->
            DockHead("已完成", dayDone.size)
        else -> null
    }
    val dockKey by remember(state) {
        derivedStateOf {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
            val away = first != null && (state.firstVisibleItemIndex > 0 || first.offset < 0)
            if (away) first?.key else null
        }
    }
    val dock = if (reportDock && dockKey != null) dockOf(dockKey) else null
    // 侧页（±1 天）也在跑这段，它们 reportDock = false，绝不能上报 null 去覆盖落位页的值
    LaunchedEffect(dock, reportDock) { if (reportDock) onDock(dock) }

    // 2026-11-10 深浅巡检 D4：列表视口上边界是硬切 —— 卡片滚到周条下方时被
    // 「拿尺子裁一刀」，字还能看见半截（截图：还图书馆的书 只剩上半）。给上沿一条 22dp 的
    // 渐隐（把内容本身擦淡，不是盖色），滚到顶时强度归零，首条卡片不受影响。
    val canScrollUp by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = state,
        // 和周条同处一个 Column：必须用 weight 吃剩余高度，用 fillMaxSize 会让列表底部伸出页面
        modifier = listModifier.verticalTopFade(if (canScrollUp) 1f else 0f, 22.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, LIST_BOTTOM),
        // 行距不再由列表级的 Arrangement 统一给，而是每条自己在 padding 里留 6dp ——
        // 见下面每处 `.padding(bottom = DAY_ROW_SPACING)` 的注释。
        verticalArrangement = Arrangement.Top
    ) {
        // 这一天什么都没有（没有当日条目、没有循环、没有逾期、没有无期限、也没有已完成）：
        // 原来只有一片留白，看着像没加载出来。账目页早有这块空状态，待办页一直没有（2026-11-08 补）。
        if (orderedOpen.isEmpty() && orderedOverdue.isEmpty() && orderedNoDue.isEmpty() &&
            dayLoops.isEmpty() && dayDone.isEmpty()
        ) {
            item(key = "emptyDay") {
                TodoEmptyDay(
                    isToday = isToday,
                    modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.78f)
                )
            }
        }
        itemsIndexed(orderedOpen, key = { _, t -> t.id }) { _, t ->
            val isDragging = dragId == t.id
            val isRestoredRow = restoredId != null && t.id == restoredId
            val leavingNow = t.id in exitingIds
            // 行本体只写一遍：正常分支 / 离场分支只差外层（是否包一层会收高度的壳）
            val body: @Composable (Modifier) -> Unit = { rowModifier ->
                TodoRow(
                    t = t,
                    onToggleTodo = toggleWithExit, onOpenDetail = onOpenDetail,
                    onLongPress = onLongPressTodo,
                    onMeasured = { h ->
                        if (dragId != t.id && h > 0) rowPx = h + with(density) { DAY_ROW_SPACING.toPx() }
                    },
                    dragModifier = Modifier.pointerInput(t.id, openOrder.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDragStart(t.id, 0)
                            },
                            onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                            onDragEnd = { onDragFinish() },
                            onDragCancel = { onDragFinish() }
                        )
                    },
                    modifier = rowModifier,
                    // 离场由外层 RowExitShell 的「收起」负责，行自己别再横着让开
                    animateLeave = !leavingNow
                )
            }
            if (leavingNow) {
                // 位置动画必须关掉：下面的行由这一行正在变矮的真实高度推着走，
                // 再叠一层 placement 就是两套时钟（本次修的正是这个）。
                RowExitShell(
                    id = t.id, spec = exitSpec, fadeSpec = exitFadeSpec, strikeSpec = exitStrikeSpec,
                        onSettled = ::settleExit,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    ).zIndex(1f)
                ) {
                    // 行距由 RowExitShell 里的 RevealRow 在裁剪区外面按同一条进度收
                    body(Modifier)
                }
            } else if (isRestoredRow) {
                // 撤回（撤销删除 / 取消完成）：与记账页同一套「长回来」，见 [RowRestoreShell]。
                // 位置动画必须关掉 —— 下面的行由这一行正在变高的真实高度推着走，
                // 再叠一层 placement 弹簧就是「上面的还没长完、下面已经沉到底」的双时钟。
                RowRestoreShell(
                    id = t.id, spec = exitSpecs.restore, fadeSpec = exitSpecs.restoreFade,
                    // 收尾交给 pulseRestore 的 800ms 兜底：这里提前清会让这一行在中途换外壳。
                    onFinished = {},
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                    )
                ) {
                    // 行距由 RowRestoreShell 里的 Spacer 按同一条进度补
                    body(Modifier)
                }
            } else {
                body(
                    Modifier
                    // 位置动画：让「让位」的那几条平滑滑到新位置。
                    // 被拖起的那条**必须关掉** —— 它的位置由手指的 translationY 直接控制，
                    // 再叠一层弹簧动画会两边打架、视觉上就是「画面突然闪一下」。
                    // 平时（非拖动）只在「刚勾掉/删掉一条」的窗口里开（holdPlacement）：
                    // 切天时「过往未完成/无期限/每日固定」的 key 跨天不变、只是顺序变了，
                    // 位置动画会把它们从旧位置动到新位置 → 「条目先窜到顶格、再回落」。
                    .animateItem(
                        fadeInSpec = null,
                        // 被撤回的那条不做位置动画：它在**插进列表的第一次布局**里，位置可能
                        // 还在视口上方（LazyColumn 把滚动锚在原来第一条上），
                        // 让 animateItem 去「动」它，就会演出「半张卡从顶边滑下来」——
                        // 用户 2026-11-09 报的就是这个。只留淡入，下面的行照常弹簧让位。
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    )
                        // 行距（6dp）从「列表级 Arrangement」搬进每条自己的 padding：
                        // 分节收起时的高度动画只收得到 item 自己的高度，列表级的 spacedBy
                        // 收不到 —— N 条 × 6dp 会留成一条空带，收到最后再补位，
                        // 看着就是「收完又往上跳一下」（用户 2026-11-10 截图反馈的残影）。
                        .padding(bottom = DAY_ROW_SPACING)
                    // 撤回补回来的那张画在最上层（不透明地盖住从它身下滑走的邻居），
                    // 免得邻居画在它上面 → 两张卡的字互相压。
                    .zIndex(if (isDragging) 2f else if (isRestoredRow) 1f else 0f)
                    // 拖起时放大 2% + 加投影（抬起来的反馈），其余卡片靠弹簧动画让位
                    .graphicsLayer {
                        translationY = if (isDragging) dragDy else 0f
                        val sc = if (isDragging) 1.022f else 1f
                        scaleX = sc; scaleY = sc
                    }
                )
            }
        }
        if (dayLoops.isNotEmpty()) {
            item(key = "loopHead") {
                SectionHead(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    )) {
                    // 和其它三个分区（过往未完成 / 无期限 / 已完成）用同一个 SectionLabel：
                    // 原来这里是一句纯 Text(13sp 灰)、没有红竖条也没有条数，
                    // 四个小节标题长得不一样，看着像漏了样式。
                    Box(Modifier.padding(top = 12.dp, bottom = 2.dp + DAY_ROW_SPACING)) { SectionLabel("循环待办", dayLoops.size) }
                }
            }
            // 顺序表优先（新加进来的补在最前，和另外三节一个口径）
            val loopsToDraw = orderedLoops
            items(loopsToDraw, key = { "loop-" + it.id }) { loop ->
                val isDraggingLoop = dragId == loop.id
                // 原来外面套的 SwipeRow 是 enabled=false 的死代码（行内左右滑永远划不出来：
                // 列表横向滑动已经用于切天），已删；勾选/删除都在卡片与设置页里。
                LoopTodoCard(
                    loop, startMs, onToggleLoop,
                    onOpenDetail = onOpenLoopDetail,
                    onMeasured = { h ->
                        if (dragId != loop.id && h > 0) rowPx = h + with(density) { DAY_ROW_SPACING.toPx() }
                    },
                    dragModifier = Modifier.pointerInput(loop.id, loopsToDraw.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDragStart(loop.id, 3)
                            },
                            onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                            onDragEnd = { onDragFinish() },
                            onDragCancel = { onDragFinish() }
                        )
                    },
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    ).padding(bottom = DAY_ROW_SPACING)
                )
            }
        }
        if (noDueTodos.isNotEmpty()) {
            item(key = "noDueHead") {
                SectionHead(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    )) {
                    // 2026-11-10 用户要求「无期限放到过往未完成之前」：它现在紧挨「循环待办」，
                    // 上面不再有可折叠的分节，所以固定 12dp，不再跟着 overdue 的进度收缩。
                    Box(Modifier.padding(top = 12.dp, bottom = 2.dp + DAY_ROW_SPACING)) {
                        SectionLabel(stringResource(R.string.todo_no_due))
                    }
                }
            }
            items(orderedNoDue, key = { "nodue-" + it.id }) { t ->
                val isDragging = dragId == t.id
                val isRestoredRow = restoredId != null && t.id == restoredId
                val leavingNow = t.id in exitingIds
                // 离场：用这一行自己的「收起」进度（高度→0），不再走 list 的 placement 补位
                val body: @Composable (Modifier) -> Unit = { rowModifier ->
                    TodoRow(
                        t = t,
                        onToggleTodo = toggleWithExit, onOpenDetail = onOpenDetail,
                        onLongPress = onLongPressTodo,
                        onMeasured = { h ->
                            if (dragId != t.id && h > 0) rowPx = h + with(density) { DAY_ROW_SPACING.toPx() }
                        },
                        dragModifier = Modifier.pointerInput(t.id, noDueOrder.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDragStart(t.id, 2)
                            },
                                onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                                onDragEnd = { onDragFinish() },
                                onDragCancel = { onDragFinish() }
                            )
                        },
                        modifier = rowModifier,
                        animateLeave = !leavingNow
                    )
                }
                if (leavingNow) {
                    RowExitShell(
                        id = t.id, spec = exitSpec, fadeSpec = exitFadeSpec, strikeSpec = exitStrikeSpec,
                        onSettled = ::settleExit,
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        ).zIndex(1f)
                    ) {
                        // 行距由 RowExitShell 里的 RevealRow 在裁剪区外面按同一条进度收
                        body(Modifier)
                    }
                } else if (isRestoredRow) {
                    // 与当天那一节同一套：从 0 高度长回来，邻居被真实高度一点点压下去。
                    RowRestoreShell(
                        id = t.id, spec = exitSpecs.restore, fadeSpec = exitSpecs.restoreFade,
                        onFinished = {},
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        )
                    ) {
                        body(Modifier)
                    }
                } else {
                    body(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            // 被撤回的那条不做位置动画：它在**插进列表的第一次布局**里，位置可能
                            // 还在视口上方（LazyColumn 把滚动锚在原来第一条上），
                            // 让 animateItem 去「动」它，就会演出「半张卡从顶边滑下来」——
                            // 用户 2026-11-09 报的就是这个。只留淡入，下面的行照常弹簧让位。
                            placementSpec = rowPlacement,
                            fadeOutSpec = tween(280, easing = Motion.PaperExit)
                        )
                            .zIndex(if (isDragging) 2f else if (isRestoredRow) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragDy else 0f
                                val sc = if (isDragging) 1.022f else 1f
                                scaleX = sc; scaleY = sc
                            }
                            .padding(bottom = DAY_ROW_SPACING)
                    )
                }
            }
        }
        if (overdueTodos.isNotEmpty()) {
            item(key = "overdueHead") {
                SectionHead(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    )) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { overdueExpanded = !overdueExpanded }
                        .padding(top = 12.dp, bottom = 2.dp + DAY_ROW_SPACING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("过往未完成", overdueTodos.size)
                    Spacer(Modifier.weight(1f))
                    SectionArrow(
                        expanded = overdueExpanded,
                        labelExpanded = "收起",
                        labelCollapsed = "展开",
                        // 箭头角度直接从主进度映射：不做第二个 150/200ms 的独立动画，
                        // 否则会出现「标题已经转完、卡片还在动」。
                        rotation = { 180f * (1f - overdueProgress.value) },
                        color = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else GrayStrong
                    )
                }
                }
            }
            // 单一进度模型：每条的高度 = 该条自然高度 × 局部进度，局部进度由分节主进度派生；
            // 同一个映射就是「展开自上而下、收起自下而上」，不需要两套 delay 公式。
            // 行距放在内容里（随高度一起被裁），折叠到 0 时不留残余空带；
            // 0 高度时 AnimatedReveal 不摆放子节点 → 没有可点击 / 可聚焦 / 可朗读的透明区域。
            itemsIndexed(
                orderedOverdue,
                key = { _, it -> "over-" + it.id }
            ) { i, t ->
                val isDragging = dragId == t.id
                val isRestoredRow = restoredId != null && t.id == restoredId
                val start = itemStartProgress(i, orderedOverdue.size, SECTION_STAGGER_SPAN)
                val leavingNow = t.id in exitingIds
                // 展开/收起用分节主进度（同一条进度就是「展开自上而下、收起自下而上」）
                val local = { itemLocalProgress(overdueProgress.value, start) }
                // ★ 2026-11-18（8.5.8）：这一节原来自己手搓了「高度→0 + 分节淡出」，
                //   所以勾掉一条时**没有划掉线、淡出用的是分节的窗口**（起步就被淡没），
                //   用户的原话是「今日的动画非常之好，可是过往未完成里怎么还是快的动画」。
                //   现在和当天 / 无期限两节走同一个 [RowExitShell]：划掉线 + 掉淡 + 收高度共用一套时钟。
                val body: @Composable (Modifier) -> Unit = { rowModifier ->
                    TodoRow(
                        t = t,
                        modifier = rowModifier,
                        onToggleTodo = toggleWithExit, onOpenDetail = onOpenDetail,
                        onLongPress = onLongPressTodo,
                        onMeasured = { h ->
                            if (dragId != t.id && h > 0) rowPx = h + with(density) { DAY_ROW_SPACING.toPx() }
                        },
                        // 离场由外层 RowExitShell 的「收起」负责，行自己别再横着让开
                        animateLeave = !leavingNow,
                        // 拖动：只在这一节内部换位（跨节没有语义）
                        dragModifier = Modifier.pointerInput(t.id, overOrder.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragStart(t.id, 1)
                                },
                                onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
                                onDragEnd = { onDragFinish() },
                                onDragCancel = { onDragFinish() }
                            )
                        }
                    )
                }
                if (leavingNow) {
                    // 与当天那一节完全相同：位置动画关掉（下面的行由这一行正在变矮的真实高度推着走），
                    // 行距由 RowExitShell 里的 RevealRow 在裁剪区外面按同一条进度收。
                    RowExitShell(
                        id = t.id, spec = exitSpec, fadeSpec = exitFadeSpec, strikeSpec = exitStrikeSpec,
                        onSettled = ::settleExit,
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        ).zIndex(1f)
                    ) {
                        body(Modifier)
                    }
                } else if (isRestoredRow) {
                    // 这一节原来只有「分节展开进度」那套外壳，撤回时会一帧占满高度；
                    // 改用与另外两节同一个 [RowRestoreShell]。
                    RowRestoreShell(
                        id = t.id, spec = exitSpecs.restore, fadeSpec = exitSpecs.restoreFade,
                        onFinished = {},
                        modifier = Modifier.animateItem(
                            fadeInSpec = null, placementSpec = null, fadeOutSpec = null
                        )
                    ) {
                        body(Modifier)
                    }
                } else {
                    RevealRow(
                        progress = local,
                        // 2026-11-18：这一节原来把行距塞在裁剪区里（`TodoRow(modifier = padding(bottom=…)`），
                        // 于是收高度最前面 6dp 全长在行距上、卡片本体看起来没动。现在改用 [RevealRow]：
                        // 裁剪区只包卡片本体，行距在同一 Column 里、按同一条进度缩。
                        // animateItem / zIndex / graphicsLayer 仍挂在**惰性列表的直接子节点**（Column）上。
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = null,
                                // 高度每帧都真实报给列表，下面的行是被真实高度推着走的；
                                // 再叠一层 placement 弹簧会让它们追不上正在变化的卡片（互相压住）。
                                placementSpec = null,
                                fadeOutSpec = tween(280, easing = Motion.PaperExit)
                            )
                            .zIndex(if (isDragging) 2f else if (isRestoredRow) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragDy else 0f
                                val sc = if (isDragging) 1.022f else 1f
                                scaleX = sc; scaleY = sc
                            }
                    ) {
                        body(Modifier)
                    }
                }
            }
        }
        // 已完成：沉到列表最下方，默认折叠成一行
        if (dayDone.isNotEmpty()) {
            item(key = "doneHead") {
                SectionHead(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = rowPlacement,
                        fadeOutSpec = tween(280, easing = Motion.PaperExit)
                    )) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { doneExpanded = !doneExpanded }
                        .padding(top = 12.dp, bottom = 2.dp + DAY_ROW_SPACING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("已完成", dayDone.size)
                    Spacer(Modifier.weight(1f))
                    // 「清空」：这一天的已完成一次删掉（用户 2026-11-03 点名要的）。
                    // 2026-11-04：折叠状态下不出现——收起来时这行只该是「已完成 N · 展开」，
                    // 摆一个删除入口在旁边既挤又容易被误触，展开后再给。
                    // 尾巴两颗（清空 + 展开）同高：原来「清空」带 6dp 上下内边距、「展开」没有，
                    // 展开那一刻这一行被撑高约 12dp，整行垂直居中就把「已完成」和两颗文字一起往下推
                    // —— 用户 2026-11-04：「展开的时候整个标题往下走好几个像素」。
                    // 现在两颗用同一套竖向内边距，行高在两种状态下完全一致。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = doneExpanded,
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(120))
                        ) {
                            Text(
                                "清空", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onClearDone(dayDone) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        SectionArrow(
                            expanded = doneExpanded,
                            labelExpanded = "收起",
                            labelCollapsed = "展开",
                            rotation = { 180f * (1f - doneProgress.value) },
                            color = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else GrayStrong,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                }
            }
            // 同「过往未完成」：不摘条目（0 高度不再贡献行距），避免收完一帧跳变
            // 与「过往未完成」同一套单一进度模型（各自独立的进度实例，不共用同一个 Animatable）
            itemsIndexed(
                dayDone,
                key = { _, it -> "done-" + it.id }
            ) { i, t ->
                val isRestoredRow = restoredId != null && t.id == restoredId
                val start = itemStartProgress(i, dayDone.size, SECTION_STAGGER_SPAN)
                val local = { itemLocalProgress(doneProgress.value, start) }
                RevealRow(
                    progress = local,
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = null,
                            // 被撤回的那条不做位置动画：插进列表的第一次布局里它可能还在视口上方，
                            // 让 animateItem 去「动」它会演出「半张卡从顶边滑下来」
                            placementSpec = null,
                            fadeOutSpec = tween(280, easing = Motion.PaperExit)
                        )
                        .zIndex(if (isRestoredRow) 1f else 0f)
                ) {
                    TodoRow(
                        t = t,
                        modifier = Modifier,
                        onToggleTodo = onToggleTodo, onOpenDetail = onOpenDetail,
                        onLongPress = onLongPressTodo,
                        onMeasured = {},
                        restore = onRestoreTodo
                    )
                }
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
    onMeasured: (Int) -> Unit,
    onLongPress: ((Todo) -> Unit)? = null,
    restore: ((Todo) -> Unit)? = null,
    modifier: Modifier = Modifier, dragModifier: Modifier = Modifier,
    /** 见 [TodoCard] 的同名参数：离场由父层的「原地收起」负责时传 false */
    animateLeave: Boolean = true
) {
    // 左右滑操作已整体删除（SwipeRow 在待办行上永远是 enabled=false，用户根本划不出来 ——
    // 列表横向滑动是「换一天」）。现在的入口：点条目 → 详情弹窗里改/删；已完成分区再点一次勾选 = 取消完成。
    TodoCard(
        t = t,
        onToggle = { done ->
            // 取消已完成 → 走「带撤回的还原」；其余（勾上、未完成区取消）直接写库
            if (!done && restore != null) restore(t) else onToggleTodo(t, done)
        },
        onOpenDetail = onOpenDetail,
        onLongPress = onLongPress,
        showHandle = restore == null,
        reorderHandleModifier = dragModifier,
        measureModifier = Modifier.onGloballyPositioned { onMeasured(it.size.height) },
        outerModifier = modifier,
        animateLeave = animateLeave
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LoopTodoCard(
    loop: LoopTodo, dayMs: Long, onToggle: (Long, Long, Boolean) -> Unit,
    onOpenDetail: (LoopTodo) -> Unit = {},
    onMeasured: (Int) -> Unit = {},
    modifier: Modifier = Modifier, dragModifier: Modifier = Modifier
) {
    val done = LoopSchedule.isDoneOn(loop, dayMs)
    // 循环待办勾上不离场，树是稳定的 → 这条时钟可以住在这里（离场行为什么不行，见 LocalRowStrike）
    val strike = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        // 曲线必须和离场行一致（PenSweep）：用 Glide 时前 1/4 时间就走完 68%，
        // 看上去像「闪现一条线」，与普通待办勾掉时那道慢慢划出来的线明显不是一个东西。
        strike.animateTo(if (done) 1f else 0f, tween(ROW_STRIKE_MS, easing = Motion.PenSweep))
    }
    // 循环待办勾上**不离场**，树是稳定的，这里可以直接用 animateColorAsState
    val loopInk by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = DONE_INK_MS, easing = Motion.ColorSettle),
        label = "loopTitleInk"
    )
    val shape = cardShape(CardRadius.ROW)
    Card(modifier = modifier.fillMaxWidth()
            .onGloballyPositioned { onMeasured(it.size.height) }
            // 2026-11-05 材质分层：内容卡片改实心纸面（玻璃只留给底栏/悬浮钮/弹层）
            .paperSurface(shape, dark = isDarkTheme, elevation = 4.dp, strokeInset = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        // 左边距改成和普通待办卡同一套（start 4 / end 8 + 把手 20 + 勾选框 36），
        // 标题因此落在同一个 x 上 —— 两节挨着看不会左右错位（原来这里是 horizontal = 14dp）。
        Row(Modifier.heightIn(min = LIST_ROW_MIN_HEIGHT)
                .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // 拖动把手：与普通待办卡同一个样式（20×40dp 触摸区、不画底色、透明度 0.34），
            // 长按起拖由 dragModifier 接手势。循环待办以前没有把手也没接手势（用户 2026-11-09 提的）。
            Box(
                dragModifier.size(width = 20.dp, height = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("≡", fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f))
            }
            // 勾选框之后整块可点：点条目 → 详情（2026-11-09 用户要求把「循环 · 每 N 天」
            // 从条目上挪进详情页，条目只留两行 —— 那就必须有个地方能点开看到它）。
            Row(
                Modifier.weight(1f).combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "查看详情",
                    onClick = { onOpenDetail(loop) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShixuCheck(
                    checked = done,
                    onToggle = { onToggle(loop.id, dayMs, !done) },
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = if (done) "标记未完成" else "标记完成"
                )
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(loop.title, style = RowType.rowTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = loopInk,
                        modifier = Modifier.strikeLine(
                            progress = { strike.value },
                            color = MaterialTheme.colorScheme.onSurface
                        ))
                    // 备注：设置页能填之后，这里也要能看到（原来 detail 字段全项目无人读取）
                    if (loop.detail.isNotBlank()) {
                        Text(loop.detail, style = RowType.rowSub(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rowSubTight())
                    }
                    // 「循环 · 每 N 天」这行已移进详情弹窗（用户 2026-11-09：条目界面只显示两行）。
                    // 条目上仍然留着右侧那枚闭环双箭头当「这是个循环」的记号。
                }
                // 循环图标：自己画的闭环双箭头（原来是 emoji 🔁，彩色且各机型字形不一）
                // 2026-11-10 深浅巡检 D3：默认 onSurfaceVariant 在深色卡上几乎看不见（描边只有 1.1dp），
                // 改用 onSurface 的 62%：深浅两边都够，又不会抢标题。
                LoopIcon(size = 16.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            }
        }
    }
}

/**
 * 一条待办卡片。
 *
 * [showHandle] 拖动把手只在「未完成」分区出现（已完成分区不给把手，不给拖动）。
 * [measureModifier] 让外层量到这张卡的真实高度，拖动换位阈值按它算（写死会导致越拖越偏）。
 */
/** 顶部月份行在滚动时接管显示的那一节（见 [TodoHeader] 的 dock 参数） */
data class DockHead(val title: String, val count: Int?)

/**
 * 小节标题行（循环待办 / 过往未完成 / 无期限 / 已完成）。
 *
 * 2026-11-05 三改（用户在六个方案里选了 ③「收进周条」）：
 * **不再吸顶**。原来无论怎么画（铺纸色、发丝线、磨砂），带子都属于「凭空多出来的一条表面」，
 * 而卡片从它底下穿过时总有一处要么糊、要么切得生硬。这一版干脆不留带子：
 * 标题跟着列表滚走，滚过去之后把这一节的名字搬到顶部「11月」那一行去
 * （见 [TodoHeader] 的 dock 参数），读者靠顶部那一行知道自己正看哪一节。
 * 于是列表上边界永远是干净的纸面，没有任何覆盖层。
 *
 * 本组件只做两件事：上报自己的窗口坐标（父层据此判断「这一节是不是已经滚出顶部」），
 * 以及把位置动画挂上（标题也是列表条目了，删一条时能和卡片一起平滑补位）。
 */
@Composable
private fun SectionHead(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxWidth()) { content() }
}

/**
 * 一条待办的「离场外壳」：勾上之后，行不立刻从列表里消失，而是原地从自然高度收到 0。
 *
 * 为什么要有它：`animateItem` 对**被删掉**的条目只能演淡出，占位在第一帧就没了 ——
 * 下面的行立刻用另一个时长的补位动画顶上来，两套时钟 ⇒ 用户看到的
 * 「上面的还没动，下面的就急着向上走了」。这里改成让「这一行自己变矮」，
 * 下面的行是被真实高度推着走的（`placementSpec = null`），全列表只有一个时钟。
 *
 * 2026-11-18 二次回归：只有「一条时钟」还不够 —— 行距当时塞在裁剪区**里面**，
 * 而 `EaseInOutCubic` 起步很慢，于是收高度最前面那 6dp 全长在行距上（≈55ms / 5 帧）：
 * 卡片本体一像素没动，下面的行已经往上走了 —— 观感依旧是「上下脱节」。
 * 现在行距挪到裁剪区**外面**、跟同一条进度一起收（见 [RevealRow]）。
 */
@Composable
internal fun RowExitShell(
    id: Long,
    spec: AnimationSpec<Float>,
    fadeSpec: AnimationSpec<Float>,
    strikeSpec: AnimationSpec<Float>,
    onSettled: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** 见 [RevealRow] 的同名参数：行距在行内（待办）传 6dp，在 LazyColumn 的 spacedBy 里（账目）传 0dp。 */
    spacing: Dp = DAY_ROW_SPACING,
    content: @Composable () -> Unit
) {
    val p = remember(id) { Animatable(1f) }
    val fade = remember(id) { Animatable(1f) }
    // 「划掉」那道线：0 = 没划，1 = 划满
    val strike = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        // 两条曲线并行起跑、时长不同：透明度先到 0，高度后收完。
        // 三行 MotionDebug.log 常驻（logging 关时是空调用），调手感时能直接从 logcat 读时长。
        MotionDebug.log("exit", "id=$id 开始")
        launch { strike.animateTo(1f, strikeSpec) }
        launch {
            fade.animateTo(0f, fadeSpec)
            MotionDebug.log("exit", "id=$id 已化掉")
        }
        p.animateTo(0f, spec)
        MotionDebug.log("exit", "id=$id 位置合上")
        onSettled(id)
    }
    // 离场行不再「墨→灰」：这一版是「一边划一边淡出」，130ms 的淡出里再叠一层变灰，
    // 文字会在淡出走到六成时就先没了，看着像空档（实测 99ms 已读作全空，而淡出要到 136ms）。
    // 恒提供 0f = 保持本来的墨色，让「看不见」这件事只由不透明度负责。
    CompositionLocalProvider(
        LocalRowDoneInk provides 0f,
        LocalRowStrike provides strike.value
    ) {
        RevealRow(progress = { p.value }, alpha = { fade.value }, modifier = modifier, spacing = spacing) { content() }
    }
}

/**
 * 撤销删除时的「长回来」外壳：与 [RowExitShell] 完全相反的一条时钟。
 *
 * 为什么不能直接把行插回列表：那样它一帧就占满高度，下面的行跟着一帧沉下去 ——
 * 用户的原话是「一撤回之后条目直接闪现然后下面的条目自动往下沉」。
 * 这里让**这一行自己从 0 高度长回自然高度**，下面的行被真实高度一点点压下去（一条时钟）。
 *
 * 另外两条是「别把背景带进来」（2026-11-18 第二轮，用户原话「抠图不干净，明明可以只扣
 * 白色的地方，非要带上背景」）：
 *  ① 纸面从盒子上沿齐平开始长、**不位移**（[RestoreRise] 里 placeRelative(0,0)）——
 *    盒子里面永远只有纸，没有一像素页面底色；
 *  ② 不透明度只花 200ms 变实（[ROW_RESTORE_FADE_MS]）—— 半透明的白纸混上米色底就是
 *    「脏」的来源；「慢慢出现」交给高度那条 440ms 的时钟。
 * 调用方把这一行的 `placementSpec` 设成 **null**（邻居由真实高度逐帧推着走，不能再叠弹簧），
 * 外壳自己也不许给内容加位移 —— 见 [RestoreRise]。
 */
@Composable
internal fun RowRestoreShell(
    id: Long,
    spec: AnimationSpec<Float>,
    fadeSpec: AnimationSpec<Float>,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = DAY_ROW_SPACING,
    content: @Composable () -> Unit
) {
    val p = remember(id) { Animatable(0f) }
    val fade = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        launch { fade.animateTo(1f, fadeSpec) }
        p.animateTo(1f, spec)
        onFinished(id)
    }
    Column(modifier) {
        RestoreRise(progress = { p.value }) {
            // 纸背由 [RestoreRise] 自己画（盒子多大就画多大、始终不透明）；这里淡入的只是
            // 「这一行自己」—— 描边、顶部受光、文字。两者同色，所以这一行在偏透明的那几帧
            // 也只是同色叠同色：既不混进页面底色，也不会出现被切掉一半的字
            // （字要等卡片长到 74% 高、字形完整装得下时才开始显形，见 [ROW_RESTORE_FADE_MS]）。
            Box(Modifier.revealAlpha { fade.value }) { content() }
        }
        Spacer(Modifier.height(spacing * p.value.coerceIn(0f, 1f)))
    }
}

/**
 * 「越长越高」的一行：布局高度 = 自然高 × p（所以下面的行是被真实高度推着走的）。
 *
 * ⚠️ 两条铁则（2026-11-18 用户报「抠图不干净，明明可以只扣白色的地方，非要带上背景」）：
 *  ① **纸面从盒子上沿齐平开始长，不许位移**。这里原本让子节点按 `(1-p)·自然高·0.35`
 *    往下缩（想做出「从底下升上来」的位移），结果盒子永远只有 p 高、卡片被推到盒子下半截，
 *    **盒子顶部那一条就露出页面米色** —— 卡片只剩一条窄白条飘在背景里。
 *    现在一律 `placeRelative(0, 0)`：盒子里面只有纸，一像素背景色都没有。
 *  ② **上沿与下沿都按卡片圆角收**（不是直角裁）。收高度必然要把卡片截断，
 *    直角截出来的是一条没有描边的白条（像被裁刀切了一刀）；按 [CardRadius.ROW] 收圆角后，
 *    读者看到的是「一张矮一点的卡」，不是「一张被裁坏的卡」。
 *    ⚠️ 上沿同样要收（2026-11-18 第三轮，用户原话「恢复时的条目上方出现直角」）：
 *    纸背是一块**直角矩形**，而卡片自己的纸面是圆角的 —— 两者形状不一致，
 *    圆角让出来的那两块三角形里露出的就是纸背的**方角**，看着像卡片顶上加了个直角。
 *    现在纸背与裁剪用**同一个**圆角形状（半径还按当前高度自适应，矮的时候才不会被拉成橄榄形），
 *    纸背的轮廓与卡片完全重合。
 *
 * 与 [AnimatedReveal] 的关系：这里等于 AnimatedReveal + 圆角切口 + 高度由进度直接给。
 * 不要合并 —— 离场那条要的是直角截面（它正在化掉，切口越不显眼越好）。
 */
@Composable
private fun RestoreRise(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    /** 卡片圆角（[CardRadius.ROW]）：下沿的切口也照这个圆角收，切口才像「卡片的底边」而不是被裁掉的一截。 */
    cornerRadius: Dp = CardRadius.ROW,
    content: @Composable () -> Unit
) {
    // 纸背色要在组合里读（isDarkTheme 是 @Composable），drawBehind 里只留一个捕获值。
    val paper = paperBaseColor(isDarkTheme)
    Layout(
        modifier = modifier
            // 纸背：盒子多大就铺多大，**始终不透明**。里面的那一行是从透明淡入的，
            // 没有这层的话，前几帧字和描边浮在页面米色上 —— 用户说的「抠图带上了背景」。
            // 形状必须与卡片一致（圆角），否则卡片圆角让出来的两个三角里露出纸背的**方角**，
            // 用户看到的就是「条目上方出现直角」。半径按当前高度自适应：盒子还只有十几像素高时
            // 若硬套 20dp 圆角，两块圆角会叠成一个橄榄形。
            .drawBehind {
                val r = currentCornerRadius(cornerRadius)
                drawRoundRect(color = paper, cornerRadius = CornerRadius(r, r))
            }
            // 裁切也用同一个形状：下沿的切口是「矮一点的卡片的底边」，
            // 上沿则保证卡片自己的描边/阴影不越出这个圆角轮廓。
            .drawWithContent {
                val r = currentCornerRadius(cornerRadius)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, size.width, size.height),
                            cornerRadius = CornerRadius(r, r)
                        )
                    )
                }
                clipPath(path) { this@drawWithContent.drawContent() }
            },
        content = content
    ) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}
        val child = measurables.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
        )
        val p = progress().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
        val full = child.height
        val visible = (full * p).roundToInt().coerceIn(0, full)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else child.width
        layout(width, visible) {
            if (visible > 0) child.placeRelative(0, 0)
        }
    }
}

/**
 * 当前这一格里能用的圆角半径：原本是卡片自己的 [CardRadius.ROW]，
 * 但不超过盒子高度（和宽度）的一半 —— 盒子还只有十几像素高时硬套 20dp 圆角，
 * 上下两块圆角会互相吃掉、整块变成橄榄形。夹过之后矮的时候就是一个「薄一点的圆角条」。
 */
private fun DrawScope.currentCornerRadius(radius: Dp): Float =
    radius.toPx().coerceAtMost(size.height / 2f).coerceAtMost(size.width / 2f)

/**
 * 「按进度收高度」的一行 = 卡片本体（被按进度裁）+ 行距（按同一进度缩）。
 *
 * 行距必须在 [AnimatedReveal] **外面**：
 *  - 放里面 ⇒ 裁剪从行距那一截开始吃，卡片本体延后 6dp 才动，起步那几帧卡片看起来是静止的；
 *  - 放外面且不随进度 ⇒ 收完后残余 6dp 空带、再「往上跳一下」。
 * 两个都踩过，所以这里只有一个写法：`高度 = 卡高 × p`，下面再垫 `行距 × p`。
 *
 * 透明度走绘制阶段（[revealAlpha]），不触发重组/重测。分节展开/收起也用这一套，
 * 高度公式与旧版 `(卡高 + 行距) × p` 完全等价，折叠到 0 不留残带。
 */
@Composable
internal fun RevealRow(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    /**
     * 收起时一并缩掉的行距。待办页是行内自带的 [DAY_ROW_SPACING]；
     * 记账页的行距来自 LazyColumn 的 `Arrangement.spacedBy`，此时传 0.dp（否则会垫双份）。
     */
    spacing: Dp = DAY_ROW_SPACING,
    /**
     * 整行不透明度。默认由进度派生（分节展开/收起用）；
     * 离场时传自己的时钟 —— 让「化掉」比「位置合上」先完成（见 [ROW_EXIT_FADE_MS]）。
     */
    alpha: (() -> Float)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier) {
        AnimatedReveal(
            progress = progress,
            modifier = Modifier.revealAlpha {
                alpha?.invoke()
                    ?: contentAlpha(progress(), SECTION_FADE_START, SECTION_FADE_RANGE)
            }
        ) { content() }
        Spacer(Modifier.height(spacing * progress().coerceIn(0f, 1f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoCard(
    t: Todo, onToggle: (Boolean) -> Unit, onOpenDetail: (Todo) -> Unit,
    onLongPress: ((Todo) -> Unit)? = null,
    reorderHandleModifier: Modifier = Modifier, showHandle: Boolean = false,
    measureModifier: Modifier = Modifier, outerModifier: Modifier = Modifier,
    /**
     * 勾上之后的「让开 14dp + 淡到 0.72」由谁来演。
     *
     * 循环待办勾上**不离开列表**（这一天仍然显示），所以那一下「确认」得留在这里；
     * 但当天 / 过往未完成 / 无期限三节的条目勾上就真的离场了，离场现在由父层
     * 的「原地收起」（高度→0）负责（见 [ExitingShell]）—— 这里必须关掉，
     * 否则同一行同时在「横着让开」和「竖着收起」，看着像被两只手往两个方向拽。
     */
    animateLeave: Boolean = true
) {
    val shape = cardShape(CardRadius.ROW)
    val dark = isDarkTheme
    val cardPress = remember { MutableInteractionSource() }
    // 勾上 = 墨色**渐渐**退成灰（见 DONE_INK_MS）。
    // animateColorAsState 首次组合直接取目标值，所以「已完成」小节里那些本来就是灰的条目
    // 不会在滚动进场时闪一下墨色；只有同一条卡片 done 翻转时才演过渡。
    val cs = MaterialTheme.colorScheme
    val inkAnim by animateColorAsState(
        targetValue = if (t.done) cs.onSurfaceVariant else cs.onSurface,
        animationSpec = tween(durationMillis = DONE_INK_MS, easing = Motion.ColorSettle),
        label = "todoTitleInk"
    )
    // 正在离场的那一行：灰跟着淡出时钟走（见 LocalRowDoneInk），否则树重建会跳帧
    val exitInk = LocalRowDoneInk.current
    val titleInk = if (exitInk == null) inkAnim else lerp(cs.onSurface, cs.onSurfaceVariant, exitInk)
    // 勾上完成 → **点击那一刻就写库**，动画只负责视觉。
    //
    // ★ 旧版把真正的 `onToggle(true)` 放在 `delay(220)` 之后（滑出动画结束时）。
    //   这 220ms 里只要这一行离开组合 —— 手指一甩让它滚出视口被 LazyColumn 回收、切 Tab、
    //   点 › 换天、按返回桌面 —— LaunchedEffect 会被取消，`onToggle` 永不执行：
    //   卡片滑出去了、库里还是未完成、撤回提示也不弹，回来它又出现，看着像「勾选失败」。
    //   现在先落库（数据正确），滑出只是这一帧的装饰；父层 holdPlacement 窗口里
    //   placementSpec 是开的，下面那些条目照样平滑地「顶上来」。
    // 只对「勾上」做：取消完成是把它捞回来，滑出去反而像又丢了。
    var leaving by remember(t.id) { mutableStateOf(false) }
    val slide by animateFloatAsState(
        targetValue = if (leaving && animateLeave) 1f else 0f,
        // 7.4.0（方案 P2「完成 ≠ 删除」）：原来勾上 = 整行飞出屏幕右侧，与「删除」同一个动作，
        // 读者分不清是"完成了"还是"没了"。现在只向右让开 16dp + 淡到 0.38 —— 行还在原地，
        // 勾选本身就是确认，不需要整条逃走（240ms 收束）。
        animationSpec = tween(durationMillis = 320, easing = Motion.PaperInOut),
        label = "todoRowLeave"
    )
    // 兜底：万一这条没被移走（同分区重排等），0.7 秒后复位，别让它永远隐身
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(360)
            leaving = false
        }
    }
    Card(modifier = outerModifier.fillMaxWidth()
            // 完成 = 轻轻「让开」14dp + 淡到 0.72（不再 fade 到 0）——循环待办勾上之后这一天仍然显示，
            // 若淡到接近透明再回弹，看起来就是「闪了一下又回来」。真离开的行由 animateItem 的
            // fadeOutSpec(280ms, PaperExit) 负责退场，这里只管「确认」这一下。
            .graphicsLayer { translationX = slide * 14.dp.toPx(); alpha = 1f - 0.28f * slide }
            .paperSurface(shape, dark = isDarkTheme, elevation = 4.dp, strokeInset = 2.dp)
            .pressScale(cardPress, pressed = Motion.PRESS_CARD)
            .then(measureModifier),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape) {
        // 左右内边距分开写：左 4dp（原来 8dp），省下的 4dp 正好抵掉勾选框加宽的那 8dp 的一半 ——
        // 标题的起始位置基本没动（4+20+36 = 60dp，原来 8+20+4+28 = 60dp，完全一致）。
        Row(Modifier.heightIn(min = LIST_ROW_MIN_HEIGHT)
                .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (showHandle) {
                // 拖动把手：单独吃掉长按手势，不打扰卡片点击。
                // 触摸区 24×40dp（够大好拖），**不画底色** —— 那层极淡灰底用户说很丑已去掉。
                // 宽度 26→24dp 是为了把勾选框整体左移、给标题腾地方（用户 2026-10-15 要求）。
                Box(
                    // 5.8.4：把手 24→20dp（用户「圆圈和最左边那个把手之间隔得有点多，再往左缩一点」）
                    reorderHandleModifier.size(width = 20.dp, height = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 把手只是「可拖动」的提示，不该跟标题抢注意力：透明度压到 0.34
                    // （用户 2026-11-05：可以再淡一点）。拖起时卡片放大 2%，反馈已经够。
                    Text("≡", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f))
                }
            }
            // 其余区域：点击打开详情；**长按 = 进整理页并预选这条**（用户 2026-11-03 要的批量整理入口）
            Row(
                Modifier.weight(1f).combinedClickable(
                    interactionSource = cardPress, indication = null,
                    // 读屏只念「双击激活」时，听不出这一下会发生什么；补上动词
                    // （长按的入口是整理页，也一并说清楚）
                    onClickLabel = "查看详情",
                    onLongClickLabel = if (onLongPress != null) "整理" else null,
                    onClick = { onOpenDetail(t) },
                    onLongClick = { onLongPress?.invoke(t) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pc = priorityColor(t.priority, dark)
                // 优先级红竖条已删（用户 2026-11-05：「红色的这个竖条可以直接去掉都没关系」）。
                // 优先级不再靠独立色条表达 —— 圆环勾选框的环色本来就跟着优先级走（tint = pc），
                // 一个符号足够；原来色条离圆圈只有 4dp，读起来像「圆圈左边多了个红点」。
                // 4dp 呼吸改由卡片左内边距（4dp）负责：勾选框热区要从 28dp 加宽到 36dp，
                // 横向得把这 8dp 从别处省出来，否则标题整体右移、每行都要重排。
                // 勾选框交互区 28dp，环（20dp）在其中居中。
                // 圆环勾选框（5.8.0）：环色跟优先级走，勾上整颗实心 + 白勾
                // 热区 28 → 36×44dp（2026-11-08 审计 C 项）：28dp 太窄，点偏 5~6dp 就落到卡片上，
                // 弹出的「详情」弹窗看着像「点勾选框点出了别的东西」。环本身仍是 20dp，视觉不变。
                ShixuCheck(
                    touch = 36.dp,
                    touchHeight = 44.dp,
                    checked = t.done,
                    // 勾上 = **先写库**再播离开动画（旧版反过来，动画期被回收就丢勾选）
                    onToggle = {
                        if (t.done) onToggle(false)
                        else if (!leaving) { leaving = true; onToggle(true) }
                    },
                    tint = pc,
                    dark = dark,
                    contentDescription = if (t.done) "标记未完成" else "标记完成"
                )
                Column(Modifier.weight(1f)) {
                    // 行高显式收紧：CJK 字体默认行高约 1.6em，标题+副标题两行各留一大截空白，
                    // 实测墨迹间隙 31px≈11.8dp（用户反馈"主标题和备注之间有点大"）。
                    // 21sp/17sp 落到约 7dp，行盒仍大于字形墨迹，不会切字。
                    // 划掉进度：CompositionLocal 只能在 composable 体里读，不能塞进 draw 的 lambda
                    val exitStrike = LocalRowStrike.current
                    Text(t.title, style = RowType.rowTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = titleInk,
                        // 勾掉时的「划掉」是画出来的：离场行跟着外壳那条 150ms 时钟从左划到右；
                        // 已完成分区里的行（不在离场）直接划满。原来用 textDecoration，
                        // 整段瞬间出现，没有「笔划过去」的过程（用户 2026-11-18）。
                        modifier = Modifier.strikeLine(
                            progress = { exitStrike ?: if (t.done) 1f else 0f },
                            color = cs.onSurface
                        ))
                    // 列表条目只显示备注与优先级，**绝不显示日期**。
                    // 用户明确要求过多次：条目本来就按天分区展示，日期是冗余信息；
                    // 未来日期的待办一旦带上日期，看起来就像"给条目加了时间"（被批评过）。
                    // 副标题只放备注 —— 「高优先」这类文字已去掉（用户 2026-11-05）：
                    // 优先级由勾选框环色表达，再写一遍等于同一件事说了两次。
                    val sub = t.detail
                    if (sub.isNotBlank()) {
                        // 宋体的 ascent+descent 本身就是 1.5em，行盒已经压到字形高、退无可退
                        // （Trim.Both 只能裁掉 lineHeight 多加的那点），所以再缩只能让副行「往上贴」1.5dp。
                        Text(sub, style = RowType.rowSub(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rowSubTight())
                    }
                }
            }
        }
    }
}

/**
 * 分组小节头（5.8.0）：主色短竖条 + 加粗标题 + 数量（数量是普通文字，不做胶囊）。
 *
 * 原来只是一行 13sp 灰字，比条目里的副标题（11.5sp）还轻，翻到列表中部时
 * 分不清「这一条属于哪个分组」。竖条让它在视觉上成为一条真正的分节。
 */
@Composable
private fun SectionLabel(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(2.5.dp).height(11.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        )
        // 5.8.3：12.5sp 半粗 → 14sp 加粗。和设置页分组标题（15sp + 竖条）配成一套，
        // 原来小节标题比条目标题（15sp Medium）还轻，一眼看不出这是「分组」。
        Text(
            title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 7.dp)
        )
        if (count != null) {
            Text(
                "$count", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp)
            )
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
    // 焦点状态：用来判断「这一下点击是不是把这个框点开的那一下」（见 FirstTapCursor.kt）
    var titleFocused by remember(key) { mutableStateOf(false) }
    var detailFocused by remember(key) { mutableStateOf(false) }
    val title = titleV.text
    val detail = detailV.text
    // 备注默认收起（用户很少填）：已有备注时自动展开，否则点「＋ 备注」才出现
    var showDetail by remember(key) { mutableStateOf(!(editing?.detail ?: "").isBlank()) }
    // 2026-11-07 用户：新建待办默认就选「低」；「无」这一档取消。
    // 老数据是 0 的也按「低」显示（DB 已经迁移过，这里只是兜底）。
    var priority by remember(key) {
        mutableIntStateOf(
            (editing?.priority ?: PRIORITY_LOW).let { if (it <= 0) PRIORITY_LOW else it }
        )
    }
    var dueMs by remember(key) {
        mutableLongStateOf(editing?.dueDate?.takeIf { it != 0L } ?: defaultDueMs)
    }
    /**
     * 「无期限」= dueDate 0。
     *
     * 原来这里只把 0 当成「没读到」、回落成 defaultDueMs（当前显示日），于是
     * ① 新建永远建不出无期限的条目；② 编辑一条无期限的待办、什么都不改直接保存，
     *    就被强行改成今天 —— 它从「无期限」分区里消失，而且再也改不回去。
     */
    var noDue by remember(key) { mutableStateOf(editing != null && editing.dueDate == 0L) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val dueCal = remember(dueMs) { DateUtils.calFrom(dueMs) }

    /**
     * 光标落点（用户 2026-11-02 定的口径）：
     *  - **第一次**点进输入框 → 光标到末尾（长备注一进来就看见尾巴）；
     *  - 之后的点击 → 点两个字之间就停在两个字之间。
     *
     * 两条路一起用：弹窗刚打开时先把项目框设到末尾；之后每次「把某个框点开的那一下」
     * 交给 firstTapCursorToEnd（FirstTapCursor.kt）在抬手时补一次。
     */
    fun toEnd(v: TextFieldValue) = v.copy(selection = TextRange(v.text.length))

    LaunchedEffect(key) {
        withFrameNanos { }
        withFrameNanos { }
        titleV = toEnd(titleV)
    }

    ShixuDialog(
        title = if (editing == null) "新增待办" else "修改待办",
        onDismissRequest = onDismiss,
        content = {
            ShixuField(
                value = titleV, onValueChange = { titleV = it; error = null }, label = "项目",
                singleLine = true,
                isError = error != null,
                innerModifier = Modifier
                    .onFocusChanged { titleFocused = it.isFocused }
                    .firstTapCursorToEnd({ titleFocused }, { titleV }) { titleV = it })
            // 空标题时给一句红字（原来只 if (isNotBlank()) 静默吞掉，用户以为保存键坏了）
            error?.let {
                Text(it, fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error)
            }
            if (showDetail) {
                ShixuField(
                    value = detailV, onValueChange = { detailV = it },
                    label = "备注（可选）", singleLine = true,
                    innerModifier = Modifier
                        .onFocusChanged { detailFocused = it.isFocused }
                        .firstTapCursorToEnd({ detailFocused }, { detailV }) { detailV = it })
                // 收起 / ＋备注 是同一个开关的两态，必须同一边同一位置：
                // 原来「收起」右对齐、「＋备注」用 TextButton（自带最小宽度 + 内边距，
                // 左边比「优先级」「日期」这些标签多缩进约 11dp）。两处一起收成左对齐。
                Text(
                    "收起 ▴", fontSize = DialogText.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 32.dp)
                        .clickable { showDetail = false }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            } else {
                Text(
                    "＋ 备注", fontSize = DialogText.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 32.dp)
                        .clickable { showDetail = true }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
            Text("优先级", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 「无」档已取消（用户 2026-11-07）。三档都带色点，和列表左侧色条一一对应。
                listOf(
                    PRIORITY_LOW to "低", PRIORITY_MID to "中", PRIORITY_HIGH to "高"
                ).forEach { (p, label) ->
                    ShixuChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        compact = true,
                        label = { Text(label, fontSize = 13.sp) },
                        leadingIcon = {
                            Box(Modifier.size(9.dp).background(priorityColor(p, isDarkTheme), CircleShape))
                        }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 38.dp)) {
                Text("日期", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                if (noDue) {
                    Text("不设日期", fontSize = DialogText.Value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                    StepperArrow(ChevronDir.Left) { dueMs = DateUtils.addDays(dueCal, -1).timeInMillis }
                    Text(DateUtils.formatMonthDayCn(dueCal), fontSize = DialogText.Value,
                        modifier = Modifier.padding(horizontal = 1.dp))
                    StepperArrow(ChevronDir.Right) { dueMs = DateUtils.addDays(dueCal, 1).timeInMillis }
                }
                // 「无期限」开关：不选日期就留在「无期限」分区（新建、编辑都用它）
                ShixuChip(
                    selected = noDue,
                    onClick = {
                        if (noDue) {
                            noDue = false
                            // ★ 从「无期限」改回有日期时，dueMs 可能还是 0（编辑一条
                            //   dueDate=0 的旧条目、调用处又没给 defaultDueMs）——
                            //   不补就会显示「1月1日」（1970）并把 dueDate 存成 1970 年的时间戳，
                            //   这条待办随即从「今天」消失、只能去「过往未完成」里找回来。
                            //   （2026-11-08 审计发现的真 bug）
                            if (dueMs == 0L) dueMs = DateUtils.startOfDayMs(System.currentTimeMillis())
                        } else noDue = true
                    },
                    compact = true,
                    label = { Text(stringResource(R.string.todo_no_due), fontSize = 13.sp) }
                )
            }
        },
        confirmButton = {
            ShixuDialogButton(stringResource(R.string.action_save)) {
                if (title.isBlank()) error = "请输入待办内容"
                else {
                    onSave(title.trim(), detail.trim(), if (noDue) 0L else dueMs, priority)
                    onDismiss()
                }
            }
        },
        dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) }
    )
}

/**
 * 「这一天没有待办」的空状态。
 *
 * 图形语言跟账目页的 [EmptyLedger] 是同一套：48 单位网格、3 单位笔宽、
 * 苍色墨线 0.68 / 次级 0.54，全图只在末尾点一枚朱红圆点当印。
 * 这里画的是「两张待勾的空圈 + 两条越来越短的书目」，红点落在第二条末尾 ——
 * 说明这块地方本来该有一串待勾的事，只是今天正好空着。
 */
@Composable
private fun TodoEmptyDay(isToday: Boolean, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(62.dp)) {
                val u = size.width / 48f
                val ink = muted.copy(alpha = 0.68f)
                val sw = 3f * u
                // ① 两枚空圈：待勾的事
                drawCircle(ink, radius = 4.2f * u, center = Offset(12f * u, 15f * u),
                    style = Stroke(width = sw))
                drawCircle(ink, radius = 4.2f * u, center = Offset(12f * u, 29f * u),
                    style = Stroke(width = sw))
                // ② 两行条目：第二行更短，和账目页同一支笔、稍弱一级
                val rows = ink.copy(alpha = 0.54f)
                drawLine(rows, Offset(21f * u, 15f * u), Offset(41f * u, 15f * u), sw, StrokeCap.Round)
                drawLine(rows, Offset(21f * u, 29f * u), Offset(31f * u, 29f * u), sw, StrokeCap.Round)
                // ③ 朱红小圆点：落在第二行末尾右侧
                drawCircle(
                    color = accent.copy(alpha = 0.94f),
                    radius = 3.2f * u,
                    center = Offset(35f * u, 29f * u)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (isToday) "今天还没有待办" else "这天没有待办",
                fontSize = 13.sp, color = muted, textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 分节标题右侧的「收起 ▴ / 展开 ▾」。
 *
 * 文案跟**目标状态**走（点下去立刻变成「收起 / 展开」，不等动画结束）；
 * 箭头角度跟**主进度**走（`rotation` 在绘制阶段读，不另起一个动画时钟）——
 * 这样不会出现「箭头已经转完、卡片还在动」的割裂感。
 */
@Composable
private fun SectionArrow(
    expanded: Boolean,
    labelExpanded: String,
    labelCollapsed: String,
    rotation: () -> Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (expanded) labelExpanded else labelCollapsed, fontSize = 12.sp, color = color)
        Text(
            "▴",
            fontSize = 12.sp,
            color = color,
            modifier = Modifier
                .padding(start = 3.dp)
                .graphicsLayer { rotationZ = rotation() }
        )
    }
}
