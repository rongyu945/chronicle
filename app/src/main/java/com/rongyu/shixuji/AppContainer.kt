package com.rongyu.shixuji

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rongyu.shixuji.data.AppDatabase
import com.rongyu.shixuji.data.AutoBackup
import com.rongyu.shixuji.data.TagPalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** 应用级依赖容器 */
object AppContainer {
    lateinit var database: AppDatabase
        private set

    // v1 -> v2: 新增 loop_todos / acc_tags / api_models / app_prefs 四表
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS loop_todos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, doneThisWeek INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS acc_tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, icon TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS api_models (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, modelName TEXT NOT NULL, quota TEXT NOT NULL, resetDate TEXT NOT NULL, resetCycle TEXT NOT NULL, baseUrl TEXT NOT NULL, apiKey TEXT NOT NULL, keyName TEXT NOT NULL, platform TEXT NOT NULL, color INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS app_prefs (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

    // v2 -> v3: todos.dueDate / accounts.date 加索引
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_dueDate ON todos (dueDate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_date ON accounts (date)")
        }
    }

    // v3 -> v4: todos 加 priority/sortOrder；新增 quick_entries 表
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todos ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE todos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS quick_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, type INTEGER NOT NULL, tag TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
        }
    }

    // v4 -> v5: todos 加 doneAt（完成时刻，已完成分区按它倒序排）
    // 回填：老库里已勾掉的条目用截止日/创建时刻兜底，只是决定它们在「已完成」区里的先后
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todos ADD COLUMN doneAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE todos SET doneAt = CASE WHEN dueDate != 0 THEN dueDate ELSE createdAt END WHERE done = 1")
        }
    }

    // v5 -> v6: 删除「模型看板」整块功能（用户 2026-10-15 要求彻底删掉界面与设置里的联动）
    // 只丢这一张表；待办/账目/标签/偏好/常用记账一律不动。
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS api_models")
        }
    }

    // v6 -> v7: 标签色板整体换新（用户 2026-10-25：「颜色之间的色差明显一点」，
    // 且已有标签也要一起重排）。旧色板 6 个色系各 4 色，同系内必然接近；
    // 新色板是 CIELAB 里按最远点散布取的 24 色，两两色差最小 26。
    // 做法：按 acc_tags.id 顺序把 24 色依次涂上去（超过 24 个标签就循环）；
    // 旧颜色写进 app_prefs.acc_tag_colors_v6（JSON），万一想回滚有据可查。
    // 只动 acc_tags.color —— 待办/账目/偏好一律不碰，账目用的标签名不受影响。
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val ids = ArrayList<Long>()
            val names = ArrayList<String>()
            val oldColors = ArrayList<Long>()
            db.query("SELECT id, name, color FROM acc_tags ORDER BY id ASC").use { c ->
                while (c.moveToNext()) {
                    ids.add(c.getLong(0)); names.add(c.getString(1)); oldColors.add(c.getLong(2))
                }
            }
            if (ids.isEmpty()) return   // 没有标签：不写空记录，也不白跑一遍 UPDATE
            val old = JSONArray()
            ids.indices.forEach { i ->
                old.put(JSONObject().put("name", names[i]).put("color", oldColors[i]))
            }
            db.execSQL(
                "INSERT OR REPLACE INTO app_prefs(`key`, value) VALUES('acc_tag_colors_v6', ?)",
                arrayOf(old.toString())
            )
            ids.indices.forEach { i ->
                db.execSQL(
                    "UPDATE acc_tags SET color = ? WHERE id = ?",
                    arrayOf<Any>(TagPalette.colorAt(i), ids[i])
                )
            }
        }
    }

    /**
     * 7 → 8：色板换成《中国风传统色卡》的清透色（用户 2026-11-01 嫌旧色「灰灰的脏脏的」）。
     * 和 6→7 同一套做法：按 id 顺序按新色板重涂，旧值写进 app_prefs.acc_tag_colors_v7 备查。
     * 只动颜色，不碰标签名/账目关联，所以失败也不会丢数据。
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val ids = ArrayList<Long>()
            val names = ArrayList<String>()
            val oldColors = ArrayList<Long>()
            db.query("SELECT id, name, color FROM acc_tags ORDER BY id ASC").use { c ->
                while (c.moveToNext()) {
                    ids.add(c.getLong(0)); names.add(c.getString(1)); oldColors.add(c.getLong(2))
                }
            }
            if (ids.isEmpty()) return
            val old = JSONArray()
            ids.indices.forEach { i ->
                old.put(JSONObject().put("name", names[i]).put("color", oldColors[i]))
            }
            db.execSQL(
                "INSERT OR REPLACE INTO app_prefs(`key`, value) VALUES('acc_tag_colors_v7', ?)",
                arrayOf(old.toString())
            )
            ids.indices.forEach { i ->
                db.execSQL(
                    "UPDATE acc_tags SET color = ? WHERE id = ?",
                    arrayOf<Any>(TagPalette.colorAt(i), ids[i])
                )
            }
        }
    }


    /**
     * 8 → 9：循环待办从「每周几」改成「开始日期 + 下一次提醒日期 → 间隔天数」。
     *
     * 表结构必须整张重建（要删 dayOfWeek / doneThisWeek 两列）。Room 的校验是
     * 逐列比对（列名/类型/非空/默认值），所以新表**不写 DEFAULT**、所有列都用显式值插入，
     * 与实体里「全部 NOT NULL、无 defaultValue」保持一致。
     *
     * 迁移口径：旧的「每周几」等价于「间隔 7 天」。下一次提醒 = 今天起最近的那个该星期几
     * （含今天）；开始日期 = 它往前 7 天（保持 7 天的间隔自洽）；本周已勾选的，
     * 把那一天的日期记进 doneDates，用户看到的状态不会平白丢掉。
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `loop_todos_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `detail` TEXT NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, `nextDate` INTEGER NOT NULL, " +
                    "`intervalDays` INTEGER NOT NULL, `doneDates` TEXT NOT NULL)"
            )
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            // 本周一（ISO：周一=1）的日期，用来把 doneThisWeek 映射成具体哪一天
            val mondayIso = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                val iso = if (get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                else get(Calendar.DAY_OF_WEEK) - 1
                add(Calendar.DAY_OF_MONTH, 1 - iso)
            }
            data class Row(
                val id: Long, val title: String, val detail: String,
                val dow: Int, val doneThisWeek: Boolean
            )
            val rows = ArrayList<Row>()
            db.query("SELECT id, title, detail, dayOfWeek, doneThisWeek FROM loop_todos ORDER BY id ASC")
                .use { c ->
                    while (c.moveToNext()) {
                        rows.add(Row(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4) == 1))
                    }
                }
            rows.forEach { r ->
                val dow = r.dow.coerceIn(1, 7)
                // 今天起最近的那个该星期几（含今天）
                val next = (today.clone() as Calendar).apply {
                    val todayIso = if (get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                    else get(Calendar.DAY_OF_WEEK) - 1
                    add(Calendar.DAY_OF_MONTH, (dow - todayIso + 7) % 7)
                }
                val start = (next.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -7) }
                // 本周那天若已经过去（且勾过），记成已完成
                val weekDay = (mondayIso.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dow - 1) }
                val doneDates = if (r.doneThisWeek && weekDay.timeInMillis <= today.timeInMillis) {
                    Math.floorDiv(weekDay.timeInMillis, 86_400_000L).toString()
                } else ""
                db.execSQL(
                    "INSERT INTO `loop_todos_new` (`id`,`title`,`detail`,`startDate`,`nextDate`,`intervalDays`,`doneDates`) " +
                        "VALUES (?,?,?,?,?,?,?)",
                    arrayOf<Any>(r.id, r.title, r.detail, start.timeInMillis, next.timeInMillis, 7, doneDates)
                )
            }
            db.execSQL("DROP TABLE `loop_todos`")
            db.execSQL("ALTER TABLE `loop_todos_new` RENAME TO `loop_todos`")
        }
    }

    /**
     * 全部迁移，按版本顺序。
     * 仪器测试（app/src/androidTest/.../MigrationTest.kt）直接引用这份，
     * 免得测试里另抄一份 SQL —— 抄的那份改了不会有人发现，等于没测。
     */
    val MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
    )

    fun init(context: Context) {
        if (::database.isInitialized) return
        // 刻意不保留 fallbackToDestructiveMigration：漏写迁移时宁可崩溃暴露，
        // 也不要静默删库重建（记账数据不可再生）。改动版本号前请先做好「备份/恢复」。
        database = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "shixuji.db"
        ).addMigrations(*MIGRATIONS)
            .build()

        // ★ 自动备份（用户 2026-11-01 要求：改任何一项条目后自动更新备份）
        // 用 Room 的失效追踪当触发器：任何业务表被写都会回调，不用在几十个 ViewModel
        // 调用点各插一句（漏一个就少一处备份）。改动密集时 AutoBackup 内部做 1.5s 防抖。
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(AutoBackup.TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    AutoBackup.schedule(context)
                }
            }
        )
        // 启动时先打一份底：把"上次运行结束到这 次启动之间"的变化也补进备份
        AutoBackup.schedule(context)
    }
}
