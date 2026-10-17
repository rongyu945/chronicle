package com.rongyu.shixuji

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.rongyu.shixuji.data.AppPref
import com.rongyu.shixuji.theme.BgLight
import com.rongyu.shixuji.theme.ShixuJiTheme
import com.rongyu.shixuji.ui.*
import kotlinx.coroutines.launch

enum class ShixuTab(val label: String, val iconRes: Int) {
    TODO("待办", R.drawable.ic_tab_todo),
    ACCOUNT("账目", R.drawable.ic_tab_wallet),
    SETTINGS("设置", R.drawable.ic_tab_settings)
}

val DEFAULT_TAB_ORDER = listOf("待办", "账目", "设置")

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
fun MainScreen(initialPrefs: Map<String, String>? = null) {
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

    // 启动页仅早晚出现：用首帧派生，非早晚直接进主界面，避免 splash 空窗口/白闪
    val isSplashTime = remember {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        (h in 0..6) || (h in 21..23)
    }
    var splashDone by remember { mutableStateOf(!isSplashTime) }

    /**
     * 循环待办按周清零 + 写周戳：两件事必须在**同一个事务**里。
     *
     * 拆成两次独立写入时，中间被中断（进程被杀 / 崩溃）会留下
     * 「已清零但周戳没写」→ 下次启动又清一遍（还只是重复劳动）；
     * 若顺序反过来（先写周戳再清零）中断就变成「整周都不再清零」——
     * 用户看到的是「每周循环的待办这周怎么不出现了」。放一个事务里就都不会发生。
     *
     * 注意：局部函数必须声明在使用之前（Kotlin 不允许在前面的 lambda 里引用后面的局部函数）。
     */
    suspend fun resetLoopIfNewWeek() {
        val thisWeek = DateUtils.startOfWeek(DateUtils.todayCal()).timeInMillis.toString()
        AppContainer.database.withTransaction {
            if (prefDao.get("loop_week") != thisWeek) {
                loopDao.resetAllDone()
                prefDao.put(AppPref("loop_week", thisWeek))
            }
        }
    }

    // 首次读取设置 + 循环待办按周重置
    LaunchedEffect(Unit) {
        prefDao.get("songti")?.let { isSongti = it == "1" }
        prefDao.get("dark")?.let { isDark = when (it) { "1" -> true; "0" -> false; else -> null } }
        prefDao.get("font_scale")?.let { s -> s.toFloatOrNull()?.let { fontScale = it } }
        prefDao.get("tab_order")?.let { raw -> parseTabOrder(raw)?.let { tabOrder = it } }
        resetLoopIfNewWeek()
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
            m["songti"]?.let { isSongti = it == "1" }
            m["dark"]?.let { d -> isDark = when (d) { "1" -> true; "0" -> false; else -> null } }
            m["font_scale"]?.let { s -> s.toFloatOrNull()?.let { fontScale = it } }
            m["tab_order"]?.let { raw -> parseTabOrder(raw)?.let { tabOrder = it } }
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
        NoPressFeedback { SplashScreen(onEnter = { splashDone = true }) }
        return
    }

    // 跨午夜自动刷新"今天"（挂前台过夜时红圈不会停在昨天）
    var todayTick by remember { mutableLongStateOf(DateUtils.todayCal().timeInMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            val now = DateUtils.todayCal().timeInMillis
            if (now != todayTick) todayTick = now
            // 循环待办按周重置：不只靠启动时那一次，App 一直挂后台跨过周一也要清零
            resetLoopIfNewWeek()
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
            val blurPx by animateFloatAsState(if (modalOpen) 15f else 0f, tween(220), label = "glassBlur")
            // 液态玻璃背景层：只给底栏采样用。
            // ⚠️ 底栏必须写在挂了 recordBackdrop 的那层**外面**，否则会把自己也录进去 → 递归。
            val backdrop = rememberLiquidGlassBackdrop()
            Box(Modifier.fillMaxSize()) {
                // 原来的内容层（弹窗打开时整体模糊）——只多了 .recordBackdrop()，
                // 它只是把这里画的东西额外录一份给底栏用，不改变任何渲染结果。
                Box(Modifier.fillMaxSize().then(
                        if (blurPx > 0.4f) Modifier.blur(blurPx.dp) else Modifier
                    ).recordBackdrop(backdrop)
                ) {
                    // 底层柔光色斑：玻璃要有东西可透
                    ColorBlobsBackground(dark)

                    Scaffold(containerColor = Color.Transparent, bottomBar = {}) { innerPadding ->
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                val from = orderedTabs.indexOf(initialState)
                                val to = orderedTabs.indexOf(targetState)
                                val dir = if (to >= from) 1 else -1
                                (slideInHorizontally(tween(260)) { w -> dir * w / 3 } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(260)) { w -> -dir * w / 3 } + fadeOut(tween(160)))
                            },
                            label = "tab"
                        ) { tab ->
                            // 用 SaveableStateHolder 保住每个 Tab 的界面状态（翻到哪一周/滚动位置等）
                            Box(Modifier.padding(innerPadding)) {
                                holder.SaveableStateProvider(tab.name) {
                                    when (tab) {
                                        ShixuTab.TODO -> TodoScreen(
                                            onOpenAccount = { selectedName = ShixuTab.ACCOUNT.name },
                                            todayTick = todayTick
                                        )
                                        ShixuTab.ACCOUNT -> AccountScreen(todayTick = todayTick)
                                        ShixuTab.SETTINGS -> SettingsScreen(
                                            isSongti = isSongti,
                                            isDark = isDark,
                                            tabOrder = tabOrder,
                                            fontScale = fontScale,
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

                // 浮起来的玻璃胶囊底栏（在模糊层之外：图标清晰，背景被玻璃折射）
                GlassBottomBar(
                    items = orderedTabs.map { TabItem(it.label, it.iconRes) },
                    selectedIndex = orderedTabs.indexOf(selected),
                    onSelect = { i -> selectedName = orderedTabs[i].name },
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                )
            }
          }
        }
    }
}
