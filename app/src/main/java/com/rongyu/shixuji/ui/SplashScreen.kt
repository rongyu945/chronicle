package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.Accent
import com.rongyu.shixuji.theme.songtiFamily
import java.util.Calendar

/**
 * 红楼梦诗词（需求：启动页随机展示，无作者→佚名 无题→无题）。
 *
 * 诗句池共 82 句、16 位作者，来自 `/Volumes/TFcard/时序集/设计文件/红楼诗词完整版.md`，
 * 按「贾宝玉 → 林黛玉 → 薛宝钗 → … → 曹雪芹」的顺序排列。
 * 正文长度 12~20 字；启动页整句不换行、字号自适应，最长那句（20 字）会缩到 13sp。
 * 增删诗句时同步更新那份 md 与本列表。
 */
private data class Poem(val text: String, val author: String, val title: String)

private val poems = listOf(
    Poem("秀玉初成实，堪宜待凤凰。", "贾宝玉", "有凤来仪"),
    Poem("蘅芜满静苑，萝薜助芬芳。", "贾宝玉", "蘅芷清芬"),
    Poem("深庭长日静，两两出婵娟。", "贾宝玉", "怡红快绿"),
    Poem("秋容浅淡映重门，七节攒成雪满盆。", "贾宝玉", "咏白海棠"),
    Poem("闲趁霜晴试一游，酒杯药盏莫淹留。", "贾宝玉", "访菊"),
    Poem("携锄秋圃自移来，篱畔庭前故故栽。", "贾宝玉", "种菊"),
    Poem("持螯更喜桂阴凉，泼醋擂姜兴欲狂。", "贾宝玉", "螃蟹咏"),
    Poem("酒未开樽句未裁，寻春问腊到蓬莱。", "贾宝玉", "访妙玉乞红梅"),
    Poem("枕上轻寒窗外雨，眼前春色梦中人。", "贾宝玉", "春夜即事"),
    Poem("倦绣佳人幽梦长，金笼鹦鹉唤茶汤。", "贾宝玉", "夏夜即事"),
    Poem("绛芸轩里绝喧哗，桂魄流光浸茜纱。", "贾宝玉", "秋夜即事"),
    Poem("梅魂竹梦已三更，锦罽鹴衾睡未成。", "贾宝玉", "冬夜即事"),
    Poem("恒王好武兼好色，遂教美女习骑射。", "贾宝玉", "姽婳词"),
    Poem("茜纱窗下，我本无缘；黄土垄中，卿何薄命。", "贾宝玉", "芙蓉女儿诔"),
    Poem("名园筑何处？仙境别红尘。", "林黛玉", "世外仙源"),
    Poem("杏帘招客饮，在望有山庄。", "林黛玉", "杏帘在望·代作"),
    Poem("半卷湘帘半掩门，碾冰为土玉为盆。", "林黛玉", "咏白海棠"),
    Poem("无赖诗魔昏晓侵，绕篱欹石自沉音。", "林黛玉", "咏菊"),
    Poem("欲讯秋情众莫知，喃喃负手叩东篱。", "林黛玉", "问菊"),
    Poem("篱畔秋酣一觉清，和云伴月不分明。", "林黛玉", "菊梦"),
    Poem("铁甲长戈死未忘，堆盘色相喜先尝。", "林黛玉", "螃蟹咏"),
    Poem("花谢花飞花满天，红消香断有谁怜？", "林黛玉", "葬花吟"),
    Poem("眼空蓄泪泪空垂，暗洒闲抛却为谁？", "林黛玉", "题帕三绝·其一"),
    Poem("抛珠滚玉只偷潸，镇日无心镇日闲。", "林黛玉", "题帕三绝·其二"),
    Poem("彩线难收面上珠，湘江旧迹已模糊。", "林黛玉", "题帕三绝·其三"),
    Poem("秋花惨淡秋草黄，耿耿秋灯秋夜长。", "林黛玉", "秋窗风雨夕"),
    Poem("粉堕百花洲，香残燕子楼。", "林黛玉", "唐多令·柳絮"),
    Poem("一代倾城逐浪花，吴宫空自忆儿家。", "林黛玉", "五美吟·西施"),
    Poem("肠断乌骓夜啸风，虞兮幽恨对重瞳。", "林黛玉", "五美吟·虞姬"),
    Poem("绝艳惊人出汉宫，红颜命薄古今同。", "林黛玉", "五美吟·明妃"),
    Poem("瓦砾明珠一例抛，何曾石尉重娇娆。", "林黛玉", "五美吟·绿珠"),
    Poem("长揖雄谈态自殊，美人巨眼识穷途。", "林黛玉", "五美吟·红拂"),
    Poem("桃花帘外东风软，桃花帘内晨妆懒。", "林黛玉", "桃花行"),
    Poem("三五中秋夕，清游拟上元。", "林黛玉", "中秋夜大观园即景联句·与史湘云联句"),
    Poem("芳园筑向帝城西，华日祥云笼罩奇。", "薛宝钗", "凝晖钟瑞"),
    Poem("珍重芳姿昼掩门，自携手瓮灌苔盆。", "薛宝钗", "咏白海棠"),
    Poem("怅望西风抱闷思，蓼红苇白断肠时。", "薛宝钗", "忆菊"),
    Poem("诗余戏笔不知狂，岂是丹青费较量。", "薛宝钗", "画菊"),
    Poem("桂霭桐阴坐举觞，长安涎口盼重阳。", "薛宝钗", "螃蟹咏"),
    Poem("白玉堂前春解舞，东风卷得均匀。", "薛宝钗", "临江仙·柳絮"),
    Poem("焦首朝朝还暮暮，煎心日日复年年。", "薛宝钗", "更香·灯谜"),
    Poem("有眼无珠腹内空，荷花出水喜相逢。", "薛宝钗", "竹夫人·灯谜"),
    Poem("神仙昨日降都门，种得蓝田玉一盆。", "史湘云", "咏白海棠和韵·其一"),
    Poem("蘅芷阶通萝薜门，也宜墙角也宜盆。", "史湘云", "咏白海棠和韵·其二"),
    Poem("别圃移来贵比金，一丛浅淡一丛深。", "史湘云", "对菊"),
    Poem("弹琴酌酒喜堪俦，几案婷婷点缀幽。", "史湘云", "供菊"),
    Poem("秋光叠叠复重重，潜度偷移三径中。", "史湘云", "菊影"),
    Poem("岂是绣绒残吐，卷起半帘香雾。", "史湘云", "如梦令·柳絮"),
    Poem("三五中秋夕，清游拟上元。", "史湘云", "中秋夜大观园即景联句·与林黛玉联句"),
    Poem("名园筑出势巍巍，奉命何惭学浅微。", "贾探春", "万象争辉"),
    Poem("斜阳寒草带重门，苔翠盈铺雨后盆。", "贾探春", "咏白海棠"),
    Poem("瓶供篱栽日日忙，折来休认镜中妆。", "贾探春", "簪菊"),
    Poem("露凝霜重渐倾欹，宴赏才过小雪时。", "贾探春", "残菊"),
    Poem("空挂纤纤缕，徒垂络络丝。", "贾探春", "南柯子·柳絮·与贾宝玉合作"),
    Poem("园成景备特精奇，奉命羞题额旷怡。", "贾迎春", "旷性怡情"),
    Poem("山水横拖千里外，楼台高起五云中。", "贾惜春", "文章造化"),
    Poem("秀水明山抱复回，风流文采胜蓬莱。", "李纨", "文采风流"),
    Poem("白梅懒赋赋红梅，逞艳先迎醉眼开。", "李纨", "咏白海棠"),
    Poem("赤壁沉埋水不流，徒留名姓载空舟。", "薛宝琴", "怀古绝句·赤壁怀古"),
    Poem("铜铸金镛振纪纲，声传海外播戎羌。", "薛宝琴", "怀古绝句·交趾怀古"),
    Poem("名利何曾伴汝身，无端被诏出凡尘。", "薛宝琴", "怀古绝句·钟山怀古"),
    Poem("壮士须防恶犬欺，三齐位定盖棺时。", "薛宝琴", "怀古绝句·淮阴怀古"),
    Poem("蝉噪鸦栖转眼过，隋堤风景近如何。", "薛宝琴", "怀古绝句·广陵怀古"),
    Poem("衰草闲花映浅池，桃枝桃叶总分离。", "薛宝琴", "怀古绝句·桃叶渡怀古"),
    Poem("黑水茫茫咽不流，冰弦拨尽曲中愁。", "薛宝琴", "怀古绝句·青冢怀古"),
    Poem("寂寞脂痕渍汗光，温柔一旦付东洋。", "薛宝琴", "怀古绝句·马嵬怀古"),
    Poem("小红骨贱最身轻，私掖偷携强撮成。", "薛宝琴", "怀古绝句·蒲东寺怀古"),
    Poem("不在梅边在柳边，个中谁拾画婵娟。", "薛宝琴", "怀古绝句·梅花观怀古"),
    Poem("汉苑零星有限，隋堤点缀无穷。", "薛宝琴", "西江月·柳絮"),
    Poem("月挂中天夜色寒，清光皎皎影团团。", "香菱", "咏月·其一"),
    Poem("非银非水映窗寒，试看晴空护玉盘。", "香菱", "咏月·其二"),
    Poem("精华欲掩料应难，影自娟娟魄自寒。", "香菱", "咏月·其三"),
    Poem("香篆销金鼎，脂冰腻玉盆。", "妙玉", "中秋夜大观园即景联句·续作"),
    Poem("未卜三生愿，频添一段愁。", "贾雨村", "对月有怀口占五言一律"),
    Poem("时逢三五便团圆，满把晴光护玉栏。", "贾雨村", "中秋对月口占五言一律"),
    Poem("天上一轮才捧出，人间万姓仰头看。", "贾雨村", "对月寓怀口号一绝"),
    Poem("玉在椟中求善价，钗于奁内待时飞。", "甄士隐", "中秋对月"),
    Poem("世人都晓神仙好，惟有功名忘不了。", "跛足道人", "好了歌"),
    Poem("惯养娇生笑你痴，菱花空对雪澌澌。", "癞头和尚", "嘲甄士隐"),
    Poem("无材可去补苍天，枉入红尘若许年。", "曹雪芹", "石上偈"),
    Poem("满纸荒唐言，一把辛酸泪。", "曹雪芹", "自题一绝"),
    Poem("假作真时真亦假，无为有处有还无。", "曹雪芹", "太虚幻境对联"),
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

/** 启动页：仅早晚出现（0-7 早 早安浅色 / 21-24 晚 晚安深色），随机红楼梦诗词，用打包宋体 */
@Composable
fun SplashScreen(onEnter: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isMorning = hour in 0..6           // 早间 0-7
    val isEvening = hour in 21..23         // 晚间 21-24
    val show = isMorning || isEvening      // 仅早晚出现
    if (!show) {
        // 非早晚：异步跳转主界面（避免 composition 期间副作用）
        LaunchedEffect(Unit) { onEnter() }
        return
    }

    val poem = remember { poems.random() }
    val greeting = if (isMorning) "早安" else "晚安"
    // 早 浅色米暖渐变，晚 深色
    val bg = if (isMorning)
        Brush.verticalGradient(listOf(Color(0xFFFFF7E0), Color(0xFFFFE3B3)))
    else
        Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))
    val textColor = if (isMorning) Color(0xFF3A2E1E) else Color(0xFFE8E6E0)
    val subColor = if (isMorning) Color(0xFF8A7A5A) else Color(0xFFA0A0B0)

    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)) {
            Text(greeting,
                fontFamily = songtiFamily,
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(Modifier.height(28.dp))
            // 诗句：整句必须一行放下（最长 20 字，固定 18sp 要 360dp，而可用宽度只有约 280dp）
            FitOneLine(poem.text, maxSize = 18.sp, minSize = 13.sp, color = textColor)
            Spacer(Modifier.height(10.dp))
            // 出处行同样整行不换行：最长的一条「—— 林黛玉《中秋夜大观园即景联句·与史湘云联句》」
            // 有 22 字，固定 14sp 要 308dp，必折行
            FitOneLine("—— ${poem.author}《${poem.title}》",
                maxSize = 14.sp, minSize = 10.sp, color = subColor, lineHeightRatio = 1.5f)
            Spacer(Modifier.height(60.dp))
            Button(
                onClick = onEnter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMorning) Accent else Color(0xFF4A6FA5)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                Text("开启时序集", fontFamily = songtiFamily, fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
    }
}
