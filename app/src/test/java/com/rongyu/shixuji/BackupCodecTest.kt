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
        root.put("schema", 3)
        root.put("dbVersion", 10)
        root.put("appVersion", "7.5.1")
        root.put("loopDayEpoch", "civil")

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
            // 新格式（5.5.0 起）：直接给两个日期 + 间隔 + 已勾的日子
            put(JSONObject().apply {
                put("id", 1); put("title", "值日")
                put("startDate", 1_700_000_000_000L); put("nextDate", 1_700_259_200_000L)
                put("intervalDays", 3); put("doneDates", "19675")
            })
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
        // 间隔以两个日期为准（文件里写 3，日期实际相差 3 天 → 3）
        assertEquals(3, d.loopTodos!!.single().intervalDays)
        assertEquals("餐饮", d.accTags!!.single().name)
        assertEquals(500L, d.quickEntries!!.single().amountCents)
        assertEquals(5, d.count)
        assertEquals("7.5.1", d.appVersion)
        assertEquals(10, d.dbVersion)
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
        // 没有两个日期、只有越界的 dayOfWeek → 按「每周 7 天」还原，星期几夹到 1
        assertEquals("旧格式兜底成每周", 7, d.loopTodos!!.single().intervalDays)
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

    /**
     * 2026-11-08：文件被记事本/网盘另存过会带 UTF-8 BOM，`JSONObject("\uFEFF{...}")` 直接抛。
     * 用户看到「不是有效的 JSON 文件」，而内容其实是完好的备份。
     */
    @Test
    fun 带BOM的备份文件照常能读() {
        val data = 解析("\uFEFF" + 完整备份())
        assertTrue(data.todos?.isNotEmpty() == true)
    }

    // ===== 7.5.1（外部审查报告 P1）新增：备份偏好白名单 / doneDates 口径 / 严格整数 =====

    private fun 带偏好的备份(vararg prefs: Pair<String, Any>): String {
        val root = JSONObject().apply {
            put("schema", BackupCodec.SCHEMA)
            put("appVersion", "7.5.1")
            put("loopDayEpoch", "civil")
            put("todos", JSONArray())
        }
        prefs.forEach { (k, v) -> root.put(k, v) }
        return root.toString()
    }

    /** 审查 4.2：问候边界和撤回时长一直没进备份 —— 换手机后这两项悄悄回默认。 */
    @Test
    fun 备份现在带上问候时段与撤回时长() {
        val d = 解析(
            带偏好的备份(
                "pref_undo_ms" to "8000",
                "pref_greet_morning_end" to 425,
                "pref_greet_noon_start" to 710,
                "pref_greet_noon_end" to 790,
                "pref_greet_night_start" to 1290,
            )
        )
        assertEquals("8000", d.prefs["undo_ms"])
        assertEquals("425", d.prefs["greet_morning_end"])
        assertEquals("710", d.prefs["greet_noon_start"])
        assertEquals("790", d.prefs["greet_noon_end"])
        assertEquals("1290", d.prefs["greet_night_start"])
    }

    @Test
    fun 越界的撤回时长与自相矛盾的问候时段整组丢弃() {
        // 撤回时长超出 2–16 秒
        assertNull(解析(带偏好的备份("pref_undo_ms" to "60000")).prefs["undo_ms"])
        // 早安结束跑到午安开始之后（会画出一条反向墨线）→ 四个边界整组不要
        val d = 解析(
            带偏好的备份(
                "pref_greet_morning_end" to 800,
                "pref_greet_noon_start" to 720,
                "pref_greet_noon_end" to 780,
                "pref_greet_night_start" to 1260,
            )
        )
        assertNull(d.prefs["greet_morning_end"])
        assertNull(d.prefs["greet_noon_start"])
        assertNull(d.prefs["greet_noon_end"])
        assertNull(d.prefs["greet_night_start"])
    }

    /**
     * 审查 4.5：旧版（6.9.4 之前）的 doneDates 是 `floorDiv(本地零点)`，
     * 东半球比公历日序号小 1。库里的 `loop_epoch_day_v2` 迁移只管库里原有数据，
     * 导入进来的记录它不会再管一次 —— 必须在解析阶段按来源版本补上。
     */
    @Test
    fun 旧版备份的勾选日期按旧口径平移() {
        val old = JSONObject().apply {
            put("schema", 2)
            put("appVersion", "6.9.3")            // 最后一个用旧口径的版本
            put("todos", JSONArray())
            put("loopTodos", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "值日")
                    put("startDate", 1_700_000_000_000L); put("nextDate", 1_700_259_200_000L)
                    put("intervalDays", 3); put("doneDates", "19675")
                })
            })
        }
        val d = 解析(old.toString())
        val expected = (19675L + com.rongyu.shixuji.data.LoopSchedule.legacyDayShift()).toString()
        assertEquals(expected, d.loopTodos!!.single().doneDates)
    }

    @Test
    fun 新版本备份的勾选日期不会被二次平移() {
        val now = JSONObject().apply {
            put("schema", 2)
            put("appVersion", "6.9.4")            // 第一个用公历日序号的版本
            put("todos", JSONArray())
            put("loopTodos", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "值日")
                    put("startDate", 1_700_000_000_000L); put("nextDate", 1_700_259_200_000L)
                    put("intervalDays", 3); put("doneDates", "19675")
                })
            })
        }
        assertEquals("19675", 解析(now.toString()).loopTodos!!.single().doneDates)
    }

    /** 审查 4.8：`12.9` 以前被静默截成 `12`，等于把「文件写错了」变成「导入一个合法错值」。 */
    @Test
    fun 小数金额被判坏而不是截断() {
        val root = JSONObject().apply {
            put("schema", 3)
            put("appVersion", "7.5.1")
            put("todos", JSONArray())
            put("accounts", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "午饭"); put("amountCents", 12.9)
                    put("type", 0); put("date", 1_700_000_000_000L)
                })
            })
        }
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(root.toString()))
    }

    /** 审查 4.9：全 App 口径是「金额绝对值 + type」，导入格式不该比输入框更宽松。 */
    @Test
    fun 负金额被判坏() {
        val root = JSONObject().apply {
            put("schema", 3)
            put("appVersion", "7.5.1")
            put("todos", JSONArray())
            put("accounts", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "午饭"); put("amountCents", -1580)
                    put("type", 0); put("date", 1_700_000_000_000L)
                })
            })
        }
        assertEquals(BackupCodec.ERR_CORRUPT, 失败码(root.toString()))
    }

    @Test
    fun 版本号比较认主次修() {
        assertTrue(BackupCodec.versionAtLeast("6.9.4", 6, 9, 4))
        assertTrue(BackupCodec.versionAtLeast("7.5.1", 6, 9, 4))
        assertTrue(BackupCodec.versionAtLeast("6.10.0", 6, 9, 4))
        assertTrue(!BackupCodec.versionAtLeast("6.9.3", 6, 9, 4))
        assertTrue(!BackupCodec.versionAtLeast("5.9.30", 6, 9, 4))
    }

    /**
     * 审查 5.4：老版本曾把**毫秒时间戳**写进 doneDates（1.7e12 量级）。
     * 旧清洗只过滤 >= 0，那些数会被当成「一万多年后的日序号」保留，
     * 排序后占满「只留最近 120 个」的名额，把真实完成记录挤出去。
     */
    @Test
    fun 毫秒时间戳不会被当成日序号混进完成历史() {
        val root = JSONObject().apply {
            put("schema", 3)
            put("appVersion", "7.5.2")
            put("loopDayEpoch", "civil")
            put("todos", JSONArray())
            put("loopTodos", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1); put("title", "值日")
                    put("startDate", 1_700_000_000_000L); put("nextDate", 1_700_259_200_000L)
                    put("intervalDays", 3)
                    put("doneDates", "19675,1700000000000,19676,abc")
                })
            })
        }
        val d = 解析(root.toString())
        assertEquals("19675,19676", d.loopTodos!!.single().doneDates)
    }
}
