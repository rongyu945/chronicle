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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.data.AppPref
import com.rongyu.shixuji.theme.BgLight
import com.rongyu.shixuji.theme.ShixuJiTheme
import com.rongyu.shixuji.ui.*
import kotlinx.coroutines.launch

enum class ShixuTab(val label: String, val iconRes: Int) {
    TODO("待办", R.drawable.ic_tab_todo),
    ACCOUNT("账目", R.drawable.ic_tab_wallet),
    MODEL("模型", R.drawable.ic_tab_model),
    SETTINGS("设置", R.drawable.ic_tab_settings)
}

val DEFAULT_TAB_ORDER = listOf("待办", "账目", "模型", "设置")

/** 字号档位 */
val FONT_SCALES = listOf("小" to 0.9f, "标准" to 1.0f, "大" to 1.15f)

@Composable
fun MainScreen() {
    val prefDao = AppContainer.database.appPrefDao()
    val loopDao = AppContainer.database.loopTodoDao()
    val scope = rememberCoroutineScope()

    // 持久化设置状态
    var loaded by remember { mutableStateOf(false) }
    // 默认宋体（用户要求）；若设置里存过开关，下面读偏好时会覆盖
    var isSongti by remember { mutableStateOf(true) }
    var isDark by remember { mutableStateOf<Boolean?>(null) }   // null=跟随系统
    var tabOrder by remember { mutableStateOf(DEFAULT_TAB_ORDER) }
    var fontScale by remember { mutableStateOf(1.0f) }

    // 启动页仅早晚出现：用首帧派生，非早晚直接进主界面，避免 splash 空窗口/白闪
    val isSplashTime = remember {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        (h in 0..6) || (h in 21..23)
    }
    var splashDone by remember { mutableStateOf(!isSplashTime) }

    // 首次读取设置 + 循环待办按周重置
    LaunchedEffect(Unit) {
        prefDao.get("songti")?.let { isSongti = it == "1" }
        prefDao.get("dark")?.let { isDark = when (it) { "1" -> true; "0" -> false; else -> null } }
        prefDao.get("font_scale")?.let { s -> s.toFloatOrNull()?.let { fontScale = it } }
        prefDao.get("tab_order")?.let { raw ->
            val parsed = raw.split(",").filter { it.isNotBlank() }
            if (parsed.size == 4) tabOrder = parsed
        }
        // 循环待办：进入新的一周就清零，否则"每周循环"永远只生效一次
        val thisWeek = DateUtils.startOfWeek(DateUtils.todayCal()).timeInMillis.toString()
        if (prefDao.get("loop_week") != thisWeek) {
            loopDao.resetAllDone()
            prefDao.put(AppPref("loop_week", thisWeek))
        }
        loaded = true
    }

    // 偏好读完之前不渲染主界面：避免"先按默认样式画一帧、再跳成宋体/深色"的闪烁
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color(0xFF0E0F12) else BgLight))
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
            val thisWeek = DateUtils.startOfWeek(DateUtils.todayCal()).timeInMillis.toString()
            if (prefDao.get("loop_week") != thisWeek) {
                loopDao.resetAllDone()
                prefDao.put(AppPref("loop_week", thisWeek))
            }
        }
    }

    val orderedTabs: List<ShixuTab> = remember(tabOrder) {
        val m = tabOrder.mapNotNull { label -> ShixuTab.entries.find { it.label == label } }
        if (m.size == 4) m else ShixuTab.entries.toList()
    }
    // 默认页 = 底栏第一个（设置里可调）
    var selected by remember { mutableStateOf(orderedTabs.first()) }
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
            Box(
                Modifier.fillMaxSize().then(
                    if (blurPx > 0.4f) Modifier.blur(blurPx.dp) else Modifier
                )
            ) {
                // 底层柔光色斑：玻璃卡片要有东西可透
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
                                        onOpenAccount = { selected = ShixuTab.ACCOUNT },
                                        todayTick = todayTick
                                    )
                                    ShixuTab.ACCOUNT -> AccountScreen(todayTick = todayTick)
                                    ShixuTab.MODEL -> ModelBoardScreen()
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
                                            selected = ShixuTab.entries.find { it.label == newOrder.first() } ?: ShixuTab.TODO
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 浮起来的玻璃胶囊底栏
                GlassBottomBar(
                    items = orderedTabs.map { TabItem(it.label, it.iconRes) },
                    selectedIndex = orderedTabs.indexOf(selected),
                    onSelect = { i -> selected = orderedTabs[i] },
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
