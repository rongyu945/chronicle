package com.rongyu.shixuji

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AppDatabase
import com.rongyu.shixuji.data.QuickEntry
import com.rongyu.shixuji.data.Todo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO 层冒烟（静态审查 QA-08：原有测试全是纯逻辑 JVM 单测，没有一条碰真 Room）。
 *
 * 覆盖的都是**曾经真出过问题**的路径：
 *  - 新增待办要取 `minSortOrder() - 1` 才会恒在最上面；
 *  - 撤回「修改」走 REPLACE 覆盖同一 id，不能抛主键冲突（曾一点撤回就崩）；
 *  - 勾选/取消勾选要把 doneAt 一起写；
 *  - 改标签名必须同步到 accounts 与 quick_entries（否则老账目变孤儿标签）。
 */
@RunWith(AndroidJUnit4::class)
class DaoSmokeTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun 新增取最小顺序且撤回覆盖同id不崩() = runBlocking {
        val dao = db.todoDao()
        assertEquals("空表时 minSortOrder 应为 0", 0, dao.minSortOrder())

        val id1 = dao.insert(Todo(title = "第一条", sortOrder = dao.minSortOrder() - 1))
        assertTrue(id1 > 0)
        assertEquals(-1, dao.minSortOrder())

        val id2 = dao.insert(Todo(title = "第二条", sortOrder = dao.minSortOrder() - 1))
        assertTrue("新条目要比旧条目更小（更靠前）", dao.minSortOrder() < -1)

        // 撤回「修改」：同 id 再写一次（REPLACE）不能抛 SQLiteConstraintException
        dao.upsert(Todo(id = id2, title = "改回原样", sortOrder = -99))
        assertEquals("改回原样", dao.getById(id2)?.title)
        assertEquals(-99, dao.minSortOrder())

        // 勾选 / 取消勾选：done 与 doneAt 一起写
        dao.setDoneFull(id1, true, 12_345L)
        dao.getById(id1)!!.let {
            assertTrue(it.done)
            assertEquals(12_345L, it.doneAt)
        }
        dao.setDoneFull(id1, false, 0L)
        dao.getById(id1)!!.let {
            assertTrue(!it.done)
            assertEquals("取消勾选必须把 doneAt 清零", 0L, it.doneAt)
        }
    }

    @Test
    fun 标签改名同步到账目与常用记账() = runBlocking {
        val acc = db.accountDao()
        val tagDao = db.accTagDao()
        val quick = db.quickEntryDao()

        tagDao.upsert(AccTagEntity(name = "餐饮", color = 0xFFA51E36))
        acc.insert(Account(title = "早饭", amountCents = 1200, type = 0, tag = "餐饮", date = 1))
        acc.insert(Account(title = "午饭", amountCents = 2500, type = 0, tag = "餐饮", date = 2))
        val quickId = quick.insert(QuickEntry(title = "早饭", amountCents = 1200, tag = "餐饮"))
        assertTrue(quickId > 0)

        db.withTransaction {
            val tag = tagDao.getAll().single()
            tagDao.update(tag.id, "吃饭", tag.color)
            acc.renameTag("餐饮", "吃饭")
            quick.renameTag("餐饮", "吃饭")
        }

        assertEquals(2, acc.countByTag("吃饭"))
        assertEquals("旧名字下不该还有账目（孤儿标签）", 0, acc.countByTag("餐饮"))
        assertEquals("吃饭", quick.getAll().single().tag)
        assertNotNull(tagDao.getAll().singleOrNull())
    }

    @Test
    fun 偏好整组删除只删指定键() = runBlocking {
        val prefs = db.appPrefDao()
        prefs.put(com.rongyu.shixuji.data.AppPref("dark", "2"))
        prefs.put(com.rongyu.shixuji.data.AppPref("font_scale", "1.15"))
        prefs.put(com.rongyu.shixuji.data.AppPref("loop_week", "123"))

        prefs.removeAll(AppDatabase.PREF_KEYS)

        assertEquals(null, prefs.get("dark"))
        assertEquals(null, prefs.get("font_scale"))
        assertEquals("内部状态 loop_week 不属于界面设置，不该被恢复流程抹掉", "123", prefs.get("loop_week"))
    }
}
