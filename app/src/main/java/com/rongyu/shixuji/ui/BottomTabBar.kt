package com.rongyu.shixuji.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.rongyu.shixuji.R
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import com.rongyu.shixuji.ui.motion.MotionDebug
import com.rongyu.shixuji.ui.motion.motionSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import com.rongyu.shixuji.ui.motion.Motion

/** 一个底栏项：标题 + 自绘图标资源 */
data class TabItem(val label: String, val iconRes: Int)

/**
 * 底栏图标 = **直接从设计稿抠下来的位图**（res/drawable-nodpi/ic_tab_*.png）。
 *
 * 2026-11-06 用户给的 ChatGPT 设计稿里，三个图标是「实心主体 + 白色细节（勾 / 钱包扣 / 齿轮孔）」。
 * 用户要求白色部分**全部扣掉**：所以抠图时白色区域 alpha 直接留 0，
 * 这里只画一层主体、由 tint 上色 —— 勾和扣是镂空的，透出来的是底栏玻璃本身，
 * 深浅模式都跟着玻璃走，不再额外叠白色。
 */
@Composable
private fun TabIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    val size = (24 * TAB_BAR_SCALE).dp
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * 底栏整体**等比缩放系数**（唯一真源）。
 *
 * 用户 2026-10-16：「底栏有点大了，适当缩小一点，就是等比例缩小」→ 胶囊高度 / 圆钮 / 图标 /
 * 文字 / 文字行高 / 图标与文字间距全部由它派生，改这一个数就整体等比变。
 * 悬浮按钮的落点也跟着走（见 [FAB_CLEARANCE]），否则底栏矮下去后 ＋ / 今 会显得悬空。
 */
internal const val TAB_BAR_SCALE = 0.90f
/** 胶囊高度（基准 64dp × 系数） */
internal val TAB_BAR_HEIGHT = (64 * TAB_BAR_SCALE).dp

/** 胶囊比基准矮掉的高度 */
internal val TAB_BAR_SHIFT = (64 * (1 - TAB_BAR_SCALE)).dp
/** 悬浮按钮距屏幕底（基准 92dp）—— 跟着底栏一起往下收，保持与底栏同样的视觉间距 */
internal val FAB_CLEARANCE = 92.dp - TAB_BAR_SHIFT

/**
 * 列表底部留白（待办页 / 账目页共用同一份）。
 *
 * 要盖住「玻璃底栏 + 两个悬浮按钮」的高度，所以比 [FAB_CLEARANCE] 大不少；
 * 两页原本各写了一份 private 的 180dp —— 两处独立写死的数迟早会走散
 * （改一页忘另一页，用户看到的就是「这页能滑到底、那页最后一条压着底栏」），
 * 统一放到这里，要调一起调。
 */
internal val LIST_BOTTOM = 205.dp

/**
 * 没有悬浮按钮的页面（设置页各二级页）的底部留白。
 *
 * 比 [LIST_BOTTOM] 小 30dp：设置页没有 ＋ / 今 两个悬浮钮，只需让最后一行不被玻璃底栏压住。
 * 原来这两处各写了一个裸的 `150.dp`，和列表页的 180 一样是"写死的数迟早走散"，
 * 一起挪到这里 —— 要让全部页面同时抬起来，改这两行就够。
 */
internal val LIST_BOTTOM_PLAIN = 150.dp

/**
 * 底栏液态玻璃的**迭代档位**（编译期开关，一档一编译做真机对比；定稿后写死并删掉分支）。
 *
 *  0 基线：原来的平涂体色 + 中间 0.85 白的一条直杠高光
 *  1 玻璃层次：体色改「上亮下淡」渐变 + 底部内阴影 + 镜面大师描边（沿轮廓渐变）
 *  2 强折射：折射带 20→26dp、位移 22→30dp、模糊 10→9dp，并加边缘色散（细彩边）
 *  3 选中胶囊玻璃化：浅红底上再叠一层顶部反光 + 内阴影 + 描边，从"色块"变成"玻璃片"
 *  4 （已删）切页光扫：曾经切 tab 时一道高光扫过，2026-11-06 用户否掉
 */
internal const val BAR_GLASS_STAGE = 8

/**
 * 浮动玻璃胶囊底栏。
 *
 * 三个图标是自绘的同一套（24 网格 / 2dp 描边 / 圆头端点），视觉重量一致。
 *
 * 选中态 = **一枚 56dp 的正圆把图标和文字一起包住**（浅红底），切 tab 时用弹簧滑过去。
 * 图标与文字间距 1.5dp —— 文字必须显式给 lineHeight：主题里 bodyLarge 带 24sp 行高，
 * 10.5sp 的字套上 24sp 行框会在上下各留一大截空白，看起来就是"字离图标很远"。
 */
@Composable
fun GlassBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LiquidGlassBackdropState? = null,
) {
    // 底栏形状 = **真正的胶囊**（半径严格等于 高/2）。
    // 原来是 RoundedCornerShape(34 × 0.90 = 30.6dp)，比 高/2 = 28.8dp 大，
    // 靠 Skia 把圆角按比例压回去才勉强成胶囊 —— 压回去的过程中两条弧的圆心就飘了，
    // 选中圆贴到端头时看着"不同心"。这里直接写 50%，圆弧半径可预期，
    // 透镜（见下）按 R − gap 取半径就能与端头严格同心。
    val shape = RoundedCornerShape(percent = 50)
    val circleSize = (56 * TAB_BAR_SCALE).dp
    val barHeight = TAB_BAR_HEIGHT

    // 底栏形状（给液态玻璃的折射用）。
    // 三图标重构（用户删掉「模型」后要求）：胶囊不再靠左右 34dp 边距拉满整屏，
    // 而是「每项固定 84dp」收紧成一小条居中 —— 三项散在大宽屏上会显得很空。
    // 每项 84dp 与四项时代的 85.75dp 基本一致，图标观感不变，胶囊总宽 252dp。
    //
    // 用 Box 而不是 BoxWithConstraints：宽度完全由 items.size 决定（barW = 每项 × 项数），
    // 从来不读约束。原来那层 BoxWithConstraints 白付一次 subcomposition，lint 也报
    // UnusedBoxWithConstraintsScope（`gradle lintDebug` 唯一的 ERROR）。
    // 2026-11-06：84 → 108。上游 demo 是 `padding(horizontal = 36dp)` + 3 项 →
    // 每项 (360-72)/3 = 96dp，滑块宽高比 ≈ 1.9:1，那才是"横向胶囊"。
    // 我们原来每项 75.6dp，滑块 75.6×50.4 = 1.5:1，怎么调都像"圆角方块"。
    // 108×0.9 = 97.2dp，总宽 291.6dp，两边留白 34dp —— 和 App 其他页面 36dp 边距一致。
    val itemWidth = (108 * TAB_BAR_SCALE).dp
    Box(
        modifier.height(barHeight)
    ) {
        val barW = itemWidth * items.size
        // Kyant 的 tabsBackdrop：把「底栏这层玻璃 + 内容」录一份，供选中滑块采样。
        // 滑块因此看到的是**底栏玻璃**而不是原始页面 —— 真·玻璃叠玻璃。
        val barLayer = rememberLiquidGlassBackdrop()
        // ⚠️ 玻璃必须是**独立的一层**，图标/文字画在它外面。
        // 若把图标也画进带 RenderEffect 的那层，图标会被折射+模糊一起糊掉。
        Box(
            Modifier
                .width(barW)
                .fillMaxHeight()
                // 阶段 5：给整块玻璃一个投影。原来玻璃分支完全没有 elevation，
                // 白底 App 上白玻璃＝看不见，"它是一块浮起来的实体"这个读感全丢了。
                .then(
                    // 2026-11-05：默认 Modifier.shadow 是纯黑、按 elevation 加浓，
                    // 压在米色纸上就是一片 17% 灰的污渍（实测阴影区 RGB 208 对背景 251）。
                    // 改成显式低 alpha：环境光极淡、接触阴影稍实，才像"浮起 2mm"而不是"脏了一块"。
                    if (BAR_GLASS_STAGE >= 7) Modifier.shadow(
                        12.dp, shape, clip = false,
                        ambientColor = Color(0x0A000000),
                        spotColor = Color(0x1A000000)
                    )
                    else if (BAR_GLASS_STAGE >= 6) Modifier.shadow(20.dp, shape, clip = false)
                    else if (BAR_GLASS_STAGE >= 5) Modifier.shadow(16.dp, shape, clip = false)
                    else Modifier
                )
        ) {
            // 底栏（胶囊）在背景层里的位置。底栏自己不移动，所以这个值稳定；
            // 滑块的采样偏移 = 它 + 滑块在本层里的偏移，两者都用**同一帧**的状态算出，
            // 快速拖动时就不会再错开一帧（见 liquidGlass(samplingOffset) 的注释）。
            val barInBg = remember { mutableStateOf(Offset.Zero) }
            // ── ① 底层：玻璃 + 「红色版本」的图标文字，整块录进 barLayer ─────────────
            // 这是上游 tabsBackdrop 的做法：选中格的图标是红的，不是因为我们判断 index 改色，
            // 而是滑块像一片滤片 —— 滑到谁头上，谁就露出下面那层的红。
            // 这层不能直接被看见（红图标由可见层的灰图标盖住），只作为滑块的采样源。
            Box(Modifier.matchParentSize().recordBackdrop(barLayer)) {
            if (backdrop != null) {
                val stage = BAR_GLASS_STAGE
                Box(
                    Modifier
                        .matchParentSize()
                        .onGloballyPositioned { c ->
                            backdrop.coordinates?.let { bg -> barInBg.value = offsetOf(bg, c) }
                        }
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = shape,
                            // 2026-11-05 液态玻璃第二轮：折射带 26→32dp、位移 30→44dp、模糊 9→7dp。
                            // 原来折射带只占胶囊高度的 45%，位移又被 9dp 的模糊盖住 —— 边缘看着只是"糊"，
                            // 没有透镜把内容弯折过去的读感。加大位移 + 少模糊，边缘的弯曲才看得见。
                            // 2026-11-05 第四轮：照着 Kyant0 的 demo 实测配方重标
                            // —— 她的底栏是 lens(24dp, 24dp) + blur(8dp) + vibrancy()。
                            // 我上一版 40/56dp + 3.5dp 模糊是"想当然"：位移过大玻璃边缘被拉成
                            // 果冻，模糊过小把卡片文字整片透出来，反而显脏。她的配比是
                            // 带高=位移=24dp、模糊 8dp —— 内容只留色块和大形状，才是干净的通透。
                            // 2026-11-10：与整理页胶囊同一套比例（折射=高度的 1/4、模糊=高度的 0.4，
                            // 封顶 22dp）。底栏高 57.6dp ⇒ 14.4dp 折射、22dp 模糊。
                            refractionHeight = TAB_BAR_HEIGHT * 0.25f,
                            refractionAmount = TAB_BAR_HEIGHT * 0.25f,
                            blurRadius = (TAB_BAR_HEIGHT * 0.40f).coerceAtMost(22.dp),
                            depthEffect = true,
                            // 体色：上亮下淡。原来是 0.16 平涂，看着是"一层奶白膜"，
                            // 渐变之后上半边受光、下半边压深，才有玻璃的厚度。
                            tint = if (stage >= 6) {
                                // 2026-11-05 液态玻璃第二轮：0.30 → 0.20。
                                // 体色太厚时，折射全被这层白盖住 —— 诊断（折射拉到 120dp）证明着色器一直在工作，
                                // 只是"奶白"把它糊平了。降低体色，边缘弯折才透得出来；
                                // 亮度感交给 rimLight 与 SPECULAR 描边，不再靠堆白。
                                // 通透：体色只剩一层极薄的凉白（0.06），
                                // 玻璃的"存在"靠边缘环境光 + SPECULAR 环 + 投影三件事，
                                // 不再靠往身上刷白漆。
                                // 2026-11-05：浅色不再"刷白"。真玻璃压在浅纸上，体色是
                                // **略暗、略暖**的（吸光），只有边缘亮 —— 白体色一上去就成了白药丸。
                                // 这里用暖灰 0.05，玻璃比背景暗一点点，形状靠边缘光闭合。
                                // 她背后是彩色照片，白 0.4 压得住；我们背后是纸白卡片，
                                // 刷白只会把玻璃和卡片的界线抹掉。这里改成**比纸略暗**的暖灰：
                                // 真玻璃压在浅纸上就是吸一点光、比周围暗一档，靠面级方向光显出体积。
                                // 深色不再刷白：深底上刷白等于给玻璃蒙一层灰，
                                // 而且底下的白字被 8dp 模糊揉开后，白体色会把它放大成一块亮斑
                                // （实测雾区 (101,98,96) 对周围 (30,27,27)）。
                                // 深色改成"压暗"：玻璃比背景更沉，透上来的亮内容自然被压下去。
                                // 2026-11-06：浅色回到上游的配方 —— `containerColor = Color(0xFFFAFAFA).copy(0.4f)`。
                                // 我前面几版一直不敢刷白（理由：纸白背景上刷白＝看不见），
                                // 所以给了 5% 暖灰，结果底栏和背景同色、滑块那块 10% 黑直接压在米色纸上，
                                // 看着就是"一块脏灰"。她的逻辑相反：底栏要比背景**更白**才浮得起来，
                                // 滑块压在"白玻璃"上，10% 黑才是一层干净的灰玻璃。
                                (if (isDarkTheme) Color(0xFF0B0908) else Color(0xFFFAFAFA))
                                    .copy(alpha = if (isDarkTheme) 0.28f else 0.40f)
                            } else if (stage >= 1) {
                                Color.White.copy(alpha = if (isDarkTheme) 0.12f else 0.10f)
                            } else {
                                Color.White.copy(alpha = if (isDarkTheme) 0.10f else 0.14f)
                            },
                            // 阶段 6/7 的薄纱：浅色主题下白底上白玻璃＝隐形，
                            // 极淡的黑纱把它压出一点点，才看得出"这里隔了一层介质"。
                            // 薄纱归零：挂着的一层黑纱就是"灰蒙蒙"的来源。
                            veil = Color.Transparent,
                            // 下半边只压一丁点，保留"玻璃有厚度"的读感，但不吃透光。
                            // 上游是单一 onDrawSurface(containerColor)，没有"上亮下淡"。
                            // 体色到 0.40 之后再叠一层渐变，下缘会积成一条白边。
                            tintBottom = if (stage >= 1) {
                                Color.White.copy(alpha = if (isDarkTheme) 0.03f else 0f)
                            } else null,
                            // 上游第一层底栏根本不传 innerShadow（只有滑块传，且乘 pressProgress）。
                            // 我们这条 7dp 在白胶囊底缘积成一条暗带，整块玻璃就"重"了 —— 浅色归零。
                            innerShadow = if (stage >= 1 && isDarkTheme) 7.dp else 0.dp,
                            dispersion = if (stage >= 2) 0.08f else 0f,
                            // 2026-11-05 液态玻璃第二轮：真玻璃的轮廓会收一圈环境光，
                            // 边缘按平方衰减提亮；顺带把透过去的颜色增一点饱和（1.10）。
                            // 这两个值只在 shader 里加，不改任何几何/布局。
                            rimLight = if (stage >= 2) 0.10f else 0f,
                            // 增饱和交给 vibrancy（她那个 setSaturation(1.5)），shader 里不再重复乘
                            saturation = 1f,
                            vibrancy = if (stage >= 2) 1.20f else 1f,
                            // 面级方向光：她 Highlight.Default 是 White α0.5 / 45° / falloff 1。
                            // 米底上 0.5 会整片泛白，取 0.30 —— 朝光那半面提亮，玻璃才有体积。
                            // 2026-11-06（用户：「边缘的高光和中间的透明之间的过渡非常生硬」）：
                            // 浅色不再画 1px 描边 —— 那条线内是纯色、线外是背景，中间没有过渡，
                            // 看着就是贴了一圈塑料边。改成纯着色器：高光带从轮廓向内 ~30dp 渐隐。
                            // 深色保持原样（用户：深色非常 perfect，别动）。
                            highlightAlpha = when {
                                !isDarkTheme && stage >= 2 -> 0.34f
                                stage >= 2 -> 0.26f
                                else -> 0f
                            },
                            highlightAngle = 45f,
                            highlightFalloff = if (isDarkTheme) 1f else 1.4f,
                            // 深色维持原来的贴边亮（0.9 × 24dp ≈ 22dp）；
                            // 浅色给独立宽度 34dp —— 比折射带略宽，光晕从轮廓往里慢慢化开，
                            // 和折射那条弯折带**叠在一起**而不是各画一圈。
                            highlightSpread = if (isDarkTheme) 0.9f else 2.0f,
                            highlightWidth = if (isDarkTheme) 0.dp else 34.dp,
                            // 高光不能是纯白：底栏体色是 40% 暖白压在米色纸上，中心偏暖；
                            // 边缘来一圈冷白，两者就是两个色相 —— 放大看像镶了圈冷边。
                            // 换成同色相的暖白，中心到边缘是同一块介质，一体感立刻起来。
                            highlightColor = if (isDarkTheme) Color(0xFFFFF8F0)
                                             else Color(0xFFFFFBF2),
                            // 2026-11-06（用户：「底栏和背景的融合度有点稍微高了」「高光可以是
                            // 胶囊周围的白色细线」）：浅色恢复一圈 1px 白线。
                            // 它不再承担"过渡"——过渡由 34dp 光带负责，细线只负责**闭形**：
                            // 白玻璃压在米色纸上，没有这条线轮廓就散进背景里。
                            rim = if (stage >= 1) GlassRim.SPECULAR else GlassRim.LEGACY,
                        )
                )
            } else {
                Box(
                    Modifier.matchParentSize().glassSurface(
                        shape, dark = isDarkTheme, ring = true, elevation = 7.dp, clipContent = false
                    )
                )
            }
                // 红图标层：和可见层的 Row 完全同一套尺寸/间距，只是颜色不同。
                // 两层的排布必须严格一致，否则滑块里的红图标会和灰图标错位半个像素。
                Box(Modifier.width(barW).fillMaxHeight().clip(shape)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        items.forEach { item ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    TabIcon(item.iconRes, MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height((1.5 * TAB_BAR_SCALE).dp))
                                    Text(
                                        item.label,
                                        fontSize = (11 * TAB_BAR_SCALE).sp,
                                        lineHeight = (12 * TAB_BAR_SCALE).sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 玻璃的"边"：一条极淡的暖灰细线。浅底上白高光闭合不了形状，
            // 缺了它，右半段（背后没有内容时）就是一块没有边界的白。
            if (backdrop != null) {
                Box(
                    Modifier
                        .width(barW)
                        .fillMaxHeight()
                        .border(
                            1.dp,
                            // 浅色体色 0.40 白之后，白胶囊与米色背景本身就有清晰边界，
                            // 再压一条暖灰硬边就是两条线叠着 → 显脏。深色仍需要它闭合形状。
                            if (isDarkTheme) Color.White.copy(alpha = 0.10f)
                            else Color.Transparent,
                            shape
                        )
                )
            }
            Box(Modifier.width(barW).fillMaxHeight().clip(shape)) {
                val itemW = itemWidth

        // ── 选中透镜（阶段 8）───────────────────────────────────────────────
        // 几何（同心是硬约束，不能靠眼调）：
        //   底栏 = 胶囊，半径 R = 高/2
        //   透镜 = 内缩 gap，半径 r = R − gap，四边留白都等于 gap
        //   ⇒ 选中第 1 个 tab 时，透镜左弧圆心 = gap + r = R = 底栏端头弧圆心，
        //     半径正好差一个 gap —— 两条弧严格同心。
        // 优化前是「50.4dp 正圆 + 端头 28.8dp 弧」：圆离左边 12.6dp、离上边 3.6dp，
        // 圆心压根不在一处，所以无论怎么看都是歪的。
        val gap = (barHeight - circleSize) / 2
        // 2026-11-06（用户：「底栏和滑块的侧边又不是同心圆了」）：
        // 四周必须**同时**内缩 gap。改成整格宽（只缩上下）之后，滑块端头的圆心是
        // `lensH/2 = R − gap`，而底栏端头的圆心在 `R` —— 两个圆心差了 gap，
        // 弧线看起来就是「里外错开一层」。之前嫌 63×50 圆乎乎，是把 itemW 从 84
        // 加到 108 来解决的（89×42 ≈ 2.1:1），不该拿同心去换比例。
        val lensW = itemW - gap * 2
        val lensH = circleSize
        // 选中胶囊自己的圆角：正胶囊（高的一半）
        val pillShape = RoundedCornerShape(lensH / 2)
        val density = LocalDensity.current
        val gapPx = with(density) { gap.toPx() }
        val stepPx = with(density) { itemW.toPx() }
        // 用 Animatable 而不是 animateDpAsState：需要读 velocity 来做"跑起来会拉长"的形变
        val lensX = remember { Animatable(stepPx * selectedIndex) }
        // 7.4.1（方案 P0-5）：底栏胶囊是横向位移，减弱动效时直接落位（连同拖动松手的吸附）。
        val reducedMotion = LocalReducedMotion.current
        LaunchedEffect(selectedIndex) {
            val target = stepPx * selectedIndex
            // 距离决定用哪种曲线（用户 2026-11-05：点「设置」时红框「突然跳过来」，要一个过渡）：
            //   相邻一格  → 弹簧，带一点回弹，灵动；
            //   隔了一格以上 → 用 tween 滑过去，时长随距离增长（290ms / 400ms），
            //                 弹簧在长距离下初速太大、看着就是"瞬移"。
            val gaps = abs(target - lensX.value) / stepPx.coerceAtLeast(1f)
            val spec: AnimationSpec<Float> =
                if (gaps <= 1.05f) Motion.AccentSpring
                else tween(
                    durationMillis = MotionDebug.ms((330f + 110f * (gaps - 1f)).toInt().coerceAtMost(440)),
                    easing = Motion.PaperInOut
                )
            if (reducedMotion) lensX.snapTo(target) else lensX.animateTo(target, spec)
        }

        // ── 拖动：按住红胶囊横向拖，胶囊跟手走，松手吸附到最近的一项并切页 ──────────────
        // 用户 2026-11-05：「选中的红色标识应该可以拖动它走，而不是只能点击」。
        // 实现要点：
        //   1. 拖动中不用 lensX（Animatable 只能靠协程改，逐帧 launch 会抖动），
        //      另起 dragX 直接跟手指，绘制时二选一；松手才把位置交回 lensX 收尾。
        //   2. 用 detectHorizontalDragGestures：手指移动超过 touchSlop 才开始拖，
        //      所以轻点各个 tab 依旧走子节点的 clickable，两套手势不打架。
        //   3. 拖过某一项的中心时给一次轻微震动（TextHandleMove），手感上有"格子"。
        var dragging by remember { mutableStateOf(false) }
        var dragX by remember { mutableFloatStateOf(0f) }
        // 拖动中的速度：原来形变读的是 lensX.velocity，拖动时 Animatable 根本没在跑，
        // 所以拖动过程胶囊是"死"的。这里自己算速度，让拖起来也有拉长/压扁。
        var dragVel by remember { mutableFloatStateOf(0f) }
        // 只有"从当前选中那一格起手"才认拖（用户 2026-11-05）：
        //   红框在「待办」上时拖动 → 跟手走；
        //   手指在「账目」「设置」这些没选中的格子之间划 → 一动不动，当没看见。
        // 轻点任意一格仍然是切换（点击不走这里，走子节点的 clickable）。
        var dragAllowed by remember { mutableStateOf(false) }
        val speed = remember { VelocityTracker() }
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        var lastHover by remember { mutableFloatStateOf(-1f) }
        val maxLensX = stepPx * (items.size - 1).coerceAtLeast(0)
        val shownX = if (dragging) dragX else lensX.value
        // 拖动中"胶囊此刻停在谁头上" = 图标高亮的依据（松手前就先给反馈）
        val hoverIndex = if (dragging) {
            (dragX / stepPx).roundToInt().coerceIn(0, items.size - 1)
        } else selectedIndex

        // 切 tab 的高光扫过已删除（2026-11-06 用户：「底栏别点一下然后一道光闪一下，
        // 一点也不好看」）。它和胶囊位移是同一时刻发生的两件事，读起来像故障闪屏；
        // 胶囊本身有弹簧位移 + 形变，已经够了。

        Row(
            Modifier.fillMaxSize()
                // selectedIndex 必须进 key：不然起手那刻读到的还是上一次的选中格，
                // 判断"是不是从选中格起手"会一直用错格子。
                .pointerInput(items.size, stepPx, selectedIndex) {
                    val cellPad = 10.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            // 起手点必须在当前选中那一格（左右各放宽 10dp，照顾从格子边缘起手）。
                            // 不在 → 整段手势作废，红框不动（拖到别处也不会误切页）。
                            dragAllowed = offset.x >= selectedIndex * stepPx - cellPad &&
                                offset.x <= (selectedIndex + 1) * stepPx + cellPad
                            if (dragAllowed) {
                                // 胶囊中心直接压到手指下面：用户要的是「手指在哪，红框就在哪」，
                                // 不是「从原来那格开始按位移走」。胶囊中心 = shownX + stepPx/2，
                                // 所以 shownX = 手指 x − stepPx/2。
                                speed.resetTracking()
                                dragging = true
                                dragX = (offset.x - stepPx / 2f).coerceIn(0f, maxLensX)
                                lastHover = -1f
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            // 无论认不认这次拖，都要 consume，否则松手会退化成一次点击、
                            // 把页面切到手指划过的格子（用户要的是"当没看见"）。
                            change.consume()
                            if (dragAllowed) {
                                // 用绝对位置而不是累加：累加会跟手指之间累积漂移，
                                // 直接读 change.position 保证胶囊中心和手指严丝合缝。
                                // 两端加橡皮筋：超出去只跟 1/3，松手回弹。
                                // 硬 clamp 的话拖到尽头手指还在动、胶囊却纹丝不动（像卡住）；
                                // 留一点可拉的距离，手感上「到头了」但仍有反馈。
                                val raw = change.position.x - stepPx / 2f
                                dragX = when {
                                    raw < 0f -> raw * 0.34f
                                    raw > maxLensX -> maxLensX + (raw - maxLensX) * 0.34f
                                    else -> raw
                                }
                                speed.addPosition(change.uptimeMillis, change.position)
                                dragVel = speed.calculateVelocity().x
                                val ho = (dragX / stepPx).roundToInt().coerceIn(0, items.size - 1).toFloat()
                                if (ho != lastHover) {
                                    lastHover = ho
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                          if (dragAllowed) {
                            // 动量投影（Apple 手感的关键半步）：不是「离哪格近就落哪格」，
                            // 而是把当前速度沿惯性外推一小段，再取外推点的最近格 ——
                            // 于是快速一甩能跨过一格，慢慢挪则老老实实落在最近那格。
                            val endV = speed.calculateVelocity().x
                            // 0.12 → 0.35（2026-11-06）：Apple 那条投影公式 (v/1000)·d/(1−d) 在 d=0.998 时
                            // 约等于 v×0.5 秒，原来的 0.12 只有四分之一，导致快速一甩跨不到格。
                            // 保留「最多跨两格」的硬夹 —— 一次翻三格反而不受控。
                            val projected = dragX + (endV * 0.35f).coerceIn(-stepPx * 2f, stepPx * 2f)
                            val rawIdx = (projected / stepPx).roundToInt()
                            val target = rawIdx.coerceIn(0, items.size - 1)
                            scope.launch {
                                // 先把位置钉在手指松开的地方（这一帧不能跳回旧 tab），
                                // 再切页 —— 切页引起的 LaunchedEffect 会把它弹到目标格。
                                lensX.snapTo(dragX.coerceIn(0f, maxLensX))
                                dragging = false
                                if (target != selectedIndex) {
                                    // 换页：位移动画交给 LaunchedEffect(selectedIndex)，
                                    // 这里再 animateTo 一次会把它的 tween 顶掉。
                                    onSelect(target)
                                } else {
                                    // 拖回原来那格时 selectedIndex 没变、LaunchedEffect 不会重跑，
                                    // 这里补一次归位，否则胶囊会停在半路上。
                                    if (reducedMotion) lensX.snapTo(target * stepPx)
                                    else lensX.animateTo(
                                        target * stepPx,
                                        Motion.AccentSpring
                                    )
                                }
                            }
                          }
                        },
                        onDragCancel = {
                            if (dragAllowed) {
                                val snap = (dragX.coerceIn(0f, maxLensX) / stepPx).roundToInt() * stepPx
                                scope.launch {
                                    lensX.snapTo(dragX)
                                    dragging = false
                                    if (reducedMotion) lensX.snapTo(snap)
                                    else lensX.animateTo(snap, Motion.AccentSpring)
                                }
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == hoverIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.03f else 1f,
                    animationSpec = motionSpec(spring<Float>(dampingRatio = 0.80f, stiffness = 500f)),
                    label = "iconScale"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(
                            // 点击也要有触觉：原来只有拖动经过格子时震（hover 那处），
                            // 轻点切页完全没反馈（用户 2026-11-06：「底栏的点击是没有振动的」）。
                            // 已经在这一页再点一次就不震 —— 没有变化就不该有反馈。
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (i != selectedIndex) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 未选中用**暖灰**：主题里的 onSurfaceVariant 是灰蓝色，
                        // 压在暖米色背景 + 白玻璃上会显冷、显脏（和整套暖色调打架）。
                        TabIcon(
                            iconRes = item.iconRes,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFF6E6357),
                            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                        Spacer(Modifier.height((1.5 * TAB_BAR_SCALE).dp))
                        Text(
                            item.label,
                            fontSize = (11 * TAB_BAR_SCALE).sp,
                            lineHeight = (12 * TAB_BAR_SCALE).sp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFF6E6357)
                        )
                    }
                }
            }
        }

        // ── ④ 滑块：最上层，采样「页面 + 底栏自己那层玻璃」──────────────
        // 必须在图标 Row **之后**绘制：它要盖住下面那层的灰图标、露出 barLayer 里的红图标。
        // 顺序写反 → 灰图标压在胶囊上，选中态完全看不见。
        Box(
            Modifier
                // x 同样要内缩 gap，否则滑块左弧圆心落在 lensH/2 而不是 R 上（不同心）
                .offset { IntOffset((shownX + gapPx).roundToInt(), gapPx.roundToInt()) }
                .size(width = lensW, height = lensH)
                .graphicsLayer {
                    // 速度驱动的形变：跑得越快，横向拉长、纵向压扁、整体微微抬起，
                    // 到位后速度归零 → 自动收回 1:1。这是液态金属/软体的读感，
                    // 用静态阴影做不出来。
                    val v = abs(if (dragging) dragVel else lensX.velocity)
                    val s = (v / 6000f).coerceIn(0f, 0.18f)
                    scaleX = 1f + s
                    scaleY = 1f - s * 0.45f
                    translationY = -6.dp.toPx() * s
                }
                .then(
                    // 上游的 shadow 也乘 pressProgress —— 静止时没有投影。
                    // 我们只在拖动（按下）时给，静止不留灰边。
                    if (BAR_GLASS_STAGE >= 3 && (dragging || isDarkTheme)) Modifier.shadow(
                        8.dp, pillShape, clip = false,
                        ambientColor = Color(0x0C000000),
                        spotColor = Color(0x24000000),
                    ) else Modifier
                )
                // 2026-11-05（用户指出）：这颗胶囊以前是**自己画的一块色** ——
                // background(primaryContainer@0.58) 再叠几条亮边。它再好看也只是贴纸，
                // 一出现就把整条底栏切成"玻璃 + 贴纸"两块，一体性全毁。
                //
                // 现在它是**同一套液态玻璃**：lens 折射 + 面级方向光 + 内阴影 + 外投影，
                // 体色只是一层 12% 的红（染色），不是实色。参数照 Kyant 的滑块：
                // lens(10dp, 14dp) + chromaticAberration，她那个滑块就是纯玻璃 + 黑 10%。
                .then(
                    // 完全照 Kyant 的滑块：effects 只有 lens(10dp, 14dp, chromaticAberration)，
                    // 采样源是 combined(页面, 底栏层)，体色只有 10% 黑/白 ——
                    // 选中色不靠染色，靠下面 barLayer 里那份红图标透上来。
                    if (backdrop != null && supportsLiquidGlass) Modifier.liquidGlass(
                        backdrop = backdrop,
                        over = barLayer,
                        shape = pillShape,
                        // 位移 14→6dp：她的滑块背后是照片，边缘拉伸看不出来；我们的背景是
                        // 一片纯净的米色纸 + 白玻璃，折射没有内容可弯 —— 位移大了只会在滑块
                        // 轮廓上收出一圈灰弧（亮暗错位的接缝），看着就是"脏"。
                        // 小位移 + 带高一起收，边缘只剩极短的一道弯折。
                        // ★ 这是之前最大的误解：上游滑块的 lens 也乘 pressProgress
                        //   `lens(10dp*progress, 14dp*progress, chromaticAberration = true)`
                        //   即 **静止时压根没有折射**。她静止的滑块就是一块极简的半透明薄片，
                        //   所有玻璃效果（lens / highlight / shadow / innerShadow）只在按住时出现。
                        // 我们之前给了常驻的 lens + 高光 + 内阴影 + 投影 —— 一块"半吊子玻璃"，
                        // 在纯净的米色纸上折射没内容可弯，只剩一圈采样错位造成的灰弧 = 脏。
                        // 深色常驻（用户 2026-11-06：深色已经相当漂亮，别动）；
                        // 浅色照上游 —— 静止没有折射。
                        //
                        // 位移从 14 收到 8：滑块现在是**整格宽**（86×50），紧贴相邻格子，
                        // 14dp 的位移会把隔壁和下面那行红字吸进滑块边缘 —— 深色下能看到
                        // "账目"两个字的重影（实测截图）。收到 8dp 只弯折边界一小段。
                        refractionHeight = if (dragging || isDarkTheme) 8.dp else 0.dp,
                        refractionAmount = if (dragging || isDarkTheme) 8.dp else 0.dp,
                        // 她的滑块不做 blur（只有 lens）。模糊会把叠上来的图标糊掉。
                        blurRadius = 0.dp,
                        depthEffect = false,
                        // 浅色**不能跟上游用黑 10%**。她能这么用是因为底栏身后是照片：
                        // 一层黑落在有纹理的照片上像"磨砂玻璃片"；我们身后是纯净的米色纸，
                        // 同样的黑落上去就是一块灰斑（放大看：一圈暗环 + 均匀灰）。
                        // 浅色改成"更白更冷的一层"：暖米底上压一块冷白玻璃，
                        // 靠色调差 + 外投影 + 内阴影读出"这块是浮起来的"，
                        // 而不是靠压暗 —— 压暗在浅暖底上永远显脏。
                        tint = if (isDarkTheme) Color.White.copy(alpha = 0.10f)
                               else Color.Black.copy(alpha = 0.06f),
                        tintBottom = null,
                        // 上游乘 pressProgress：静止 0，按住 8dp
                        innerShadow = if (dragging || isDarkTheme) 8.dp else 0.dp,
                        dispersion = if (dragging || isDarkTheme) 0.10f else 0f,
                        rimLight = if (dragging || isDarkTheme) 0.12f else 0f,
                        saturation = 1f,
                        vibrancy = 1.15f,
                        // Highlight.Default 0.5 **乘 pressProgress** → 静止 0，按住 0.5
                        // 浅色静止也给一点面级高光：滑块是一块纯色薄片，边界和白玻璃
                        // 直接硬切；一圈从轮廓向内渐隐的亮光能把这条边界"化开"。
                        // 它不像 1px 描边那样是一条线，所以不会生硬。
                        highlightAlpha = if (isDarkTheme) 0.30f
                                         else if (dragging) 0.50f else 0.34f,
                        highlightAngle = 45f,
                        highlightFalloff = 1f,
                        rim = if (isDarkTheme && BAR_GLASS_STAGE >= 1) GlassRim.SPECULAR else GlassRim.NONE,
                        highlightSpread = if (isDarkTheme) 0.9f else 2.0f,
                        // 浅色滑块静止时折射是 0，光带若跟着折射走就被钳成 1px 硬线 ——
                        // 独立给 13dp，边缘是一圈向内渐隐的亮，不是一道线。
                        highlightWidth = if (isDarkTheme) 0.dp else 13.dp,
                        highlightColor = if (isDarkTheme) Color(0xFFFFF8F0)
                                         else Color(0xFFFFFBF2),
                        // 滑块是**会移动**的玻璃：采样偏移必须用本帧的 shownX，
                        // 不能等 onGloballyPositioned 回调（那是上一帧的位置）——
                        // 快速拖动时两者差十几像素，图标就在里面颤（慢速看不出）。
                        samplingOffset = {
                            Offset(
                                barInBg.value.x + shownX + gapPx,
                                barInBg.value.y + gapPx
                            )
                        },
                        topHighlight = false,
                    ) else Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = if (isDarkTheme) 0.86f else 0.58f
                        ),
                        pillShape
                    )
                )
        )
            }
        }
    }
}

/**
 * 选中透镜的"玻璃片"装饰：顶缘反光 + 底缘内阴影 + 一圈亮边 + 随速度摆向的流光。
 *
 * 底色仍是原来的 `primaryContainer`（不动配色口径），只是把它从"平涂色块"变成一块薄玻璃 ——
 * 上面受光、下面压深、轮廓有一圈亮边，和底栏那层玻璃的读法一致。
 *
 * 形状从正圆改成**胶囊**：透镜要跟底栏端头同心（半径 = 底栏半径 − 留白），
 * 正圆做不到，胶囊才能四边留白一致。
 */
private fun Modifier.glassPillOverlay(dark: Boolean, lens: Animatable<Float, *>): Modifier =
    drawWithContent {
        drawContent()
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        val inset = 0.5.dp.toPx()
        // 订阅 lens.value：让本绘制每帧重跑（速度在变，流光要跟着动）
        val pos = lens.value
        // 顶缘反光：上缘最亮，到 70% 高处消失
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.40f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.70f
            ),
            cornerRadius = r
        )
        // 底缘内阴影：给一个"下缘更厚"的读感
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.12f else 0.05f)),
                startY = size.height * 0.55f,
                endY = size.height
            ),
            cornerRadius = r
        )
        // 随速度摆向的流光：透镜往右跑时，高光落在左半边（光被"甩"在后面），
        // 停下时速度归零 → 流光回到正中，只剩对称的一点点。
        val v = (lens.velocity / 7000f).coerceIn(-1f, 1f)
        if (abs(v) > 0.02f) {
            val cx = size.width * (0.5f - v * 0.35f)
            val bandW = size.width * 0.55f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (if (dark) 0.10f else 0.30f) * (0.35f + 0.65f * abs(v))),
                        Color.Transparent
                    ),
                    start = Offset(cx - bandW / 2f, 0f),
                    end = Offset(cx + bandW / 2f, size.height)
                ),
                cornerRadius = r
            )
        }
        // 一圈亮边
        drawRoundRect(
            color = Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = r,
            style = Stroke(1.dp.toPx())
        )
        // 让编译器别把 pos 优化掉（同时保证每帧重绘）
        if (pos.isNaN()) return@drawWithContent
    }
