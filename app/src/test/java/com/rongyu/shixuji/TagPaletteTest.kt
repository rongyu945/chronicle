package com.rongyu.shixuji

import com.rongyu.shixuji.data.AccTagEntity
import com.rongyu.shixuji.data.TagPalette
import com.rongyu.shixuji.data.orderTagsByUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 标签色板与排序的守护测试。
 *
 * 色板是**手工算出来**的（CIELAB 里按最远点散布取的 24 色），改一个 hex 就可能让两个颜色
 * 变得分不开 —— 而这种问题在真机上只表现为「两个标签的圆点看着一样」，很难被发现。
 * 这里把「两两色差」写成断言，改色板时必须同时满足。
 */
class TagPaletteTest {

    private fun rgb(c: Long) = intArrayOf(
        ((c shr 16) and 0xFF).toInt(), ((c shr 8) and 0xFF).toInt(), (c and 0xFF).toInt()
    )

    /** sRGB → CIELAB（D65），用来算 ΔE76 */
    private fun lab(c: Long): DoubleArray {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val (r, g, b) = rgb(c).map { lin(it) }
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 1.00000
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun deltaE(a: Long, b: Long): Double {
        val (la, lb) = lab(a) to lab(b)
        return sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    @Test
    fun `色板有 24 个不重复的颜色`() {
        assertEquals(24, TagPalette.ALL.size)
        assertEquals(24, TagPalette.ALL.toSet().size)
    }

    @Test
    fun `任意两色的色差都不小于 20`() {
        // 用户 2026-10-25 的要求是「色差明显一点」。2026-11-01 换成传统清透色后
        // 实测最小 21.0（上一版 26.0，但整体发浊），所以门槛仍是 20。
        var min = Double.MAX_VALUE
        var worst = ""
        for (i in TagPalette.ALL.indices) {
            for (j in i + 1 until TagPalette.ALL.size) {
                val d = deltaE(TagPalette.ALL[i], TagPalette.ALL[j])
                if (d < min) {
                    min = d
                    worst = "%06X / %06X".format(TagPalette.ALL[i] and 0xFFFFFF, TagPalette.ALL[j] and 0xFFFFFF)
                }
            }
        }
        assertTrue("最接近的一对 $worst 只差 ΔE=$min", min >= 20.0)
    }

    @Test
    fun `色板里没有接近无标签灰的颜色`() {
        // 账目页「无标签」用的灰是 TagPalette.UNLABELED（苍白 #D1D9E0）：色板里若混进
        // 相近的灰，用户会分不清「这是个标签」还是「这没标签」。实测最小 26.7。
        val gray = TagPalette.UNLABELED
        val min = TagPalette.ALL.minOf { deltaE(it, gray) }
        assertTrue("色板与无标签灰的色差只有 $min", min >= 25.0)
    }

    @Test
    fun `超过 24 个标签时循环取色不会越界`() {
        assertEquals(TagPalette.ALL[0], TagPalette.colorAt(0))
        assertEquals(TagPalette.ALL[0], TagPalette.colorAt(24))
        assertEquals(TagPalette.ALL[3], TagPalette.colorAt(27))
        assertEquals(TagPalette.ALL[23], TagPalette.colorAt(-1))
    }

    // ===== 记账弹窗里的标签排序（最近 30 天最常用在前） =====

    private fun tag(id: Long, name: String) = AccTagEntity(id = id, name = name, color = TagPalette.colorAt(id.toInt()))

    @Test
    fun `按最近 30 天用量从多到少排`() {
        val tags = listOf(tag(1, "吃饭"), tag(2, "交通"), tag(3, "购物"))
        val ordered = orderTagsByUsage(tags, mapOf("购物" to 9, "交通" to 2, "吃饭" to 5))
        assertEquals(listOf("购物", "吃饭", "交通"), ordered.map { it.name })
    }

    @Test
    fun `没用过的标签排在最后且保持原顺序`() {
        val tags = listOf(tag(1, "吃饭"), tag(2, "交通"), tag(3, "购物"), tag(4, "房租"))
        // 交通、房租 30 天内一次没用 → 按标签本身的顺序垫在末尾
        val ordered = orderTagsByUsage(tags, mapOf("购物" to 3, "吃饭" to 1))
        assertEquals(listOf("购物", "吃饭", "交通", "房租"), ordered.map { it.name })
    }

    @Test
    fun `用量相同时保持标签原顺序`() {
        val tags = listOf(tag(1, "甲"), tag(2, "乙"), tag(3, "丙"))
        val ordered = orderTagsByUsage(tags, mapOf("甲" to 2, "乙" to 2, "丙" to 2))
        assertEquals(listOf("甲", "乙", "丙"), ordered.map { it.name })
    }

    @Test
    fun `没有使用记录时顺序完全不变`() {
        val tags = listOf(tag(1, "甲"), tag(2, "乙"), tag(3, "丙"))
        assertEquals(listOf("甲", "乙", "丙"), orderTagsByUsage(tags, emptyMap()).map { it.name })
    }
}
