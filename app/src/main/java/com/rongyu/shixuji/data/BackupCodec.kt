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

    /** 备份文件**格式**版本。改导出结构时 +1；恢复时按来源版本决定字段怎么读。 */
    const val SCHEMA = 2

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
    private const val MAX_FONT_SCALE = 2.0f

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
    ) {
        /** 文件里一共有多少条记录（提示语用） */
        val count: Int get() =
            (todos?.size ?: 0) + (accounts?.size ?: 0) + (loopTodos?.size ?: 0) +
                (accTags?.size ?: 0) + (quickEntries?.size ?: 0)

        /** 恢复前给用户看的一句话概览 */
        fun summary(): String = buildString {
            append("待办 ${todos?.size ?: 0} 条 · 账目 ${accounts?.size ?: 0} 条")
            append(" · 标签 ${accTags?.size ?: 0} 个 · 每周固定 ${loopTodos?.size ?: 0} 条")
            append(" · 常用记账 ${quickEntries?.size ?: 0} 个")
            val missing = buildList {
                if (todos == null) add("待办")
                if (accounts == null) add("账目")
                if (accTags == null) add("标签")
                if (loopTodos == null) add("每周固定")
                if (quickEntries == null) add("常用记账")
            }
            if (missing.isNotEmpty()) append("（文件里没有${missing.joinToString("、")}这一段，恢复时不动它）")
            appVersion?.let { append("\n备份来自 时序集 $it") }
            append("（格式 $SCHEMA / 数据库 $dbVersion）")
        }
    }

    sealed class Result {
        class Ok(val data: Data) : Result()
        /** [code] 取 ERR_* 之一；[why] 是给用户看的一句人话 */
        class Bad(val code: Int, val why: String) : Result()
    }

    /** 解析 + 校验。任何情况下都不会抛异常、不会碰数据库。 */
    fun parse(json: String): Result {
        val root = try {
            JSONObject(json)
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
                    priority = num(o, "priority").coerceIn(0L, 3L).toInt(),
                    sortOrder = num(o, "sortOrder").toInt(),
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
            val loops = section(root, "loopTodos", "每周固定") { o ->
                LoopTodo(
                    id = positive(num(o, "id")),
                    title = text(o, "title"),
                    detail = text(o, "detail"),
                    dayOfWeek = num(o, "dayOfWeek").coerceIn(1L, 7L).toInt(),
                    doneThisWeek = flag(o, "doneThisWeek"),
                )
            }
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
                    sortOrder = num(o, "sortOrder").toInt(),
                )
            }
            Result.Ok(
                Data(
                    todos = todos, accounts = accounts, loopTodos = loops, accTags = tags,
                    quickEntries = quicks,
                    prefs = readPrefs(root),
                    appVersion = if (root.has("appVersion")) text(root, "appVersion") else null,
                    dbVersion = num(root, "dbVersion", 0L).toInt(),
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

    private fun num(o: JSONObject, key: String, def: Long = 0L): Long = when (val v = raw(o, key)) {
        null -> def
        is Number -> v.toLong()
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
    private fun money(o: JSONObject, key: String, label: String): Long {
        val v = num(o, key)
        if (v > MAX_CENTS || v < -MAX_CENTS) throw Corrupt("$label 的金额越界（$v 分）")
        return v
    }

    private fun positive(v: Long): Long = if (v > 0) v else 0

    /** 时间戳：负数 / 远期（多半是字段串了）一律归 0（= 无期限），不拒绝整份文件 */
    private fun saneTs(v: Long): Long = if (v in 0..MAX_TS) v else 0L
}
