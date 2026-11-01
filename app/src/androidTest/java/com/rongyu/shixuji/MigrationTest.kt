package com.rongyu.shixuji

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rongyu.shixuji.data.AppDatabase
import com.rongyu.shixuji.data.TagPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Room 历史库升级的真实回归（静态审查 RISK-02：有 1→6 六段手写迁移，却没有任何迁移测试）。
 *
 * 做法是 **真库**，不是拼 SQL 字符串猜：
 * 用本工程已导出的 schema 快照（app/schemas/3..7.json，已挂进 androidTest 的 assets）
 * 造出 v3 / v4 / v5 / v6 的真数据库，塞入数据，再跑 [AppContainer.MIGRATIONS] 升到当前版本，
 * 由 Room 自己校验「升级后的表结构与最新 schema 是否一致」。
 *
 * 迁移引用的是**生产代码那一份**（AppContainer.MIGRATIONS），测试里不另抄 SQL ——
 * 抄一份的话，改了生产迁移测试照样绿，等于没测。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /** v3 → 最新：老待办/账目/标签一条不丢，新列按默认值补上，api_models 被删掉 */
    @Test
    fun v3升到v6数据不丢() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt) VALUES (1,'老待办','细节',0,0,1000)")
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt) VALUES (2,'已做完','',1,5000,2000)")
            execSQL("INSERT INTO accounts (id,title,amountCents,type,tag,date) VALUES (1,'早饭',1200,0,'餐饮',3000)")
            execSQL("INSERT INTO acc_tags (id,name,color,icon) VALUES (1,'餐饮',-16711936,'')")
            execSQL("INSERT INTO loop_todos (id,title,detail,dayOfWeek,doneThisWeek) VALUES (1,'每周大扫除','',6,0)")
            execSQL("INSERT INTO api_models (id,modelName,quota,resetDate,resetCycle,baseUrl,apiKey,keyName,platform,color) VALUES (1,'m','0','','','','k','','',0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            // 待办：文案/完成态保留，新列 priority/sortOrder/doneAt 默认 0
            db.query("SELECT title, detail, done, dueDate, createdAt, priority, sortOrder, doneAt FROM todos ORDER BY id").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("老待办", c.getString(0))
                assertEquals("细节", c.getString(1))
                assertEquals(0, c.getInt(2))
                assertEquals(1000L, c.getLong(4))
                assertEquals(0, c.getInt(5))
                assertEquals(0, c.getInt(6))
                assertEquals(0L, c.getLong(7))
                assertTrue(c.moveToNext())
                assertEquals("已做完", c.getString(0))
                assertEquals(1, c.getInt(2))
                assertEquals(5000L, c.getLong(3))
            }
            // 账目 / 标签 / 每周固定：条数与内容原样
            assertCount(db, "accounts", 1)
            assertCount(db, "acc_tags", 1)
            assertCount(db, "loop_todos", 1)
            db.query("SELECT amountCents, tag FROM accounts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1200L, c.getLong(0))
                assertEquals("餐饮", c.getString(1))
            }
            // 常用记账表是 v4 新建的，v3 升级后应为空表（不是不存在）
            assertCount(db, "quick_entries", 0)
            // api_models 在 v6 被删
            assertNoTable(db, "api_models")
            // 索引不能被迁移弄丢（v2→v3 建的）
            assertHasIndex(db, "todos", "index_todos_dueDate")
            assertHasIndex(db, "accounts", "index_accounts_date")
        }
    }

    /**
     * v4 → 最新：最重要的是 4→5 的 doneAt 回填 ——
     * 已完成条目用「截止日兜底、没有截止日就用创建时刻」，未完成的保持 0。
     */
    @Test
    fun v4升到v6会回填doneAt() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder) VALUES (1,'有截止日','',1,5000,1000,2,-3)")
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder) VALUES (2,'无截止日','',1,0,2000,1,7)")
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder) VALUES (3,'没做完','',0,9000,3000,3,1)")
            execSQL("INSERT INTO quick_entries (id,title,amountCents,type,tag,sortOrder) VALUES (1,'早饭',1200,0,'餐饮',0)")
            execSQL("INSERT INTO api_models (id,modelName,quota,resetDate,resetCycle,baseUrl,apiKey,keyName,platform,color) VALUES (1,'m','0','','','','k','','',0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            db.query("SELECT title, priority, sortOrder, doneAt FROM todos ORDER BY id").use { c ->
                assertEquals(3, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(5000L, c.getLong(3))      // 已完成 + 有截止日 → 用截止日
                assertEquals(2, c.getInt(1))           // 优先级原样
                assertEquals(-3, c.getInt(2))          // 手动顺序原样
                assertTrue(c.moveToNext())
                assertEquals(2000L, c.getLong(3))      // 已完成 + 无截止日 → 用创建时刻
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(3))         // 未完成 → 不填
            }
            assertCount(db, "quick_entries", 1)
            assertNoTable(db, "api_models")
        }
    }

    /** v5 → 最新：删 api_models，其余表一条不动（用户 2026-10-15 要求彻底删掉模型看板） */
    @Test
    fun v5升到v6只删模型表() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder,doneAt) VALUES (1,'待办','',1,8000,1000,1,0,7000)")
            execSQL("INSERT INTO accounts (id,title,amountCents,type,tag,date) VALUES (1,'收入',50000,1,'其它',3000)")
            execSQL("INSERT INTO app_prefs (`key`,value) VALUES ('dark','2')")
            execSQL("INSERT INTO api_models (id,modelName,quota,resetDate,resetCycle,baseUrl,apiKey,keyName,platform,color) VALUES (1,'m','0','','','','k','','',0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            db.query("SELECT done, doneAt FROM todos").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(7000L, c.getLong(1))      // 已有的 doneAt 不被二次回填
            }
            assertCount(db, "accounts", 1)
            assertCount(db, "app_prefs", 1)
            db.query("SELECT value FROM app_prefs WHERE `key`='dark'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2", c.getString(0))
            }
            assertNoTable(db, "api_models")
        }
    }

    /**
     * v6 → v7：标签色板整体换新。
     *
     * 断言三件事：①按 acc_tags.id 顺序依次涂上新色板的颜色（第 1 个拿色板第 1 色）；
     * ②旧颜色写进 app_prefs.acc_tag_colors_v6 留档（想回滚有据可查）；
     * ③账目、待办、常用记账一条不动 —— 这次迁移只该碰颜色。
     */
    @Test
    fun v6升到v7会重排标签颜色() {
        val oldRed = 0xFFFF3B30L
        val oldGreen = 0xFF34C759L
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO acc_tags (id,name,color,icon) VALUES (1,'餐饮',$oldRed,'')")
            execSQL("INSERT INTO acc_tags (id,name,color,icon) VALUES (2,'交通',$oldGreen,'')")
            execSQL("INSERT INTO accounts (id,title,amountCents,type,tag,date) VALUES (1,'早饭',1200,0,'餐饮',3000)")
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder,doneAt) VALUES (1,'待办','',0,0,1000,1,0,0)")
            execSQL("INSERT INTO quick_entries (id,title,amountCents,type,tag,sortOrder) VALUES (1,'早饭',1200,0,'餐饮',0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            db.query("SELECT id, name, color FROM acc_tags ORDER BY id").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("餐饮", c.getString(1))
                assertEquals(TagPalette.colorAt(0), c.getLong(2))
                assertTrue(c.moveToNext())
                assertEquals("交通", c.getString(1))
                assertEquals(TagPalette.colorAt(1), c.getLong(2))
            }
            // 旧颜色留档（JSON 数组，含名称与原色值）
            db.query("SELECT value FROM app_prefs WHERE `key`='acc_tag_colors_v6'").use { c ->
                assertTrue("旧颜色没有留档", c.moveToFirst())
                val saved = c.getString(0)
                assertTrue(saved, saved.contains("餐饮") && saved.contains(oldRed.toString()))
            }
            // 其它表一条不动
            assertCount(db, "accounts", 1)
            assertCount(db, "todos", 1)
            assertCount(db, "quick_entries", 1)
            db.query("SELECT tag FROM accounts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("餐饮", c.getString(0))   // 账目挂的是标签名，不受换色影响
            }
        }
    }

    /**
     * v7 → v8：色板换成传统清透色（用户 2026-11-01 嫌旧色「灰灰的脏脏的」）。
     * 和 6→7 同一套口径：按 id 顺序重涂、旧值留档、其余表一条不动。
     */
    @Test
    fun v7升到v8会重排标签颜色() {
        val oldColor = 0xFFA34729L     // v7 色板的第 1 色
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO acc_tags (id,name,color,icon) VALUES (1,'餐饮',$oldColor,'')")
            execSQL("INSERT INTO acc_tags (id,name,color,icon) VALUES (2,'交通',$oldColor,'')")
            execSQL("INSERT INTO accounts (id,title,amountCents,type,tag,date) VALUES (1,'早饭',1200,0,'餐饮',3000)")
            execSQL("INSERT INTO todos (id,title,detail,done,dueDate,createdAt,priority,sortOrder,doneAt) VALUES (1,'待办','',0,0,1000,1,0,0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            db.query("SELECT id, name, color FROM acc_tags ORDER BY id").use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(TagPalette.colorAt(0), c.getLong(2))
                assertTrue(c.moveToNext())
                assertEquals(TagPalette.colorAt(1), c.getLong(2))
            }
            db.query("SELECT value FROM app_prefs WHERE `key`='acc_tag_colors_v7'").use { c ->
                assertTrue("旧颜色没有留档", c.moveToFirst())
                val saved = c.getString(0)
                assertTrue(saved, saved.contains("餐饮") && saved.contains(oldColor.toString()))
            }
            assertCount(db, "accounts", 1)
            assertCount(db, "todos", 1)
        }
    }

    /**
     * v8 → v9：循环待办从「每周几」改成「开始日期 + 下一次提醒日期 → 间隔天数」。
     *
     * 断言：①条数与文案一条不丢；②间隔一律 7 天（旧的「每周几」等价于每周一次）；
     * ③下一次提醒 = 今天起最近的那个星期几（含今天），不早于今天；
     * ④开始日期正好是下一次提醒前 7 天；⑤没勾过的 doneDates 是空串。
     */
    @Test
    fun v8升到v9循环待办改成两个日期() {
        helper.createDatabase(dbName, 8).apply {
            execSQL("INSERT INTO loop_todos (id,title,detail,dayOfWeek,doneThisWeek) VALUES (1,'每周大扫除','周日做','7',0)")
            execSQL("INSERT INTO loop_todos (id,title,detail,dayOfWeek,doneThisWeek) VALUES (2,'浇花','','3',1)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, AppDatabase.DB_VERSION, true, *AppContainer.MIGRATIONS).use { db ->
            db.query("SELECT id, title, detail, startDate, nextDate, intervalDays, doneDates FROM loop_todos ORDER BY id").use { c ->
                assertEquals(2, c.count)
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                while (c.moveToNext()) {
                    assertEquals("一律每周一次", 7, c.getInt(5))
                    val start = c.getLong(3)
                    val next = c.getLong(4)
                    assertTrue("下一次提醒不能早于今天", next >= today)
                    assertTrue("下一次提醒最多推到 7 天后", next - today < 7L * 86_400_000L)
                    assertEquals("开始日期 = 下一次提醒 − 7 天", next - 7L * 86_400_000L, start)
                    // 本周勾过的（id=2）记到「那一天」，没勾过的（id=1）是空串
                    if (c.getLong(0) == 1L) assertEquals("没勾过就是空串", "", c.getString(6))
                    else assertTrue("勾过的应记下具体哪天", c.getString(6).isNotBlank())
                }
            }
            db.query("SELECT title, detail FROM loop_todos ORDER BY id").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("每周大扫除", c.getString(0))
                assertEquals("周日做", c.getString(1))
                assertTrue(c.moveToNext())
                assertEquals("浇花", c.getString(0))
            }
        }
    }

    private fun assertCount(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, expected: Int) {
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("$table 条数不对", expected, c.getInt(0))
        }
    }

    private fun assertNoTable(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { c ->
            assertFalse("表 $table 应该已经被删掉", c.moveToFirst())
        }
    }

    private fun assertHasIndex(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, index: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name=? AND name=?", arrayOf(table, index)).use { c ->
            assertTrue("索引 $index 丢了", c.moveToFirst())
        }
    }
}
