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

    private const val TAG = "ShixuJi"
    private const val FILE_LATEST = "时序集_自动备份.json"
    private const val FILE_DAILY_PREFIX = "时序集_自动备份_"
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

        // ★ 空库绝不覆盖已有备份（见 BackupWriter.hasAnyData 注释）
        if (!BackupWriter.hasAnyData()) return@withLock false

        val json = BackupWriter.buildJson()
        val where = ArrayList<String>()

        // ① 私有副本（一定写，失败也不影响 ②）
        runCatching {
            val dir = app.getExternalFilesDir(null) ?: app.filesDir
            BackupWriter.writeAtomic(File(dir, "auto_backup.json"), json)
        }.onFailure { Log.w(TAG, "private auto backup failed", it) }

        // ② 共享存储（卸载后仍在）——API 29+ 走 MediaStore，无需权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                if (writeShared(app, FILE_LATEST, json)) where.add("下载/$RELATIVE_DIR/$FILE_LATEST")
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                writeShared(app, "$FILE_DAILY_PREFIX$day.json", json)
                pruneDaily(app)
            }.onFailure { Log.w(TAG, "shared auto backup failed", it) }
        }

        dao.put(AppPref(PREF_LAST_AT, System.currentTimeMillis().toString()))
        dao.put(AppPref(PREF_LAST_WHERE, where.joinToString("、")))
        true
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

    /**
     * 写（或覆盖）共享存储里的一份 JSON。
     *
     * MediaStore 的 insert 每次都新建文件（同名会自动变成「…(1).json」），所以必须先按
     * DISPLAY_NAME 找到自己写过的那条，再用 "wt" 模式截断覆盖；找不到才 insert。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeShared(ctx: Context, name: String, json: String): Boolean {
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val bytes = json.toByteArray(Charsets.UTF_8)

        var uri: Uri? = null
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(name), null
        )?.use { c -> if (c.moveToFirst()) uri = ContentUris.withAppendedId(collection, c.getLong(0)) }

        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + RELATIVE_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri!!)?.use { it.write(bytes) }
                ?: return false
            // 写完才让它对其它 App 可见（避免别的程序读到写了一半的文件）
            resolver.update(uri!!, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } else {
            resolver.openOutputStream(uri!!, "wt")?.use { it.write(bytes) } ?: return false
        }
        return true
    }

    /** 每日快照只留最近 [DAILY_KEEP] 份（按名字排序 = 按日期排序） */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun pruneDaily(ctx: Context) {
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val names = ArrayList<Pair<String, Long>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$FILE_DAILY_PREFIX%"), null
        )?.use { c ->
            while (c.moveToNext()) names.add(c.getString(1) to c.getLong(0))
        }
        names.sortedBy { it.first }.dropLast(DAILY_KEEP).forEach { (_, id) ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }
}
