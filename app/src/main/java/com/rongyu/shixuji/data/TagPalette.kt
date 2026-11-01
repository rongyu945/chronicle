package com.rongyu.shixuji.data

/**
 * 记账标签可选色（24 色，8 列 × 3 行）。
 *
 * 色源：《中国风传统色卡》159 色（用户 2026-11-01 提供）。
 * 用户反馈旧色板「灰灰的脏脏的」—— 旧 24 色是把色相均分后压在中低明度/低饱和里挑的，
 * 看着像蒙了一层土。新色板改成**传统色里的清透色**：
 *  - 行1 朱（红橙）：粉红/嫣红/妃色/石榴红/樱桃红/绛紫/琥珀/橘红
 *  - 行2 黄绿：姜黄/杏黄/秋香色/葱绿/青翠/翡翠色/苍翠/艾绿
 *  - 行3 青蓝紫墨：湖蓝/苍青/靛青/藏青/宝蓝/紫色/丁香色/墨色
 * 刻意剔掉了传统色里偏浊的棕/褐/橄榄（茶色、驼色、赭、竹青、豆青…）——它们正是
 * 「脏」的来源；保留的清透色饱和度都在 C ≥ 25 或明度 L ≥ 70，落在白卡上不发灰。
 *
 * 约束（TagPaletteTest 守护，改色必须同时满足）：
 *  - 两两色差 ΔE76 最小 21.0、中位 69.5（上一版是 26 / 87，但那一版整体发浊）；
 *  - 与「无标签灰」[UNLABELED] 的最小色差 26.7。
 *
 * 颜色是**按值存进标签记录**的（AccTagEntity.color），所以：
 *  - 换色板不会自动改已有标签的颜色 —— 本次例外，用户要求也重排已有标签，
 *    由 AppContainer.MIGRATION_7_8 一次性把已有标签按 id 顺序重新涂抹
 *    （旧值写进 app_prefs.acc_tag_colors_v7 作回滚参照，v6 那轮的原值仍在 acc_tag_colors_v6）；
 *  - 老标签若还存着旧色板的颜色，选择器顶部会单独放一格它的原色 + 「当前」，
 *    否则打开修改会一个圈都不亮，像是颜色丢了。
 */
object TagPalette {
    val ROWS: List<Pair<String, List<Long>>> = listOf(
        // 朱：粉红 嫣红 妃色 石榴红 樱桃红 绛紫 琥珀 橘红
        "行1" to listOf(0xFFFFB3A7, 0xFFEF7A82, 0xFFED5736, 0xFFF20C00, 0xFFC93756, 0xFF8C4356, 0xFFCA6924, 0xFFFF7500),
        // 黄绿：姜黄 杏黄 秋香色 葱绿 青翠 翡翠色 苍翠 艾绿
        "行2" to listOf(0xFFFFC773, 0xFFFFA631, 0xFFD9B611, 0xFF9ED900, 0xFF00E079, 0xFF3DE1AD, 0xFF519A73, 0xFFA4E2C6),
        // 青蓝紫墨：湖蓝 苍青 靛青 藏青 宝蓝 紫色 丁香色 墨色
        "行3" to listOf(0xFF30DFF3, 0xFF7397AB, 0xFF177CB0, 0xFF2E4E7E, 0xFF4B5CC4, 0xFF8D4BBB, 0xFFCCA4E3, 0xFF50616D)
    )

    /** 与 [ROWS] 一一对应的传统色名（只作文档/调试参考，界面不显示） */
    val NAMES: List<String> = listOf(
        "粉红", "嫣红", "妃色", "石榴红", "樱桃红", "绛紫", "琥珀", "橘红",
        "姜黄", "杏黄", "秋香色", "葱绿", "青翠", "翡翠色", "苍翠", "艾绿",
        "湖蓝", "苍青", "靛青", "藏青", "宝蓝", "紫色", "丁香色", "墨色"
    )

    /** 扁平化的 24 色，顺序与网格一致（换色板/迁移都按这个顺序取色） */
    val ALL: List<Long> = ROWS.flatMap { it.second }

    /**
     * 「没有标签」用的中性灰（苍白）。不属于可选色板 —— 选择器里不显示，
     * 账目行/饼图遇到无标签条目才用它。测试会断言它与 24 色的最小色差。
     */
    const val UNLABELED: Long = 0xFFD1D9E0

    /** 第 n 个标签该用的颜色（超过 24 个标签就循环复用） */
    fun colorAt(index: Int): Long = ALL[((index % ALL.size) + ALL.size) % ALL.size]

    /** 不在当前色板里的颜色（老色板 / 自定义）—— 选择器要单独给它们留一格 */
    fun isInPalette(color: Long): Boolean = ALL.any { it == color }
}

/**
 * 记账弹窗里标签的排列顺序：**最近 30 天最常用的排前面**，没用过的排最后。
 *
 * 用户 2026-10-25 要求：「记账界面的标签按照从最常用到最不常用排序」，口径为最近 30 天。
 * - 同次数保持标签本来的顺序（`sortedWith` 是稳定排序，入参来自 `ORDER BY id ASC`）；
 * - 没用过的（usage 里没有或为 0）按原顺序排在末尾；
 * - 这样每月自然重排一次，且不会因为一次误记就长期霸榜。
 */
fun orderTagsByUsage(tags: List<AccTagEntity>, usage: Map<String, Int>): List<AccTagEntity> =
    tags.sortedWith(compareByDescending { usage[it.name] ?: 0 })
