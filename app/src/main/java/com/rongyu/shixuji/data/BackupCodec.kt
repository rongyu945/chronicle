package com.rongyu.shixuji.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件的**解析 / 校验层**（恢复时先整份读进来验，再动数据库）。
 *
 * 为什么把「读文件」从 SettingsViewModel 里拆出来、且必须是纯函数：
 *
 * ① **原实现是一边解析一边 clear() + insert()**。文件里只要有一条坏记录（某个字段是字符串、
 *    或者前面几段正常后面一段不是数组），流程就会停在半途 —— 库里已经被 clear 掉、只恢复了一半，
 *    而用户看到的是「恢复失败」。恢复是不可逆操作，必须**先全部读进来、校验通过，再一次性落库**。
 *
 * ② **「这段不在文件里」和「这段是空数组」是两种不同意思**（原实现不分）：
 *    前者说明这份备份根本没导出那张表（旧版本文件 / 手改过的文件），恢复时**不能动**那张表；
 *    后者说明用户当时确实没有数据，恢复时要清空。原实现不看存在性就直接 clear()，
 *    于是一份只有 todos 的文件会把账目、标签、每周固定、常用记账全部清空 —— 静默丢数据。
 *
 * ③ 拆成纯函数就能在 JVM 上单测（见 test/…/BackupCodecTest.kt），不必每次都装到设备上试。
 *
 * 校验策略（宁可拒绝整份文件，也不要写坏数据）：
 *  - 结构不对（不是 JSON 对象、缺少待办/账目两段、某段不是数组、某条不是对象）→ 拒绝；
 *  - 金额/时间戳越界（只可能是文件坏了）→ 拒绝；
 *  - 只影响显示、不影响数据完整性的字段（优先级、类型、星期几、时间戳为负）→ **夹到合法范围**后接受。
 */
object BackupCodec {

    /**
     * 备份文件**格式**版本。改导出结构时 +1；恢复时按来源版本决定字段怎么读。
     *
     * | 版本 | 变化 |
     * |---|---|
     * | 1 | 最早的文件，没有 schema 字段（当作 1） |
     * | 2 | 循环待办改成 startDate/nextDate/intervalDays/doneDates |
     * | 3 | 7.5.1：偏好白名单补齐（撤回时长、四个问候边界）+ 写出 `loopDayEpoch:"civil"` |
     */
    const val SCHEMA = 3

    /** -1 = 不是本站备份 / 结构不对 */
    const val ERR_FORMAT = -1
    /** -2 = 来自更新的 App 版本（字段读不懂，拒绝而不是静默丢字段） */
    const val ERR_NEWER = -2
    /** -3 = 内容损坏（字段类型错、金额越界） */
    const val ERR_CORRUPT = -3

    /** 金额上限：一百万亿元（分）。超过它只可能是文件坏了，不是真数据 */
    private const val MAX_CENTS = 100_000_000_000_000L
    /** 时间戳上限：2100-01-01。超过它多半是把金额写进了时间字段 */
    private const val MAX_TS = 4_102_444_800_000L
    /** 字号档位合法区间（设置里只有 0.9 / 1.0 / 1.15 三档，留点余量） */
    private const val MIN_FONT_SCALE = 0.5f
    /** 与 ui/Undo.kt 的 UNDO_MIN_SEC..UNDO_MAX_SEC 同一个区间（2–16 秒） */
    private const val MIN_UNDO_MS = 2_000
    private const val MAX_UNDO_MS = 16_000
    private const val MAX_FONT_SCALE = 2.0f

    /**
     * **可迁移的用户偏好**白名单：备份里的 JSON 键 → `app_prefs` 的键。
     *
     * 三处共用这一份，别各写一套（旧版就是导出 5 个键、恢复替换又照着另一份清单删）：
     * - [BackupWriter.buildJson] 导出；
     * - [readPrefs] 解析；
     * - 恢复时按「文件里带了哪几个键」把它们整组替换。
     *
     * **只放用户自己设过的东西**。`auto_backup_at` / `auto_backup_where` / `auto_backup_file` /
     * `auto_backup_off` / `loop_epoch_day_v2` 这类是**运行状态**，各机器独立，不能跟着备份走：
     * A 机的「上次备份时间」搬到 B 机毫无意义，`loop_epoch_day_v2` 更是迁移标记。
     */
    val PREF_MAP: List<Pair<String, String>> = listOf(
        "pref_budget" to "budget_month",
        "pref_font_scale" to "font_scale",
        "pref_songti" to "songti",
        "pref_dark" to "dark",
        "pref_tab_order" to "tab_order",
        "pref_undo_ms" to "undo_ms",
        "pref_greet_morning_start" to GreetingTimes.KEY_MORNING_START,
        "pref_greet_morning_end" to GreetingTimes.KEY_MORNING_END,
        "pref_greet_noon_start" to GreetingTimes.KEY_NOON_START,
        "pref_greet_noon_end" to GreetingTimes.KEY_NOON_END,
        "pref_greet_night_start" to GreetingTimes.KEY_NIGHT_START,
        "pref_greet_night_end" to GreetingTimes.KEY_NIGHT_END,
    )

    /** `app_prefs` 里属于「用户偏好」的全部键（恢复时整组替换用） */
    val PREF_KEYS: List<String> = PREF_MAP.map { it.second }

    /** 某一段读坏了：带上人话原因，一路抛到 [parse] 变成 Bad */
    private class Corrupt(val why: String) : Exception(why)

    /**
     * 一份备份的全部内容。
     *
     * **每个集合为 null 表示「文件里没有这一段」→ 恢复时不要碰那张表**；
     * 空 list 表示文件里写的是空数组 → 恢复时把那张表清空。
     */
    class Data(
        val todos: List<Todo>?,
        val accounts: List<Account>?,
        val loopTodos: List<LoopTodo>?,
        val accTags: List<AccTagEntity>?,
        val quickEntries: List<QuickEntry>?,
        val prefs: Map<String, String>,
        val appVersion: String?,
        val dbVersion: Int,
        /** 导入时把旧口径的 [LoopTodo.doneDates] 平移了几天（0 = 没动） */
        val loopDayShifted: Int = 0,
    ) {
        /** 文件里一共有多少条记录（提示语用） */
        val count: Int get() =
            (todos?.size ?: 0) + (accounts?.size ?: 0) + (loopTodos?.size ?: 0) +
                (accTags?.size ?: 0) + (quickEntries?.size ?: 0)

        /** 恢复前给用户看的一句话概览 */
        fun summary(): String = buildString {
            append("待办 ${todos?.size ?: 0} 条 · 账目 ${accounts?.size ?: 0} 条")
            append(" · 标签 ${accTags?.size ?: 0} 个 · 循环待办 ${loopTodos?.size ?: 0} 条")
            append(" · 常用记账 ${quickEntries?.size ?: 0} 个")
            val missing = buildList {
                if (todos == null) add("待办")
                if (accounts == null) add("账目")
                if (accTags == null) add("标签")
                if (loopTodos == null) add("循环待办")
                if (quickEntries == null) add("常用记账")
            }
            if (missing.isNotEmpty()) append("（文件里没有${missing.joinToString("、")}这一段，恢复时不动它）")
            appVersion?.let { append("\n备份来自 时序集 $it") }
            append("（格式 $SCHEMA / 数据库 $dbVersion）")
            if (loopDayShifted != 0) {
                append("\n旧版备份：循环待办的勾选日期已按老口径平移 ${loopDayShifted} 天")
            }
        }
    }

    sealed class Result {
        class Ok(val data: Data) : Result()
        /** [code] 取 ERR_* 之一；[why] 是给用户看的一句人话 */
        class Bad(val code: Int, val why: String) : Result()
    }

    /** 解析 + 校验。任何情况下都不会抛异常、不会碰数据库。 */
    fun parse(json: String): Result {
        // ★ 先剥 BOM 再 trim：文件被 Windows 记事本 / 网盘 / 微信「另存为」处理过时，
        //   UTF-8 BOM（U+FEFF）会留在字符串首位，`JSONObject` 直接抛异常，
        //   用户看到「不是有效的 JSON 文件」——而内容其实完好无损。
        val text = json.removePrefix("\uFEFF").trim()
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return Result.Bad(ERR_FORMAT, "不是有效的 JSON 文件")
        }
        // 至少要有一段核心数据，否则多半是选错了文件（比如把账单 CSV 选进来了）
        if (!root.has("todos") && !root.has("accounts")) {
            return Result.Bad(ERR_FORMAT, "这不像时序集的备份文件（找不到待办/账目）")
        }
        // 最早的备份文件没写 schema 字段 → 当 1 处理（字段少，下面都按默认值读）
        val srcSchema = num(root, "schema", 1L).toInt()
        if (srcSchema > SCHEMA) return Result.Bad(ERR_NEWER, "备份来自更新的 App 版本，请先升级再恢复")

        return try {
            val todos = section(root, "todos", "待办") { o ->
                Todo(
                    id = positive(num(o, "id")),
                    title = text(o, "title"),
                    detail = text(o, "detail"),
                    done = flag(o, "done"),
                    dueDate = saneTs(num(o, "dueDate")),
                    createdAt = saneTs(num(o, "createdAt")),
                    // 仍然是 0..3：0 = 「无」，数据层照旧认它（旧版 App 也读得懂）。
                    // 新版只是在**选择器里不再提供**这一档，不代表 0 非法。
                    priority = num(o, "priority").coerceIn(0L, 3L).toInt(),
                    sortOrder = num(o, "sortOrder")
                        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                    doneAt = saneTs(num(o, "doneAt")),
                )
            }
            val accounts = section(root, "accounts", "账目") { o ->
                val cents = money(o, "amountCents", "账目")
                Account(
                    id = positive(num(o, "id")),
                    title = text(o, "title"),
                    amountCents = cents,
                    type = if (num(o, "type") == 1L) 1 else 0,
                    tag = text(o, "tag"),
                    date = saneTs(num(o, "date")),
                )
            }
            // 旧备份的 doneDates 是「本地零点 / 86400000」，东半球比公历日序号小 1。
            // 库里那份一次性迁移（loop_epoch_day_v2）只管**库里原有**的数据，
            // 导入进来的记录它不会再管一次 —— 所以必须在这里按来源版本补上。
            val appVer = if (root.has("appVersion")) text(root, "appVersion") else null
            val dayShift = legacyDoneDateShift(root, srcSchema, appVer)
            val loops = section(root, "loopTodos", "循环待办") { o -> loopOf(o, dayShift) }
            val tags = section(root, "accTags", "标签") { o ->
                AccTagEntity(
                    id = positive(num(o, "id")),
                    name = text(o, "name"),
                    color = num(o, "color"),
                    icon = text(o, "icon"),
                )
            }
            val quicks = section(root, "quickEntries", "常用记账") { o ->
                QuickEntry(
                    id = positive(num(o, "id")),
                    title = text(o, "title"),
                    amountCents = money(o, "amountCents", "常用记账"),
                    type = if (num(o, "type") == 1L) 1 else 0,
                    tag = text(o, "tag"),
                    sortOrder = num(o, "sortOrder")
                        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                )
            }
            Result.Ok(
                Data(
                    todos = todos, accounts = accounts, loopTodos = loops, accTags = tags,
                    quickEntries = quicks,
                    prefs = readPrefs(root),
                    appVersion = appVer,
                    dbVersion = num(root, "dbVersion", 0L).toInt(),
                    loopDayShifted = if (dayShift != 0L && loops?.any { it.doneDates.isNotBlank() } == true)
                        dayShift.toInt() else 0,
                )
            )
        } catch (e: Corrupt) {
            Result.Bad(ERR_CORRUPT, e.why)
        } catch (e: Exception) {
            Result.Bad(ERR_CORRUPT, "文件内容有问题：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 设置项。数值类的做范围校验 —— 字号若是被写坏成 "999"，
     * 恢复后界面会直接崩（所有字被放大 999 倍），这种宁可丢弃这一项也不要写进去。
     */
    private fun readPrefs(root: JSONObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        // 预算：整数「分」，0 ~ 上限
        if (root.has("pref_budget")) {
            val v = text(root, "pref_budget")
            val cents = v.toLongOrNull()
            if (cents != null && cents in 0..MAX_CENTS) out["budget_month"] = v
        }
        // 字号
        if (root.has("pref_font_scale")) {
            val v = text(root, "pref_font_scale")
            val f = v.toFloatOrNull()
            if (f != null && f in MIN_FONT_SCALE..MAX_FONT_SCALE) out["font_scale"] = v
        }
        if (root.has("pref_songti")) {
            val v = text(root, "pref_songti")
            if (v == "0" || v == "1") out["songti"] = v
        }
        if (root.has("pref_dark")) {
            val v = text(root, "pref_dark")
            if (v == "0" || v == "1" || v == "2") out["dark"] = v
        }
        if (root.has("pref_tab_order")) {
            // 只做结构校验：具体合不合法由 MainScreen 那份解析负责（它会过滤未知项并要求全覆盖）
            val v = text(root, "pref_tab_order")
            if (v.isNotBlank() && v.none { it == '\n' || it == '\r' }) out["tab_order"] = v
        }
        // 撤回停留时间：与设置页滑块同一区间（ui/Undo.kt 的 UNDO_MIN_SEC..UNDO_MAX_SEC）
        if (root.has("pref_undo_ms")) {
            val v = text(root, "pref_undo_ms")
            val ms = v.toIntOrNull()
            if (ms != null && ms in MIN_UNDO_MS..MAX_UNDO_MS) out["undo_ms"] = v
        }
        // 四个问候边界：必须**成组**判断 —— 单独夹一个会让三段重叠或反序。
        // 思路与编辑页一致：把文件里的四个值读出来，`isValid()` 过了才整组收下，
        // 任何一个越界/缺项就整组丢弃（回归默认），不让界面画出反向墨线。
        // 7.6.1：六个边界（多了早安起点、晚安终点）。旧备份只有四个键 —— 缺的那两个
        // 按默认值（00:00 / 24:00）补齐，这样 7.6.0 及更早的文件还能正常恢复。
        val greetEdges = listOf(
            Triple("pref_greet_morning_start", GreetingTimes.KEY_MORNING_START, GreetingEdge.MORNING_START),
            Triple("pref_greet_morning_end", GreetingTimes.KEY_MORNING_END, GreetingEdge.MORNING_END),
            Triple("pref_greet_noon_start", GreetingTimes.KEY_NOON_START, GreetingEdge.NOON_START),
            Triple("pref_greet_noon_end", GreetingTimes.KEY_NOON_END, GreetingEdge.NOON_END),
            Triple("pref_greet_night_start", GreetingTimes.KEY_NIGHT_START, GreetingEdge.NIGHT_START),
            Triple("pref_greet_night_end", GreetingTimes.KEY_NIGHT_END, GreetingEdge.NIGHT_END),
        )
        val greetDefaults = mapOf(
            GreetingEdge.MORNING_START to GreetingTimes.DEFAULT.morningStart,
            GreetingEdge.MORNING_END to GreetingTimes.DEFAULT.morningEnd,
            GreetingEdge.NOON_START to GreetingTimes.DEFAULT.noonStart,
            GreetingEdge.NOON_END to GreetingTimes.DEFAULT.noonEnd,
            GreetingEdge.NIGHT_START to GreetingTimes.DEFAULT.nightStart,
            GreetingEdge.NIGHT_END to GreetingTimes.DEFAULT.nightEnd,
        )
        if (greetEdges.any { root.has(it.first) }) {
            val mins = greetEdges.associateWith { (json, _, edge) ->
                if (root.has(json)) text(root, json).toIntOrNull() else greetDefaults.getValue(edge)
            }
            if (mins.values.none { it == null }) {
                val v = { e: GreetingEdge -> mins.getValue(greetEdges.first { it.third == e })!! }
                val t = GreetingTimes(
                    morningStart = v(GreetingEdge.MORNING_START),
                    morningEnd = v(GreetingEdge.MORNING_END),
                    noonStart = v(GreetingEdge.NOON_START),
                    noonEnd = v(GreetingEdge.NOON_END),
                    nightStart = v(GreetingEdge.NIGHT_START),
                    nightEnd = v(GreetingEdge.NIGHT_END),
                )
                if (t.isValid()) {
                    greetEdges.forEach { (_, pref, edge) -> out[pref] = t.valueOf(edge).toString() }
                }
            }
        }
        return out
    }

    // ===== 读取小工具：类型不对就报「坏」（而不是静默取默认值）=====

    /** 读一段数组；**键不存在返回 null**（= 这份文件没导出这张表），存在但不是数组 → 坏 */
    private fun <T> section(root: JSONObject, key: String, label: String, read: (JSONObject) -> T): List<T>? {
        if (!root.has(key)) return null
        val arr: JSONArray = root.optJSONArray(key) ?: throw Corrupt("$label 那一段不是数组")
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: throw Corrupt("$label 第 ${i + 1} 条不是一条记录")
            try {
                out += read(o)
            } catch (e: Corrupt) {
                throw e
            } catch (e: Exception) {
                throw Corrupt("$label 第 ${i + 1} 条读不出来：${e.message ?: e.javaClass.simpleName}")
            }
        }
        return out
    }

    private fun raw(o: JSONObject, key: String): Any? = if (o.has(key) && !o.isNull(key)) o.opt(key) else null

    /**
     * 读整数。
     *
     * ★ 7.5.1：`Number` 不再直接 `toLong()` —— 那样 `12.9` 会被静默截成 `12`，
     * 于是「文件里写错了」变成「导入了一个看起来合法的错值」（金额少 9 毛、日期错一天）。
     * 现在整数域要求**真的整数**：`12` / `12.0` 收，`12.9` 判坏。
     */
    private fun num(o: JSONObject, key: String, def: Long = 0L): Long = when (val v = raw(o, key)) {
        null -> def
        is Int, is Long, is Short, is Byte -> v.toLong()
        is Number -> {
            val d = v.toDouble()
            if (d.isNaN() || d.isInfinite() || d != Math.floor(d)) {
                throw Corrupt("「$key」应该是整数（文件里是 $v）")
            }
            if (d < -9.2e18 || d > 9.2e18) throw Corrupt("「$key」超出整数范围（$v）")
            v.toLong()
        }
        is String -> v.trim().toLongOrNull() ?: throw Corrupt("「$key」不是数字")
        else -> throw Corrupt("「$key」不是数字")
    }

    private fun text(o: JSONObject, key: String, def: String = ""): String = when (val v = raw(o, key)) {
        null -> def
        is String -> v
        is Number, is Boolean -> v.toString()
        else -> throw Corrupt("「$key」不是文本")
    }

    /** 勾选状态：宽容一点（旧文件里可能是 0/1 数字或 "true" 字符串），读不懂就当 false */
    private fun flag(o: JSONObject, key: String): Boolean = when (val v = raw(o, key)) {
        null -> false
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "true" || v == "1"
        else -> false
    }

    /** 金额：越界一律拒绝（宁可整份不恢复，也不要写进一笔莫名其妙的天文数字） */
    /**
     * 金额（分）。
     *
     * ★ 7.5.1：要求 **≥ 0**。全 App 的口径是「金额存绝对值，正负由 `type` 决定」——
     * 输入框（`parseYuanToCents`）、算式（`AmountExpr`）、详情页都这么写。备份文件若带负数，
     * 界面再按 type 加一次符号就成了双重负号（支出显示成收入、饼图对不上）。
     * 导入格式不该比输入控件更宽松，因此负数直接判坏，让用户看到「哪一条金额不对」。
     */
    private fun money(o: JSONObject, key: String, label: String): Long {
        val v = num(o, key)
        if (v < 0) throw Corrupt("$label 的金额不能是负数（$v 分）")
        if (v > MAX_CENTS) throw Corrupt("$label 的金额越界（$v 分）")
        return v
    }

    /**
     * 一条循环待办。
     *
     * 新备份（5.5.0 起）直接有 startDate / nextDate / intervalDays / doneDates；
     * 旧备份只有 dayOfWeek（每周几）—— 按「间隔 7 天」还原成两个日期：
     * 下一次提醒 = 今天起最近的那个星期几（含今天），开始日期 = 它往前 7 天。
     * 旧备份里的 doneThisWeek 无法定位到具体哪一天，只能丢掉那一个勾（不影响待办本身）。
     */
    /**
     * `doneDates` 清洗：只留**合法日序号**、去重、升序、最多留最近 [DONE_KEEP] 个。
     *
     * ★ 7.5.2（审查 5.4）：上限必须和迁移函数共用 [LoopSchedule.isDayNumber]。
     *   旧实现只过滤 `>= 0` —— 更老版本写进这个字段的**毫秒时间戳**（1.7e12 量级）
     *   会当成年份一万多的「日序号」保留下来，排序后占满「只留最近 120 个」的名额，
     *   把真实的完成记录挤出去。不认识的值一律丢弃，不留永远匹配不上的数字。
     */
    private fun cleanDoneDates(raw: String): String {
        val nums = raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { LoopSchedule.isDayNumber(it) }
            .distinct()
            .sorted()
        val kept = if (nums.size > DONE_KEEP) nums.takeLast(DONE_KEEP) else nums
        return kept.joinToString(",")
    }

    private const val DONE_KEEP = 120

    /**
     * 这份备份的 `doneDates` 是不是**旧口径**（`floorDiv(本地零点, 86400000)`），需要平移几天。
     *
     * 判定顺序：
     * 1. 文件写了 `loopDayEpoch == "civil"` → 新口径，不动（schema 3 起一律写这一项）；
     * 2. 没写就看 `appVersion`：**6.9.4 之前**的版本用旧口径（逐版比对过归档源码，
     *    6.9.3 还是 `floorDiv`，6.9.4 起才换成 [LoopSchedule.daysFromCivil]）；
     * 3. 版本也读不出来（很老的手改文件）→ 不动，宁可疑而少动，别把好数据再搅乱一次。
     *
     * 平移量沿用库内迁移同一套口径（[LoopSchedule.legacyDayShift]，东半球 1 天、西半球 0 天）。
     */
    private fun legacyDoneDateShift(root: JSONObject, srcSchema: Int, appVersion: String?): Long {
        val marker = if (root.has("loopDayEpoch")) text(root, "loopDayEpoch") else null
        if (marker == "civil") return 0
        if (marker != null) return 0          // 未来口径：读不懂就不动
        if (srcSchema >= SCHEMA) return 0     // 理论上 schema 3+ 一定带标记，兜底
        val ver = appVersion ?: return 0
        return if (versionAtLeast(ver, 6, 9, 4)) 0 else LoopSchedule.legacyDayShift()
    }

    /** `"6.9.4"` → 与 (6,9,4) 比较。只认开头的 `主.次.修`，后缀（-beta）忽略。 */
    internal fun versionAtLeast(version: String, major: Int, minor: Int, patch: Int): Boolean {
        val nums = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(version.trim())
            ?: return false
        val a = nums.groupValues[1].toIntOrNull() ?: return false
        val b = nums.groupValues[2].toIntOrNull() ?: 0
        val c = nums.groupValues[3].toIntOrNull() ?: 0
        return when {
            a != major -> a > major
            b != minor -> b > minor
            else -> c >= patch
        }
    }

    private fun loopOf(o: JSONObject, dayShift: Long): LoopTodo {
        val start = saneTs(num(o, "startDate"))
        val next = saneTs(num(o, "nextDate"))
        if (start > 0 && next > 0) {
            // 间隔与两个日期对不上时以日期为准（间隔本来就是算出来的，字段可能是旧的）
            val s = LoopSchedule.dayStart(start)
            // ★ 下一次提醒早于开始日期 = 坏数据。`intervalOf` 会把它夹成 1 天，
            //   于是 `isDueOn` 的 floorMod(diff,1) 恒为 0 —— 这条循环从恢复那天起**每天都出现**。
            //   UI 有红字拦（下次提醒不能早于开始），但备份文件/手改文件不经过 UI。
            //   这里归到开始那天（最保守：用户看得见、能自己改），而不是整天刷屏。
            val n = LoopSchedule.dayStart(next).takeIf { it >= s } ?: s
            return LoopTodo(
                id = positive(num(o, "id")),
                title = text(o, "title"),
                detail = text(o, "detail"),
                startDate = s,
                nextDate = n,
                intervalDays = LoopSchedule.intervalOf(s, n),
                // ★ doneDates 是「哪几天勾过」的逗号串，必须清洗后入库：
                //   旧版原样收下任意长度/含非数字的字符串，之后每次勾选都要 split 它，
                //   也会被原样写回备份文件、一路传下去。这里只留数字、去重、按数值排序、留最近 120 个。
                // 旧口径的备份先平移再清洗（清洗会排序去重，顺序无所谓）
                doneDates = cleanDoneDates(
                    LoopSchedule.shiftDoneDates(text(o, "doneDates"), dayShift)
                ),
                // v10 新增；老备份没有这个键 → 0，恢复后按 nextDate 排（= 升级前的行为）
                sortOrder = num(o, "sortOrder").coerceIn(0L, 1_000_000L).toInt(),
            )
        }
        val dow = num(o, "dayOfWeek").coerceIn(1L, 7L).toInt()
        val cal = java.util.Calendar.getInstance()
        val todayIso = if (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY) 7
        else cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        cal.add(java.util.Calendar.DAY_OF_MONTH, ((dow - todayIso + 7) % 7).toInt())
        val nextMs = LoopSchedule.dayStart(cal.timeInMillis)
        val startMs = (java.util.Calendar.getInstance().apply {
            timeInMillis = nextMs; add(java.util.Calendar.DAY_OF_MONTH, -7)
        }).timeInMillis
        return LoopTodo(
            id = positive(num(o, "id")),
            title = text(o, "title"),
            detail = text(o, "detail"),
            startDate = startMs,
            nextDate = nextMs,
            intervalDays = 7,
            doneDates = "",
        )
    }

    private fun positive(v: Long): Long = if (v > 0) v else 0

    /** 时间戳：负数 / 远期（多半是字段串了）一律归 0（= 无期限），不拒绝整份文件 */
    private fun saneTs(v: Long): Long = if (v in 0..MAX_TS) v else 0L
}
