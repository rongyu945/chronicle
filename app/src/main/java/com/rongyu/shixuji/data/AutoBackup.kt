package com.rongyu.shixuji.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import android.util.Log
import com.rongyu.shixuji.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份。
 *
 * 由来（2026-11-01）：一次误操作把用户手机上的本 App 卸载了，由于所有数据都在 App 私有目录里，
 * 卸载 = 待办/账目/标签全部归零。用户要求：**改任何一项条目之后都自动更新备份**，
 * 而且备份必须活得比 App 久 —— 只写 App 私有目录等于白备份。
 *
 * 因此写入两处：
 *  1. `Download/时序集备份/时序集_自动备份.json`（共享存储，走 MediaStore，**不需要任何权限**，API 29+）
 *     —— 卸载重装后仍在，能在系统「文件/下载」里看到，也能被本 App 的「备份/恢复 → 从备份文件恢复」选中；
 *     重装换了 uid 之后这个名字可能被上一版的行占死，此时自动改叫 `时序集_自动备份(2).json`（见 writeLatest）。
 *  2. App 私有目录里的一份副本（`auto_backup.json`）—— API 28 及以下没有 MediaStore.Downloads，
 *     这就是唯一的落点；同时也方便 App 内部诊断。
 *
 * 另外每天单独留一份 `时序集_自动备份_YYYY-MM-DD.json`（只写一次，最多留 10 份）：
 * 「最新」那份是被反复覆盖的，万一某次写坏/写空，还有当天的快照兜底。
 *
 * 触发方式：Room 的 [androidx.room.InvalidationTracker] —— 任何表被写（新增/修改/删除/勾选/恢复）
 * 都会回调，不需要在几十个 ViewModel 调用点各插一句（漏一个就少一处备份）。
 * 改动密集时做 1.5 秒防抖，避免连续撤销/拖动顺序时反复写盘。
 *
 * 注意：观察的表里**不含 app_prefs** —— 本类自己要把「上次备份时间」写进 app_prefs，
 * 若连它也观察就会「备份 → 写偏好 → 又触发备份」无限自激。
 */
object AutoBackup {

    /** 上次自动备份完成的时刻（ms），0 = 还没备份过 */
    const val PREF_LAST_AT = "auto_backup_at"

    /** 上次备份写到哪（给设置页显示） */
    const val PREF_LAST_WHERE = "auto_backup_where"

    /** 用户关掉自动备份时置 "1" */
    const val PREF_DISABLED = "auto_backup_off"

    /**
     * 上次真正写进共享存储的文件名。
     *
     * 为什么需要记它：重装一次 uid 就变，上一版留下的同名文件从此既写不进也删不掉
     *（见 [writeLatest] 的长注释）。换了名字之后记下来，下轮直接命中，不用每次都去撞那个
     * 注定失败的名字、更不会再攒一堆 `(1)(2)(3)` 残骸。
     */
    const val PREF_NAME = "auto_backup_file"

    private const val TAG = "ShixuJi"
    private const val FILE_LATEST = "时序集_自动备份.json"
    private const val FILE_DAILY_PREFIX = "时序集_自动备份_"
    /**
     * 「恢复前」快照的前缀。**刻意不用每日快照那个前缀** —— 每日快照只留 10 份，
     * 靠名字排序挤掉最旧的；恢复前快照是「用户选错文件时的后悔药」，不该被常规清理顺手删掉。
     */
    private const val FILE_PRE_RESTORE_PREFIX = "时序集_恢复前_"
    private const val RELATIVE_DIR = "时序集备份"
    private const val DAILY_KEEP = 10
    private const val DEBOUNCE_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null

    /** 需要被观察的表（改了这些就备份；app_prefs 刻意不在其中，见类注释） */
    val TABLES = arrayOf("todos", "accounts", "acc_tags", "loop_todos", "quick_entries")

    /** 防抖排队：连续的多次改动只写一次 */
    fun schedule(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { backupNow(app) }.onFailure { Log.w(TAG, "auto backup failed", it) }
        }
    }

    /** 立刻备份一次（设置页「立即备份」、启动时打底） */
    suspend fun backupNow(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        val dao = AppContainer.database.appPrefDao()
        if (dao.get(PREF_DISABLED) == "1") return@withLock false

        // ★ 空库绝不覆盖已有备份（见 BackupWriter.Snapshot 注释）。
        //   判断与内容取自**同一个事务**：不会出现「判定非空、拼出来是空」然后把空备份写上去。
        val snap = BackupWriter.buildSnapshot()
        if (!snap.hasBusinessData) return@withLock false
        val json = snap.json

        // ① 私有副本（一定写，失败也不影响 ②）
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val wrotePrivate = BackupWriter.writeAtomic(File(dir, "auto_backup.json"), json)
        if (!wrotePrivate) Log.w(TAG, "private auto backup failed")
        // 私有副本校验（写的时候已经验过内容，这里只确认文件真的在那个位置）
        val privateOk = wrotePrivate && File(dir, "auto_backup.json").length() > 0

        // ② 共享存储（卸载后仍在）——API 29+ 走 MediaStore，无需权限
        var sharedName: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                sharedName = writeLatest(app, json, dao.get(PREF_NAME))
                sharedName?.let { used ->
                    // 记住这次真正写成功的文件名：下次直接命中，不用每轮都去撞那个写不进的名字
                    if (used != dao.get(PREF_NAME)) dao.put(AppPref(PREF_NAME, used))
                }
                writeDailyOnce(app, json)
                pruneDaily(app)
            }.onFailure { Log.w(TAG, "shared auto backup failed", it) }
        }

        // ★ 只有真写成功过才更新「上次备份时间」。
        //   两处都失败时若照旧写时间戳，设置页会显示一个很新的时间点，
        //   用户以为备份好好的，其实文件根本没写成。
        if (!privateOk && sharedName == null) {
            Log.w(TAG, "auto backup wrote nothing, keep PREF_LAST_AT")
            return@withLock false
        }
        dao.put(AppPref(PREF_LAST_AT, System.currentTimeMillis().toString()))
        // 位置只记共享存储（卸载后仍在的那份）；私有副本单独在 UI 里说明，
        // 免得「位置」为空却看着像成功了（审查 4.6）。
        dao.put(AppPref(PREF_LAST_WHERE, sharedName?.let { "下载/$RELATIVE_DIR/$it" }.orEmpty()))
        true
    }

    /** 恢复前快照的结果：调用方要能分清「没东西可备份」和「备份失败」 */
    enum class SnapshotResult {
        /** 写成功了（私有或共享至少一处） */
        WRITTEN,

        /** 库里本来就没数据，没什么可备份的 —— 不是失败 */
        EMPTY,

        /** 有数据但一份都没写成：**不允许继续恢复**（否则用户以为有退路，其实没有） */
        FAILED,
    }

    /**
     * 恢复备份**之前**自己留一份快照。
     *
     * 恢复是不可撤销的操作：用户在「另存为」里存的那份可能挑了个事后找不到的目录，
     * 也可能等发现选错文件时那份已经被覆盖了。这里额外写一份到
     * `下载/时序集备份/时序集_恢复前_<时间戳>.json` 与 App 私有目录，
     * 文件名独立、不参与每日快照的 10 份上限。
     *
     * ★ 7.5.1（审查 4.3）：返回值从 Boolean 改成三态。旧版返回 false 也被忽略，
     *   于是「快照没写成」照样弹确认框覆盖现有数据 —— 用户以为留了后路，其实没有。
     */
    suspend fun snapshotBeforeRestore(context: Context): SnapshotResult = mutex.withLock {
        val app = context.applicationContext
        val snap = BackupWriter.buildSnapshot()
        if (!snap.hasBusinessData) return@withLock SnapshotResult.EMPTY
        val json = snap.json
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val privateOk = BackupWriter.writeAtomic(File(dir, "before_restore_$stamp.json"), json)
        if (!privateOk) Log.w(TAG, "pre-restore snapshot (private) failed")

        var sharedOk = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                sharedOk = writeShared(app, "$FILE_PRE_RESTORE_PREFIX$stamp.json", json)
            }.onFailure { Log.w(TAG, "pre-restore snapshot (shared) failed", it) }
        }
        if (privateOk || sharedOk) SnapshotResult.WRITTEN else SnapshotResult.FAILED
    }

    /** 是否开启（设置页开关用） */
    suspend fun enabled(): Boolean = AppContainer.database.appPrefDao().get(PREF_DISABLED) != "1"

    suspend fun setEnabled(on: Boolean) {
        val dao = AppContainer.database.appPrefDao()
        if (on) dao.removeAll(listOf(PREF_DISABLED)) else dao.put(AppPref(PREF_DISABLED, "1"))
    }

    suspend fun lastAt(): Long =
        AppContainer.database.appPrefDao().get(PREF_LAST_AT)?.toLongOrNull() ?: 0L

    suspend fun lastWhere(): String =
        AppContainer.database.appPrefDao().get(PREF_LAST_WHERE).orEmpty()

    // ===== MediaStore 读写 =====

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeShared(ctx: Context, name: String, json: String): Boolean {
        rowOf(ctx, name)?.let { deleteRow(ctx, it) }
        return insertFresh(ctx, name, json.toByteArray(Charsets.UTF_8))
    }

    /**
     * 挑一个**本次安装真能写**的文件名，写进去。
     *
     * ★ 为什么不能「同名就覆盖」这么简单（2026-11-10 真机日志抓到的死结）：
     *   MediaStore 的行是「谁建谁写」。应用每重装一次 uid 就变（实测 u0_a190 → u0_a253），
     *   上次安装留下的 `时序集_自动备份.json` 从此**既写不进也删不掉**（SecurityException 被
     *   runCatching 吞掉）。旧逻辑接着走「同名重插」，而 MediaStore 在写完后
     *   `update(IS_PENDING=0)` 要给文件定稿、定稿时按 _data 找唯一路径 —— 那个路径被
     *   别人的行占着，于是抛 `IllegalStateException: Failed to build unique file`。
     *   结果：共享存储那份备份自 2026-11-03 起再没更新过（Download/时序集备份 里 66 个文件
     *   全是 (1)…(8) 这样的残骸），而设置页因为私有副本写成功仍然显示「刚刚备份」。
     *
     * 所以：优先用上次成功的名字；被占/写不进就依次换 `时序集_自动备份(2).json`…，
     * 直到有一个真的定稿成功。换成功一次之后把名字记进 app_prefs，下次直接用。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeLatest(ctx: Context, json: String, prefer: String?): String? {
        val names = LinkedHashSet<String>()
        if (!prefer.isNullOrBlank()) names.add(prefer)
        names.add(FILE_LATEST)
        for (i in 2..6) names.add(bumpName(FILE_LATEST, i))
        for (n in names) writeTwoPhase(ctx, n, json)?.let { return it }
        return null
    }

    /** 「最新一份」的临时名：`时序集_自动备份.json` → `时序集_自动备份.part.json` */
    internal fun partNameOf(target: String): String = target.removeSuffix(".json") + ".part.json"

    /**
     * 旧那份在「新那份还没定稿」期间的名字：`时序集_自动备份.json` → `时序集_自动备份.上一版.json`。
     *
     * ★ 7.5.5（复核报告 2.4）：旧实现在改名之前就把旧行**删掉**了 —— 注释里却写「任何一步失败都
     *   不会碰旧文件」，两句话对不上。虽然那一刻 `.part.json` 里已经是一份**校验过**的完整内容
     *   （所以不会真的两份都没有），但用户点开「下载/时序集备份」会发现正式名字没了，只剩个
     *   `.part.json`。现在的顺序是：旧行**改名留证** → `.part` 转正 → 转正成功后才删掉「上一版」。
     *   任何一步失败，最后一份可用备份都还在（要么是原名，要么是 `.上一版`，要么是 `.part`）。
     */
    internal fun prevNameOf(target: String): String = target.removeSuffix(".json") + ".上一版.json"

    /**
     * **两阶段写入**：先把内容写进一个**新文件**（`.part.json`），校验通过后再把它改成正式名字。
     *
     * ★ 7.5.1（审查 4.4）：旧实现是对已有的 `时序集_自动备份.json` 直接 `openOutputStream(uri,"wt")`
     *   原位截断，失败时还会把旧行删掉再重插。写入中断 / 空间不足 / 进程被杀 → 留下半份 JSON，
     *   而这份恰恰是「唯一的那份最新备份」，备份把数据吃掉了。
     *
     * 顺序：清残留 .part → 新建并写入 .part → **读回来解析校验** → 旧行改名成 `.上一版`（留证，不删）
     * → .part 转正 → 转正成功后删掉 `.上一版`。
     *
     * ★ 7.5.5（复核报告 2.4）：旧顺序是「删旧行 → 改名」，注释却声称「任何一步失败都不会碰旧文件」。
     *   现在做到名副其实：在新那份成为正式名字之前，旧那份**一直存在**（最多改个名）。
     *   失败路径的返回值也保证了这一点：改名被拒时返回 `.part.json`（已校验的完整内容），
     *   同时把 `.上一版` 改回原名；连这一步都失败时，`.上一版` 仍在目录里，仍是可恢复的备份。
     *
     * @return 实际可用的文件名（正常就是 [target]；改名被系统拒绝时是 `.part.json` 那个名字）
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeTwoPhase(ctx: Context, target: String, json: String): String? {
        val part = partNameOf(target)
        // 上一轮留下的半成品：删得掉就删，删不掉说明不是本次安装的行，这轮换名
        rowOf(ctx, part)?.let { if (!deleteRow(ctx, it)) return null }
        if (!insertFresh(ctx, part, json.toByteArray(Charsets.UTF_8))) return null
        if (!verifyRow(ctx, part)) {
            rowOf(ctx, part)?.let { deleteRow(ctx, it) }
            return null
        }
        // ★ 7.5.5：旧行**先改名留证**，不直接删（见函数注释）
        val prev = prevNameOf(target)
        if (rowOf(ctx, target) != null) {
            rowOf(ctx, prev)?.let { deleteRow(ctx, it) }   // 上一轮的留证行：删不掉就让下面的改名失败，走保守分支
            if (!renameRow(ctx, target, prev)) return part // 旧行动不了 → 旧文件原样还在，用 part 名交差
        }
        if (renameRow(ctx, part, target)) {
            // 新名字已经就位，才可以清掉留证
            rowOf(ctx, prev)?.let { deleteRow(ctx, it) }
            return target
        }
        // 转正失败：把留证改回原名（改不回来也无所谓，`.上一版` 本身就是一份可用备份）
        rowOf(ctx, prev)?.let { renameRow(ctx, prev, target) }
        return part
    }

    /** 把刚写出去的文件重新读回来，用备份解析器验一遍（真的落盘了、而且读得回来） */
    @RequiresApi(Build.VERSION_CODES.Q)
    internal fun verifyRow(ctx: Context, name: String): Boolean {
        val uri = rowOf(ctx, name) ?: return false
        return runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return false
            text.isNotBlank() && BackupCodec.parse(text) is BackupCodec.Result.Ok
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameRow(ctx: Context, from: String, to: String): Boolean {
        val uri = rowOf(ctx, from) ?: return false
        return runCatching {
            ctx.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, to)
            }, null, null) > 0
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteRow(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    /** `时序集_自动备份.json` → `时序集_自动备份(2).json`（与系统自动去重的写法一致，用户能看懂） */
    internal fun bumpName(base: String, n: Int): String =
        base.removeSuffix(".json") + "($n).json"

    /**
     * 每日快照：**一天只写一份**。
     *
     * 旧逻辑每轮都试写同名文件，写不进就 insert 一份副本，于是一天攒出 (1)…(8)（实测
     * 2026-11-03 那天 9 份）。当天已经有这份（不管是谁建的）就当作已完成，不再追加。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeDailyOnce(ctx: Context, json: String): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val name = "$FILE_DAILY_PREFIX$day.json"
        if (rowOf(ctx, name) != null) return true
        return insertFresh(ctx, name, json.toByteArray(Charsets.UTF_8))
    }

    /** 按 DISPLAY_NAME 找本目录下的那条记录（跨目录同名的不要） */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun rowOf(ctx: Context, name: String): Uri? {
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val want = (Environment.DIRECTORY_DOWNLOADS + "/" + RELATIVE_DIR).trimEnd('/')
        var found: Uri? = null
        runCatching {
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val rel = (c.getString(1) ?: "").trimEnd('/')
                    if (rel == want) { found = ContentUris.withAppendedId(collection, c.getLong(0)); break }
                }
            }
        }
        return found
    }

    /** 新建一行并定稿；定稿失败（路径被别人占）就把这行删掉、返回 false */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertFresh(ctx: Context, name: String, bytes: ByteArray): Boolean {
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val fresh = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        val out = runCatching { resolver.openOutputStream(fresh) }.getOrNull()
        if (out == null) {
            runCatching { resolver.delete(fresh, null, null) }
            return false
        }
        val wrote = runCatching { out.use { it.write(bytes) } }.isSuccess
        if (!wrote) {
            runCatching { resolver.delete(fresh, null, null) }
            return false
        }
        val done = runCatching {
            resolver.update(fresh, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        }.isSuccess
        if (!done) {
            // 定稿失败（Failed to build unique file）：清掉 pending 行，交给上层换名
            runCatching { resolver.delete(fresh, null, null) }
            return false
        }
        return true
    }

    /**
     * 每日快照只留最近 [DAILY_KEEP] 份（按名字排序 = 按日期排序）。
     *
     * ★ 坑（2026-11-08 审计发现，真会吃掉最新那份备份）：
     *   `LIKE '时序集_自动备份_%'` 里的 `_` 在 SQL 里是「任意一个字符」的**通配符**，不是字面下划线。
     *   所以 `时序集_自动备份.json`（最新那份）也命中这个模式（第一个 `_` 匹配 `_`、第二个匹配 `.`、
     *   `%` 匹配 `json`）。而 `'.'(0x2E) < '_'(0x5F)`，按名字排序它排最前 ——
     *   攒满 10 份每日快照之后，`dropLast(10)` 每次删掉的正好是它：
     *   写完最新备份 → 立刻被 prune 删掉，设置页却还在说「这是最新的一份」。
     *   两个修法一起上：① 前缀里的 `_` / `%` 全部转义并声明 `ESCAPE '\'`；
     *   ② 即使命中，也显式跳过 [FILE_LATEST]（双保险，防止以后改前缀又踩回去）。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun pruneDaily(ctx: Context) {
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val pattern = FILE_DAILY_PREFIX
            .replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + "%"
        val names = ArrayList<Pair<String, Long>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
            arrayOf(pattern), null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name == FILE_LATEST) continue
                names.add(name to c.getLong(0))
            }
        }
        dailyToDelete(names, DAILY_KEEP, FILE_LATEST).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    /**
     * 「该删哪几份每日快照」的纯函数版（`pruneDaily` 负责 MediaStore 读写，这里只做决策）。
     *
     * 抽出来是为了能在 JVM 上单测 —— 这条规则踩过一次真事故：
     * `时序集_自动备份.json` 会被前缀 LIKE 误命中、又因为 `'.' < '_'` 排在最前而被删掉。
     * 无论上游查询怎么改，这里都**硬性排除** [latest]。
     */
    internal fun dailyToDelete(
        names: List<Pair<String, Long>>, keep: Int, latest: String
    ): List<Long> = names
        .filter { it.first != latest }
        .sortedBy { it.first }
        .dropLast(keep.coerceAtLeast(0))
        .map { it.second }
}
