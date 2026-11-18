package com.rongyu.shixuji.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.songtiFamily
import java.util.Calendar
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.motionSpec

/**
 * 红楼梦诗词（需求：启动页随机展示，无作者→佚名 无题→无题）。
 *
 * 诗句池共 119 句、23 位作者，按原著回目归类，来自 `/Volumes/TFcard/时序集/设计文件/红楼梦诗词名句摘录.md`（原《红楼诗词完整版.md》的扩充版，
 * 桌面同名文件即其来源，改诗时三处同步）。
 * 正文长度 5~26 字；启动页整句不换行、字号自适应：18sp 起，装不下就等比缩到 10sp
 *（26 字那三句在 13sp 装不下，故水位取 10sp；系统字号放大 1.15 档仍装得下）。
 */
private data class Poem(val text: String, val author: String, val title: String)

private val poems = listOf(
    // ── 第一回｜开篇题咏与《好了歌》 ──
    Poem("无材可去补苍天，枉入红尘若许年。", "石头", "石上偈"),
    Poem("满纸荒唐言，一把辛酸泪！", "曹雪芹", "题石头记"),
    Poem("未卜三生愿，频添一段愁。", "贾雨村", "对月有怀"),
    Poem("天上一轮才捧出，人间万姓仰头看。", "贾雨村", "中秋对月口号"),
    Poem("惯养娇生笑你痴，菱花空对雪澌澌。", "癞头和尚", "叹英莲"),
    Poem("世人都晓神仙好，惟有功名忘不了！", "跛足道人", "好了歌"),
    Poem("因嫌纱帽小，致使锁枷扛。", "甄士隐", "好了歌注"),
    // ── 第五回｜金陵十二钗判词 ──
    Poem("霁月难逢，彩云易散。", "曹雪芹", "金陵十二钗又副册判词·晴雯"),
    Poem("枉自温柔和顺，空云似桂如兰。", "曹雪芹", "金陵十二钗又副册判词·袭人"),
    Poem("根并荷花一茎香，平生遭际实堪伤。", "曹雪芹", "金陵十二钗副册判词·香菱"),
    Poem("可叹停机德，堪怜咏絮才。", "曹雪芹", "金陵十二钗正册判词·林黛玉与薛宝钗"),
    Poem("二十年来辨是非，榴花开处照宫闱。", "曹雪芹", "金陵十二钗正册判词·贾元春"),
    Poem("才自清明志自高，生于末世运偏消。", "曹雪芹", "金陵十二钗正册判词·贾探春"),
    Poem("富贵又何为，襁褓之间父母违。", "曹雪芹", "金陵十二钗正册判词·史湘云"),
    Poem("欲洁何曾洁，云空未必空。", "曹雪芹", "金陵十二钗正册判词·妙玉"),
    Poem("子系中山狼，得志便猖狂。", "曹雪芹", "金陵十二钗正册判词·贾迎春"),
    Poem("勘破三春景不长，缁衣顿改昔年妆。", "曹雪芹", "金陵十二钗正册判词·贾惜春"),
    Poem("凡鸟偏从末世来，都知爱慕此生才。", "曹雪芹", "金陵十二钗正册判词·王熙凤"),
    Poem("势败休云贵，家亡莫论亲。", "曹雪芹", "金陵十二钗正册判词·贾巧姐"),
    Poem("桃李春风结子完，到头谁似一盆兰。", "曹雪芹", "金陵十二钗正册判词·李纨"),
    Poem("情天情海幻情身，情既相逢必主淫。", "曹雪芹", "金陵十二钗正册判词·秦可卿"),
    // ── 第五回｜《红楼梦曲》 ──
    Poem("开辟鸿蒙，谁为情种？", "警幻仙姑", "红楼梦引子"),
    Poem("空对着，山中高士晶莹雪；终不忘，世外仙姝寂寞林。", "警幻仙姑", "终身误"),
    Poem("一个是阆苑仙葩，一个是美玉无瑕。", "警幻仙姑", "枉凝眉"),
    Poem("一个是水中月，一个是镜中花。", "警幻仙姑", "枉凝眉"),
    Poem("喜荣华正好，恨无常又到。", "警幻仙姑", "恨无常"),
    Poem("自古穷通皆有定，离合岂无缘？", "警幻仙姑", "分骨肉"),
    Poem("幸生来英豪阔大宽宏量，从未将儿女私情略萦心上。", "警幻仙姑", "乐中悲"),
    Poem("气质美如兰，才华馥比仙。", "警幻仙姑", "世难容"),
    Poem("觑着那侯门艳质同蒲柳，作践得公府千金似下流。", "警幻仙姑", "喜冤家"),
    Poem("将那三春看破，桃红柳绿待如何？", "警幻仙姑", "虚花悟"),
    Poem("机关算尽太聪明，反算了卿卿性命。", "警幻仙姑", "聪明累"),
    Poem("劝人生，济困扶穷；休似俺那爱银钱、忘骨肉的狠舅奸兄！", "警幻仙姑", "留余庆"),
    Poem("气昂昂头戴簪缨，光灿灿胸悬金印。", "警幻仙姑", "晚韶华"),
    Poem("箕裘颓堕皆从敬，家事消亡首罪宁。", "警幻仙姑", "好事终"),
    Poem("看破的，遁入空门；痴迷的，枉送了性命。", "警幻仙姑", "飞鸟各投林"),
    // ── 第十八回｜元妃省亲与大观园题咏 ──
    Poem("衔山抱水建来精，多少工夫筑始成。", "贾元春", "题大观园"),
    Poem("园成景备特精奇，奉命羞题额旷怡。", "贾迎春", "旷性怡情"),
    Poem("名园筑就势巍巍，奉命多惭学浅微。", "贾探春", "万象争辉"),
    Poem("山水横拖千里外，楼台高起五云中。", "贾惜春", "文章造化"),
    Poem("秀水明山抱复回，风流文采胜蓬莱。", "李纨", "文采风流"),
    Poem("高柳喜迁莺出谷，修篁时待凤来仪。", "薛宝钗", "凝晖钟瑞"),
    Poem("借得山川秀，添来气象新。", "林黛玉", "世外仙源"),
    Poem("秀玉初成实，堪宜待凤凰。", "贾宝玉", "有凤来仪"),
    Poem("蘅芜满净苑，萝薜助芬芳。", "贾宝玉", "蘅芷清芬"),
    Poem("绿蜡春犹卷，红妆夜未眠。", "贾宝玉", "怡红快绿"),
    Poem("一畦春韭绿，十里稻花香。", "林黛玉（代贾宝玉作）", "杏帘在望"),
    // ── 第二十三回｜贾宝玉四时即事 ──
    Poem("枕上轻寒窗外雨，眼前春色梦中人。", "贾宝玉", "春夜即事"),
    Poem("倦绣佳人幽梦长，金笼鹦鹉唤茶汤。", "贾宝玉", "夏夜即事"),
    Poem("绛云轩里绝喧哗，桂魄流光浸茜纱。", "贾宝玉", "秋夜即事"),
    Poem("松影一庭惟见鹤，梨花满地不闻莺。", "贾宝玉", "冬夜即事"),
    // ── 第二十七回｜林黛玉《葬花吟》 ──
    Poem("花谢花飞花满天，红消香断有谁怜？", "林黛玉", "葬花吟"),
    Poem("一年三百六十日，风刀霜剑严相逼。", "林黛玉", "葬花吟"),
    Poem("未若锦囊收艳骨，一抔净土掩风流。", "林黛玉", "葬花吟"),
    Poem("质本洁来还洁去，强于污淖陷渠沟。", "林黛玉", "葬花吟"),
    Poem("侬今葬花人笑痴，他年葬侬知是谁？", "林黛玉", "葬花吟"),
    Poem("一朝春尽红颜老，花落人亡两不知！", "林黛玉", "葬花吟"),
    // ── 第三十四回｜林黛玉题帕三绝 ──
    Poem("眼空蓄泪泪空垂，暗洒闲抛却为谁？", "林黛玉", "题帕三绝·其一"),
    Poem("枕上袖边难拂拭，任他点点与斑斑。", "林黛玉", "题帕三绝·其二"),
    Poem("彩线难收面上珠，湘江旧迹已模糊。", "林黛玉", "题帕三绝·其三"),
    // ── 第三十七回｜咏白海棠 ──
    Poem("玉是精神难比洁，雪为肌骨易销魂。", "贾探春", "咏白海棠"),
    Poem("淡极始知花更艳，愁多焉得玉无痕。", "薛宝钗", "咏白海棠"),
    Poem("出浴太真冰作影，捧心西子玉为魂。", "贾宝玉", "咏白海棠"),
    Poem("偷来梨蕊三分白，借得梅花一缕魂。", "林黛玉", "咏白海棠"),
    Poem("自是霜娥偏爱冷，非关倩女欲离魂。", "史湘云", "咏白海棠·其一"),
    Poem("花因喜洁难寻偶，人为悲秋易断魂。", "史湘云", "咏白海棠·其二"),
    // ── 第三十八回｜菊花诗与螃蟹咏 ──
    Poem("念念心随归雁远，寥寥坐听晚砧迟。", "薛宝钗", "忆菊"),
    Poem("蜡屐远来情得得，冷吟不尽兴悠悠。", "贾宝玉", "访菊"),
    Poem("昨夜不期经雨活，今朝犹喜带霜开。", "贾宝玉", "种菊"),
    Poem("萧疏篱畔科头坐，清冷香中抱膝吟。", "史湘云", "对菊"),
    Poem("隔坐香分三径露，抛书人对一枝秋。", "史湘云", "供菊"),
    Poem("毫端蕴秀临霜写，口角噙香对月吟。", "林黛玉", "咏菊"),
    Poem("聚叶泼成千点墨，攒花染出几痕霜。", "薛宝钗", "画菊"),
    Poem("孤标傲世偕谁隐，一样花开为底迟。", "林黛玉", "问菊"),
    Poem("短鬓冷沾三径露，葛巾香染九秋霜。", "贾探春", "簪菊"),
    Poem("窗隔疏灯描远近，篱筛破月锁玲珑。", "史湘云", "菊影"),
    Poem("醒时幽怨同谁诉，衰草寒烟无限情。", "林黛玉", "菊梦"),
    Poem("半床落月蛩声切，万里寒云雁阵迟。", "贾探春", "残菊"),
    Poem("饕餮王孙应有酒，横行公子竟无肠。", "贾宝玉", "螃蟹咏"),
    Poem("螯封嫩玉双双满，壳凸红脂块块香。", "林黛玉", "螃蟹咏"),
    Poem("眼前道路无经纬，皮里春秋空黑黄。", "薛宝钗", "螃蟹咏"),
    // ── 第四十五回｜林黛玉《秋窗风雨夕》 ──
    Poem("秋花惨淡秋草黄，耿耿秋灯秋夜长。", "林黛玉", "秋窗风雨夕"),
    Poem("谁家秋院无风入，何处秋窗无雨声。", "林黛玉", "秋窗风雨夕"),
    Poem("不知风雨几时休，已教泪洒窗纱湿。", "林黛玉", "秋窗风雨夕"),
    // ── 第四十八至四十九回｜香菱咏月三稿 ──
    Poem("月桂中天夜色寒，清光皎皎影团团。", "香菱", "咏月·初稿"),
    Poem("淡淡梅花香欲染，丝丝柳带露初干。", "香菱", "咏月·再稿"),
    Poem("一片砧敲千里白，半轮鸡唱五更残。", "香菱", "咏月·定稿"),
    // ── 第五十回｜芦雪庭联句与红梅诗 ──
    Poem("一夜北风紧，开门雪尚飘。", "王熙凤、李纨（联句）", "芦雪庭即景联句"),
    Poem("桃未芳菲杏未红，冲寒先喜笑东风。", "邢岫烟", "赋得红梅花"),
    Poem("冻脸有痕皆是血，酸心无恨亦成灰。", "李纹", "赋得红梅花"),
    Poem("疏是枝条艳是花，春妆儿女竞奢华。", "薛宝琴", "赋得红梅花"),
    Poem("不求大士瓶中露，为乞孀娥槛外梅。", "贾宝玉", "访妙玉乞红梅"),
    // ── 第五十一回｜薛宝琴怀古十绝 ──
    Poem("赤壁沉埋水不流，徒留名姓载空舟。", "薛宝琴", "赤壁怀古"),
    Poem("铜柱金城振纪纲，声传海外播戎羌。", "薛宝琴", "交趾怀古"),
    Poem("名利何曾伴汝身，无端被诏出凡尘。", "薛宝琴", "钟山怀古"),
    Poem("壮士须防恶犬欺，三齐位定盖棺时。", "薛宝琴", "淮阴怀古"),
    Poem("蝉噪鸦栖转眼过，隋堤风景近如何？", "薛宝琴", "广陵怀古"),
    Poem("衰草闲花映浅池，桃枝桃叶总分离。", "薛宝琴", "桃叶渡怀古"),
    Poem("黑水茫茫咽不流，冰弦拨尽曲中愁。", "薛宝琴", "青冢怀古"),
    Poem("寂寞脂痕积汗光，温柔一旦付东洋。", "薛宝琴", "马嵬怀古"),
    Poem("小红骨贱一身轻，私掖偷携强撮成。", "薛宝琴", "蒲东寺怀古"),
    Poem("不在梅边在柳边，个中谁拾画婵娟？", "薛宝琴", "梅花观怀古"),
    // ── 第六十四回｜林黛玉《五美吟》 ──
    Poem("一代倾城逐浪花，吴宫空自忆儿家。", "林黛玉", "五美吟·西施"),
    Poem("肠断乌啼夜啸风，虞兮幽恨对重瞳。", "林黛玉", "五美吟·虞姬"),
    Poem("绝艳惊人出汉宫，红颜命薄古今同。", "林黛玉", "五美吟·明妃"),
    Poem("瓦砾明珠一例抛，何曾石尉重娇娆？", "林黛玉", "五美吟·绿珠"),
    Poem("长剑雄谈态自殊，美人巨眼识穷途。", "林黛玉", "五美吟·红拂"),
    // ── 第七十回｜桃花行与柳絮词 ──
    Poem("桃花帘外东风软，桃花帘内晨妆懒。", "林黛玉", "桃花行"),
    Poem("桃花帘外开仍旧，帘中人比桃花瘦。", "林黛玉", "桃花行"),
    Poem("若将人泪比桃花，泪自长流花自媚。", "林黛玉", "桃花行"),
    Poem("岂是绣绒才吐，卷起半帘香雾。", "史湘云", "如梦令·咏柳絮"),
    Poem("空挂纤纤缕，徒垂络络丝。", "贾探春", "南柯子·咏柳絮（上阕）"),
    Poem("落去君休惜，飞来我自知。", "贾宝玉", "南柯子·咏柳絮（下阕）"),
    Poem("粉堕百花洲，香残燕子楼。", "林黛玉", "唐多令·咏柳絮"),
    Poem("几处落红庭院，谁家香雪帘栊？", "薛宝琴", "西江月·咏柳絮"),
    Poem("白玉堂前春解舞，东风卷得均匀。", "薛宝钗", "临江仙·咏柳絮"),
    Poem("好风凭借力，送我上青云！", "薛宝钗", "临江仙·咏柳絮"),
    // ── 第七十六回｜凹晶馆联诗 ──
    Poem("寒塘渡鹤影，冷月葬花魂。", "史湘云、林黛玉（联句）", "凹晶馆联诗"),
    // ── 第七十八回｜晴雯诔文 ──
    Poem("茜纱窗下，我本无缘；黄土垄中，卿何薄命。", "贾宝玉", "芙蓉女儿诔"),
)

/**
 * 一行放下的自适应字号文本：先按 [maxSize] 量一次实际宽度，再按可用宽度反推字号。
 * 装得下就保持 [maxSize]；装不下就等比缩放，缩到 [minSize] 为止；配合 maxLines=1 绝不折行。
 *
 * 为什么不用固定字号：启动页两行（诗句 + 出处）长度差很多 ——
 * 诗句最长 20 字、出处最长 22 字，可用宽度只有约 280dp（360dp 屏减左右各 40 内边距），
 * 而且字号放大档（1.15）还会再放大 15%，固定字号必然折行。
 */
@Composable
private fun FitOneLine(
    text: String,
    maxSize: TextUnit,
    minSize: TextUnit,
    color: Color,
    lineHeightRatio: Float = 1.65f,
    weight: FontWeight = FontWeight.Normal
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availPx = constraints.maxWidth
        val measured = remember(text, availPx, maxSize) {
            measurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontFamily = songtiFamily, fontSize = maxSize, fontWeight = weight)
            ).size.width
        }
        val size = if (measured > 0 && measured > availPx) {
            (maxSize.value * availPx / measured).coerceAtLeast(minSize.value).sp
        } else maxSize
        Text(text,
            fontFamily = songtiFamily, fontSize = size, fontWeight = weight, color = color,
            lineHeight = size * lineHeightRatio, textAlign = TextAlign.Center,
            maxLines = 1, softWrap = false,
            modifier = Modifier.fillMaxWidth())
    }
}

/** 「开启时序集」胶囊的高度（圆角吃满半高即为胶囊）。 */
private val SPLASH_BTN_H = 46.dp

/** 三段各自的配色。文案、色值都照设计稿（宣纸白 / 浅青瓷 / 墨夜）。 */
private data class SplashPalette(
    val bg: Brush,
    val text: Color,
    val sub: Color,
    val btnText: Color,
    val dark: Boolean,
)

/**
 * 欢迎页（2026-11-11 改版，7.2.5）。
 *
 * 相对上一版变了四件事：
 *  ① **三个时段**：早安 / 午安 / 晚安，边界由设置页「问候时段」决定（[GreetingTimes]），
 *    不再是写死的 0-6 / 21-23。两段之间的时间（如 07:00–12:00）仍直接进主界面。
 *  ② **字框式布局**：问候退到左上角小字，诗句与出处装进一个直角细框、四角加小角标。
 *  ③ **框与按钮视为一体**：「开启时序集」挪进框内，整块一起竖向居中 ——
 *    上一版按钮单独吊在屏幕下方 74dp 处，视觉上跟诗句是两件事。
 *  ④ **按钮做成液态玻璃胶囊**（统一配方见 [liquidCapsule]）+ 点击震动。
 *
 * 玻璃要采样身后那层渐变，所以背景单独画在一层挂了 [recordBackdrop] 的 Box 里，
 * 且玻璃按钮必须在那层**外面**（否则会把自己录进去 → 递归）。
 */
@Composable
fun SplashScreen(
    onEnter: () -> Unit,
    times: GreetingTimes = GreetingTimes.DEFAULT,
    // 7.5.0：设置页的「预览欢迎页」用它把时段钉死（不必改系统时间），
    // 传了就是预览态 —— 不改 splashDone、不写任何偏好，按钮变成「返回设置」。
    previewKind: Int? = null,
) {
    val cal = Calendar.getInstance()
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val kind = previewKind ?: times.greetingAt(nowMin)
    if (kind == null) {
        // 非问候时段：异步跳转主界面（避免 composition 期间副作用）
        LaunchedEffect(Unit) { onEnter() }
        return
    }

    val poem = remember { poems.random() }
    val greeting = when (kind) {
        GreetingTimes.GREETING_MORNING -> "早安"
        GreetingTimes.GREETING_NOON -> "午安"
        else -> "晚安"
    }
    val palette = when (kind) {
        GreetingTimes.GREETING_MORNING -> SplashPalette(
            bg = Brush.verticalGradient(listOf(Color(0xFFFFF7E0), Color(0xFFFFE3B3))),
            text = Color(0xFF3A2E1E), sub = Color(0xFF8A7A5A),
            btnText = Color(0xFF7A2E24), dark = false,
        )
        GreetingTimes.GREETING_NOON -> SplashPalette(
            bg = Brush.verticalGradient(listOf(Color(0xFFF4FAF8), Color(0xFFDDEDE9))),
            text = Color(0xFF2E3A38), sub = Color(0xFF6C837C),
            btnText = Color(0xFF2E5B50), dark = false,
        )
        else -> SplashPalette(
            bg = Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))),
            text = Color(0xFFE8E6E0), sub = Color(0xFFA0A0B0),
            btnText = Color(0xFFEDE9DF), dark = true,
        )
    }

    // 进场动画：背景淡入 + 整块内容轻微上浮（300ms）。只做一次、不经手指。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // 7.4.1：走 token（跟着 debug 慢放）+ 减弱动效时瞬变（欢迎页是「叙事型」动画，最该被尊重）。
    val alphaIn by animateFloatAsState(if (entered) 1f else 0f, motionSpec(Motion.enter(460)), label = "splashFade")
    val riseIn by animateFloatAsState(
        if (entered) 0f else 20f, motionSpec(Motion.glide(460)), label = "splashRise"
    )

    val frameColor = palette.text.copy(alpha = if (palette.dark) 0.24f else 0.26f)
    val cornerColor = palette.text.copy(alpha = if (palette.dark) 0.48f else 0.52f)

    Box(Modifier.fillMaxSize()) {
        // ① 背景层：一层竖向渐变。按钮不再采样它（见 GlassEnterButton 的注释），
        //    留这层只为铺底色。
        Box(Modifier.fillMaxSize().background(palette.bg))

        // ② 问候：左上角小字
        Text(
            greeting,
            fontFamily = songtiFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = palette.text, letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 30.dp, top = 22.dp)
                .graphicsLayer { alpha = alphaIn }
        )

        // ②' 预览态：右上角标一行，避免把人唬住以为真的换了时段
        if (previewKind != null) {
            Text(
                "预览 · 不写入设置",
                fontFamily = songtiFamily, fontSize = 11.sp, color = palette.sub,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 30.dp, top = 28.dp)
                    .graphicsLayer { alpha = alphaIn }
            )
        }

        // ③ 字框 + 按钮：当作一块整体，在屏内竖向居中
        Box(
            Modifier.fillMaxSize().padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = alphaIn; translationY = riseIn }
                    // 直角细框 + 四角小角标。画在 padding 之外，所以框正好包住内容。
                    .drawBehind {
                        val sw = 1.dp.toPx()
                        val inset = sw / 2f
                        drawRect(
                            color = frameColor,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - sw, size.height - sw),
                            style = Stroke(sw)
                        )
                        val arm = 13.dp.toPx()
                        val asw = 1.3.dp.toPx()
                        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                            drawLine(cornerColor, Offset(x, y), Offset(x + dx, y), asw)
                            drawLine(cornerColor, Offset(x, y), Offset(x, y + dy), asw)
                        }
                        corner(0f, 0f, arm, arm)
                        corner(size.width, 0f, -arm, arm)
                        corner(0f, size.height, arm, -arm)
                        corner(size.width, size.height, -arm, -arm)
                    }
                    .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 24.dp)
            ) {
                // 诗句与出处：整句必须一行放下（最长 20 / 22 字），字号自适应
                FitOneLine(poem.text, maxSize = 18.sp, minSize = 10.sp, color = palette.text)
                Spacer(Modifier.height(9.dp))
                FitOneLine(
                    "—— ${poem.author}《${poem.title}》",
                    maxSize = 13.sp, minSize = 9.5.sp, color = palette.sub, lineHeightRatio = 1.5f
                )
                Spacer(Modifier.height(26.dp))
                GlassEnterButton(
                    label = if (previewKind != null) "返回设置" else "开启时序集",
                    fg = palette.btnText,
                    dark = palette.dark,
                    onClick = onEnter
                )
            }
        }
    }
}

/**
 * 「开启时序集」胶囊按钮。
 *
 * 2026-11-11 重做：这里**不再走 `liquidCapsule` 的 AGSL 折射**。
 *
 * 那个配方是给「背后有东西可折」的场景写的（整理页浮在列表上、底栏浮在内容上）；
 * 欢迎页背后只是一层均匀的竖向渐变，模糊与折射都采不到任何信息 —— 一圈折射带只会在
 * 均匀底色上糊出一块脏白，白 0.20 又把薄荷底洗成灰白，再加一道黑投影，看着像塑料药片。
 *
 * 现在照设计稿（HTML 预览里那一版）逐条搬过来，全部用普通绘制完成：
 *   体色  白 0.20（深色 0.13）                —— `background`
 *   高光  左上 30% / 12% 处的柔光团，白 0.55  —— `Brush.radialGradient`
 *   描边  1dp 白 0.35                          —— `border`
 *   投影  0/6/14/-4 黑 0.16 ＋ 0/2/4 黑 0.10   —— 两层 `shadow`
 * 均匀底上玻璃本来就靠这四样成像，不需要真的采样背景（那份背景本身也没有细节可采）。
 */
@Composable
private fun GlassEnterButton(
    label: String,
    fg: Color,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = remember { RoundedCornerShape(SPLASH_BTN_H / 2) }
    val press = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // 深色底上白 0.13 会灰成一块「塑料板」——真玻璃在暗底上只是**微微发亮**，
    // 靠的是那道亮边，所以体色压到 0.08、描边提到 0.38。
    val bodyAlpha = if (dark) 0.08f else 0.20f
    val haloAlpha = if (dark) 0.20f else 0.55f

    val shadowMain = if (dark) 0.30f else 0.13f
    val shadowTight = if (dark) 0.18f else 0.075f
    Box(
        Modifier
            .height(SPLASH_BTN_H)
            // 投影必须只画在胶囊**外面**：`Modifier.shadow` 是画在整个节点后面的，
            // 而这颗胶囊体色只有白 0.20 —— 投影会从半透明体里透出来，把整块压灰
            // （实测比设计稿暗 8~10 个色阶，下半部分更明显）。
            .dropShadow(SPLASH_BTN_H / 2, dy = 7.dp, blur = 14.dp, spread = (-5).dp,
                color = Color.Black.copy(alpha = shadowMain))
            .dropShadow(SPLASH_BTN_H / 2, dy = 2.dp, blur = 4.dp, spread = 0.dp,
                color = Color.Black.copy(alpha = shadowTight))
            .clip(shape)
            // ① 体色：一层半透明白，透出身后渐变
            .background(Color.White.copy(alpha = bodyAlpha))
            // ② 高光团：CSS 里的 radial-gradient(120% 90% at 30% 12%)
            .drawWithContent {
                drawContent()
                // 高光是**椭圆**：设计稿是 radial-gradient(120% 90% at 30% 12%)，
                // 竖向只铺到 90% 高度、并在 60% 处衰减为 0。
                //
                // 这里踩过一个大坑：早先用 `scale(1f, k, pivot = 光心)` 再 `drawRect(brush)`
                // 整块画布一起被压扁 —— 画满整个画布的矩形被压成**一条横带**
                // （实测 y=29..63 亮、之外没有），上沿出现一道硬邦邦的亮条，看着就是割裂。
                // 正确做法是绕原点压扁，并把矩形按 1/k 拉高，才覆盖得住整块画布。
                val c = Offset(size.width * 0.30f, size.height * 0.12f)
                val rx = size.width * if (dark) 0.54f else 0.72f      // 1.2×w×0.6
                val ry = size.height * if (dark) 0.54f else 0.54f     // 0.9×h×0.6
                val k = ry / rx
                val brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = haloAlpha), Color.Transparent),
                    center = Offset(c.x, c.y / k),
                    radius = rx
                )
                withTransform({ scale(1f, k, pivot = Offset.Zero) }) {
                    drawRect(brush, topLeft = Offset.Zero, size = Size(size.width, size.height / k))
                }
            }
            // ③ 描边：1dp 白 0.35（画在内侧，与 CSS 的 border 同口径）
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.38f else 0.35f), shape)
            .clickable(interactionSource = press, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .pressScale(press, pressed = Motion.PRESS_BUTTON),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = songtiFamily, fontSize = 15.5.sp, color = fg, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
    }
}

/**
 * 只画在形状**外侧**的柔和投影。
 *
 * 为什么不用 `Modifier.shadow`：它把投影画在整个节点后面，胶囊体色只有白 0.20，
 * 投影会透上来把胶囊压灰 —— 实测比设计稿暗 8~10 个色阶，而且下半部分比上半部分更脏。
 * 这里改成「一圈圈向外扩的环」：每层都用 `Path.op(Difference)` 挖掉胶囊本身，
 * 所以内部一个像素都不会被染；alpha 按 (1-t)² 递减，叠起来接近高斯尾巴。
 */
private fun Modifier.dropShadow(
    radius: Dp,
    dy: Dp,
    blur: Dp,
    spread: Dp,
    color: Color,
): Modifier = drawBehind {
    val r = radius.toPx()
    val dyPx = dy.toPx()
    val blurPx = blur.toPx()
    val spreadPx = spread.toPx()
    if (blurPx <= 0f) return@drawBehind
    val inner = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
    val steps = 12
    // 重叠层的 alpha 会累加：Σ ≈ color.alpha × (1 − d/blur)³，所以单层取 3α/n·(1−t)²。
    val unit = 3f * color.alpha / steps
    for (i in steps downTo 1) {
        val t = i / steps.toFloat()
        val grow = spreadPx + blurPx * t
        val a = unit * (1f - t) * (1f - t)
        if (a <= 0.002f) continue
        val ring = Path().apply {
            addRoundRect(
                RoundRect(
                    -grow, dyPx - grow, size.width + grow, size.height + dyPx + grow,
                    CornerRadius(r + grow)
                )
            )
        }
        ring.op(ring, inner, PathOperation.Difference)
        drawPath(ring, color.copy(alpha = a))
    }
}
