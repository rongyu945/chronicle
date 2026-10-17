package com.rongyu.shixuji

import com.rongyu.shixuji.data.BackupCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份文件解析（data/BackupCodec.kt）的回归测试。
 *
 * 这些用例对应的是**真出过 / 差点出**的数据事故，不是凑覆盖率：
 *  - 缺段的文件会把没导出的表清空（静默丢数据）；
 *  - 坏记录会让「边解析边写库」停在半途（库里已被 clear，只恢复一半）；
 *  - 字号被写坏成 999 恢复后界面直接崩。
 *
 * 用 testImplementation 拉进来的真 org.json 在 JVM 上跑，不必装到设备上试。
 */
class BackupCodecTest {

    private fun 完整备份(): String {
        val root = JSONObject()
        root.put("app", "时序集")
        root.put("schema", 2)
        root.put("dbVersion", 6)
        root.put("appVersion", "5.2.0")

        root.put("todos", JSONArray().apply {
            put(JSONObject().apply {
                put("id", 7); put("title", "交作业"); put("detail", "物理实验报告")
                put("done", true); put("dueDate", 1_700_000_000_000)
                put("createdAt", 1_699_000_000_000)
                put("priority", 3); put("sortOrder", -2); put("doneAt", 1_700_000_500_000)
            })
        })
        root.put("accounts", JSONArray().apply {
            put(JSONObject().apply {
                put("id", 3); put("title", "午饭"); put("amountCents", 1580)
                put("type", 0); put("tag", "餐饮"); put("date", 1_700_000_000_000)
            })
        })
        root.put("loopTodos", JSONArray().apply {
            put(JSONObject().apply { put("id", 1); put("title", "值日"); put("dayOfWeek", 3); put("doneThisWeek", false) })
        })
        root.put("accTags", JSONArray().apply {
            put(JSONObject().apply { put("id", 1); put("name", "餐饮"); put("color", 0xFFFF3B30L); put("icon", "") })
        })
        root.put("quickEntries", JSONArray().apply {
            put(JSONObject().apply { put("id", 1); put("title", "地铁"); put("amountCents", 500); put("type", 0); put("sortOrder", 0) })
        })
        root.put("pref_budget", "200000")
        root.put("pref_font_scale", "1.15")
        root.put("pref_dark", "1")
        root.put("pref_tab_order", "账目,待办,设置")
        return root.toString()
    }

    private fun 解析(json: String): BackupCodec.Data {
        val r = BackupCodec.parse(json)
        assertTrue("本应解析成功，实际：${(r as? BackupCodec.Result.Bad)?.why}", r is BackupCodec.Result.Ok)
        return (r as BackupCodec.Result.Ok).data
    }

    private fun 失败码(json: String): Int {
        val r = BackupCodec.parse(json)
        assertTrue("本应被拒绝，实际解析成功", r is BackupCodec.Result.Bad)
        return (r as BackupCodec.Result.Bad).code
    }

    @Test
    fun 完整备份的所有字段都能读回来() {
        val d = 解析(完整备份())
        val t = d.todos!!.single()
        assertEquals(7L, t.id)
        assertEquals("交作业", t.title)
        assertEquals("物理实验报告", t.detail)
        assertTrue(t.done)
        assertEquals(1_700_000_000_000L, t.dueDate)
        assertEquals(3, t.priority)
        assertEquals(-2, t.sortOrder)          // 新条目的负序号不能丢，否则「新建的在最上面」会失效
        assertEquals(1_700_000_500_000L, t.doneAt)
        assertEquals(1580L, d.accounts!!.single().amountCents)
        assertEquals(3, d.loopTodos!!.single().dayOfWeek)
        assertEquals("餐饮", d.accTags!!.single().name)
        assertEquals(500L, d.quickEntries!!.single().amountCents)
        assertEquals(5, d.count)
        assertEquals("5.2.0", d.appVersion)
        assertEquals(6, d.dbVersion)
        // 设置项
        assertEquals("200000", d.prefs["budget_month"])
        assertEquals("1.15", d.prefs["font_scale"])
        assertEquals("1", d.prefs["dark"])
        assertEquals("账目,待办,设置", d.prefs["tab_order"])
    }

    @Test
    fun 文件里没有的那一段保持为null_恢复时不能清空那张表() {
        // 这是数据事故的核心：以前不看存在性就 clear()，一份只有 todos 的备份会把账目全清掉
        val root = JSONObject().apply {
            put("schema", 2)
            put("todos", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("title", "只剩待办") })
            })
        }
        val d = 解析(root.toString())
        assertNotNull("todos 段存在 → 不是 null（恢复时要清表重建）", d.todos)
        assertNull("accounts 段不在文件里 → 必须是 null（恢复时不许动账目表）", d.accounts)
        assertNull(d.accTags)
        assertNull(d.loopTodos)
        assertNull(d.quickEntries)
        assertTrue(d.summary().contains("没有账目"))
    }

    @Test
    fun 空数组段表示真没有数据_恢复时要清空该表() {
        val root = JSONObject().apply {
            put("schema", 2)
            put("todos", JSONArray())
            put("accounts", JSONArray())
        }
        val d = 解析(root.toString())
        assertNotNull("空数组 ≠ 缺段：必须清空", d.todos)
        assertEquals(0, d.todos!!.size)
        assertEquals(0, d.accounts!!.size)
    }

    @Test
    fun 旧版文件缺字段用默认值_负数时间戳归零() {
        val root = JSONObject().apply {
            put("schema", 1)                      // 初版：没有 priority/sortOrder/doneAt/dbVersion
            put("todos", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "老的"); put("dueDate", -5)   // 坏时间戳
                })
            })
        }
        val d = 解析(root.toString())
        val t = d.todos!!.single()
        assertEquals(0, t.priority)
        assertEquals(0, t.sortOrder)
        assertEquals(0L, t.doneAt)
        assertEquals("负时间戳应是「无期限」而不是 1970 年之前", 0L, t.dueDate)
        assertEquals(0, d.dbVersion)
    }

    @Test
    fun 越界字段夹到合法区间而不是拒绝整份() {
        val root = JSONObject().apply {
            put("schema", 2)
            put("todos", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("title", "x"); put("priority", 9); put("dueDate", 9_999_999_999_999_999L) })
            })
            put("loopTodos", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("title", "y"); put("dayOfWeek", 0) })
            })
            put("accounts", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("amountCents", 100); put("type", 7) })
            })
        }
        val d = 解析(root.toString())
        assertEquals("优先级越界 → 夹到 3", 3, d.todos!!.single().priority)
        assertEquals("远期时间戳 → 归 0（多半是字段串了）", 0L, d.todos!!.single().dueDate)
        assertEquals("星期几越界 → 夹到 1", 1, d.loopTodos!!.single().dayOfWeek)
        assertEquals("类型只认 1=收入，其余当支出", 0, d.accounts!!.single().type)
    }

    @Test
    fun 数字型字段里混进文字_整份拒绝且不动数据库() {
        // 以前 optLong("amountCents") 会静默取 0 —— 一笔账变成 0.00 元，用户完全看不出来
        val json = JSONObject().apply {
            put("schema", 2)
            put("accounts", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("amountCents", "一百块") })
            })
        }.toString()
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(json))
    }

    @Test
    fun 金额越界_拒绝整份() {
        val json = JSONObject().apply {
            put("schema", 2)
            put("accounts", JSONArray().apply {
                put(JSONObject().apply { put("id", 1); put("amountCents", 999_999_999_999_999_999L) })
            })
        }.toString()
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(json))
    }

    @Test
    fun 空数组段不是数组_拒绝整份() {
        val json = JSONObject().apply {
            put("schema", 2)
            put("todos", "不是数组")
        }.toString()
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(json))

        val json2 = JSONObject().apply {
            put("schema", 2)
            put("todos", JSONArray().apply { put("不是对象") })
        }.toString()
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(json2))
    }

    @Test
    fun 来自更新版本的文件被拒绝() {
        val json = JSONObject().apply {
            put("schema", BackupCodec.SCHEMA + 1)
            put("todos", JSONArray())
        }.toString()
        assertEquals(BackupCodec.ERR_NEWER, 失败码(json))
    }

    @Test
    fun 不是备份文件被拒绝() {
        assertEquals(BackupCodec.ERR_FORMAT, 失败码("hello world"))
        assertEquals(BackupCodec.ERR_FORMAT, 失败码("{}"))
        // 只有设置项的片段（手工拼的文件）也不算备份
        assertEquals(BackupCodec.ERR_FORMAT, 失败码(JSONObject().apply { put("pref_budget", "1") }.toString()))
    }

    @Test
    fun 被写坏的字号不会恢复进设置() {
        // 字号若被写坏成 999，恢复后所有文字放大 999 倍 → 界面直接不可用
        val root = JSONObject().apply {
            put("schema", 2)
            put("todos", JSONArray())
            put("pref_font_scale", "999")
            put("pref_dark", "7")
            put("pref_budget", "abc")
            put("pref_tab_order", "待办,账目,设置")
        }
        val d = 解析(root.toString())
        assertNull("越界字号必须丢弃", d.prefs["font_scale"])
        assertNull("非法明暗值必须丢弃", d.prefs["dark"])
        assertNull("非法预算必须丢弃", d.prefs["budget_month"])
        assertEquals("待办,账目,设置", d.prefs["tab_order"])
    }
}
