package com.rongyu.shixuji.data

import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.BuildConfig
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 备份 JSON 的唯一生产者。
 *
 * 原来这段代码长在 SettingsViewModel 里（只有「导出备份」这一条路会调），
 * 现在多了一个调用方 —— 自动备份（AutoBackup）在每次改动条目后自己写一份。
 * 两处如果各写一份拼装逻辑，迟早会分叉（一份漏字段、一份格式不同），
 * 恢复时才发现读不回来。所以抽到这里，导出按钮与自动备份共用同一份。
 *
 * 格式见 [BackupCodec]（SCHEMA=3）：todos / accounts / loopTodos / accTags / quickEntries
 * + 可迁移的用户偏好（白名单见 [BackupCodec.PREF_MAP]）。
 */
object BackupWriter {

    /** 备份文件格式版本，改动输出结构时必须 +1（读文件那边在 BackupCodec.SCHEMA） */
    const val SCHEMA = BackupCodec.SCHEMA

    /**
     * ★ 6 张表的取数必须包在**同一个事务**里。
     *
     * 原来每条 DAO 查询各自一个事务：备份（改动后 1.5 秒防抖）若恰好和一次「标签改名」或
     * 「批量整理」交错，就会导出一份**内部不一致**的文件 —— 比如标签表已经是新名字、
     * 而账目的 `tag` 还是旧名字。恢复回库里就是孤儿标签（正是 `updateTag` 拼命用事务避免的状态）。
     * `withTransaction` 在 SQLite 里是单连接串行的，整段取数天然是同一个快照。
     */
    /**
     * 一份备份的**完整快照**：内容 + 「库里到底有没有数据」。
     *
     * ★ 7.5.1（审查 4.7）：旧版是两个函数 —— `hasAnyData()` 先查一遍，`buildJson()` 再查一遍拼 JSON。
     * 两次读表之间若发生「清空 / 恢复 / 批量删除」，就会出现「判定非空 → 拼出来是空的」
     * 然后把**空备份**覆盖到共享存储（备份反而把数据吃掉）。现在两者在同一个 Room 事务里算完，
     * 判断和内容一定来自同一时刻。
     */
    class Snapshot(val json: String, val hasBusinessData: Boolean, val exportedAt: Long)

    suspend fun buildSnapshot(): Snapshot = AppContainer.database.withTransaction {
        val db = AppContainer.database
        val todoDao = db.todoDao()
        val accountDao = db.accountDao()
        val loopDao = db.loopTodoDao()
        val tagDao = db.accTagDao()
        val quickDao = db.quickEntryDao()
        val prefDao = db.appPrefDao()

        val root = JSONObject()
        root.put("app", "时序集")
        root.put("schema", SCHEMA)
        root.put("exportedAt", System.currentTimeMillis())
        // 数据库版本与 App 版本：恢复时用来判断"这份文件来自哪一版、字段够不够"
        root.put("dbVersion", AppDatabase.DB_VERSION)
        root.put("appVersion", BuildConfig.VERSION_NAME)
        // ★ 循环待办 doneDates 的口径标记（6.9.4 起是公历日序号）。
        //   导入时靠它判断要不要按旧口径平移，别用「有没有字段/版本号猜」。
        root.put("loopDayEpoch", "civil")

        val todosArr = JSONArray()
        todoDao.getAll().forEach { t ->
            todosArr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("detail", t.detail)
                put("done", t.done); put("dueDate", t.dueDate); put("createdAt", t.createdAt)
                // 这三个漏掉会静默丢数据：恢复后所有优先级变「无」、拖动顺序被打乱、
                // 已完成分区的先后也丢了（早期版本确实漏了，这里补上）
                put("priority", t.priority); put("sortOrder", t.sortOrder); put("doneAt", t.doneAt)
            })
        }
        root.put("todos", todosArr)

        val accArr = JSONArray()
        accountDao.getAll().forEach { a ->
            accArr.put(JSONObject().apply {
                put("id", a.id); put("title", a.title); put("amountCents", a.amountCents)
                put("type", a.type); put("tag", a.tag); put("date", a.date)
            })
        }
        root.put("accounts", accArr)

        val loopArr = JSONArray()
        loopDao.getAll().forEach { l ->
            loopArr.put(JSONObject().apply {
                put("id", l.id); put("title", l.title); put("detail", l.detail)
                // 5.5.0 起循环待办靠「开始日期 + 下一次提醒 + 间隔天数」表达
                put("startDate", l.startDate); put("nextDate", l.nextDate)
                put("intervalDays", l.intervalDays); put("doneDates", l.doneDates)
                put("sortOrder", l.sortOrder)
                // 仍写一份「星期几」，好让旧版本 App 打开新备份时不至于把这条丢掉
                // （旧版只认 dayOfWeek，缺了会兜底成周一）。方向反过来不行：
                // 旧备份没有上面四个键，读的时候按 7 天 / 最近的该星期几补出来。
                val cal = Calendar.getInstance().apply { timeInMillis = l.nextDate }
                put("dayOfWeek", if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                else cal.get(Calendar.DAY_OF_WEEK) - 1)
                put("doneThisWeek", false)
            })
        }
        root.put("loopTodos", loopArr)

        val tagArr = JSONArray()
        tagDao.getAll().forEach { t ->
            tagArr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("color", t.color); put("icon", t.icon)
            })
        }
        root.put("accTags", tagArr)

        val quickArr = JSONArray()
        quickDao.getAll().forEach { q ->
            quickArr.put(JSONObject().apply {
                put("id", q.id); put("title", q.title); put("amountCents", q.amountCents)
                put("type", q.type); put("tag", q.tag); put("sortOrder", q.sortOrder)
            })
        }
        root.put("quickEntries", quickArr)

        // 用户偏好按白名单导出（BackupCodec.PREF_MAP）：少写一项 = 换手机后那项设置悄悄回默认
        BackupCodec.PREF_MAP.forEach { (jsonKey, prefKey) ->
            prefDao.get(prefKey)?.let { root.put(jsonKey, it) }
        }

        val hasData = todosArr.length() > 0 || accArr.length() > 0 || loopArr.length() > 0 ||
            tagArr.length() > 0 || quickArr.length() > 0
        Snapshot(root.toString(2), hasData, System.currentTimeMillis())
    }

    /** 只要内容（导出按钮、分享、恢复前快照都用它） */
    suspend fun buildJson(): String = buildSnapshot().json

    /**
     * 库里有没有任何业务数据。
     *
     * 挡一种致命场景：App 被卸载/清数据后重装，库里是空的，此时自动备份若照常覆盖
     * 共享存储里那份，就会用**空备份**把用户的真数据抹掉 —— 备份反而成了数据杀手。
     * 所以 AutoBackup 在写了之前先问一句：真的一条都没有吗？是就不写。
     */
    @Deprecated("改用 buildSnapshot()：判断和内容必须来自同一个事务")
    suspend fun hasAnyData(): Boolean {
        val db = AppContainer.database
        return db.todoDao().getAll().isNotEmpty() ||
            db.accountDao().getAll().isNotEmpty() ||
            db.loopTodoDao().getAll().isNotEmpty() ||
            db.accTagDao().getAll().isNotEmpty() ||
            db.quickEntryDao().getAll().isNotEmpty()
    }

    /**
     * 私有目录里的安全替换：写 `.tmp` → **回读校验** → 再定稿。
     *
     * 旧版只是「写 tmp 再 rename」，rename 失败就 `target.writeText(text)` 直接覆盖 ——
     * 而这一步恰好是「把唯一一份好备份截断」的地方（写一半被杀进程就留下半个 JSON）。
     * 现在 tmp 落盘后先用 [BackupCodec.parse] 验一遍（内容是我们自己刚拼的，验的是
     * 「文件真的完整写到磁盘上了」），再 rename；rename 不通（跨挂载点/被占用）时
     * 退化成「已验证内容的 copyTo」，仍比盲目覆盖安全。
     *
     * 返回 false 表示**没有**写成功 —— 调用方不能把它当成「最近备份已更新」。
     */
    fun writeAtomic(target: File, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        return try {
            // 7.7.1：写 + **fsync** 再 rename。
            //   rename 只保证「目录项指向哪份数据」，不保证数据本身已落盘；掉电/被硬杀的
            //   情况下，可能出现「名字已经是新的、内容是空的」——那正是把唯一一份好备份
            //   变成 0 字节的那条路径（外部复核报告 §1.2 的「步骤 4/5 之间」）。
            java.io.FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                runCatching { out.fd.sync() }   // 不支持时（部分 SAF/沙盒）忽略，不影响后续校验
            }
            // 回读校验：长度对不上 / 解析不通过 = 这次写入不可信
            val back = tmp.readBytes()
            if (back.size != bytes.size) throw IllegalStateException("写入不完整（${back.size}/${bytes.size} 字节）")
            if (BackupCodec.parse(String(back, Charsets.UTF_8)) !is BackupCodec.Result.Ok) {
                throw IllegalStateException("写出的内容解析不通过")
            }
            if (tmp.renameTo(target)) return true
            // rename 失败：保留旧文件，用已校验过的内容覆盖
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            true
        } catch (e: Exception) {
            android.util.Log.w("ShixuJi", "atomic write failed: ${target.name}", e)
            runCatching { tmp.delete() }
            false
        }
    }
}
