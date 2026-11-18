package com.rongyu.shixuji

import com.rongyu.shixuji.ui.csvCell
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CSV 单元格转义（7.5.1 审查 4.11 起加公式防护）。
 *
 * 背景：标题/标签是用户随便输的，以 `= + - @` 开头的单元格会被 Excel / LibreOffice
 * 当公式执行 —— 打开一份账单就可能发请求、跳外链。导出的是普通文本，不该有这种副作用。
 */
class CsvExportTest {

    @Test fun `危险前缀加单引号`() {
        // 加了单引号之后整格含逗号和引号，所以外面还会套一层引号、内部引号翻倍
        assertEquals(
            "\"'=HYPERLINK(\"\"http://x\"\",\"\"点我\"\")\"",
            csvCell("=HYPERLINK(\"http://x\",\"点我\")")
        )
        // 含逗号 → 整格再套一层引号
        assertEquals("\"'+SUM(1,2)\"", csvCell("+SUM(1,2)"))
        assertEquals("\"'@SUM(1,2)\"", csvCell("@SUM(1,2)"))
        assertEquals("'-2+3", csvCell("-2+3"))
    }

    @Test fun `金额列不做公式防护`() {
        // 金额是程序生成的 +12.34 / -12.34，加了单引号表格软件就当文本，没法合计
        assertEquals("-12.34", csvCell("-12.34", guardFormula = false))
        assertEquals("+12.34", csvCell("+12.34", guardFormula = false))
    }

    @Test fun `普通文本与转义规则不变`() {
        assertEquals("午饭", csvCell("午饭"))
        assertEquals("\"带,逗号\"", csvCell("带,逗号"))
        assertEquals("\"他说\"\"你好\"\"\"", csvCell("他说\"你好\""))
    }

    /**
     * 7.5.5（复核报告 2.11）：前导空白 / 不可见字符不能绕过前缀检查。
     *
     * 旧实现只看 `v[0]`：`" =1+1"`、`"\t=1+1"`、BOM 开头的 `"\uFEFF=1+1"` 全部原样导出。
     * 不同表格软件对这些前导字符的裁剪规则不一样，不能靠「Excel 大概会当文本」。
     */
    @Test fun `前导空白与不可见字符也要挡住公式`() {
        assertEquals("' =1+1", csvCell(" =1+1"))
        assertEquals("'\t=1+1", csvCell("\t=1+1"))                        // 制表不在转义集合里，只加单引号
        assertEquals("'\uFEFF=1+1", csvCell("\uFEFF=1+1"))
        assertEquals("\"'\u3000@SUM(1,2)\"", csvCell("\u3000@SUM(1,2)"))   // 全角空格开头；含逗号故整格再加引号
        assertEquals("'\u00A0-2", csvCell("\u00A0-2"))
        // 前导空白但后面不是公式符号 → 不动作
        assertEquals("  午饭", csvCell("  午饭"))
        assertEquals("", csvCell(""))
    }
}
