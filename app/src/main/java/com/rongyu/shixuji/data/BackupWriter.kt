package com.rongyu.shixuji.data

import com.rongyu.shixuji.AppContainer
import com.rongyu.shixuji.BuildConfig
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
 * 格式见 [BackupCodec]（SCHEMA=2）：todos / accounts / loopTodos / accTags / quickEntries
 * + 若干界面设置偏好。
 */
object BackupWriter {

    /** 备份文件格式版本，改动输出结构时必须 +1（读文件那边在 BackupCodec.SCHEMA） */
    const val SCHEMA = BackupCodec.SCHEMA

    suspend fun buildJson(): String {
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

        prefDao.get("budget_month")?.let { root.put("pref_budget", it) }
        prefDao.get("font_scale")?.let { root.put("pref_font_scale", it) }
        prefDao.get("songti")?.let { root.put("pref_songti", it) }
        prefDao.get("dark")?.let { root.put("pref_dark", it) }
        prefDao.get("tab_order")?.let { root.put("pref_tab_order", it) }

        return root.toString(2)
    }

    /**
     * 库里有没有任何业务数据。
     *
     * 挡一种致命场景：App 被卸载/清数据后重装，库里是空的，此时自动备份若照常覆盖
     * 共享存储里那份，就会用**空备份**把用户的真数据抹掉 —— 备份反而成了数据杀手。
     * 所以 AutoBackup 在写了之前先问一句：真的一条都没有吗？是就不写。
     */
    suspend fun hasAnyData(): Boolean {
        val db = AppContainer.database
        return db.todoDao().getAll().isNotEmpty() ||
            db.accountDao().getAll().isNotEmpty() ||
            db.loopTodoDao().getAll().isNotEmpty() ||
            db.accTagDao().getAll().isNotEmpty() ||
            db.quickEntryDao().getAll().isNotEmpty()
    }

    /**
     * 先写 .tmp 再改名 —— 写一半被打断时，磁盘上留下的仍是上一份完整备份。
     * （自动备份会频繁覆盖同一个文件，直接 `writeText` 在写入过程中被杀进程就会留下半个 JSON，
     * 而这份「半个 JSON」恰好是唯一的一份，等于备份把数据吃掉了。）
     */
    fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            // rename 失败（极少见：跨挂载点/文件被占用）→ 退化成覆盖写，至少别留 .tmp
            target.writeText(text)
            tmp.delete()
        }
    }
}
