package com.rongyu.shixuji

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.withTransaction
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.data.AppPref
import com.rongyu.shixuji.data.LoopSchedule
import com.rongyu.shixuji.theme.BgLight
import com.rongyu.shixuji.theme.ShixuJiTheme
import com.rongyu.shixuji.ui.*
import kotlinx.coroutines.launch
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug
import com.rongyu.shixuji.ui.motion.MotionDebugOverlay
import com.rongyu.shixuji.ui.motion.SequentialTabContent
import com.rongyu.shixuji.ui.motion.motionSpec


enum class ShixuTab(val label: String, val iconRes: Int) {
    TODO("待办", R.drawable.ic_tab_todo),
    ACCOUNT("账目", R.drawable.ic_tab_wallet),
    SETTINGS("设置", R.drawable.ic_tab_settings)
}

val DEFAULT_TAB_ORDER = listOf("待办", "账目", "设置")

/**
 * 外部入口（长按图标的快捷方式）传进来的 action，见 res/xml/shortcuts.xml。
 *
 * 系统点快捷方式时只是把 action 塞进 intent 打开 MainActivity，主机这边解析成
 * 「切到哪一页 / 要不要直接弹录入框」。action 字符串必须与 shorts.xml 里逐字一致。
 */
object AppActions {
    const val ADD_ACCOUNT = "com.rongyu.shixuji.action.ADD_ACCOUNT"
    const val ADD_TODO = "com.rongyu.shixuji.action.ADD_TODO"
    const val TAB_ACCOUNT = "com.rongyu.shixuji.action.TAB_ACCOUNT"
    const val TAB_SETTINGS = "com.rongyu.shixuji.action.TAB_SETTINGS"
}

/** 字号档位 */
val FONT_SCALES = listOf("小" to 0.9f, "标准" to 1.0f, "大" to 1.15f)

/**
 * 解析「底栏顺序」偏好。返回 null = 存档不合法，调用方用 [DEFAULT_TAB_ORDER]。
 *
 * 必须先按**当前** Tab 集合过滤再校验数量：历史存档里可能还带着已经删掉的「模型」
 * （底栏曾经是四项），不过滤就永远对不上、只能回落默认。
 * 纯函数，可单测（MainScreenTest）。
 */
internal fun parseTabOrder(raw: String): List<String>? {
    val parsed = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val known = parsed.filter { lb -> ShixuTab.entries.any { it.label == lb } }.distinct()
    return if (known.size == ShixuTab.entries.size) known else null
}

/**
 * @param initialPrefs 启动时同步读到的界面设置（MainActivity 传进来）。有了它，
 *   首帧就能按用户设的明暗/宋体/字号/底栏顺序渲染 —— 否则会先按默认画一帧
 *   （深色冷启动时那一帧是浅色，就是"闪白"）。传 null（读不到）时行为和以前一样：
 *   先铺一块跟随系统的色底，等 Room 读完再进主界面。
 */
@Composable
fun MainScreen(
    initialPrefs: Map<String, String>? = null,
    /** 长按图标快捷方式带来的待处理 action；消费后由这里写回 null */
    pendingAction: MutableState<String?>? = null
) {
    val prefDao = AppContainer.database.appPrefDao()
    val loopDao = AppContainer.database.loopTodoDao()
    val scope = rememberCoroutineScope()

    // 持久化设置状态。初值优先用启动时同步读到的那份（见 initialPrefs 说明），
    // 读不到才退回默认值 —— 这样首帧就是对的，不会"先默认再跳"。
    var loaded by remember { mutableStateOf(initialPrefs != null) }
    // 默认宋体（用户要求）；若设置里存过开关，下面读偏好时会覆盖
    var isSongti by remember { mutableStateOf(initialPrefs?.get("songti")?.let { it == "1" } ?: true) }
    var isDark by remember {
        mutableStateOf<Boolean?>(
            when (initialPrefs?.get("dark")) { "1" -> true; "0" -> false; else -> null }
        )
    }   // null=跟随系统
    var tabOrder by remember {
        mutableStateOf(initialPrefs?.get("tab_order")?.let { parseTabOrder(it) } ?: DEFAULT_TAB_ORDER)
    }
    var fontScale by remember {
        mutableStateOf(initialPrefs?.get("font_scale")?.toFloatOrNull() ?: 1.0f)
    }
    // 撤回球停留时长（毫秒）。夹在设置页滑块的范围内，存档里写了离谱的值也不会把提示条卡住/一闪就没。
    // 欢迎页三个问候时段的分界（2026-11-11）。同步读到的初始值直接当首帧用，
    // 免得欢迎页先按默认 0-7 判断一次、再跳成用户设的时间。
    var greetingTimes by remember { mutableStateOf(GreetingTimes.fromPrefs(initialPrefs)) }
    // 「问候时段」页里点「预览」时置位，由下面顶层那层铺满整屏画欢迎页（见那里的注释）。
    var previewGreetingKind by remember { mutableStateOf<Int?>(null) }
    var undoMs by remember {
        mutableStateOf(
            (initialPrefs?.get("undo_ms")?.toIntOrNull() ?: UNDO_LIFE_DEFAULT_MS)
                .coerceIn(UNDO_MIN_SEC * 1000, UNDO_MAX_SEC * 1000)
        )
    }

    // 欢迎页只在三个问候时段出现：用首帧派生，其余时间直接进主界面，避免空窗口/白闪。
    // 阈值来自设置（GreetingTimes），不再写死 0-6 / 21-23。
    fun greetingActiveNow(t: GreetingTimes): Boolean {
        val cal = java.util.Calendar.getInstance()
        return t.greetingAt(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)) != null
    }

    // ★ 5.8（审查）：首帧分三态 —— 同步读到偏好 → 立刻定；**读失败（initialPrefs == null）先不下结论**。
    //   旧版拿默认时段算一次 splashDone，随后异步读到真实偏好只更新了 greetingTimes，
    //   那个已经定下的 splashDone 不会重算 —— 该进欢迎页的可能直接进主界面（或反之）。
    //   现在读失败时 splashDone 保持 false，等异步读完（早于 `loaded = true`）再定，
    //   而「未加载」那段本来就只画底色，所以不会闪。
    val prefsSynced = initialPrefs != null
    var splashDone by remember {
        mutableStateOf(prefsSynced && !greetingActiveNow(GreetingTimes.fromPrefs(initialPrefs)))
    }

    /**
     * 循环待办：把「下一次提醒」追到今天。
     *
     * 5.5.0 起循环待办不再按「每周几」+ 每周清零，而是「开始日期 + 下一次提醒日期」推出间隔、
     * 之后按间隔成序列出现。日子走过之后要把 nextDate 推到最近的一次 ——
     * 推的是**整倍数间隔**，所以提醒日序列不变、已勾选的记录也不会错位。
     *
     * 没有直接算好日期再查库，而是每次启动/每分钟扫一遍（条数是个位数），
     * 免得「App 开着跨过零点」那天该出现的提醒不出现（旧版靠 60s 轮询兜过同类问题）。
     *
     * 注意：局部函数必须声明在使用之前（Kotlin 不允许在前面的 lambda 里引用后面的局部函数）。
     */
    suspend fun advanceLoops() {
        val today = DateUtils.startOfToday().timeInMillis
        val all = loopDao.getAll()
        if (all.isEmpty()) return
        AppContainer.database.withTransaction {
            all.forEach { l ->
                LoopSchedule.advanceNext(l, today)?.let { next -> loopDao.setNextDate(l.id, next) }
            }
        }
    }

    // 首次读取设置 + 循环待办按周重置
    LaunchedEffect(Unit) {
        prefDao.get("songti")?.let { isSongti = it == "1" }
        prefDao.get("dark")?.let { isDark = when (it) { "1" -> true; "0" -> false; else -> null } }
        prefDao.get("font_scale")?.let { s -> s.toFloatOrNull()?.let { fontScale = it } }
        prefDao.get("tab_order")?.let { raw -> parseTabOrder(raw)?.let { tabOrder = it } }
        prefDao.get("undo_ms")?.toIntOrNull()?.let {
            undoMs = it.coerceIn(UNDO_MIN_SEC * 1000, UNDO_MAX_SEC * 1000)
        }
        greetingTimes = GreetingTimes.fromPrefs(
            buildMap { GreetingTimes.PREF_KEYS.forEach { k -> prefDao.get(k)?.let { put(k, it) } } }
        )
        // 同步读失败过：现在用真实偏好补上首帧判断（此时还没 loaded，界面只有底色）
        if (!prefsSynced) splashDone = !greetingActiveNow(greetingTimes)
        advanceLoops()
        loaded = true
    }

    /**
     * 偏好表被**外部整体改写**（恢复备份会把界面设置一起换掉）时同步界面状态。
     *
     * 这几项原本只在启动时读一次，恢复完备份得重启 App 才生效 —— 用户看到的
     * 是「恢复成功了，但字号/深色/底栏顺序还是老的」。这里监听偏好表补上。
     * 第一次发射与上面那次读取重复，忽略。
     */
    LaunchedEffect(Unit) {
        var first = true
        prefDao.observeAll().collect { rows ->
            if (first) { first = false; return@collect }
            val m = rows.associate { it.key to it.value }
            // ⚠️ 这几行必须**带默认值**（缺键 = 回默认），不能只在键存在时才赋值：
            //    恢复备份时会先把 PREF_KEYS 整组删掉再写文件里带的项 —— 文件里没写 dark/songti 等
            //    就表示「那一刻是默认外观」。只在键存在时赋值的写法会让旧值留下来，
            //    与「备份 = 那一刻的完整状态」的约定相反（用户看到的是恢复了备份、外观却没变）。
            isSongti = m["songti"]?.let { it == "1" } ?: true
            isDark = when (m["dark"]) { "1" -> true; "0" -> false; else -> null }
            fontScale = m["font_scale"]?.toFloatOrNull() ?: 1.0f
            tabOrder = m["tab_order"]?.let { raw -> parseTabOrder(raw) } ?: DEFAULT_TAB_ORDER
            undoMs = (m["undo_ms"]?.toIntOrNull() ?: UNDO_LIFE_DEFAULT_MS)
                .coerceIn(UNDO_MIN_SEC * 1000, UNDO_MAX_SEC * 1000)
            greetingTimes = GreetingTimes.fromPrefs(m)
        }
    }

    // 偏好读完之前不渲染主界面：避免"先按默认样式画一帧、再跳成宋体/深色"的闪烁。
    // 底色同样按「用户设的明暗」而不是系统（seeded 生效时通常走不到这里）
    if (!loaded) {
        val preDark = isDark ?: isSystemInDarkTheme()
        Box(Modifier.fillMaxSize().background(if (preDark) Color(0xFF0E0F12) else BgLight))
        return
    }

    if (!splashDone) {
        NoPressFeedback { SplashScreen(times = greetingTimes, onEnter = { splashDone = true }) }
        return
    }

    // 跨午夜自动刷新"今天"（挂前台过夜时红圈不会停在昨天）
    //
    // ★ 5.7（审查）：旧版是 `delay(60_000)` 死循环，每分钟醒一次；而且它由组合生命周期管，
    //   Activity 停下但没销毁时协程仍在跑 —— 后台过夜照样每分钟唤醒一次。
    //   现在：①只在**前台**跑（repeatOnLifecycle STARTED，切后台即挂起）；
    //   ②直接睡到下一个本地午夜（[ui.msUntilNextMidnight]），不再每分钟轮询；
    //   ③回前台立刻重算一次，后台那段时间跨的天不会漏。
    var todayTick by remember { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // ★ 7.5.5（复核报告 3.1 / 3.5）：睡眠目标只在「时间自然流逝」时有效。
    //   手动改时间、换时区、系统校时之后，那个目标时刻可能已经过期（要再睡近一整天），
    //   所以把「时钟变了」做成一个令牌当 key —— 一变就重启循环、立刻重算 today 并追一次 nextDate。
    val clockToken = rememberClockChangeToken()
    LaunchedEffect(lifecycleOwner, clockToken) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                val now = DateUtils.todayCal().timeInMillis
                if (now != todayTick) {
                    todayTick = now
                    // ★ 循环待办只在**真正跨天**时追一次「下一次提醒」（advanceLoops 本身幂等）
                    advanceLoops()
                }
                kotlinx.coroutines.delay(msUntilNextMidnight())
            }
        }
    }

    val orderedTabs: List<ShixuTab> = remember(tabOrder) {
        val m = tabOrder.mapNotNull { label -> ShixuTab.entries.find { it.label == label } }
        if (m.size == ShixuTab.entries.size) m else ShixuTab.entries.toList()
    }
    // 默认页 = 底栏第一个（设置里可调）
    //
    // 用 rememberSaveable 存**Tab 名字**（不是序号）：进程被系统回收后再回来
    // 仍然停在原来那一页（原来只 remember，回来永远跳回第一个 Tab）。
    // 存名字而不是下标，是因为底栏顺序用户能改，下标会错位；名字不认识就回落第一个。
    var selectedName by rememberSaveable { mutableStateOf("") }
    val selected = remember(selectedName, orderedTabs) {
        orderedTabs.firstOrNull { it.name == selectedName } ?: orderedTabs.first()
    }
    // 方案 P0-6：Tab 切换是用户最常触发的动画，先给它一个日志锚点
    // （慢放时覆盖层能看到「谁 → 谁」，配合帧率判断是不是这一下掉帧）。
    // 7.7.0 起这里只记**请求**（用户点了哪个）；真正的换页过程由 SequentialTabContent
    // 记 tab_exit / tab_swap / tab_enter_end —— 排查快速连点时三者必须分开看。
    LaunchedEffect(selected) { MotionDebug.log("tab_req", selected.label) }

    // ── 长按图标的快捷方式 ──
    // 「记一笔 / 记待办」不只是切页，还要把那两个页面里的录入弹窗顶起来：
    // 这里只放一个一次性开关，由对应页面自己消费掉（页面内部知道怎么开自己的弹窗）。
    var openAddAccount by remember { mutableStateOf(false) }
    // 账目搜索页是否开着（AccountScreen 回报）。搜索页盖住整块内容区时，
    // 右上角的桃枝装饰会压到「取消」上，所以这一层要知道它开没开。
    var searchPageOpen by remember { mutableStateOf(false) }
    var openAddTodo by remember { mutableStateOf(false) }
    val action = pendingAction?.value
    LaunchedEffect(action) {
        when (action) {
            AppActions.ADD_ACCOUNT -> { openAddAccount = true; selectedName = ShixuTab.ACCOUNT.name }
            AppActions.ADD_TODO -> { openAddTodo = true; selectedName = ShixuTab.TODO.name }
            AppActions.TAB_ACCOUNT -> selectedName = ShixuTab.ACCOUNT.name
            AppActions.TAB_SETTINGS -> selectedName = ShixuTab.SETTINGS.name
            else -> return@LaunchedEffect          // 普通启动来的 MAIN action 不理
        }
        pendingAction?.value = null                // 消费掉，避免下次重组又弹一遍
    }
    val holder = rememberSaveableStateHolder()
    val dark = when (isDark) { true -> true; false -> false; null -> isSystemInDarkTheme() }

    ShixuJiTheme(useSongti = isSongti, darkTheme = dark) {
        // 字号档位：直接放大 fontScale，所有文字（含硬编码 fontSize）一起变，dp 尺寸不变
        val ld = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(ld.density, ld.fontScale * fontScale)
        ) {
          NoPressFeedback {
            // 弹窗打开 → 主界面实时模糊；松手恢复。安卓 12 以下自动不生效（退化成不模糊）
            val modalOpen = GlassModalState.isOpen
            // 【2026-11-06 修「弹窗弹出又卡又不连贯」】
            // 原来 blurPx 是 animateFloatAsState(0f → 15f, 220ms)：Modifier.blur() 走
            // RenderEffect，半径每变一点点就得重建 effect、把 1080×2400 的整块背景
            // 重新高斯一遍。录屏逐帧量化（screenrecord 60fps）：那 220ms 里帧间变化量
            // 一直顶在 2~3 万像素、中间还有整帧停顿（第 135 帧与上一帧完全相同的重复帧），
            // 峰值比静止时高两个数量级 —— 用户读到的就是「又卡又不连贯」。
            // 现在半径只在 0 ↔ 12dp 之间切一次，"背景退后"由遮罩的 alpha 渐变补
            // （纯合成，不值钱）；12dp 而非 15dp，省下的也是真实渲染时间。
            // 【只跳一次，不逐帧、也不分档】
            // 三轮实测（screenrecord 逐帧量化）：
            //   · 逐帧 0→15dp：每帧重建 RenderEffect 把整屏重糊一遍，220ms 里帧间变化一直顶在
            //     2~3 万像素、中途还有整帧停顿 → 用户说的"又卡又不连贯"。
            //   · 分三档（每档 55ms）：把一次大跳拆成三次，结果得到三次停顿，更糟。
            //   · 一刀到位：只有开头那一帧贵一次，之后的动画（遮罩、弹窗本体）全程没有停顿。
            // 所以取第三种：半径直接给 17dp，"背景沉下去"由遮罩 alpha（200ms）负责，
            // 弹窗本体 200ms 落位。代价是背景是"对焦"而不是"慢慢糊开"——换来的是不卡。
            // 【2026-11-08：10 → 17】卡的根因是**逐帧**改半径（每帧重建 RenderEffect 把
            // 整屏重糊），而这里只在 0 ↔ N 之间跳一次，所以 10dp 与 17dp 的代价完全相同
            // （都只贵开头那一帧）。10dp 在 1080 宽屏上后面的字还看得清，玻璃读成
            // 「半透明纸」；17dp 才真的失焦，才是 iOS 弹窗背后那个观感。
            val blurPx = if (modalOpen) 17f else 0f
            val dimAlpha by animateFloatAsState(
                targetValue = if (modalOpen) 0.24f else 0f,
                animationSpec = motionSpec(tween(200)),
                label = "modalDim"
            )
            // 液态玻璃背景层：只给底栏采样用。
            // ⚠️ 底栏必须写在挂了 recordBackdrop 的那层**外面**，否则会把自己也录进去 → 递归。
            val backdrop = rememberLiquidGlassBackdrop()
            // 2026-11-06：周条也要做成玻璃，但它就住在 recordBackdrop(backdrop) 那棵子树里
            // —— 拿 backdrop 采样等于采自己 → 递归。所以单独给「纸底」录一层：
            // 只有 ColorBlobsBackground，没有任何页面内容。周条身后本来也只有纸底
            // （上边是表头、下边是列表，都不跟它重叠），这层录出来正好是它该透的东西。
            val paperLayer = rememberLiquidGlassBackdrop()
            Box(Modifier.fillMaxSize()) {
                // 原来的内容层（弹窗打开时整体模糊）——只多了 .recordBackdrop()，
                // 它只是把这里画的东西额外录一份给底栏用，不改变任何渲染结果。
                // 【2026-11-08 修「背景其实根本没糊」】
                // blur 与 recordBackdrop 原先挂在**同一个**节点上
                // （Modifier.blur(...).recordBackdrop(...)），实测 blur 全程无效：
                // 把半径 10 → 17 → 60dp，截图上背景的高频能量（拉普拉斯标准差）
                // 分别是 20.36 / 20.26 / 20.25，与半径完全无关，只有遮罩压暗生效
                // —— 等于「玻璃背后是清晰的字」，玻璃读成一张半透明纸。
                // 现在拆成两层：外层只负责模糊，内层只负责录 backdrop
                // （录进去的是清晰内容，正好是底栏 shader 要的输入），互不干扰。
                Box(
                    Modifier.fillMaxSize().then(
                        if (blurPx > 0.4f) Modifier.blur(blurPx.dp) else Modifier
                    )
                ) {
                Box(Modifier.fillMaxSize().recordBackdrop(backdrop)) {
                    // 底层柔光色斑：玻璃要有东西可透
                    // 外面那层 recordBackdrop 只装纸底本身，专供周条采样。
                    Box(Modifier.fillMaxSize().recordBackdrop(paperLayer)) {
                        ColorBlobsBackground(dark)
                    }

                    CompositionLocalProvider(LocalPaperBackdrop provides paperLayer) {
                    Scaffold(containerColor = Color.Transparent, bottomBar = {}) { innerPadding ->
                        // ⚠️ 读 CompositionLocal 必须在 @Composable 作用域里 —— SequentialTabContent 内部
                        // 的 pointerInput / graphicsLayer lambda 都不是 @Composable，所以在这里读成布尔值传进去。
                        val reducedMotion = LocalReducedMotion.current
                        // 7.7.0：三页切换改为「串行纸面递换」（旧页退场 → 隐藏交接帧 → 新页入场）。
                        //
                        // 弃用 AnimatedContent 不是因为"它慢"，而是它的 togetherWith 让入场与离场成为
                        // 同一个 Transition 的两侧：两页从第 0ms 起**同时**绘制，必然出现「上一个还没
                        // 完全消失，下一个就过来了」。在本 App 上还多一层代价 —— 页面是透明的，玻璃卡
                        // 每帧 recordBackdrop 采样背板，两层同场时采到的是"两张纸"，玻璃质感会跟着崩。
                        // 把淡入往后挪只是缩短叠印窗口，不改变"两页同时在组合树里"这个事实，
                        // 所以直接换结构（实现见 ui/motion/TabHandoff.kt）。
                        SequentialTabContent(
                            selected = selected,
                            orderedTabs = orderedTabs,
                            stateHolder = holder,
                            reducedMotion = reducedMotion,
                            modifier = Modifier.padding(innerPadding)
                        ) { tab ->
                            // SaveableStateProvider 已由 SequentialTabContent 按 displayed 页包好
                            // （翻到哪一周 / 滚动位置 / 筛选都还在）。这里这层 Box 只为提供 BoxScope：
                            // content lambda 不是 BoxScope，PlumBranch 的 align 必须有它才生效。
                            Box(Modifier.fillMaxSize()) {
                                // 顶部右上角的桃花枝（2026-11-07 用户要的装饰）。
                                // 写在这里 = 三个 Tab 都有、都在页面内容**下面**（标题/图标浮在枝上）；
                                // 它是纯 Canvas、不消费触摸，点击照样穿透到底下的页面。
                                // 账目搜索页打开时把装饰收起来：右上角那枝正好压在「取消」上，
                                // 浅粉花瓣 + 浅色文字对比度掉到 3:1 以下，像没渲染完（2026-11-08 实测）。
                                if (!(tab == ShixuTab.ACCOUNT && searchPageOpen)) PlumBranch(
                                    flower = when (tab) {
                                        ShixuTab.TODO -> BranchFlower.TodoBlossom
                                        ShixuTab.ACCOUNT -> BranchFlower.AccountPlum
                                        ShixuTab.SETTINGS -> BranchFlower.SettingsOrchid
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                                when (tab) {
                                    ShixuTab.TODO -> TodoScreen(
                                        onOpenAccount = { selectedName = ShixuTab.ACCOUNT.name },
                                        todayTick = todayTick,
                                        openAdd = openAddTodo,
                                        onOpenAddHandled = { openAddTodo = false },
                                        undoMs = undoMs
                                    )
                                    ShixuTab.ACCOUNT -> AccountScreen(
                                        todayTick = todayTick,
                                        openAdd = openAddAccount,
                                        onOpenAddHandled = { openAddAccount = false },
                                        undoMs = undoMs,
                                        onSearchingChange = { searchPageOpen = it }
                                    )
                                    ShixuTab.SETTINGS -> SettingsScreen(
                                        isSongti = isSongti,
                                        isDark = isDark,
                                        tabOrder = tabOrder,
                                        fontScale = fontScale,
                                        undoMs = undoMs,
                                        greetingTimes = greetingTimes,
                                        // 注意：这些设置变更互不干扰，绝不能顺手改 selected，
                                        // 否则在设置页一动开关就会跳回第一个 Tab
                                        onToggleSongti = { v ->
                                            isSongti = v
                                            scope.launch { prefDao.put(AppPref("songti", if (v) "1" else "0")) }
                                        },
                                        onToggleDark = { d ->
                                            isDark = d
                                            scope.launch { prefDao.put(AppPref("dark", when (d) { true -> "1"; false -> "0"; null -> "2" })) }
                                        },
                                        onSetFontScale = { s ->
                                            fontScale = s
                                            scope.launch { prefDao.put(AppPref("font_scale", s.toString())) }
                                        },
                                        onSetUndoMs = { ms ->
                                            undoMs = ms.coerceIn(UNDO_MIN_SEC * 1000, UNDO_MAX_SEC * 1000)
                                            scope.launch { prefDao.put(AppPref("undo_ms", undoMs.toString())) }
                                        },
                                        onPreviewGreeting = { kind -> previewGreetingKind = kind },
                                        onSetGreetingTimes = { t ->
                                            greetingTimes = t
                                            scope.launch {
                                                GreetingTimes.toPrefs(t).forEach { (k, v) ->
                                                    prefDao.put(AppPref(k, v))
                                                }
                                            }
                                        },
                                        onApplyOrder = { newOrder ->
                                            tabOrder = newOrder
                                            scope.launch { prefDao.put(AppPref("tab_order", newOrder.joinToString(","))) }
                                            // 只有改底栏顺序时才切换默认页
                                            selectedName = (ShixuTab.entries.find { it.label == newOrder.first() } ?: ShixuTab.TODO).name
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
                }   // ← 模糊层（只包内容层，不含遮罩与底栏）

                // 弹窗打开时压一层暖棕：blur 半径是突变的，
                // 这层 alpha 渐变负责把它读成「背景沉下去」，而不是「画面闪了一下」。
                // 用暖棕不用纯黑 —— 纯黑压宣纸暖白会显脏（6.4.0 阴影那轮的同一个教训）。
                if (dimAlpha > 0.002f) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Color(0xFF3A2A18).copy(alpha = dimAlpha))
                    )
                }

                // 浮起来的玻璃胶囊底栏（在模糊层之外：图标清晰，背景被玻璃折射）
                //
                // 2026-11-06 对照 Apple 准则的两条修复落在这里：
                //   · P0-1 弹窗打开时底栏必须一起退场 —— 广播说背景模糊了、底栏却清楚可点，
                //     那是"看着像模态、其实不是模态"。Apple：modal 要把背景推后压暗。
                //   · P2-5 整理页有自己的「取消」，底栏等于第二条退出路径，还占着操作条的位置。
                // 两件事共用一套机制：ChromeState 里谁占台谁置位，这里只做淡出 + 轻微下移。
                val hideChrome = modalOpen || ChromeState.hidden
                val chromeAlpha by animateFloatAsState(
                    targetValue = if (hideChrome) 0f else 1f,
                    animationSpec = motionSpec(tween(if (hideChrome) 205 else 250)),
                    label = "chromeAlpha"
                )
                val chromeShift by animateDpAsState(
                    targetValue = if (hideChrome) 12.dp else 0.dp,
                    animationSpec = motionSpec(tween(if (hideChrome) 205 else 250)),
                    label = "chromeShift"
                )
                GlassBottomBar(
                    items = orderedTabs.map { TabItem(it.label, it.iconRes) },
                    selectedIndex = orderedTabs.indexOf(selected),
                    onSelect = { i -> selectedName = orderedTabs[i].name },
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                        .graphicsLayer {
                            alpha = chromeAlpha
                            translationY = chromeShift.toPx()
                        }
                        // 透明但仍然吃事件的底栏会"幽灵点击"，退场时把触摸整个吞掉
                        .then(
                            if (hideChrome) Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        e.changes.forEach { it.consume() }
                                    }
                                }
                            } else Modifier
                        )
                )

                // 7.4.1（方案 P0-6 / P3-6）：动效覆盖层。只在设置页长按「版本」打开慢放后出现；
                // 内容全是 Text，不消费触摸 —— 底下的页面照常可点。
                MotionDebugOverlay()

                // 欢迎页预览（7.5.0）。
                //
                // 为什么不放在「问候时段」页里用 Dialog 画：Dialog 是**独立窗口**，它的装饰区
                // 不含状态栏/导航栏，于是渐变只铺到状态栏下沿，顶上永远留一条深灰横带
                // （试过透明窗口底色 + 无暗幕 + FLAG_LAYOUT_NO_LIMITS 三种组合，
                // 1080×2400 上残留 62px 灰带，截图见 scratch/g750/preview3.png）。
                // 而主界面本身是 edge-to-edge 的，把预览放在这棵树的顶层就是天然的整屏。
                previewGreetingKind?.let { kind ->
                    NoPressFeedback {
                        SplashScreen(
                            times = greetingTimes,
                            previewKind = kind,
                            onEnter = { previewGreetingKind = null }
                        )
                    }
                }
            }
          }
        }
    }
}
