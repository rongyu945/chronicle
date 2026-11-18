package com.rongyu.shixuji.ui

import androidx.compose.ui.semantics.Role
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.rongyu.shixuji.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.BuildConfig
import com.rongyu.shixuji.DEFAULT_TAB_ORDER
import com.rongyu.shixuji.FONT_SCALES
import com.rongyu.shixuji.parseTabOrder
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.AutoBackup
import com.rongyu.shixuji.data.BackupWriter
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AmountExpr
import com.rongyu.shixuji.data.AppDatabase
import com.rongyu.shixuji.data.AppPref
import com.rongyu.shixuji.data.BackupCodec
import com.rongyu.shixuji.data.LoopSchedule
import com.rongyu.shixuji.data.LoopTodo
import com.rongyu.shixuji.data.QuickEntry
import com.rongyu.shixuji.data.TagPalette
import com.rongyu.shixuji.data.Todo
import com.rongyu.shixuji.theme.*
import com.rongyu.shixuji.ui.greeting.greetingSummary
import com.rongyu.shixuji.ui.greeting.compactEdge
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.ui.supportsLiquidGlass
import com.rongyu.shixuji.ui.liquidGlass
import com.rongyu.shixuji.ui.GlassRim
import androidx.compose.ui.draw.shadow
import com.rongyu.shixuji.ui.glassSurface

/**
 * 标签可选色（24 色，按色系分成 6 行）：红系 / 橙黄系 / 绿系 / 青蓝系 / 紫粉系 / 中性系。
 *
 * 用户 2026-10-14：「原来 12 色太单调，颜色再丰富一点、选择更多一点」→ 12 → 24，
 * 每行 4 色、行首标色系，一眼能挑到想要的那一档。
 * 注意：颜色是**按值存进标签记录**的（AccTag.color），所以换色板不会改动已有标签的颜色；
 * 旧 12 色全部保留在新色板里（老标签进编辑器仍然能高亮出自己那一格）。
 */
// 色板本体在 data/TagPalette.kt：那里是「CIELAB 最远点散布」选的 24 色（两两色差最小 26），
// 迁移（AppContainer.MIGRATION_6_7）也引用同一份，改色板只需改一个地方。
private val TAG_COLOR_GROUPS: List<Pair<String, List<Long>>> = TagPalette.ROWS
private val TAG_COLORS: List<Long> = TagPalette.ALL

/** 设置（按《时序集-设置详情页细节.md》：待办 / 账单 / 界面显示 / 数据 / 关于作者） */
@Composable
fun SettingsScreen(
    isSongti: Boolean,
    isDark: Boolean?,
    tabOrder: List<String>,
    fontScale: Float,
    onToggleSongti: (Boolean) -> Unit,
    onApplyOrder: (List<String>) -> Unit,
    onToggleDark: (Boolean?) -> Unit,
    onSetFontScale: (Float) -> Unit,
    /** 撤回球停留时长（毫秒） */
    undoMs: Int = UNDO_LIFE_DEFAULT_MS,
    onSetUndoMs: (Int) -> Unit = {},
    /** 欢迎页三个问候时段的分界（7.2.5 新增） */
    greetingTimes: GreetingTimes = GreetingTimes.DEFAULT,
    onSetGreetingTimes: (GreetingTimes) -> Unit = {},
    /** 请求预览某个时段的欢迎页；由 MainScreen 顶层铺满整屏画（理由见那里的注释） */
    onPreviewGreeting: (Int) -> Unit = {}
) {
    // 二级页导航：null=主菜单
    var page by remember { mutableStateOf<String?>(null) }

    /**
     * 设置页里的删除也走「撤回球」（2026-11-08）：循环待办与常用记账的「删」原来是一个字的热区、
     * 点了立刻删库，既没确认也撤不回 —— 而它们旁边的勾选框热区比它大，手指一偏就删掉了。
     */
    val undo = rememberUndoController()
    // 7.4.1：「版本」行长按的 toast 用（原来这一层没有取 Context）。
    val ctx = LocalContext.current

    // 返回键：在二级页时先回主菜单，而非退出App
    BackHandler(enabled = page != null) { page = null }

    // 标题折叠：和待办/账目共用一套（ui/TitleFold.kt），往上滑 28sp → 17sp，松手吸附
    val scrollState = rememberScrollState()
    val fold = rememberTitleFold(scrollState)

    // 每次进入本页都回到最上面（和待办/账目一致）；
    // 不重置就会保留上次滚到的位置，切过来看到的是设置列表末尾。
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }

    Box(Modifier.fillMaxSize()) {
    // 2026-11-07：原来这里自己铺一层不透明 background，把顶部桃花枝整片盖掉了。
    // 去掉后露出的是 MainScreen 那层纸底（ColorBlobsBackground），全 App 一致。
    Column(Modifier.fillMaxSize()) {
        // 标题条：往上滑字号缩小、边距收紧（背景透明）
        Text("设置",
            fontSize = titleSizeFor(fold).sp, fontWeight = FontWeight.Bold,
            letterSpacing = titleTrackingFor(fold).sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 20.dp,
                top = lerp(20.dp, 8.dp, fold),
                bottom = lerp(8.dp, 6.dp, fold)
            ))

        // 主菜单 ↔ 二级页原来是 `if (page != null) { … return@Column }` —— 纯硬切。
        //
        // 【2026-11-11 第二版】第一版用 AnimatedContent 让两页**同时对向位移**（新页从右进、
        // 旧页往左让 1/4）。实测抓到的中间帧是灾难：这个 App 的页面**本身是透明的**
        // （底色和四团柔光色斑由 MainScreen 统一铺在最底层），两页同时在场就等于两张透明纸
        // 叠在一起 —— 问候时段的「12:00 至 13:00」直接压在「循环待办 / 导出未完成待办」上。
        // 而且 AnimatedContent 永远把 target 画在上面，返回时（target=主列表）就变成
        // 玻璃卡压着二级页，字从半透明的卡里透出来，看着就是「玻璃崩了」。
        //
        // 现在自己管这一层，只让**一页动**：
        //  · 主列表原地不动，永远在场（推入时被二级页的纸底盖住，返回时被逐渐露出来）；
        //  · 二级页是唯一的动层，从右侧整幅推入 / 向右整幅滑出，且自带一层不透明纸底
        //    （[PaperPlate]），所以任何一帧都不会透出下面的内容。
        //
        val subAnim = remember { Animatable(0f) }
        // 返回时要一直画到滑出屏幕才能拆，所以单独留一份「当前还在显示的页」
        var subPage by remember { mutableStateOf<String?>(null) }
        // 7.4.1（方案 P0-5）：整幅推入是最容易引起眩晕的一类动效（尤其是横向位移），
        // 减弱动效时直接换页；时长同时过一遍 MotionDebug.ms，方便慢放对比。
        val reducedMotion = LocalReducedMotion.current
        LaunchedEffect(page) {
            val p = page
            val spec = tween<Float>(MotionDebug.ms(SUB_PUSH_MS), easing = EaseInOutCubic)
            if (p != null) {
                subAnim.snapTo(0f)
                subPage = p
                if (reducedMotion) subAnim.snapTo(1f) else subAnim.animateTo(1f, spec)
                MotionDebug.log("page", "→ $p")
            } else {
                if (reducedMotion) subAnim.snapTo(0f) else subAnim.animateTo(0f, spec)
                subPage = null
            }
        }

        BoxWithConstraints(Modifier.weight(1f)) {
        val areaW = with(LocalDensity.current) { maxWidth.toPx() }

        // 主列表永远在场：推入时它被二级页的纸底盖住，返回时它一直就在原位。
        // （不靠动画值决定组合与否 —— 那样每帧都要重组整张列表。）
        Column(Modifier.fillMaxSize()) {


        // 底部留白要够：底栏是浮起来的胶囊，留 16dp 会让最后一张卡（关于）被挡住
        // 顶部渐隐必须写在 verticalScroll **之前**（= 外层）：写在里面会跟着内容一起滚，
        // 变成一条挂在卡片上的固定横带。放在外层才是「滚动视口的遮罩」。
        Column(
            Modifier.fillMaxSize().verticalTopFade(scrollState)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LIST_BOTTOM_PLAIN),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {

            SectionCard("待办") {
                NavRow("循环待办", "按间隔提醒", divider = false) { page = "loop" }
                NavRow("导出未完成待办", "复制文本") { page = "export" }
            }
            SectionCard("账单") {
                NavRow("标签管理", "自由添加/修改", divider = false) { page = "tags" }
                NavRow("月度预算", "超支会提示") { page = "budget" }
                NavRow("常用记账", "一键记一笔") { page = "quick" }
                NavRow("账单总览", "按月查看") { page = "bills" }
                NavRow("支出明细", "标签占比饼图") { page = "pie" }
            }
            SectionCard("界面显示设置") {
                // 用户 2026-11-05：「显示界面设置的那些选项的风格去和其他的条目风格统一，
                // 选择放到二级菜单去」。原来「外观模式 / 字号」是并排 chip 直接铺在卡片里，
                // 和上下那些「标题 + 当前值 + 箭头」的行完全两个族。
                // 现在统一成 NavRow（右侧显示当前选择），点进去在二级页里单挑。
                // ⚠️ rowHairline 画的是「这一行的顶线」：所以一组里第一行必须 false（否则标题下多一道），
                //    最后一行必须是 true（否则和上一行粘在一起）。用户 2026-11-05 抓到的就是这个。
                SwitchRow("使用宋体", isSongti, onToggleSongti, divider = false)
                NavRow("外观模式", darkModeLabel(isDark)) { page = "theme" }
                NavRow("字号", fontScaleLabel(fontScale)) { page = "font" }
                NavRow("底栏排序", tabOrder.joinToString(" · ")) { page = "order" }
                // 撤回停留时间也归在这一组（用户 2026-11-05：这个功能合并进界面显示设置里面）
                NavRow("撤回停留时间", "${undoMs / 1000} 秒") { page = "undo" }
                // 问候时段也归这一组（用户 2026-11-11：「欢迎页不要单独写一个条目，直接放到界面布局里去」）：
                // 原来它独占一张「欢迎页」卡，只有一行，把设置页切成了六段，其中一段只为一行字。
                // 右侧仍三段同显（0–7 · 11:55–13 · 21–24）：只写午安一段会看不出早安晚安。
                NavRow("问候时段", greetingSummary(greetingTimes)) { page = "greet" }
            }
            SectionCard("数据") {
                NavRow("备份 / 恢复", "导出或导入全部数据", divider = false) { page = "backup" }
                NavRow("自动备份", "改动条目后自动更新") { page = "autobackup" }
            }
            SectionCard("关于作者") {
                NavRow("作者", "戎昱", divider = false, showChevron = false) {}
                // 7.4.1（方案 P0-6）：**长按「版本」** = 动效调试总开关，长按一次 2×、再一次 4×、
                // 再一次回 1×（同时开/关事件日志与左上角覆盖层）。不做成可见设置项：它是调参工具，
                // 日常使用的人点进来只会多一个看不懂的选项。
                NavRow(
                    label = "版本",
                    value = BuildConfig.VERSION_NAME + if (MotionDebug.active()) " · ${MotionDebug.slow}×" else "",
                    showChevron = false,
                    // ⚠️ 尾随 lambda 会绑到**最后一个**参数（现在是 onLongClick），
                    // 所以两个回调都要显式命名，不能写 `NavRow(...) {}`。
                    onLongClick = {
                        val f = MotionDebug.cycleSlow()
                        toast(
                            ctx,
                            if (f == 1f) "动效调试：关闭" else "动效慢放 ${f}×（日志与覆盖层已开）"
                        )
                    },
                    onClick = {}
                )
            }
        }
        }   // ← 主列表 Column 到此为止：二级页必须是 BoxWithConstraints 的兄弟，
            //    留在主列表 Column 里会拿到 0 高度（fillMaxSize 的滚动列已经吃满），
            //    二级页整页量成 0 高 = 什么都不画，而纸底的 drawImage 照样铺满全屏 = 「整页空白」。

        // 二级页：唯一的动层。横向位移走 graphicsLayer，只失效这一层，不触发重组。
        // zIndex 保证它盖在主列表之上（任意时刻都只有它在动，所以不会有互透）。
        subPage?.let { sp ->
            Box(
                Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                    translationX = areaW * (1f - subAnim.value)
                }
            ) {
                PaperPlate()
                // SettingSubPage 是 ColumnScope 的扩展，Box 里拿不到外层 Column 的隐式 receiver，
                // 所以套一层满屏 Column 给它（面积与原来的内容区等大）
                Column(Modifier.fillMaxSize()) {
                    SettingSubPage(
                        page = sp, onBack = { page = null },
                        isSongti = isSongti, onToggleSongti = onToggleSongti,
                        tabOrder = tabOrder, onApplyOrder = onApplyOrder,
                        isDark = isDark, onToggleDark = onToggleDark,
                        fontScale = fontScale, onSetFontScale = onSetFontScale,
                        undoMs = undoMs, onSetUndoMs = onSetUndoMs,
                        greetingTimes = greetingTimes, onSetGreetingTimes = onSetGreetingTimes,
                        onPreviewGreeting = onPreviewGreeting,
                        undo = undo
                    )
                }
            }
        }
    }
    }
    // 底部纸色压层：与待办/账目同一份（ui/BottomFade.kt）。
    // 设置页没有悬浮钮，直接压在内容上即可。
    BottomPaperFade()
    // 撤回球：浮在底栏上方（底栏是 92dp 的玻璃胶囊，球要避开它）
    UndoHost(
        undo,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
        lifeMs = undoMs
    )
    }
}

/** 二级页推入 / 推出的时长（只一页在动，不需要视差补偿）。 */
private const val SUB_PUSH_MS = 360

/**
 * 二级页自带的「纸底」—— 一块自己知道该画什么背景的不透明板。
 *
 * 这个 App 的页面本身是**透明**的：宣纸底 + 四团柔光色斑由 MainScreen 统一铺在整个界面
 * 最下面，各页的内容只是浮在它上面的字和卡片。好处是三层 Tab 之间切换没有底色接缝，
 * 坏处是**任意两页同时在场都会互相透**：二级页推入的那半秒，问候时段的「12:00 至 13:00」
 * 会直接压在「循环待办 / 导出未完成待办 / 标签管理」上，文字叠文字。
 *
 * 所以二级页必须自带一层不透明的纸。做法是把背景本身的那份绘制
 * （`drawColorBlobs`，和平铺在底下的完全是同一个函数）按「整屏尺寸 + 自己在纸底里的位置」
 * 平移着重画一遍 —— 盖上去的纸和身后的纸在静止时逐像素重合，所以平时看不出这里有两层。
 *
 * 底下先铺一层按**自身尺寸**铺满的纯色纸：位移动画期间 `onGloballyPositioned` 的偏移会滞后于
 * 动画，补上去的色斑可能整体偏到一边，右边缘盖不满时由这层纯色兜住（不能画更大的矩形 ——
 * `drawBehind` 不裁剪，铺多大就会盖掉屏幕外的一切）。
 */

@Composable
private fun PaperPlate() {
    val paper = LocalPaperBackdrop.current
    var shift by remember { mutableStateOf(Offset.Zero) }
    val dark = isDarkTheme
    val base = if (dark) Color(0xFF121114) else Color(0xFFF7F1E6)
    // 纸底那一层（宣纸 + 四团色斑）只转一次位图，之后每帧只是 drawImage。
    // ⚠️ 不能直接 `drawLayer(st.layer)`：那层正在被 `recordBackdrop` 每帧录制，
    //    在录制中再把它画出来会让 HWUI 拿到自引用的 RenderNode，画面从此不再刷新
    //    （实测：屏幕定格在某帧、但布局和语义树照常更新，看起来就是「整页空白」）。
    var shot by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(paper, dark) {
        val st = paper ?: return@LaunchedEffect
        withFrameNanos { }   // 等纸底这一帧录完，否则可能抓到一张空图
        shot = runCatching { st.layer.toImageBitmap() }.getOrNull()
    }
    Box(
        Modifier
            .fillMaxSize()
            // 必须裁到自己这一块：纸底图是整屏大小、还要往上平移，不裁就会把整屏都盖成纸
            .clipToBounds()
            .onGloballyPositioned { c ->
                if (!c.isAttached) return@onGloballyPositioned
                val bg = paper?.coordinates ?: return@onGloballyPositioned
                shift = offsetOf(bg, c)
            }
            .drawBehind {
                drawRect(base)
                val bmp = shot ?: return@drawBehind
                translate(-shift.x, -shift.y) { drawImage(bmp) }
            }
    )
}

@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    // 2026-11-06：分组卡改成**液态玻璃**。
    // 原来是不透明的 `colorScheme.surface`（纸白 #FFFDFA），可背景本身就是宣纸米色 ——
    // 两者只差几个色阶，卡片的边界完全立不住，一屏五块白饼摊在纸上，
    // 用户的原话是「浅色模式下的可读性着实不高」。
    //
    // 2026-11-11：玻璃外壳抽到 [GlassCard]，因为「问候时段」页也是三张卡，
    // 两页必须同一套（用户的评价是「整体语言和液态玻璃、胶囊不太符合」）。
    GlassCard {
        Column(Modifier.fillMaxWidth()) {
            // 分组标题（5.8.3，用户挑的方案二）：15sp 加粗 + 左侧 2.5dp 主色竖条。
            // 旧版是 12.5sp 半粗 —— 比它下面 15sp 的行标题还小，主次倒挂，看着不像标题。
            // 竖条与待办页小节标题同一套语言，全 App 只有一个「分组」手势。
            // title 传 null = 不画分组头：二级页里卡片标题和页面标题会撞车（「外观模式」写两遍）。
            if (title != null) Row(
                Modifier.padding(start = 16.dp, top = 13.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(2.5.dp).height(12.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                )
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 7.dp))
            }
            // 没有分组头时补一点上内距，否则第一行紧贴卡片圆角
            Column(
                modifier = Modifier.fillMaxWidth()
                    .then(if (title == null) Modifier.padding(top = 4.dp) else Modifier),
                content = content
            )
        }
    }
}

/**
 * 行间发丝线（5.8.0）：两端渐隐的 1dp 线，左右各让开 16dp。
 * 用 drawBehind 画在行自己的背景之上、内容之下，不额外占高度，
 * 所以整张卡的高度和原来完全一致（只多一条线）。
 */
private fun Modifier.rowHairline(color: Color = Color(0xFF8A8A8A).copy(alpha = 0.22f)): Modifier = drawBehind {
    val inset = 16.dp.toPx()
    val c = color
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.12f to c, 0.88f to c, 1f to Color.Transparent,
            startX = inset, endX = size.width - inset
        ),
        topLeft = Offset(inset, 0f),
        size = Size(size.width - inset * 2f, 1.dp.toPx())
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, divider: Boolean = true) {
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.13f else 0.10f)
    Row(Modifier.fillMaxWidth().then(if (divider) Modifier.rowHairline(hair) else Modifier)
            // 整行可点：原来只有那颗开关能点，15sp 的字看着像也能按，点上去没反应
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        // M3 的 Switch 是 52×32dp，比这行 15sp 的字大出整整一圈（用户 2026-11-05：
        // 「那个使用宋体的开关非常大，改得跟其他东西一样尺寸」）。改用自绘的 38×22dp
        // 小开关，行高回到 36dp，和上下导航行齐平（见 MiniSwitch.kt）。
        MiniSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavRow(
    label: String,
    value: String,
    divider: Boolean = true,
    /**
     * 7.4.1：只有「版本」行用到 —— 长按进动效调试模式，平时点它没有任何反应。
     *
     * ⚠️【2026-11-11 事故 · 必须遵守】这个参数**必须放在 onClick 之前**。
     * 第一版把它写在了 onClick 后面，于是所有 `NavRow("循环待办", "按间隔提醒") { page = "loop" }`
     * 的尾随 lambda 全部静默改绑到了 onLongClick 上 —— 编译通过、界面上"点哪儿都没反应"，
     * 要长按才跳页（用户当天就报了「设置界面每一个条目都点不开了」）。
     * Kotlin 的尾随 lambda 绑定的是**最后一个函数类型参数**，可空函数类型也算。
     * 规则：可选回调一律排在真正的主回调之前，主回调留在最后。
     */
    onLongClick: (() -> Unit)? = null,
    /**
     * 「关于作者」里的「作者」「版本」两行不给右箭头（用户 2026-11-18：这两行都没有下一级，
     * 画个箭头等于骗人去点）。布尔参数，不受尾随 lambda 绑定规则影响，但同样放主回调之前。
     */
    showChevron: Boolean = true,
    onClick: () -> Unit = {}
) {
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.13f else 0.10f)
    val base = Modifier.fillMaxWidth().then(if (divider) Modifier.rowHairline(hair) else Modifier)
    val clickMod = if (onLongClick != null) {
        base.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        base.clickable { onClick() }
    }
    Row(clickMod.padding(horizontal = 16.dp).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showChevron) {
            Box(Modifier.padding(start = 2.dp)) {
                Chevron(ChevronDir.Right, size = 12.dp, stroke = 1.2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 二级页分发 */
@Composable
private fun ColumnScope.SettingSubPage(
    page: String,
    onBack: () -> Unit,
    isSongti: Boolean,
    onToggleSongti: (Boolean) -> Unit,
    tabOrder: List<String>,
    onApplyOrder: (List<String>) -> Unit,
    isDark: Boolean?,
    onToggleDark: (Boolean?) -> Unit,
    fontScale: Float,
    onSetFontScale: (Float) -> Unit,
    undoMs: Int,
    onSetUndoMs: (Int) -> Unit,
    greetingTimes: GreetingTimes,
    onSetGreetingTimes: (GreetingTimes) -> Unit,
    onPreviewGreeting: (Int) -> Unit,
    undo: UndoController
) {
    // 二级页：标题行固定在外、内容滚动（标题同样往上滑缩小：20sp → 14sp）
    // key = page：换一个二级页要回到顶部，否则会把上一个页面的滚动位置带过来
    val subScroll = rememberScrollState()
    LaunchedEffect(page) { subScroll.scrollTo(0) }
    val subCollapsed by remember { derivedStateOf { subScroll.value > 24 } }
    val subCollapseT by animateFloatAsState(if (subCollapsed) 1f else 0f, tween(240), label = "navCollapseSub")

    // 2026-11-11「Motion P0」：这一行原来按 `subCollapseT > 0.5f` 布尔切档（top 4↔6、
    // bottom 8↔4、箭头 16↔13）—— 而同一个标题的字号是连续的 `20-6t`。结果是折叠到一半时
    // 字号在平滑变小、边距和返回箭头却在某一帧整块跳 2–3dp，看上去像卡了一下。
    // 项目铁律是「一个量连续派生」，这里改成同一口径的 lerp（端点值不变，只是中间不再跳档）。
    Column(Modifier.weight(1f).fillMaxWidth()) {
        // 标题行（滚动容器外，固定）
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(
                start = 8.dp, end = 16.dp,
                top = lerp(4.dp, 6.dp, subCollapseT),
                bottom = lerp(8.dp, 4.dp, subCollapseT)
            )) {
            ChevronButton(
                dir = ChevronDir.Left, onClick = onBack,
                box = 34.dp, chevron = lerp(16.dp, 13.dp, subCollapseT), stroke = 1.2.dp,
                corner = 10.dp, shiftX = 5.dp, contentDescription = "返回",
                modifier = Modifier.padding(end = 0.dp)
            )
            Text(when (page) {
                "loop" -> "循环待办"; "export" -> "导出未完成待办"
                "tags" -> "标签管理"; "bills" -> "账单总览"
                "pie" -> "支出明细"
                "order" -> "底栏排序"
                "budget" -> "月度预算"; "quick" -> "常用记账"
                "backup" -> "备份 / 恢复"
                "autobackup" -> "自动备份"
                "theme" -> "外观模式"
                "font" -> "字号"
                "undo" -> "撤回停留时间"
                "greet" -> "问候时段"
                else -> "设置"
            }, fontSize = (20f - 6f * subCollapseT).sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(subScroll)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LIST_BOTTOM_PLAIN)
        ) {
        when (page) {
            "loop" -> LoopTodoPage(undo)
            "export" -> ExportUndonePage()
            "tags" -> TagManagePage()
            "bills" -> BillsOverviewPage()
            "pie" -> ExpensePiePage()
            "autobackup" -> AutoBackupPage()
            "order" -> TabOrderPage(tabOrder, onApplyOrder, onBack)
            "budget" -> BudgetPage()
            "quick" -> QuickEntryPage(undo)
            "backup" -> BackupPage()
            "theme" -> ThemePickPage(isDark, onToggleDark)
            "font" -> FontScalePickPage(fontScale, onSetFontScale)
            "undo" -> UndoLifePage(undoMs, onSetUndoMs)
            "greet" -> GreetingTimesPage(greetingTimes, onSetGreetingTimes, onPreviewGreeting)
        }
        }
    }
}

// ===== 设置相关数据 ViewModel =====
class SettingsViewModel : ViewModel() {
    private val todoDao = AppContainer.database.todoDao()
    private val loopDao = AppContainer.database.loopTodoDao()
    private val tagDao = AppContainer.database.accTagDao()
    private val accountDao = AppContainer.database.accountDao()
    private val prefDao = AppContainer.database.appPrefDao()
    private val quickDao = AppContainer.database.quickEntryDao()

    val todos = todoDao.observeAll()
    val loops = loopDao.observeAll()
    val tags = tagDao.observeAll()
    val accounts = accountDao.observeAll()
    val quickEntries = quickDao.observeAll()

    // ===== 月度预算 =====
    suspend fun budgetCents(): Long = prefDao.get("budget_month")?.toLongOrNull() ?: 0L
    fun setBudget(cents: Long) = viewModelScope.launch { prefDao.put(AppPref("budget_month", cents.toString())) }

    // ===== 常用记账 =====
    fun addQuick(e: QuickEntry) = viewModelScope.launch { quickDao.insert(e) }
    fun deleteQuick(id: Long) = viewModelScope.launch { quickDao.delete(id) }
    /** 撤回删除：按原 id 原字段插回（id 非 0 时 Room 照原样写回，不会新分配一个） */
    fun restoreQuick(e: QuickEntry) = viewModelScope.launch { quickDao.upsert(e) }

    // ===== 循环待办（5.5.0：开始日期 + 下一次提醒 → 间隔天数自动推算） =====
    // 两个入口都先把日期归到当天零点再用（界面/备份传进来的可能是「当前时刻」），
    // 间隔从两个日期现算 —— 用户改任一个日期，间隔立即跟着变，不需要再问一遍「几天一次」。
    fun addLoop(title: String, detail: String, startDate: Long, nextDate: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            val start = LoopSchedule.dayStart(startDate)
            val next = LoopSchedule.dayStart(nextDate)
            // 新加的排在最后：拖动换位是按 sortOrder 排的，全给 0 的话新条目会插到中间
            loopDao.insert(LoopTodo(title = title, detail = detail, startDate = start,
                nextDate = next, intervalDays = LoopSchedule.intervalOf(start, next),
                sortOrder = loopDao.maxSortOrder() + 1))
        }
    /**
     * ★ 7.5.5（复核报告 3.9）：间隔改成**显式传入**，不再在这里按两个日期反推。
     *   `nextDate` 会被自动往前推，反推出来的是「距上次提醒多少天」，不是周期 ——
     *   旧版因此只要点一次保存就会悄悄改周期。
     */
    fun updateLoop(id: Long, title: String, detail: String, startDate: Long, nextDate: Long, intervalDays: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            val start = LoopSchedule.dayStart(startDate)
            val next = LoopSchedule.dayStart(nextDate)
            loopDao.update(id, title, detail, start, next, intervalDays.coerceIn(1, LoopSchedule.MAX_INTERVAL_DAYS))
        }
    fun toggleLoop(id: Long, dayMs: Long, done: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val loop = loopDao.getById(id) ?: return@launch
        loopDao.setDoneDates(id, LoopSchedule.withDone(loop, dayMs, done))
    }
    fun deleteLoop(id: Long) = viewModelScope.launch(Dispatchers.IO) { loopDao.delete(id) }
    /** 撤回删除：整条原样插回（含 doneDates，已勾过的那几天不会因为一次误删就没了） */
    fun restoreLoop(t: LoopTodo) = viewModelScope.launch(Dispatchers.IO) { loopDao.upsert(t) }

    /**
     * 新建标签：同名就直接改那一条的颜色，别插出两条一模一样的（列表里分不清）。
     *
     * ★ 7.5.5（复核报告 2.6）：比较用 [tagKey] 而不是字面相等 —— 账目是按**标签名字符串**关联的，
     *   「吃饭」和「吃饭 」（尾随空格）、「lunch」和「Lunch」在库里是两条，却会互相覆盖颜色、
     *   删一个把另一个的账目也清掉。显示名仍然保留用户输入的样子（只 trim），只有比较走归一。
     */
    fun addTag(name: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        val shown = name.trim()
        val key = tagKey(shown)
        val same = tagDao.getAll().firstOrNull { tagKey(it.name) == key }
        if (same != null) tagDao.update(same.id, same.name, color)
        else tagDao.upsert(AccTagEntity(name = shown, color = color))
    }

    /**
     * 改标签。**改名必须同时改账目里的 tag 字符串** —— 账目是按名字关联的，
     * 只改 acc_tags.name 会让老账目全变孤儿（灰点、饼图掉「其他」）。
     * 两件事放同一个事务里，避免改一半。
     */
    fun updateTag(tag: AccTagEntity, newName: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            if (newName != tag.name) {
                accountDao.renameTag(tag.name, newName)
                // 常用记账存的也是标签名字符串 —— 不改它，快捷记账会写出「孤儿标签」的账目
                quickDao.renameTag(tag.name, newName)
            }
            tagDao.update(tag.id, newName, color)
        }
    }

    suspend fun countByTag(name: String): Int = accountDao.countByTag(name)

    /** 删标签：同时把用到它的账目标签清空，不留孤儿标签。
     *  两件事放同一个事务里 —— 分开写若中途中断，会留下「账目标签清了、标签还在」的孤儿标签。 */
    fun deleteTag(tag: AccTagEntity) = viewModelScope.launch(Dispatchers.IO) {
        AppContainer.database.withTransaction {
            accountDao.clearTag(tag.name)
            // 同上：常用记账里的同名标签一起清掉
            quickDao.clearTag(tag.name)
            tagDao.delete(tag.id)
        }
    }

    // ===== 备份 / 恢复 =====
    /**
     * 备份文件**格式**版本。改动任何一张表的字段/含义（或 buildBackupJson 的输出结构）时都要 +1，
     * 这样将来恢复旧文件才能知道"这份文件是什么年代导的、缺哪些字段"。
     * 1 = 初版（漏 priority/sortOrder/doneAt）；2 = 补齐 todos 三项 + 记 dbVersion/appVersion。
     * 读文件那边在 [BackupCodec.SCHEMA]（同一个数，纯函数好单测）。
     */
    companion object { const val BACKUP_SCHEMA = BackupCodec.SCHEMA }

    /**
     * 导出备份（整份 JSON）。拼装逻辑已抽到 data/BackupWriter —— 自动备份用的是同一份，
     * 两边共用才不会出现「导出能读、自动备份读不回」的分叉。
     */
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) { BackupWriter.buildJson() }

    /**
     * 检查一份备份文件（恢复前先看内容，不碰数据库）。
     * 返回 Ok = 可用（[BackupCodec.Data.summary] 是给用户看的概览）；Bad = 不可用（[BackupCodec.Result.Bad.why] 是原因）。
     */
    suspend fun inspectBackup(json: String): BackupCodec.Result = withContext(Dispatchers.IO) {
        BackupCodec.parse(json)
    }

    /**
     * 恢复。返回恢复的记录条数；负数 = 失败码（-1 格式 / -2 更新版本 / -3 内容损坏 / -4 写库失败）。
     *
     * 顺序很要紧：**先整份解析校验（纯函数、不碰库）→ 通过后再在一个事务里落库**。
     * 老实现是一边解析一边 `clear()` + `insert()`：文件里有一条坏记录就停在半途，
     * 库里已经被清空、只写回一半（恢复失败 = 数据没了）。
     *
     * 各表**只在文件里确实有那一段时才清空**：缺段说明这份备份没导出那张表，
     * 保持现有数据不动（老实现不看存在性，一份只有 todos 的文件会把账目/标签全清掉）。
     *
     * 界面设置：**文件里带了哪几项就替换哪几项**，没带的保持现状（见 BackupCodec.PREF_MAP）。
     * 旧版是「整组删掉再写文件里的项」，等于把老备份里不存在的设置（问候时段、撤回时长）
     * 一律抹成默认 —— 那不是「以备份为准」，是「用旧文件清空新设置」。
     */
    suspend fun restoreFromJson(json: String): Int = withContext(Dispatchers.IO) {
        val data = when (val r = BackupCodec.parse(json)) {
            is BackupCodec.Result.Bad -> return@withContext r.code
            is BackupCodec.Result.Ok -> r.data
        }
        var written = 0
        try {
            AppContainer.database.withTransaction {
                data.todos?.let { raw ->
                    val list = dedupeById(raw) { it.id }
                    todoDao.clear(); list.forEach { todoDao.insert(it) }; written += list.size
                }
                data.accounts?.let { raw ->
                    val list = dedupeById(raw) { it.id }
                    accountDao.clear(); list.forEach { accountDao.insert(it) }; written += list.size
                }
                data.loopTodos?.let { raw ->
                    val list = dedupeById(raw) { it.id }
                    loopDao.clear(); list.forEach { loopDao.insert(it) }; written += list.size
                }
                data.accTags?.let { raw ->
                    val list = dedupeById(raw) { it.id }
                    tagDao.clear(); list.forEach { tagDao.upsert(it) }; written += list.size
                }
                data.quickEntries?.let { raw ->
                    val list = dedupeById(raw) { it.id }
                    quickDao.clear(); list.forEach { quickDao.insert(it) }; written += list.size
                }
                // 界面设置：文件里**带了哪几个键就替换哪几个**（其余保持现状）。
                // 旧版用固定的 PREF_KEYS 整组删 —— 拿一份 7.5.0 的老备份恢复时，
                // 文件里没有 greet_* / undo_ms，会把这台的问候时段和撤回时长悄悄抹回默认。
                if (data.prefs.isNotEmpty()) {
                    prefDao.removeAll(data.prefs.keys.toList())
                    data.prefs.forEach { (k, v) ->
                        // 底栏顺序顺手规范化：存档里可能还留着已删掉的「模型」那一项，
                        // 规范化后再写，值本身就不会一直脏着传下去
                        val clean = if (k == "tab_order") parseTabOrder(v)?.joinToString(",") ?: v else v
                        prefDao.put(AppPref(k, clean))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协作取消（离开页面 / 进程收尾）不是「写库失败」：照常抛出。
            // 老写法 catch (Exception) 会把取消也吞成 -4，用户看到「写库失败」其实只是被取消。
            throw e
        } catch (e: Exception) {
            // 走到这里事务已经回滚，库还是恢复前的样子；只把细节留给日志，界面仍报 -4。
            android.util.Log.w("ShixuJi", "restore failed, transaction rolled back", e)
            return@withContext -4
        }
        written
    }

    /**
     * 去掉备份文件里**重复的记录 id**。
     *
     * 落库用的是 REPLACE / @Insert(REPLACE)：两条同 id 的记录会静默互相覆盖，
     * 用户看到「说恢复了 87 条、实际只有 86 条」，手改过的备份更是无声丢记录。
     * 这里保留重复 id 的**最后一条**（与 REPLACE 后写覆盖先写的语义一致），
     * id ≤ 0 的记录（读不出来时被归一成 0）不参与去重，交给 Room 自增。
     */
    private fun <T> dedupeById(list: List<T>, idOf: (T) -> Long): List<T> {
        if (list.size < 2) return list
        val seen = HashSet<Long>()
        val out = ArrayList<T>(list.size)
        for (t in list.asReversed()) {
            val id = idOf(t)
            if (id <= 0L || seen.add(id)) out += t
        }
        out.reverse()
        return out
    }
}

// ===== 循环待办 =====
/**
 * 循环待办页（5.5.0 改版）。
 *
 * 用户 2026-11-01：「不再是每周几提醒，而是设置提醒的开始日期、下一次提醒日期，
 * 通过这两个数据自动推算出间隔几天提醒一次」。所以界面上只有两个日期，
 * 「每 N 天」是**算出来的结果**（[LoopSchedule.intervalOf]），当场显示、不用用户再填一遍间隔。
 * 改任一个日期，间隔立刻跟着变；下一次提醒早于开始日期时报红字，不许保存。
 */
@Composable
private fun LoopTodoPage(undo: UndoController, vm: SettingsViewModel = viewModel()) {
    val loops by vm.loops.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<LoopTodo?>(null) }     // null = 没开弹窗
    var adding by remember { mutableStateOf(false) }
    // ★ 5.1：跨午夜自动刷新（remember{} 会把「今天」钉死在打开页面那一刻）
    val today = rememberTodayMs()

    Column {
        loops.forEach { t ->
            val dueToday = LoopSchedule.isDueOn(t, today)
            val done = LoopSchedule.isDoneOn(t, today)
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { editing = t }
                .glassSurface(cardShape(CardRadius.ROW_TIGHT), dark = isDarkTheme, ring = true, elevation = 5.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 今天不该提醒时不给勾选框（勾了也没有对应的「这一次」），只留个占位保持对齐
                    if (dueToday) {
                        ShixuCheck(
                            checked = done,
                            onToggle = { vm.toggleLoop(t.id, today, !done) },
                            tint = MaterialTheme.colorScheme.primary,
                            touch = 34.dp,
                            contentDescription = if (done) "标记未完成" else "标记完成"
                        )
                    } else {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text("·", fontSize = 20.sp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 15.sp)
                        if (t.detail.isNotBlank()) {
                            Text(t.detail, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(loopScheduleLine(t, today), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 删除 → 先弹可撤回的提示球，再落库（顺序不能反：撤回要拿到原对象）
                    //
                    // 2026-11-08 审计 B 项：原来只有「删」这两个字的墨迹能被点到（约 15×15dp），
                    // 手指一偏就落到卡片上 —— 那不是删除失败，而是把编辑弹窗打开，看着像点错了。
                    // 现在给 44×44dp 的方形热区（Material 的最小触摸目标），墨迹还是 13sp 不变。
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .clickable(role = Role.Button) {
                                undo.show("已删除循环待办：${t.title}") { vm.restoreLoop(t) }
                                vm.deleteLoop(t.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("添加循环待办") }
    }

    if (adding) {
        LoopEditDialog(
            source = null,
            onDismiss = { adding = false },
            onConfirm = { title, detail, start, next, _ ->
                vm.addLoop(title, detail, start, next); adding = false
            })
    }
    editing?.let { t ->
        LoopEditDialog(
            source = t,
            onDismiss = { editing = null },
            onConfirm = { title, detail, start, next, interval ->
                vm.updateLoop(t.id, title, detail, start, next, interval); editing = null
            })
    }
}

/** 列表里那行小字：「每 4 天 · 下次 11月5日 周三」（今天就是提醒日时直说） */
private fun loopScheduleLine(t: LoopTodo, todayMs: Long): String {
    val interval = t.intervalDays.coerceAtLeast(1)
    if (LoopSchedule.isDueOn(t, todayMs)) return "每 $interval 天 · 今天提醒"
    val next = LoopSchedule.upcomingDate(t, todayMs)
    val cal = DateUtils.calFrom(next)
    return "每 $interval 天 · 下次 ${DateUtils.formatMonthDayCn(cal)} ${DateUtils.weekdayCn(cal)}"
}

/**
 * 添加 / 编辑循环待办的弹窗（同一个壳，`source == null` 是新建）。
 *
 * 日期用「‹ 日期 ›」步进而不是系统的 DatePickerDialog：
 * 这个 App 里日期一律是这套手势（待办、账目都一样），混进一个 Material 日历会突兀；
 * 步进一次动一天，配合右边的「今」快速回到今天。
 */
@Composable
private fun LoopEditDialog(
    source: LoopTodo?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, detail: String, startDate: Long, nextDate: Long, intervalDays: Int) -> Unit,
) {
    // ★ 5.2：同样要跟着午夜走；「今」按钮点击时再取一次当前日期，
    //   弹窗开着过夜也不会把昨天写进去。
    val todayMs = rememberTodayMs()
    var title by remember { mutableStateOf(source?.title ?: "") }
    var detail by remember { mutableStateOf(source?.detail ?: "") }
    var startMs by remember { mutableLongStateOf(source?.startDate?.takeIf { it > 0 } ?: todayMs) }
    var nextMs by remember {
        mutableLongStateOf(
            source?.nextDate?.takeIf { it > 0 }
                ?: DateUtils.addDays(DateUtils.calFrom(todayMs), 7).timeInMillis
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    // ★ 7.5.5（复核报告 3.9）：间隔是「显式值」，不再每帧按两个日期反推。
    //   - 编辑已有条目：初值 = 库里那份 intervalDays（新增首次为 null = 由两个日期推）
    //   - 之后只有用户点「间隔」那行的 ‹ › 才会改它
    //   旧版每帧反推，于是 nextDate 被自动往前推过之后，打开弹窗看间隔就不是原来的数了。
    var explicitInterval by remember {
        mutableStateOf(source?.intervalDays?.coerceIn(1, LoopSchedule.MAX_INTERVAL_DAYS))
    }
    val interval = explicitInterval ?: LoopSchedule.intervalForEdit(null, startMs, nextMs)
    val dayGap = LoopSchedule.daysBetween(LoopSchedule.dayStart(startMs), LoopSchedule.dayStart(nextMs))
    val invalid = dayGap < 0
    // ★ 5.3：间隔上限和 intervalOf() 共用 LoopSchedule.MAX_INTERVAL_DAYS。
    //   旧版只有后台夹到 3650 天、UI 什么都不管：用户把开始日期设成 2026、下一次提醒设成 2038，
    //   保存时静默变成 3650 天，提醒序列凭空多出几次（isDueOn 按 3650 的整倍数命中）。
    val tooLong = dayGap > LoopSchedule.MAX_INTERVAL_DAYS

    ShixuCompactDialog(
        shape = GlassDialogShape,
        containerColor = glassDialogContainer(),
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "添加循环待办" else "编辑循环待办") },
        text = {
            // ★ 这里**不能**再套 Modifier.verticalScroll：ShixuCompactDialog 的正文外层
            //   已经是 `Column(Modifier.heightIn(max = 560.dp).verticalScroll(bodyScroll))`，
            //   滚动容器量内容时给的是无限最大高度 —— 里面再套一个滚动容器会抛
            //   IllegalStateException: Vertically scrollable component was measured with an
            //   infinity maximum height constraints，直接崩 App。
            //   （2026-11-10 自检实测：设置 → 循环待办 → 点条目正文，必崩。）
            //   弹窗正文本来就能滚，这里只放内容。
            Column {
                ShixuField(value = title, onValueChange = { title = it; error = null },
                    label = "内容", singleLine = true, isError = error != null,
                    modifier = Modifier.fillMaxWidth())
                error?.let {
                    Text(it, fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp))
                }
                ShixuField(value = detail, onValueChange = { detail = it },
                    label = "备注（可选）", singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                LoopDateRow("开始日期", startMs, onShift = { d ->
                    startMs = DateUtils.addDays(DateUtils.calFrom(startMs), d).timeInMillis
                }, onToday = { startMs = LoopSchedule.dayStart(System.currentTimeMillis()) })
                LoopDateRow("下一次提醒", nextMs, onShift = { d ->
                    nextMs = DateUtils.addDays(DateUtils.calFrom(nextMs), d).timeInMillis
                }, onToday = { nextMs = LoopSchedule.dayStart(System.currentTimeMillis()) })

                when {
                    invalid -> Text(
                        "下一次提醒不能早于开始日期",
                        fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                    tooLong -> Text(
                        "间隔最长 ${LoopSchedule.MAX_INTERVAL_DAYS} 天（现在 $dayGap 天）",
                        fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                    else -> Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("间隔", fontSize = DialogText.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        StepperArrow(ChevronDir.Left) {
                            explicitInterval = (interval - 1).coerceAtLeast(1)
                        }
                        Text("$interval 天", fontSize = DialogText.Value,
                            modifier = Modifier.padding(horizontal = 1.dp))
                        StepperArrow(ChevronDir.Right) {
                            explicitInterval = (interval + 1).coerceAtMost(LoopSchedule.MAX_INTERVAL_DAYS)
                        }
                    }
                }
            }
        },
        confirmButton = {
            ShixuDialogButton(stringResource(R.string.action_save)) {
                when {
                    title.isBlank() -> error = "请输入内容"
                    invalid -> error = "下一次提醒不能早于开始日期"
                    tooLong -> error = "间隔最长 ${LoopSchedule.MAX_INTERVAL_DAYS} 天"
                    else -> onConfirm(title.trim(), detail.trim(), startMs, nextMs, interval)
                }
            }
        },
        dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) })
}

/** 弹窗里的一行日期：标签 + ‹ 日期 周X › + 「今」 */
@Composable
private fun LoopDateRow(label: String, ms: Long, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val cal = DateUtils.calFrom(ms)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        StepperArrow(ChevronDir.Left) { onShift(-1) }
        Text("${DateUtils.formatMonthDayCn(cal)} ${DateUtils.weekdayCn(cal)}",
            fontSize = DialogText.Value, modifier = Modifier.padding(horizontal = 1.dp))
        StepperArrow(ChevronDir.Right) { onShift(1) }
        Spacer(Modifier.weight(1f))
        Text("今", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToday() }.padding(6.dp))
    }
}

// ===== 底栏排序（长按拖动 或 ↑↓ 点击） =====
@Composable
private fun TabOrderPage(current: List<String>, onApplyOrder: (List<String>) -> Unit, onBack: () -> Unit) {
    val initial = remember(current) {
        val m = current.filter { it in DEFAULT_TAB_ORDER }
        if (m.size == DEFAULT_TAB_ORDER.size) m else DEFAULT_TAB_ORDER
    }
    var order by remember(initial) { mutableStateOf(initial) }
    var draggingLabel by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    // 换位阈值用的步距 = 行高 + 行间距：把手容器 36dp + 上下各 7dp 内距 = 50dp，再加 7dp 间距。
    // 写死旧值 62.dp 会让「拖过半行」的判定偏晚，手指要拖过一整行才会换位。
    val itemHeight = 57.dp

    Column {
        order.forEach { label ->
            key(label) {
                val isDragging = draggingLabel == label
                val index = order.indexOf(label)
                val canUp = index > 0
                val canDown = index in 0 until order.lastIndex
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragDy else 0f
                            // 拖起时整条微微放大 + 加投影，读感是「被拎起来了」；
                            // 不加的话它只是平移，会以为是自己眼花了（待办页卡片同一套 1.02）。
                            val s = if (isDragging) 1.015f else 1f
                            scaleX = s; scaleY = s
                            shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        // ★ key 不能是 order：onDrag 里换位会改 order → pointerInput 的 key 变
                        //   → 手势协程被重启，onDragEnd 不再触发，拖一格就「卡死」在那行浮起状态。
                        //   order 是 remember 出来的 MutableState，lambda 里读到的永远是最新值，
                        //   所以这里用 Unit 即可（对照 TodoScreen 的 pointerInput(t.id, size)）。
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingLabel = label; dragDy = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                    val th = itemHeight.toPx()
                                    val cur = order.indexOf(label)
                                    if (cur < 0) return@detectDragGesturesAfterLongPress
                                    if (dragDy > th / 2 && cur < order.size - 1) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        dragDy -= th
                                    } else if (dragDy < -th / 2 && cur > 0) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        dragDy += th
                                    }
                                },
                                onDragEnd = { draggingLabel = null; dragDy = 0f },
                                onDragCancel = { draggingLabel = null; dragDy = 0f }
                            )
                        }
                        .glassSurface(cardShape(CardRadius.ROW_TIGHT), dark = isDarkTheme, ring = true, elevation = 5.dp)
                ) {
                    // 行内节奏照「待办卡片」：把手在最左、名称次之、状态/操作靠右。
                    // 旧版是 14dp 四边内距 + 18sp 的「↑」「↓」字符，两个问题：
                    //   ① 符号墨迹偏基线，放在正方的点击区里看着不居中（和自己画的 chevron 差一截）；
                    //   ② 一屏三行里挤出四个可点区，边缘那两个很容易点歪。
                    Row(
                        Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(width = 20.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                            // 和待办卡片把手同一套：0.34 透明度的「≡」，只是拖得动
                            Text("≡", fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f))
                        }
                        Text(label, fontSize = 15.5.sp, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(start = 6.dp))
                        if (order.first() == label) {
                            // 「默认」从裸文字改成小胶囊：裸文字和右侧箭头挤在一起，看不出是状态还是按钮。
                            // 深色下不能直接用主色文字：暗底上的正红对比度不够（实测几乎读不出来），
                            // 底提到 0.24、字往白里掺 45%。
                            Box(
                                Modifier.clip(CapsuleShape())
                                    .background(
                                        MaterialTheme.colorScheme.primary
                                            .copy(alpha = if (isDarkTheme) 0.24f else 0.13f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    "默认", fontSize = 11.5.sp,
                                    color = if (isDarkTheme)
                                        lerp(MaterialTheme.colorScheme.primary, Color.White, 0.45f)
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                        }
                        ReorderArrow(up = true, enabled = canUp) {
                            if (canUp) order = order.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
                        }
                        ReorderArrow(up = false, enabled = canDown) {
                            if (canDown) order = order.toMutableList().also { it.add(index + 1, it.removeAt(index)) }
                        }
                    }
                }
            }
        }
        // 保存：原来用 M3 的默认 Button —— 40dp 高、圆角 20dp 偏方，和全 App 的胶囊语汇不一致。
        // 这里换成主色实心胶囊（与记账弹窗、整理页那几颗同一造型）。
        Box(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(44.dp)
                .clip(CapsuleShape())
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onApplyOrder(order); onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("保存顺序", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

/**
 * 上/下移一格的小箭头（[Chevron] 转 90° 画出来的，不用「↑」「↓」字符）。
 *
 * 用字符的问题是墨迹偏基线、正方点击区里看着不居中；而且两种符号的字面宽高比不一致，
 * 上下两颗看起来不是一套。转过的 chevron 两段仍等长、夹角 90°，与 App 别处的箭头同族。
 * 到顶/到底时只把颜色压淡，不换图标、不改尺寸（行高才不会跳）。
 */
@Composable
private fun ReorderArrow(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(percent = 50))
            // 上下两颗紧贴，撑到 48dp 会互相盖住。
            .tapNoExpand(enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Chevron(
            dir = ChevronDir.Right,
            size = 12.dp,
            stroke = 1.3.dp,
            color = tint.copy(alpha = if (enabled) 0.85f else 0.22f),
            modifier = Modifier.rotate(if (up) -90f else 90f)
        )
    }
}

/** 主页「外观模式」行右侧显示的当前值 */
private fun darkModeLabel(isDark: Boolean?): String =
    if (isDark == null) "跟随系统" else if (isDark) "深色" else "浅色"

/** 主页「字号」行右侧显示的当前值 */
private fun fontScaleLabel(scale: Float): String =
    FONT_SCALES.firstOrNull { kotlin.math.abs(it.second - scale) < 0.01f }?.first ?: "标准"

/**
 * 单选项行：行样式与 [NavRow] 完全一致（16dp 左右内距、发丝线、15sp），
 * 只把右侧的箭头换成勾、选中项文字用主色。
 *
 * 为什么不用 M3 的 RadioButton：那颗 20dp 的圆点带一圈描边，和本 App「细环 + 实心」的勾选语言
 * 不是一族；这里沿用勾选框（ShixuCheck）里的那个勾，只是缩小到 14dp。
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    divider: Boolean = true,
    onClick: () -> Unit
) {
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.13f else 0.10f)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .then(if (divider) Modifier.rowHairline(hair) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 16.dp).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = 15.sp,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Canvas(Modifier.size(14.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.10f, size.height * 0.52f)
                    lineTo(size.width * 0.38f, size.height * 0.80f)
                    lineTo(size.width * 0.90f, size.height * 0.20f)
                }
                drawPath(
                    path, accent,
                    style = Stroke(
                        width = size.width * 0.15f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/** 二级页：外观模式（跟随系统 / 浅色 / 深色） */
@Composable
private fun ThemePickPage(isDark: Boolean?, onPick: (Boolean?) -> Unit) {
    val modes = listOf("跟随系统" to (null as Boolean?), "浅色" to false, "深色" to true)
    Column {
        SectionCard(null) {
            modes.forEachIndexed { i, (label, mode) ->
                // divider = "画这一行的顶线"，所以判的是 i != 0（不是 != lastIndex）。
                // 写反了会在第一行上方多出一条线、最后一行和卡片底之间反而是空的。
                ChoiceRow(label, isDark == mode, divider = i != 0) { onPick(mode) }
            }
        }
    }
}

/**
 * 二级页：撤回停留时间（滑块）。
 *
 * 拖动时只改本地 `temp`（实时显示秒数），**松手才落库** —— 否则一路拖过去会往偏好表里写几百行，
 * 备份/恢复与 Flow 监听都会跟着抖。
 * 秒数用大字显示，滑块两端标 2 秒 / 16 秒，中间按 1 秒一档（共 15 档）。
 */
@Composable
private fun UndoLifePage(undoMs: Int, onSet: (Int) -> Unit) {
    val init = (undoMs / 1000f).coerceIn(UNDO_MIN_SEC.toFloat(), UNDO_MAX_SEC.toFloat())
    var temp by remember(undoMs) { mutableFloatStateOf(init) }
    Column {
        SectionCard(null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${temp.roundToInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        " 秒", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                // 不用 M3 的 Slider：它那颗"拇指"在新版里是一根 4dp 竖条（Material 3 expressive 的画法），
                // 放在这套宋体 + 胶囊的界面里像个游标卡尺。自己画：6dp 圆角轨道 + 20dp 白瓷圆钮。
                SecondsSlider(
                    value = temp.roundToInt(),
                    min = UNDO_MIN_SEC, max = UNDO_MAX_SEC,
                    onChange = { temp = it.toFloat() },
                    onCommit = { onSet(it * 1000) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${UNDO_MIN_SEC} 秒", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${UNDO_MAX_SEC} 秒", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 秒数滑块（自绘）。
 *
 * 交互：按下即定位（点轨道任意处直接跳过去），按住拖动实时跟手，抬手才回调 [onCommit]。
 * 值和轨道宽度都按整数秒吸附，所以拖动时读数不会出现 5.37 这种数。
 */
@Composable
private fun SecondsSlider(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    onCommit: (Int) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackIdle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val surface = MaterialTheme.colorScheme.surface
    val dark = isDarkTheme
    // 按住时圆钮"抓起来"：半径变大一点、投影加深。松手回原样。
    var grabbing by remember { mutableStateOf(false) }
    // 7.4.1：秒数滑块与时段端点用同一条抓取曲线（旧 spring(0.55,900) 会过冲，像"弹了一下"）。
    val grab by animateFloatAsState(if (grabbing) 1f else 0f, Motion.TouchSpring, label = "sliderGrab")
    BoxWithConstraints(Modifier.fillMaxWidth().height(46.dp)) {
        val thumb = 20.dp
        val widthPx = constraints.maxWidth.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val thumbPx = with(density) { thumb.toPx() }
        val usable = (widthPx - thumbPx).coerceAtLeast(1f)
        val span = (max - min).coerceAtLeast(1)
        val ratio = ((value - min).toFloat() / span).coerceIn(0f, 1f)
        val thumbCenter = thumbPx / 2f + ratio * usable

        fun valueAt(x: Float): Int {
            val r = ((x - thumbPx / 2f) / usable).coerceIn(0f, 1f)
            return (min + (r * span).roundToInt()).coerceIn(min, max)
        }

        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val r = 3.dp.toPx()
            val trackW = 6.dp.toPx()
            // 轨道：整条淡灰，已选段主色
            drawLine(
                color = trackIdle, start = Offset(r + 0f, cy), end = Offset(size.width - r, cy),
                strokeWidth = trackW, cap = StrokeCap.Round
            )
            // 已选段：主色 + 一条上亮下暗的竖向渐变叠在上面。
            // 纯一条实色主色看着像"画了根线"，加上这道光它才像一段被推进去的液体。
            drawLine(
                color = accent, start = Offset(r, cy), end = Offset(thumbCenter, cy),
                strokeWidth = trackW, cap = StrokeCap.Round
            )
            if (thumbCenter - r > 1f) {
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        ),
                        startY = cy - trackW / 2f,
                        endY = cy + trackW / 2f
                    ),
                    start = Offset(r, cy), end = Offset(thumbCenter, cy),
                    strokeWidth = trackW, cap = StrokeCap.Round
                )
            }
            // 圆钮：白瓷底 + 主色细环 + 一点投影，和 App 里其它"可拖的东西"同族。
            // 2026-11-10 用户反馈「白色高光让他看上去像缺了一角」—— 旧代码先画高光再画主色环，
            // 0.85 的白把上半个环糊掉了。现在统一走 drawGlassKnob：高光裁在圆里、主色环最后画。
            val knobR = thumbPx / 2f + 1.5.dp.toPx() * grab
            drawGlassKnob(
                center = Offset(thumbCenter, cy),
                radius = knobR,
                dark = dark,
                rimColor = accent,
                rimWidth = 2.dp.toPx(),
                shadowScale = 1f + grab * 0.7f
            )
        }

        Box(
            Modifier.fillMaxSize().pointerInput(min, max, widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    grabbing = true
                    var v = valueAt(down.position.x)
                    onChange(v)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { ch.consume(); break }
                        val nv = valueAt(ch.position.x)
                        if (nv != v) { v = nv; onChange(v) }
                        ch.consume()
                    }
                    grabbing = false
                    onCommit(v)
                }
            }
        )
    }
}

/** 二级页：字号 */
@Composable
private fun FontScalePickPage(fontScale: Float, onPick: (Float) -> Unit) {
    Column {
        SectionCard(null) {
            FONT_SCALES.forEachIndexed { i, (label, scale) ->
                ChoiceRow(
                    label,
                    kotlin.math.abs(fontScale - scale) < 0.01f,
                    divider = i != 0
                ) { onPick(scale) }
            }
        }
    }
}

@Composable
private fun LabelText(s: String, sp: Int, c: Color) {
    Text(s, fontSize = sp.sp, color = c, modifier = Modifier.padding(bottom = 8.dp))
}

// ===== 导出未完成待办 =====
@Composable
private fun ExportUndonePage(vm: SettingsViewModel = viewModel()) {
    val todos by vm.todos.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    // 按「逾期 / 今天 / 之后 / 无期限」分段导出，组内优先级高的在前、同级按日期近的在前。
    // 原来是 sortedBy { dueDate } 一把排：无期限的 dueDate=0 反而排到最前面，
    // 过期半年的和明天的混在一起，导出去看着像一堆没头绪的条目。
    val groups: List<Pair<String, List<Todo>>> = remember(todos) {
        val open = todos.filter { !it.done }
        val todayStart = DateUtils.todayCal().timeInMillis
        val todayEnd = todayStart + 24L * 60 * 60 * 1000 - 1
        // 组内优先级高的在前；同级按「离今天越近越前」——
        // 已逾期组用日期倒序（最近到期的在前，与封面的「过往未完成」同口径），
        // 今天/之后组用日期正序（越早到的越前）。
        val order = Comparator<Todo> { a, b ->
            if (a.priority != b.priority) b.priority - a.priority else a.dueDate.compareTo(b.dueDate)
        }
        val overdueOrder = Comparator<Todo> { a, b ->
            if (a.priority != b.priority) b.priority - a.priority else b.dueDate.compareTo(a.dueDate)
        }
        listOf(
            "已逾期" to open.filter { it.dueDate != 0L && it.dueDate < todayStart }.sortedWith(overdueOrder),
            "今天" to open.filter { it.dueDate in todayStart..todayEnd }.sortedWith(order),
            "之后" to open.filter { it.dueDate > todayEnd }.sortedWith(order),
            "无期限" to open.filter { it.dueDate == 0L }.sortedWith(order)
        ).filter { it.second.isNotEmpty() }
    }
    var exported by remember { mutableStateOf("") }

    Column {
        Button(onClick = {
            exported = if (groups.isEmpty()) "暂无未完成待办"
            else buildString {
                append("未完成待办（共 ${groups.sumOf { it.second.size }} 项）：\n")
                var n = 0
                groups.forEach { (name, list) ->
                    append("\n【$name】${list.size} 项\n")
                    list.forEach { t ->
                        n++
                        append("$n. ${t.title}")
                        if (t.detail.isNotBlank()) append("（${t.detail}）")
                        if (t.dueDate != 0L) append(" · ${DateUtils.formatDateCn(t.dueDate)}")
                        if (t.priority != PRIORITY_NONE) append(" · 优先级${priorityLabel(t.priority)}")
                        append("\n")
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("生成") }
        if (exported.isNotBlank()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)
                .glassSurface(cardShape(CardRadius.ROW_TIGHT), dark = isDarkTheme, ring = true, elevation = 5.dp)) {
                Text(exported, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
            }
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("待办", exported))
                toast(ctx, "已复制到剪贴板")
            }, modifier = Modifier.fillMaxWidth()) { Text("复制") }
        }
    }
}

/** 标签行右侧的两枚圆形小按钮：铅笔（改）/ 垃圾桶（删）。 */
private enum class TagActionIcon { Edit, Delete }

/**
 * 线描小图标按钮（跟《界面精致化方案》里画的一致）：30dp 圆底 + 16×16 视图框的线描图标。
 * 图标用 Canvas 手画，不用 emoji、不用位图资源——这套 UI 里所有图形都是画的。
 */
@Composable
private fun TagActionGlyph(icon: TagActionIcon, color: Color, onClick: () -> Unit) {
    // 深色下如果用「红底 + 红字」两层叠加，字会糊成一片；深色改白 7% 底。
    val bg = if (isDarkTheme) Color.White.copy(alpha = 0.07f) else color.copy(alpha = 0.12f)
    val desc = if (icon == TagActionIcon.Edit) "修改标签" else "删除标签"
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(bg)
            // 编辑与删除只隔 6dp，撑到 48dp 会盖住彼此（误删风险）。
            .tapNoExpand { onClick() }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(15.dp)) {
            // 16×16 视图框等比缩到实际像素；线宽 1.35 单位，圆头圆角。
            val u = size.minDimension / 16f
            val stroke = Stroke(width = 1.35f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (icon == TagActionIcon.Edit) {
                // 笔身：M2 14 l1 -3.6 L11.4 2 l2.6 2.6 L5.6 13 z
                val body = Path().apply {
                    moveTo(2f * u, 14f * u)
                    lineTo(3f * u, 10.4f * u)
                    lineTo(11.4f * u, 2f * u)
                    lineTo(14f * u, 4.6f * u)
                    lineTo(5.6f * u, 13f * u)
                    close()
                }
                drawPath(body, color, style = stroke)
                // 笔箍：M9.8 3.6 l2.6 2.6
                drawPath(
                    Path().apply {
                        moveTo(9.8f * u, 3.6f * u)
                        lineTo(12.4f * u, 6.2f * u)
                    },
                    color, style = stroke
                )
            } else {
                // 桶盖：M3 4.4 h10
                drawPath(
                    Path().apply {
                        moveTo(3.2f * u, 4.4f * u)
                        lineTo(12.8f * u, 4.4f * u)
                    },
                    color, style = stroke
                )
                // 提手：M6.5 4.4 V2.6 h3 V4.4（高度留足，否则和桶盖糊成一条实心横条）
                drawPath(
                    Path().apply {
                        moveTo(6.5f * u, 4.4f * u)
                        lineTo(6.5f * u, 2.7f * u)
                        lineTo(9.5f * u, 2.7f * u)
                        lineTo(9.5f * u, 4.4f * u)
                    },
                    color, style = stroke
                )
                // 桶身：跟桶盖留 0.9 单位的缝，否则两道线并成一条粗黑边
                drawPath(
                    Path().apply {
                        moveTo(4.9f * u, 5.3f * u)
                        lineTo(5.45f * u, 12.3f * u)
                        quadraticBezierTo(5.55f * u, 13.3f * u, 6.5f * u, 13.3f * u)
                        lineTo(9.5f * u, 13.3f * u)
                        quadraticBezierTo(10.45f * u, 13.3f * u, 10.55f * u, 12.3f * u)
                        lineTo(11.1f * u, 5.3f * u)
                    },
                    color, style = stroke
                )
            }
        }
    }
}

// ===== 标签管理 =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagManagePage(vm: SettingsViewModel = viewModel()) {
    val ctx = LocalContext.current
    val tags by vm.tags.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<AccTagEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AccTagEntity?>(null) }
    var usageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(deleteTarget) {
        usageCount = deleteTarget?.let { vm.countByTag(it.name) } ?: 0
    }

    Column {
        tags.forEach { t ->
            val tc = Color(t.color.toInt())
            val dark = isDarkTheme
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .glassSurface(cardShape(CardRadius.ROW_TIGHT), dark = isDarkTheme, ring = true, elevation = 5.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // 5.8.0：标签名自己变成那颗颜色的胶囊（淡底 + 同色字 + 色点），
                    // 取代原来 28dp 的圆角色块——色块是最后一个没胶囊化的形状。
                    // 标签色板本身偏浅（交通是黄绿、送礼是淡紫），同色字压在淡底上会读不出，
                    // 所以文字色往「主文字色」方向混一档，圆点保持原色。
                    val pillInk = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
                    val pillText = lerp(tc, pillInk, if (dark) 0.42f else 0.45f)
                    val pillDot = if (dark) lerp(tc, Color.White, 0.18f) else lerp(tc, Color.Black, 0.10f)
                    Row(
                        Modifier.clip(CircleShape)
                            .background(tc.copy(alpha = if (dark) 0.18f else 0.16f))
                            .padding(start = 9.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(pillDot))
                        Text(t.name, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = pillText,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    // 改 = 中性灰、删 = 红：深色主题下 primary 与 error 都是红，两个圆钮会长得一模一样
                    TagActionGlyph(TagActionIcon.Edit, MaterialTheme.colorScheme.onSurfaceVariant) { editing = t }
                    Spacer(Modifier.width(6.dp))
                    TagActionGlyph(TagActionIcon.Delete, MaterialTheme.colorScheme.error) { deleteTarget = t }
                }
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("添加标签") }
    }

    // 「已被其它标签占用」的颜色：新增时是所有标签用过的色，修改时要排除这个标签自己那颗
    // （否则打开修改，自己当前的颜色也会变成灰的，像是没选中）
    val allUsedColors = remember(tags) { tags.map { it.color }.toHashSet() }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        // 默认选第一个**还没被占用**的颜色，省得每次都要手动挑
        var color by remember(allUsedColors) {
            mutableStateOf((TAG_COLORS.firstOrNull { it !in allUsedColors } ?: TAG_COLORS.first()).toLong())
        }
        TagEditorDialog("添加标签", name, color, allUsedColors,
            onName = { name = it }, onColor = { color = it },
            onConfirm = { vm.addTag(name.trim(), color); showAdd = false },
            onDismiss = { showAdd = false })
    }

    editing?.let { tag ->
        var name by remember(tag.id) { mutableStateOf(tag.name) }
        var color by remember(tag.id) { mutableStateOf(tag.color) }
        TagEditorDialog("修改标签", name, color, allUsedColors - tag.color,
            onName = { name = it }, onColor = { color = it },
            onConfirm = {
                val newName = name.trim()
                // ★ 7.5.1（审查 P1-F）：改名必须挡住重名。账目/常用记账是按**标签名字符串**关联的，
                //   两条同名标签一改名就撞在一起：账目分不清属于哪个、删一个会把另一个的也清掉、
                //   饼图两档颜色互相覆盖。新增那边（addTag）早就有同名合并，改名这边一直漏着。
                // ★ 7.5.5（复核报告 2.6）：查重同样走归一（尾随空格 / 连续空格 / 英文大小写不敏感）
                val dup = tags.firstOrNull { it.id != tag.id && tagKey(it.name) == tagKey(newName) }
                if (newName.isBlank()) {
                    toast(ctx, "标签名不能为空")
                } else if (dup != null) {
                    toast(ctx, "已经有叫「$newName」的标签了")
                } else {
                    vm.updateTag(tag, newName, color)
                    editing = null
                }
            },
            onDismiss = { editing = null })
    }

    deleteTarget?.let { t ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { deleteTarget = null },
            compactButtons = true,
            title = { Text("删除标签「${t.name}」？") },
            text = if (usageCount > 0) {
                { Text("有 $usageCount 笔账目正在使用这个标签，删除后这些账目会变成无标签。") }
            } else null,
            confirmButton = {
                ShixuDialogButton(
                    stringResource(R.string.action_delete),
                ) { vm.deleteTag(t); deleteTarget = null }
            },
            dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel)) { deleteTarget = null } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    title: String, name: String, color: Long, usedColors: Set<Long>,
    onName: (String) -> Unit, onColor: (Long) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                ShixuField(value = name, onValueChange = onName, label = "标签名",
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("颜色：", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                TagColorPicker(color, usedColors) { onColor(it) }
            }
        },
        confirmButton = { ShixuDialogButton(stringResource(R.string.action_save)) { if (name.isNotBlank()) onConfirm() } },
        dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) })
}

/**
 * 标签颜色选择器：24 色平铺、每行 6 个（缝宽与命中区见 [Swatch]），**不写色系名** —— 直接看颜色挑（用户 2026-10-14）。
 *
 * 2026-11-01 用户口径（第三次修订，以此为准）：
 * 「标签管理页中已经用过的颜色就不用再显示了」——
 * 已被其它标签占用的颜色**整颗隐藏**，不再"变暗排到后面"。
 * 于是挑色时看到的每一格都是「现在就能用」的，一眼可选，不用先辨认哪格是灰的。
 * 例外只有两个：① 当前标签自己的颜色要留着（否则打开修改看不到自己那颗、也取消不了高亮）；
 * ② 24 色全被占满时退回显示全部（总得让人能选，并给一行说明）。
 */
@Composable
private fun TagColorPicker(selected: Long, usedColors: Set<Long>, onPick: (Long) -> Unit) {
    // 老标签用的是旧色板的颜色（不在 24 色里）→ 顶上单独放一格它的原色 + 「当前」，
    // 否则打开修改会一个圈都不亮，像是颜色丢了。
    val legacy = !TagPalette.isInPalette(selected)
    // 选中那颗自己不算「被占用」，否则打开修改时自己那格会被隐藏
    val used = usedColors - selected
    val available = TAG_COLORS.filterNot { it in used }
    val shown = available.ifEmpty { TAG_COLORS }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (legacy) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(selected, Color(selected).luminance() > 0.45f, true, onPick)
                Text("当前", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ★ 2026-11 审查 6.4（三个毛病一起修）：
        //   ① 一串 28dp 的命中区，而且因为「相邻只隔 5dp，扩到 48dp 会盖住隔壁」连扩都不敢扩 ——
        //      手指点红色却选中了橙色；
        //   ② 8 × 28 + 7 × 5 = 259dp，比正文可用宽度（≈239dp）还宽，最右那颗被卡片边缘裁成一条竖片；
        //   ③ 选中只靠一圈描边，浅色块上那圈几乎看不见，等于没有选中态。
        //   改：每行 6 颗、单格 39 × 44dp（**整格都是命中区**，邻格之间不留缝，也就不存在盖住谁），
        //   里面的色块仍是 26dp 居中 —— 看起来还是原来那排小方块，指头好点了、也不会被裁。
        // 格宽按可用宽度均分（而不是写死 39dp）：写死的话右边会剩一截空白，
        // 左边贴边、右边空 13dp，整块网格看着偏左。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellW = maxWidth / 6
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.chunked(6).forEach { row ->
                    Row {
                        row.forEach { c ->
                            Swatch(c, Color(c).luminance() > 0.45f, c == selected, onPick, cellW)
                        }
                    }
                }
            }
        }
        if (available.isEmpty()) {
            Text("颜色已用满", fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

/**
 * 单格色块：命中区 [39dp × 44dp]，里面画 26dp 的圆角色块。
 *
 * 亮色块用黑描边/黑勾、深色块用白描边/白勾；选中除了描边还多一枚对勾 ——
 * 光靠一圈描边在浅色块上几乎看不出来（审查 6.4 第三条）。
 */
@Composable
private fun Swatch(
    color: Long, light: Boolean, on: Boolean, onPick: (Long) -> Unit,
    cellWidth: Dp = 39.dp,
) {
    val ring = if (light) Color.Black.copy(alpha = .78f) else Color.White.copy(alpha = .92f)
    val tick = if (light) Color.Black.copy(alpha = .85f) else Color.White.copy(alpha = .95f)
    Box(
        Modifier
            .size(cellWidth, 44.dp)
            // 命中区就是整格：邻格之间没有缝隙，所以不会出现「扩了热区就盖住隔壁」
            .selectable(
                selected = on,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onPick(color) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(26.dp)
                .aaFill(smoothShape(8.dp, G3_SMOOTHING), Color(color))
                .then(if (on) Modifier.border(2.5.dp, ring, smoothShape(8.dp, G3_SMOOTHING)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (on) {
                Text("✓", fontSize = 12.sp, color = tick, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 自动备份设置页。
 *
 * 用户 2026-11-01 要求（在一次误卸载把数据清空之后）：改任何一项条目都自动更新备份。
 * 这一页只做三件事：开关、显示上次备份时间与位置、以及一个「立即备份一次」。
 */
@Composable
private fun AutoBackupPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    var lastAt by remember { mutableLongStateOf(0L) }
    var where by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = AutoBackup.enabled()
        lastAt = AutoBackup.lastAt()
        where = AutoBackup.lastWhere()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()
            .glassSurface(cardShape(CardRadius.ROW), dark = isDarkTheme, ring = true, elevation = 6.dp)) {
            Column {
                SwitchRow("自动备份", enabled, divider = false, onToggle = { on ->
                    enabled = on
                    scope.launch { AutoBackup.setEnabled(on); msg = if (on) "已开启" else "已关闭" }
                })
                Text(
                    if (lastAt > 0) "上次备份：${DateUtils.formatDateCn(lastAt)} ${DateUtils.formatTime(lastAt)}"
                    else "上次备份：还没有备份过",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // ★ 7.5.1（审查 4.6）：把「本机副本」和「卸载后仍在的共享副本」分开说清楚。
                //   旧版不管写到哪里都只说「上次备份：刚刚」，用户以为是卸载安全的。
                if (where.isNotBlank()) {
                    Text("位置：$where（卸载重装后仍在）", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                } else if (lastAt > 0) {
                    Text("只写到了 App 私有目录：卸载 / 清数据会一起消失",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }
        }

        Button(onClick = {
            scope.launch {
                busy = true
                val ok = runCatching { AutoBackup.backupNow(ctx) }.getOrDefault(false)
                lastAt = AutoBackup.lastAt()
                where = AutoBackup.lastWhere()
                busy = false
                msg = when {
                    !ok -> "备份没成功（或自动备份已关闭）"
                    where.isBlank() -> "已备份到本机私有目录，但共享目录没写成（卸载会丢）"
                    else -> "已备份到现在"
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "备份中…" else "立即备份一次")
        }

        msg?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp))
        }

        Box(Modifier.fillMaxWidth()
            .glassSurface(cardShape(CardRadius.ROW), dark = isDarkTheme, ring = true, elevation = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("备份放在哪", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("· 下载 / 时序集备份 / 时序集_自动备份.json（最新一份，每次改动覆盖）",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // ★ 文件名可能带 (2)(3) 这种后缀：MediaStore 的行是「谁建谁写」，重装换了 uid
                //   之后旧名字就再也写不进（见 data/AutoBackup.writeLatest 的长注释），App 会换一个
                //   自己能写的名字。所以这里不能说死「就是 时序集_自动备份.json」，
                //   真实名字以上面那行「位置：…」为准。
                Text("· 重装过的话可能叫 时序集_自动备份(2).json，以「位置」那行为准",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("· 同目录还有每天一份的快照，留 10 天",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("· 只有上面这个共享文件能扛住卸载：App 私有目录里的副本会跟着被清掉",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===== 支出明细（饼图：有标签按标签分，没标签的并成「其他」） =====
/**
 * 一个扇区：名称（标签名或「其他」）、金额（分）、颜色、以及这一档里的每一笔。
 *
 * 2026-10-25 用户要求：饼图下方的每个条目可以点击，弹窗看这一档的每一笔消费
 * （"这个月吃饭到底花在哪几笔"）。所以切片要把账目本身带出来，不能只留一个合计。
 */
/**
 * 甜甜圈圆心可用的文本宽度（dp）。外径 148dp、环宽 15.5% → 孔洞内径 ≈ 102dp，
 * 左右再各留 4dp 呼吸，超过这个宽度就必须缩字号（不许压在环上）。
 */
private val DONUT_TEXT_MAX_WIDTH = 94.dp

/** 字号档：从原大一路降到 0.42，最后一个档位兜底（再长就只能是它）。 */
private val DONUT_SCALES = listOf(1f, 0.88f, 0.78f, 0.69f, 0.60f, 0.52f, 0.45f, 0.40f)

/**
 * 甜甜圈圆心的金额（2026-11-07 建，2026-11-13 改）。
 *
 * 用户 2026-11-13：「数字应当保证在整个圆中，上下居中」。两条都落在本函数里：
 *   ① 「保证在整个圆中」—— 不再只按字符数猜档位（旧版 ≤6 位 1f / ≤9 位 0.80 / 否则 0.64，
 *      9 位带逗号其实已经顶到环上了）。改成用 [TextMeasurer] 实测三段文本的总宽，
 *      从 1f 往下试，取第一个真的放得进 [DONUT_TEXT_MAX_WIDTH] 的档位，绝不越出孔洞。
 *   ② 「上下居中」由调用方（Box contentAlignment = Center）保证：这里合成**一个**
 *      AnnotatedString 而不是三个 Text 拼 Row，高度就是一条行框，居中即墨迹居中；
 *      顺带 ¥ / 整数 / 小数共享同一条基线（旧版靠 alignByBaseline，字体缩放时容易错开）。
 */
@Composable
private fun DonutAmount(total: Long) {
    val full = formatCents(total).removePrefix("¥")
    val dot = full.lastIndexOf('.')
    val rawInt = if (dot >= 0) full.substring(0, dot) else full
    val decPart = if (dot >= 0) full.substring(dot) else ""
    // 千分位：formatCents 全 App 共用、不加逗号（账目行、导出 CSV 都依赖它），
    // 这里单独给圆心补上 —— 用户给的那张看板图上就是「¥1,286.50」的写法。
    val intPart = rawInt.reversed().chunked(3).joinToString(",").reversed()

    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val maxPx = with(LocalDensity.current) { DONUT_TEXT_MAX_WIDTH.toPx() }
    // 只跟「内容 + 可用宽度」有关，颜色不进 key（不然切深浅色会重算一遍）
    val scale = remember(intPart, decPart, maxPx) {
        DONUT_SCALES.firstOrNull { s ->
            measurer.measure(donutAmountText(intPart, decPart, s, Color.Black)).size.width <= maxPx
        } ?: DONUT_SCALES.last()
    }
    Text(
        donutAmountText(intPart, decPart, scale, ink),
        softWrap = false,
        maxLines = 1,
        color = ink,
    )
}

/** 把「¥ + 整数 + 小数」拼成一段带不同字号的富文本（不再靠 Row + alignByBaseline）。 */
private fun donutAmountText(
    intPart: String,
    decPart: String,
    s: Float,
    ink: Color
) = buildAnnotatedString {
    withStyle(
        SpanStyle(
            fontSize = (11f * s).sp, fontWeight = FontWeight.SemiBold,
            fontFamily = songtiFamily, color = ink.copy(alpha = 0.72f)
        )
    ) { append("¥") }
    withStyle(
        SpanStyle(
            fontSize = (22f * s).sp, fontWeight = FontWeight.Bold,
            fontFamily = songtiFamily, letterSpacing = (-0.4).sp, color = ink
        )
    ) { append(intPart) }
    if (decPart.isNotEmpty()) {
        withStyle(
            SpanStyle(
                fontSize = (11.5f * s).sp, fontWeight = FontWeight.SemiBold,
                fontFamily = songtiFamily, color = ink.copy(alpha = 0.78f)
            )
        ) { append(decPart) }
    }
}

/** 饼图色块之间的分隔白线宽度（用户 2026-11-02 选定的「方案 A」：2dp，圆心到边缘） */
private val PIE_DIVIDER_WIDTH = 2.dp

private class PieSlice(
    val name: String, val cents: Long, val color: Color,
    val items: List<Account> = emptyList()
)

@Composable
private fun ExpensePiePage(vm: SettingsViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val tags by vm.tags.collectAsState(initial = emptyList())
    val today = DateUtils.todayCal()
    var y by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var m0 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    // 点开某一档看明细（用户 2026-10-25：饼图下方每个条目可点击）
    var detailSlice by remember { mutableStateOf<PieSlice?>(null) }

    // 只统计「支出」（type==0）；收入不进饼图
    val monthOut = remember(accounts, y, m0) {
        val s = DateUtils.monthStartMs(y, m0)
        val e = DateUtils.monthEndMs(y, m0)
        accounts.filter { it.type == 0 && it.date in s..e }
    }
    val total = monthOut.sumOf { it.amountCents }
    // 按标签分组：有标签 → 名字=标签名、颜色=标签色（标签被删过对不上色时退灰）；
    // 没标签 → 全部并成「其他」（灰点同账目页的无标签灰）。
    val slices = remember(monthOut, tags) {
        val tagColor = tags.associate { it.name to Color(it.color.toInt()) }
        monthOut.groupBy { it.tag.trim() }
            .map { (tag, list) ->
                // 每档内部按时间倒序（最近一笔在最上面），和图例的「大额在前」互不影响
                val items = list.sortedByDescending { it.date }
                if (tag.isEmpty()) PieSlice("其他", list.sumOf { it.amountCents }, Color(TagPalette.UNLABELED), items)
                else PieSlice(tag, list.sumOf { it.amountCents }, tagColor[tag] ?: Color(0xFF75878A), items)
            }
            .filter { it.cents > 0 }
            // 有标签的按金额从大到小，「其他」永远排最后（垫底参考，不跟标签抢位）
            .sortedWith(compareBy<PieSlice> { it.name == "其他" }.thenByDescending { it.cents })
    }

    Column {
        // 月份切换：和账单总览同款 ‹ X年X月 ›
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChevronButton(ChevronDir.Left, onClick = {
                val (ny, nm) = DateUtils.addMonths(y, m0, -1); y = ny; m0 = nm
            }, box = 28.dp, chevron = 12.5.dp, stroke = 1.2.dp, color = MaterialTheme.colorScheme.onSurface,
                shiftX = 3.dp, contentDescription = "上个月")
            Text("${y}年${m0 + 1}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            ChevronButton(ChevronDir.Right, onClick = {
                val (ny, nm) = DateUtils.addMonths(y, m0, 1); y = ny; m0 = nm
            }, box = 28.dp, chevron = 12.5.dp, stroke = 1.2.dp, color = MaterialTheme.colorScheme.onSurface,
                shiftX = (-3).dp, contentDescription = "下个月")
        }
        Spacer(Modifier.height(16.dp))

        if (total <= 0L) {
            Text("本月没有支出记录", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), textAlign = TextAlign.Center)
        } else {
            // ── 2026-11-07 按用户给的看板图重画 ──
            // 实心饼 → 甜甜圈；圆心放「支出 + 金额」；图例从下方竖排挪到右侧。
            // 顶上那行「本月支出合计 ¥xxx」随圆心信息合并，删掉。
            // 环宽取直径的 15.5%（用户那张图的比例），色块之间不再画白线 —— 原图就是直接相接。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.minDimension * 0.155f
                        val r = (size.minDimension - sw) / 2f
                        val tl = Offset((size.width - 2 * r) / 2f, (size.height - 2 * r) / 2f)
                        // 口径：0 = 3 点方向，-90 = 12 点，一圈终点是 270（不是 360）。
                        // ★ 最后一片必须用 270 收尾。写 360f - acc 会多画 90°，盖掉第一片的三分之一。
                        var acc = -90f
                        slices.forEachIndexed { i, s ->
                            val sweep =
                                if (i == slices.lastIndex) 270f - acc
                                else s.cents.toFloat() / total.toFloat() * 360f
                            drawArc(
                                color = s.color, startAngle = acc, sweepAngle = sweep,
                                useCenter = false, topLeft = tl, size = Size(2 * r, 2 * r),
                                style = Stroke(sw)
                            )
                            acc += sweep
                        }
                    }
                    // 2026-11-13 用户：「圆环处直接去掉圆环中的支出二字」——
                    //   圆心只留金额。上一版把「支出」浮到上方 30dp 是为了让数字能居中；
                    //   现在这层浮层整个删掉，数字就是圆心的唯一内容（本来就以圆心居中）。
                    DonutAmount(total)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    // 图例：色点 + 名称 + 金额 + 百分比（用户 2026-11-07 选方案 B：留金额）
                    // 每行可点 → 弹窗看这一档的每一笔（2026-10-25 的既有行为，不动）
                    slices.forEach { s ->
                        val pct = s.cents.toDouble() / total.toDouble() * 100.0
                        Row(Modifier.fillMaxWidth()
                                .clip(cardShape(CardRadius.CELL))
                                .clickable { detailSlice = s }
                                .padding(vertical = 6.5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.5.dp).background(s.color, CircleShape))
                            Text(s.name, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 7.dp).weight(1f))
                            Text(formatCents(s.cents), fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                            Text("${Math.round(pct).toInt()}%", fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 5.dp).width(34.dp),
                                textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }

    // 某一档的明细：每一笔的备注 + 时间 + 金额（按时间倒序）
    detailSlice?.let { sl ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { detailSlice = null },
            title = { Text("${sl.name} · ${sl.items.size} 笔", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("合计 ${formatCents(sl.cents)}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    // 弹窗自带固定高度上限，条目多时内部滚动（不会把弹窗撑出屏幕）
                    LazyColumn(Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sl.items, key = { it.id }) { a ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(accountTitle(a), fontSize = 14.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(
                                        DateUtils.formatMonthDayCn(DateUtils.calFrom(a.date)) +
                                            " " + DateUtils.formatTime(a.date),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(formatCents(a.amountCents), fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { ShixuDialogTextButton(stringResource(R.string.action_close)) { detailSlice = null } }
        )
    }
}

// ===== 账单总览 =====
@Composable
private fun BillsOverviewPage(vm: SettingsViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val today = DateUtils.todayCal()
    var y by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var m0 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var fmt by remember { mutableStateOf("csv") }
    var range by remember { mutableStateOf("month") }   // month / year / all / custom
    var cy1 by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var cm1 by remember { mutableIntStateOf(0) }
    var cy2 by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var cm2 by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }

    val monthAcc = remember(accounts, y, m0) {
        val s = DateUtils.month(y, m0).timeInMillis
        val e = DateUtils.endOfDayMs(DateUtils.month(y, m0).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(y, m0)) }.timeInMillis)
        accounts.filter { it.date in s..e }.sortedByDescending { it.date }
    }

    // 导出区间
    val (exStart, exEnd) = when (range) {
        "year" -> {
            val s = DateUtils.todayCal().apply { set(y, 0, 1) }.timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.todayCal().apply { set(y, 11, 31) }.timeInMillis)
            s to e
        }
        "all" -> 0L to Long.MAX_VALUE
        "custom" -> {
            val s = DateUtils.month(cy1, cm1).timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.month(cy2, cm2).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(cy2, cm2)) }.timeInMillis)
            s to e
        }
        else -> {
            val s = DateUtils.month(y, m0).timeInMillis
            val e = DateUtils.endOfDayMs(DateUtils.month(y, m0).apply { set(Calendar.DAY_OF_MONTH, DateUtils.daysInMonth(y, m0)) }.timeInMillis)
            s to e
        }
    }
    val exportAcc = remember(accounts, range, exStart, exEnd) {
        accounts.filter { it.date in exStart..exEnd }.sortedByDescending { it.date }
    }
    val exportName = when (range) {
        "year" -> "时序集账单_${y}年"
        "all" -> "时序集账单_全部"
        "custom" -> "时序集账单_${cy1}年${cm1 + 1}月-${cy2}年${cm2 + 1}月"
        else -> "时序集账单_${y}年${m0 + 1}月"
    }

    var pendingText by remember { mutableStateOf<String?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(if (fmt == "csv") "text/csv" else "text/plain")
    ) { uri ->
        val text = pendingText
        if (uri != null && text != null) {
            try {
                val r = writeTextToUri(ctx, uri, text)
                toast(ctx, if (r == WriteOutcome.OK) "已导出 ${exportAcc.size} 条记录" else "导出失败：${writeOutcomeText(r, "")}")
            } catch (e: Exception) {
                toast(ctx, "导出失败：${e.message}")
            }
        }
        pendingText = null
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChevronButton(ChevronDir.Left, onClick = {
                val (ny, nm) = DateUtils.addMonths(y, m0, -1); y = ny; m0 = nm
            }, box = 28.dp, chevron = 12.5.dp, stroke = 1.2.dp, color = MaterialTheme.colorScheme.onSurface,
                shiftX = 3.dp, contentDescription = "上个月")
            Text("${y}年${m0 + 1}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            ChevronButton(ChevronDir.Right, onClick = {
                val (ny, nm) = DateUtils.addMonths(y, m0, 1); y = ny; m0 = nm
            }, box = 28.dp, chevron = 12.5.dp, stroke = 1.2.dp, color = MaterialTheme.colorScheme.onSurface,
                shiftX = (-3).dp, contentDescription = "下个月")
        }
        val total = monthAcc.sumOf { if (it.type == 1) it.amountCents else -it.amountCents }
        // 合计是这一页唯一的「结论」，字号比条目大一档、加粗，扫一眼就能拿走
        Text(
            "当月合计：${formatCents(total)}",
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        // 按天分组（最近的在最上面）
        val byDay = remember(monthAcc) {
            monthAcc.groupBy { DateUtils.calAt(it.date).get(Calendar.DAY_OF_MONTH) }
                .toSortedMap(compareByDescending { it })
        }
        byDay.forEach { (day, list) ->
            // 日期行原来是 14sp 粗体，和下面条目同名同重 —— 三行文字平铺，看不出
            // 「日期是分组、条目是一笔」。改成小号灰标签 + 右侧当天小计（与搜索页的
            // 日期分隔行同一个口径），下面的条目才抬成正文。
            val daySum = list.sumOf { if (it.type == 1) it.amountCents else -it.amountCents }
            Row(
                // 组内要贴近、组间要拉开：日期的下边距归 0，条目只留 2dp，
                // 而组与组之间靠 top = 14dp 分家（原来 12/4 + 条目 3dp，组内 29dp 反而比组间还挤）
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${m0 + 1}月${day}日", fontSize = 11.5.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f))
                // 当天只有一笔时不重复写一遍金额（上面条目那行就在下面，读起来像打了两遍）
                if (list.size > 1) {
                    Text(formatCents(daySum), fontSize = 11.5.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f))
                }
            }
            list.forEach { acc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    // 条目界面不显示时间（用户要求）；具体时刻仍能在账目详情弹窗和导出文件里看到
                    Text(accountTitle(acc), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(formatCents(acc.amountCents),
                        fontSize = 15.sp, color = if (acc.type == 1) incomeGreen else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (byDay.isEmpty()) LabelText("本月暂无账目", 13, MaterialTheme.colorScheme.onSurfaceVariant)

        Text("导出范围：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("month" to "本月", "year" to "今年", "all" to "全部", "custom" to "自定义").forEach { (k, label) ->
                ShixuChip(selected = range == k, onClick = { range = k }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        if (range == "custom") {
            MonthStepper("从", cy1, cm1, { cy1 = it }, { cm1 = it })
            MonthStepper("到", cy2, cm2, { cy2 = it }, { cm2 = it })
        }
        Text("导出格式：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("csv" to "CSV", "txt" to "文本").forEach { (k, label) ->
                ShixuChip(selected = fmt == k, onClick = { fmt = k }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        Button(onClick = {
            if (exportAcc.isEmpty()) { toast(ctx, "该范围内没有账目"); return@Button }
            pendingText = buildAccountExport(exportAcc, fmt)
            createLauncher.launch("$exportName.${if (fmt == "csv") "csv" else "txt"}")
        }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(if (exportAcc.isEmpty()) "导出文件（0 条）" else "导出文件（${exportAcc.size} 条）")
        }
    }
}

@Composable
private fun MonthStepper(
    label: String, y: Int, m0: Int, onYear: (Int) -> Unit, onMonth: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        ChevronButton(ChevronDir.Left, onClick = {
            val (ny, nm) = DateUtils.addMonths(y, m0, -1); onYear(ny); onMonth(nm)
        }, box = 25.dp, chevron = 10.5.dp, stroke = 1.1.dp, corner = 8.dp, shiftX = 3.dp, contentDescription = "上个月")
        Text("${y}年${m0 + 1}月", fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        ChevronButton(ChevronDir.Right, onClick = {
            val (ny, nm) = DateUtils.addMonths(y, m0, 1); onYear(ny); onMonth(nm)
        }, box = 25.dp, chevron = 10.5.dp, stroke = 1.1.dp, corner = 8.dp, shiftX = (-3).dp, contentDescription = "下个月")
    }
}

/**
 * CSV 单元格转义：含逗号/引号/换行时用双引号包裹并转义内部引号。
 *
 * ★ 7.5.1（审查 4.11）：用户可控文本（标题、标签）以 `= + - @` 开头时，
 *   Excel / LibreOffice / Numbers 会把整格当**公式**执行（CSV 注入）——
 *   `=HYPERLINK("http://…","点我")` 打开表格就变成一个可点的链接。
 *   这里按通行做法在危险前缀前加一个单引号（表格软件把它当「强制文本」）。
 *   金额列由程序生成（`+12.34` / `-12.34`），不经这个函数，免得金额被转成文本没法合计。
 */
internal fun csvCell(v: String, guardFormula: Boolean = true): String {
    // ★ 7.5.5（复核报告 2.11）：不能只看第 0 个字符 —— " =1+1"、全角空格开头、BOM 开头、
    //   制表/回车开头都能绕过前缀检查。先跳过前导空白与不可见字符，看第一个「有内容」的字符。
    val head = if (guardFormula) v.indexOfFirst { !it.isWhitespace() && it != '\uFEFF' && it != '\u00A0' && it != '\u3000' } else -1
    val risky = head >= 0 && when (v[head]) {
        '=', '+', '-', '@' -> true
        else -> false
    }
    val guarded = if (risky) "'$v" else v
    val s = guarded.replace("\"", "\"\"")
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$s\"" else s
}

/** 生成账单导出文本（CSV 或文本）。CSV 带 BOM，Excel 打开中文不乱码 */
private fun buildAccountExport(acc: List<Account>, fmt: String): String {
    val sb = StringBuilder()
    if (fmt == "csv") {
        sb.append('\uFEFF')
        sb.append("日期,时间,标题,标签,金额\n")
        acc.forEach { a ->
            val c = DateUtils.calAt(a.date)
            sb.append(csvCell("${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}")).append(",")
                .append(csvCell(DateUtils.formatTime(a.date))).append(",")
                .append(csvCell(accountTitle(a))).append(",")
                .append(csvCell(a.tag)).append(",")
                .append(csvCell((if (a.type == 1) "+" else "-") + centsToYuanString(a.amountCents), guardFormula = false)).append("\n")
        }
    } else {
        acc.forEach { a ->
            val sign = if (a.type == 1) "+" else "-"
            sb.append("${DateUtils.formatDateCn(a.date)}  ${DateUtils.formatTime(a.date)}  ")
                .append("[${a.tag}]  ${accountTitle(a)}  $sign${centsToYuanString(a.amountCents)} 元\n")
        }
    }
    return sb.toString()
}

// ===== 备份 / 恢复 =====
@Composable
private fun BackupPage(vm: SettingsViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // ★ 不再把拼好的 JSON 暂存在 remember 里等文件选择器回来（2026-11-08 审计 E 项）。
    //   进选择器时 Activity 可能被系统回收：回来时 composable 重建、`remember` 里的文本变 null，
    //   回调里就静默什么都不写 —— 用户点了「导出备份文件」、选好位置、提示也没弹，文件是空的/没生成。
    //   现在回调用 vm.buildBackupJson() 现场重拼一份（几百 KB 的拼装，IO 线程里几毫秒）。
    var suggestedName by remember { mutableStateOf("时序集备份.json") }
    /** true = 这次导出只是「某个动作的副产物」（如恢复前自动备份），成功与否说法不同 */
    var isAutoBackup by remember { mutableStateOf(false) }
    /** 点「从备份文件恢复」后先做自动备份，备份流程收尾时再弹确认框 */
    var wantRestore by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    /** 已经读进来、检查通过、等着用户确认的那份备份 */
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingSummary by remember { mutableStateOf("") }

    /** 真正落库。成功后提示条要说明「设置已即时生效」，见 MainScreen 里监听偏好表那段 */
    fun runRestore(json: String) {
        if (busy) return
        busy = true
        scope.launch {
            // ★ 恢复前先自己留一份（2026-11-08 审计 G 项）：用户刚才那次「另存为」可能存到了一个
            //   找不回来的目录，也可能选错文件之后才发现。这里再写一份到下载/时序集备份/ 下，
            //   文件名带「恢复前_时间戳」，不会被每日快照的 10 份上限挤掉（前缀不同，见 AutoBackup）。
            // ★ 7.5.1（审查 4.3）：恢复前快照「有数据却没写成功」时**不许恢复**。
            //   旧版忽略返回值，用户以为留了后路，其实没有。
            val snap = withContext(Dispatchers.IO) { AutoBackup.snapshotBeforeRestore(ctx) }
            if (snap == AutoBackup.SnapshotResult.FAILED) {
                toast(ctx, "恢复已取消：恢复前的保护性备份没能写成")
                busy = false
                return@launch
            }
            when (val n = vm.restoreFromJson(json)) {
                -2 -> toast(ctx, "这份备份来自更新的版本，请先升级 App")
                -1 -> toast(ctx, "文件格式不正确")
                -3 -> toast(ctx, "文件内容损坏，已保持原数据不变")
                -4 -> toast(ctx, "写入失败，数据未被修改")
                else -> toast(ctx, "已恢复 $n 条记录")
            }
            busy = false
        }
    }

    fun stampName(): String {
        val c = DateUtils.calAt(System.currentTimeMillis())
        val p = { v: Int -> v.toString().padStart(2, '0') }
        return "时序集备份_${c.get(Calendar.YEAR)}${p(c.get(Calendar.MONTH) + 1)}${p(c.get(Calendar.DAY_OF_MONTH))}" +
            "_${p(c.get(Calendar.HOUR_OF_DAY))}${p(c.get(Calendar.MINUTE))}${p(c.get(Calendar.SECOND))}.json"
    }

    /** 把 json 存到外部（用户自己挑位置），用于「导出备份」按钮 */
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        isAutoBackup = false
        // 自动备份这一环结束 → 该弹恢复确认框了
        if (wantRestore) { wantRestore = false; confirmRestore = true }
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 现场重拼：不依赖进选择器之前算好的那份（Activity 被回收就没了）
            val text = withContext(Dispatchers.IO) { vm.buildBackupJson() }
            var failed = false
            val r: WriteOutcome = try {
                withContext(Dispatchers.IO) { writeTextToUri(ctx, uri, text) }
            } catch (e: Exception) {
                failed = true
                toast(ctx, "备份失败：${e.message}")
                WriteOutcome.FAILED
            }
            if (!failed) toast(ctx, writeOutcomeText(r, "备份已保存").let { if (r == WriteOutcome.OK) it else "备份失败：$it" })
        }
    }

    /**
     * 「分享备份」：先把 json 写进自己的 cacheDir/backup_share/，再用 FileProvider 生成
     * content:// uri 交给系统分享面板（微信 / 邮件 / 网盘 / 蓝牙都能收）。
     * 比「导出备份文件」更省事：不用在文件选择器里找目录。
     */
    fun shareBackup(json: String) {
        try {
            val dir = java.io.File(ctx.cacheDir, "backup_share").apply { mkdirs() }
            // 清掉上一次的，免得 cache 里越堆越多
            dir.listFiles()?.forEach { it.delete() }
            val f = java.io.File(dir, stampName())
            f.writeText(json, Charsets.UTF_8)
            // authority 与 Manifest 一起用包名拼接：加 flavor / suffix 后仍然匹配
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "时序集备份")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "分享备份文件"))
        } catch (e: Exception) {
            toast(ctx, "分享失败：${e.message}")
        }
    }

    /**
     * 恢复前的自动备份：先写进自己的 cacheDir，再用 FileProvider 交给系统「另存为」。
     * 不能用 CreateDocument 直接拿 uri 写——没有权限；也不能静默写外部存储。
     * 结果就是：点一次恢复，会先让你选个地方存当前数据，存完才问「确认恢复？」。
     */
    val autoBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            // 用户取消了自动备份 → 不往下走，避免他还没备份就被覆盖
            wantRestore = false; isAutoBackup = false
            toast(ctx, "已取消")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val text = withContext(Dispatchers.IO) { vm.buildBackupJson() }
            // ★ 7.5.1：写不成就**不许往下走**。旧版这里同样用 `?.use{}`，流为 null 也返回 true，
            //   于是「恢复前备份没写成」却照样弹确认框，用户一点确认原数据就没了。
            val r: WriteOutcome = try {
                withContext(Dispatchers.IO) { writeTextToUri(ctx, uri, text) }
            } catch (e: Exception) {
                toast(ctx, "自动备份失败：${e.message}")
                WriteOutcome.FAILED
            }
            isAutoBackup = false
            // ★ 7.5.5（复核报告 2.3）：恢复前的备份是「后悔药」，这里只认逐字节校验通过。
            //   UNVERIFIABLE（系统不让回读）也**不放行** —— 宁可让用户换个位置存，也不能
            //   在他以为「已备份」的情况下覆盖掉原数据。
            if (r == WriteOutcome.OK) {
                toast(ctx, "已自动备份当前数据")
                confirmRestore = true
            } else {
                wantRestore = false
                toast(ctx, "恢复已取消：当前数据没能可靠备份下来（${writeOutcomeText(r, "")}）")
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                // ★ 7.5.3（审查 6.7）：读取加上限。旧版 `readText()` 会把这个文件整个读进内存 ——
                //   用户在用系统选择器时点错一个几百 MB 的视频/压缩包，就是一次 OOM 崩溃（或长时间卡死）。
                //   超过上限直接拒绝并说明，不尝试解析。
                val read = withContext(Dispatchers.IO) { readTextCapped(ctx.contentResolver, uri, MAX_BACKUP_BYTES) }
                val text = read.text
                if (read.tooLarge) {
                    toast(ctx, "这个文件超过 ${MAX_BACKUP_BYTES / (1024 * 1024)} MB，不像是时序集的备份")
                } else if (text.isBlank()) {
                    toast(ctx, "读取文件失败")
                } else {
                    // 先只**检查**文件（不碰数据库）：把里面有什么念给用户听，确认后才真恢复。
                    // 检查不过就直接拒绝 —— 老实现是边解析边写库，坏文件会把库清一半。
                    when (val r = vm.inspectBackup(text)) {
                        is BackupCodec.Result.Bad -> toast(ctx, "这份备份不能用：${r.why}")
                        is BackupCodec.Result.Ok -> {
                            pendingJson = text
                            pendingSummary = r.data.summary()
                        }
                    }
                }
                busy = false
            }
        }
    }

    Column {

        Button(onClick = {
            if (busy) return@Button
            busy = true
            busy = false
            isAutoBackup = false
            suggestedName = stampName()
            createLauncher.launch(suggestedName)
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "处理中…" else stringResource(R.string.backup_export))
        }

        OutlinedButton(onClick = {
            if (busy) return@OutlinedButton
            busy = true
            scope.launch {
                shareBackup(vm.buildBackupJson())
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.backup_share))
        }
        // 分享这条路径最容易泄数据（可能顺手发进群里）：文件是**明文** JSON、含账目金额，
        // 这里明确说一句。缓存副本（cacheDir/backup_share）下次分享时会被清掉，也提一下。

        OutlinedButton(onClick = {
            // 先备份、再恢复：这一串走完才弹「确认恢复？」
            if (busy) return@OutlinedButton
            busy = true
            busy = false
            isAutoBackup = true
            wantRestore = true
            suggestedName = "恢复前自动备份_" + stampName().removePrefix("时序集备份_")
            autoBackupLauncher.launch(suggestedName)
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "处理中…" else stringResource(R.string.backup_restore))
        }

    }

    if (confirmRestore) {
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { confirmRestore = false },
            compactButtons = true,
            title = { Text("确认恢复？") },
            text = { Text("会用备份文件替换当前全部数据，不可撤销。") },
            confirmButton = {
                ShixuDialogButton("选择文件") {
                    confirmRestore = false
                    openLauncher.launch(arrayOf("application/json", "*/*"))
                }
            },
            dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel)) { confirmRestore = false } })
    }

    // 选定文件、检查通过后的**第二次确认**：把这份备份里到底有什么念清楚再动手
    //（原来选完文件直接覆盖，用户完全不知道选中的是不是自己要的那一份）。
    pendingJson?.let { json ->
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { pendingJson = null },
            compactButtons = true,
            title = { Text("将恢复这份备份") },
            text = {
                Text(pendingSummary, fontSize = 13.sp)
            },
            confirmButton = {
                ShixuDialogButton("恢复") { pendingJson = null; runRestore(json) }
            },
            dismissButton = {
                ShixuDialogTextButton(stringResource(R.string.action_cancel)) {
                    pendingJson = null
                    toast(ctx, "已取消")
                }
            })
    }
}

/**
 * 把一段文本写进用户用 SAF 选出来的文件。
 *
 * ★ 7.5.1（审查 4.3）：**流为 null 必须当失败**。
 *   旧写法 `openOutputStream(uri)?.use { … }` 在返回 null 时什么也不做，
 *   调用方却照样 `true` / 弹「已导出」——用户以为备份好了，其实一个字节都没写。
 *   恢复流程尤其致命：恢复前的保护副本没写成，照样弹「确认恢复」，确认后旧数据就没了。
 *
 * @return true = 确实写成功（且回读到非空）；false = 没写成（调用方必须按失败处理）
 */
/**
 * 备份文件读取上限：32 MB。
 *
 * 真实备份有多大？本机两年账目 + 待办也就几百 KB，20 MB 已经是极端情况。
 * 设上限只是防「选错文件」—— 不是防正常备份。
 */
internal const val MAX_BACKUP_BYTES = 32L * 1024 * 1024

/** 带大小上限的文本读取结果 */
internal data class CappedText(val text: String, val tooLarge: Boolean)

/** 带大小上限的文本读取：[tooLarge] 为 true 时 [text] 是空串（一个字节都不解析） */
internal fun readTextCapped(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    maxBytes: Long,
): CappedText {
    val ins = resolver.openInputStream(uri) ?: return CappedText("", false)
    return ins.use { input ->
        val buf = ByteArray(64 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return@use CappedText("", true)
            out.write(buf, 0, n)
        }
        CappedText(String(out.toByteArray(), Charsets.UTF_8), false)
    }
}

/**
 * 标签名的**比较键**：首尾空白（含全角空格 U+3000）去掉、连续空白压成一个、英文不分大小写。
 *
 * 只用于判重，不改用户看到的显示名 —— 账目/常用记账是按名字符串关联标签的，
 * 归一后的名字写进库里会让老账目变孤儿。
 */
internal fun tagKey(raw: String): String =
    raw.trim().replace(Regex("[\\s\\u3000]+"), " ").lowercase()

/** 写出去的结果。7.5.5（复核报告 2.3）：不再用「回读非空」冒充成功，逐字节比对。 */
private enum class WriteOutcome {
    /** 写出去的内容读回来与预期**逐字节一致** */
    OK,
    /** 读回来了，但对不上（截断 / 被别的进程改过 / provider 返回旧内容） */
    MISMATCH,
    /** 文件已提交，但系统不允许回读（少数 provider）——这时不能说「校验通过」 */
    UNVERIFIABLE,
    /** 连写入通道都没拿到 / 写的过程抛异常 */
    FAILED,
}

/**
 * 把文本写进用户选的文件。
 *
 * ★ 7.5.5（复核报告 2.3）：旧版的成功判据是「`available()` > 0」——两处都不可靠：
 *   ① `available()` 在很多 provider 上恒返回 0（真实文件也可能 0），等于把失败当成功；
 *   ② 就算读得到，也只知道「有内容」，不知道是不是**这次要写的那份**（provider 可能返回旧内容）。
 *   现在改成：写完 close → 重新打开整个读回来 → 与预期字节**逐字节**比对。
 *   回读不了也单独成一个结果，由调用方决定怎么跟用户说（导出可以容忍，恢复前备份不能）。
 */
private fun writeTextToUri(ctx: android.content.Context, uri: android.net.Uri, text: String): WriteOutcome {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val out = runCatching { ctx.contentResolver.openOutputStream(uri) }.getOrNull() ?: return WriteOutcome.FAILED
    runCatching { out.use { it.write(bytes); it.flush() } }.onFailure { return WriteOutcome.FAILED }
    val back = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            val buf = java.io.ByteArrayOutputStream(bytes.size.coerceAtLeast(8 * 1024))
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(chunk)
                if (n <= 0) break
                buf.write(chunk, 0, n)
                // 读得比写的还多，肯定对不上，不必继续占内存
                if (buf.size() > bytes.size) return@use null
            }
            buf.toByteArray()
        }
    }.getOrNull() ?: return WriteOutcome.UNVERIFIABLE
    return if (back.contentEquals(bytes)) WriteOutcome.OK else WriteOutcome.MISMATCH
}

/** 把写入结果翻译成一句人话（导出这类「丢了还能再导」的场景用） */
private fun writeOutcomeText(r: WriteOutcome, okText: String): String = when (r) {
    WriteOutcome.OK -> okText
    WriteOutcome.MISMATCH -> "写入的内容和预期不一致，这个位置可能被占用，请换个位置再试"
    WriteOutcome.UNVERIFIABLE -> "文件已写出，但系统不允许回读校验，请打开文件确认内容"
    WriteOutcome.FAILED -> "目标文件没有写进去"
}

private fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

// ===== 月度预算 =====
@Composable
private fun BudgetPage(vm: SettingsViewModel = viewModel()) {
    var text by remember { mutableStateOf("") }
    var savedCents by remember { mutableLongStateOf(-1L) }
    // 金额非法时给红字（原来 parseYuanToCents 返回 null 就什么都不做，连提示都没有）
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cur = vm.budgetCents()
        savedCents = cur
        text = if (cur > 0) centsToYuanString(cur) else ""
    }

    Column {
        ShixuField(
            value = text, onValueChange = { text = it; error = null },
            label = "每月预算（元）", singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        error?.let {
            Text(it, fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = {
            val cents = if (text.isBlank()) 0L else parseYuanToCents(text)
            if (cents != null) {
                vm.setBudget(cents)
                savedCents = cents
                error = null
            } else {
                error = "请输入有效金额（如 12.50）"
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(stringResource(R.string.action_save)) }
        if (savedCents > 0) {
            LabelText("当前预算：${formatCents(savedCents)}", 14, MaterialTheme.colorScheme.primary)
        } else if (savedCents == 0L) {
            LabelText("当前未设置预算", 13, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== 常用记账 =====
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryPage(undo: UndoController, vm: SettingsViewModel = viewModel()) {
    val list by vm.quickEntries.collectAsState(initial = emptyList())
    val tags by vm.tags.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Column {
        list.forEach { q ->
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .glassSurface(cardShape(CardRadius.ROW), dark = isDarkTheme, ring = true, elevation = 5.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (q.tag.isNotBlank()) {
                        Text(q.tag, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp))
                    }
                    Text(formatCents(q.amountCents), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 84.dp))
                    Text("删", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            undo.show("已删除常用记账：${q.title}") { vm.restoreQuick(q) }
                            vm.deleteQuick(q.id)
                        }.padding(8.dp))
                }
            }
        }
        Button(onClick = { showAdd = true }, enabled = list.size < 6,
            modifier = Modifier.fillMaxWidth()) { Text(if (list.size >= 6) "最多 6 个" else "添加常用记账") }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        // 7.7.2：与「记一笔」同一套 —— 带光标的金额状态（点框可把光标挪到中间）
        var amountField by remember { mutableStateOf(AmountField.Empty) }
        val amount = amountField.text
        var isIncome by remember { mutableStateOf(false) }
        var tag by remember { mutableStateOf("") }
        // 名称空 / 金额非法时给一句红字：原来只 if (...) 静默不落地，用户以为保存键坏了
        var error by remember { mutableStateOf<String?>(null) }
        // ★ 7.7.9：与「记一笔」同一套输入模式（详见 [AccountInputMode]）——
        //   这一页的弹窗里有个真正的文本框（名称），它获焦时把自绘键盘收起来让给系统输入法；
        //   失焦不再自动把键盘弹回来。
        var inputMode by remember { mutableStateOf(AccountInputMode.Amount) }
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ShixuCompactDialog(
            shape = GlassDialogShape,
            containerColor = glassDialogContainer(),
            onDismissRequest = { showAdd = false },
            title = { Text("添加常用记账") },
            // 7.7.5：与「记一笔」统一，都用默认 276dp（键盘已不在弹窗里，无需加宽）
            shadowPadH = 15.dp,
            shadowPadV = 56.dp,
            // 7.7.9：让位量跟随模式，其余档交给真实 IME inset（见 DialogPlacement）
            bottomClearance = if (inputMode == AccountInputMode.Amount)
                KEYPAD_PANEL_HEIGHT + navInset + 12.dp else 0.dp,
            bottomOverlay = {
                BottomKeypadPanel(
                    visible = inputMode == AccountInputMode.Amount,
                    navInset = navInset,
                    onInsert = { k -> amountField = AmountCaret.insert(amountField, k); error = null },
                    onBackspace = { amountField = AmountCaret.backspace(amountField); error = null },
                    onClear = { amountField = AmountCaret.clear(); error = null },
                )
            },
            text = {
                // 在 Dialog 内部读（Dialog window 才是备注/名称框的 focus owner）
                val fm = LocalFocusManager.current
                val kb = LocalSoftwareKeyboardController.current
                val density = LocalDensity.current
                // 组合期取出 inset 对象本身（`WindowInsets.ime` 是 @Composable 访问器，
                // 不能写进 snapshotFlow 的 block 里）
                val imeInsets = WindowInsets.ime
                // 7.7.9：安全交接 —— hide() 只是请求，等 IME inset 真的归零再把键盘放回来
                LaunchedEffect(inputMode) {
                    if (inputMode != AccountInputMode.SwitchingToAmount) return@LaunchedEffect
                    withTimeoutOrNull(700) {
                        snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
                    }
                    if (inputMode == AccountInputMode.SwitchingToAmount) {
                        inputMode = AccountInputMode.Amount
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShixuField(value = title, onValueChange = { title = it; error = null },
                        label = "名称", singleLine = true, isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                        // 7.7.9：名称获焦 → Note 模式（键盘让位给 IME）；失焦不再重开键盘
                        innerModifier = Modifier.onFocusChanged {
                            if (it.isFocused) inputMode = AccountInputMode.Note
                        })
                    // ★ 7.5.3（审查 6.2 / 6.1）：和「记一笔」用同一套 —— 只读显示框 + 自绘小键盘，
                    //   保存时走 amountToCents（支持 128/3、199×0.85 这类算式），不再是纯数字解析。
                    AmountDisplay(
                        text = amount, label = "金额", isError = error != null,
                        isActive = inputMode == AccountInputMode.Amount,
                        caret = amountField.caret,
                        onCaretChange = {
                            amountField = AmountCaret.moveTo(amountField, it)
                            if (inputMode != AccountInputMode.Amount) {
                                // 7.7.9：先收 IME，inset 归零后再显示自绘键盘
                                inputMode = AccountInputMode.SwitchingToAmount
                                fm.clearFocus(force = true)
                                kb?.hide()
                            }
                        }
                    )
                    error?.let {
                        Text(it, fontSize = DialogText.Hint, color = MaterialTheme.colorScheme.error)
                    }
                    // ★ 7.6.10（用户）：「常用记账界面的收支、标签的顺序请改成记账界面的样子」。
                    //   记账界面（记一笔）的次序是 金额 → 收支 → 备注 → 标签 → 日期 → 键盘，
                    //   也就是**收支与标签都在键盘之上**；这里原来把键盘夹在金额和收支之间，
                    //   于是同一件事在两个弹窗里落在不同的高度上。现按记账界面的次序排：
                    //   名称 → 金额 → 收支 → 标签 → 键盘。
                    //   芯片一并改成与记账界面同规格：compact + 「标签」不带「（可选）」+ 前置色点。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShixuChip(selected = !isIncome, onClick = { isIncome = false }, compact = true,
                            label = { Text(stringResource(R.string.acc_expense)) })
                        ShixuChip(selected = isIncome, onClick = { isIncome = true }, compact = true,
                            label = { Text(stringResource(R.string.acc_income)) })
                    }
                    if (tags.isNotEmpty()) {
                        Text("标签", fontSize = DialogText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { t ->
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
                    // 7.7.3：20 键搬到 Dialog 底部的面板里（bottomOverlay），正文到这里结束
                }
            },
            confirmButton = {
                // 7.6.7：与「记一笔」同一套按钮（等宽胶囊、同一底色），不再是一颗裸文字按钮
                ShixuDialogButton(stringResource(R.string.action_save)) {
                    val cents = amountToCents(amount)
                    when {
                        title.isBlank() -> error = "请输入名称"
                        cents == null -> error = (AmountExpr.evaluate(amount) as? AmountExpr.Eval.Bad)
                            ?.kind?.message ?: "金额写错了"
                        else -> {
                            vm.addQuick(QuickEntry(title = title.trim(), amountCents = cents,
                                type = if (isIncome) 1 else 0, tag = tag, sortOrder = list.size))
                            showAdd = false
                        }
                    }
                }
            },
            dismissButton = { ShixuDialogTextButton(stringResource(R.string.action_cancel)) { showAdd = false } }
        )
    }
}
