package com.rongyu.shixuji.data

import java.math.BigInteger
import java.math.RoundingMode

/**
 * 金额输入框里的**简单算式**（用户 2026-11 提的需求）。
 *
 * 记账时常常要先算一下：三个人 AA 的 `128/3`、买了三样 `12.5+8+36`、打折 `199*0.85`。
 * 以前只能先算完再输结果，多一步还容易错。现在输入框直接写算式，保存时按结果入账。
 *
 * 口径（写死的几条，别擅自放宽）：
 * - 只认 `+ - * /`（`× ÷` 自动换掉）、括号、小数点；输入数字最多两位小数（结果不限制），
 *   结果四舍五入到分。
 * - **不认百分号**：`199*0.85` 自己换算，`-15%` 这种写法不做（免得跟减号打架、也没提示位置）。
 * - 结果必须 ≥ 0 且不超过 [MAX_CENTS]（¥999,999,999.99），与普通金额同一个上限。
 * - 除零、括号不配对、连着两个运算符 → 一律返回 null，调用方给红字。
 *
 * ★ 7.5.1（审查报告 P1-A）：中间运算改成**精确有理数**（BigInteger 分子/分母），只在最后
 * 转「分」时舍入一次。旧实现每次除法都用 `MathContext(12)` 截到 12 位有效数字，误差会在
 * 后面的乘法里放大 —— `999999999.99/7*6` 因此少 1 分（857142857.136 → .14，真值 .1342857… → .13）。
 * 输入被 sanitize 限长 48 字符，分子分母规模完全可控，不需要任何误差上界论证。
 *
 * 纯函数、不碰 Compose / Room，可直接单测（见 AmountExprTest）。
 */
object AmountExpr {

    /** 与 [com.rongyu.shixuji.ui.parseYuanToCents] 同一个上限 */
    const val MAX_CENTS = 99_999_999_999L

    private val BIG_MAX_CENTS = BigInteger.valueOf(MAX_CENTS)

    /** 输入框最多允许的长度：够写一长串加减，又不至于被人粘贴一篇文章 */
    private const val MAX_LEN = 48

    /**
     * 全角统一成半角，并把**全角数字/小数点**归一成 ASCII。
     *
     * 为什么必须归一数字：`Char.isDigit()` 对 `'１'` 返回 true，解析器于是把它吃进数字段，
     * 再交给 `BigInteger("１")` —— 直接抛异常，用户只看到「算式无效」的红字，
     * 明明输入的是合法金额（中文输入法全角模式 / 网页粘贴很常见）。
     */
    fun normalize(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            append(
                when (ch) {
                    in '０'..'９' -> '0' + (ch - '０')
                    '．' -> '.'
                    '×' -> '*'
                    '÷' -> '/'
                    '（' -> '('
                    '）' -> ')'
                    '＋' -> '+'
                    '－', '\u2212' -> '-'   // 全角减号 / 数学减号（从网页、PDF 复制常见）
                    '＊' -> '*'
                    '／' -> '/'
                    else -> ch
                }
            )
        }
    }

    /** 是否含运算符 —— 决定要不要在输入框下面显示「= ¥xx.xx」的换算结果 */
    fun isExpression(text: String): Boolean =
        normalize(text).any { it == '+' || it == '-' || it == '*' || it == '/' || it == '(' || it == ')' }

    /**
     * 过滤用户输入：只留数字、小数点、运算符、括号、空格。
     *
     * 金额框的键盘是 ASCII 全键盘（要能打 `+ - * /`），中文输入法或长按粘贴都可能带进
     * 汉字/字母，这里直接丢掉，免得拼出一条谁都看不懂的式子。
     */
    fun sanitize(raw: String): String {
        val sb = StringBuilder()
        for (ch in normalize(raw)) {
            if (ch in '0'..'9' || ch == '.' || ch == '+' || ch == '-' || ch == '*' || ch == '/' ||
                ch == '(' || ch == ')' || ch == ' '
            ) sb.append(ch)
            if (sb.length >= MAX_LEN) break
        }
        return sb.toString()
    }

    /**
     * 过滤用户输入，但**保留 `×` `÷` 的原样写法**（自绘小键盘用）。
     *
     * 为什么不能直接复用 [sanitize]：那个函数面向「贴进来的脏文本」，会把全文归一成 `*` `/`。
     * 小键盘按 `×` 是给用户看的，归一之后输入框里变成 `128/3*6` —— 和刚按下的键不一样，
     * 看着像按错了。显示归显示，[evalToCents] 求值时自己会归一。
     */
    fun sanitizeDisplay(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            val ok = ch in '0'..'9' || ch == '.' || ch == '+' || ch == '-' || ch == '*' || ch == '/' ||
                ch == '(' || ch == ')' || ch == ' ' || ch == '×' || ch == '÷'
            if (ok) sb.append(ch)
            if (sb.length >= MAX_LEN) break
        }
        return sb.toString()
    }

    /**
     * 算式失败的原因。
     *
     * ★ 7.5.5（复核报告 8.2）：以前所有失败都返回 `null`，界面只有一句「金额写错了，检查一下算式」——
     *   用户按了 `12÷` 也这么说，按了 `1/0` 也这么说，按了 20 位的也这么说，等于没解释。
     *   现在把每种失败分开，界面上「就地解释」，改掉那个字符提示立刻消失或换成下一条。
     */
    enum class ErrorKind(val message: String) {
        /** 算式还没写完（`12÷`、`(12+`）—— 输入过程中不该报错，只在提交时提示 */
        INCOMPLETE("算式还没写完"),
        /** 括号没配对 */
        PAREN("括号没配对"),
        /** 除数为 0（`1/0`、`12/(3-3)`） */
        DIV_ZERO("不能除以 0"),
        /** 结果超过金额上限 */
        RANGE("金额超出上限（最大 ¥999,999,999.99）"),
        /** 结果是负数 */
        NEGATIVE("金额不能是负数"),
        /** 其它语法问题（连着两个运算符、两个小数点、多余字符…） */
        SYNTAX("算式看不懂"),
    }

    /** 求值结果：成功给分值，失败给原因 */
    sealed interface Eval {
        data class Ok(val cents: Long) : Eval
        data class Bad(val kind: ErrorKind) : Eval
    }

    /** 解析器内部用：带原因地中断 */
    private class ParseError(val kind: ErrorKind) : RuntimeException(kind.name)

    /**
     * 算式 → 分值或失败原因（[evalToCents] 是它的「只要值」版本）。
     *
     * 判断顺序：语法 → 负数 → 上限。负数与上限都在**转成「分」之后**判，
     * 所以 `0.001` 这种不足一分的正数不会因为中间值被判成 0 而出错。
     */
    fun evaluate(text: String): Eval {
        val src = normalize(text).replace(" ", "")
        if (src.isEmpty()) return Eval.Bad(ErrorKind.INCOMPLETE)
        return try {
            val p = Parser(src)
            val v = p.parseExpr()
            p.expectEnd()
            if (v.n.signum() < 0) return Eval.Bad(ErrorKind.NEGATIVE)
            // 分 = 分子 × 100 / 分母，四舍五入（HALF_UP）。全部整数运算，中间不丢精度。
            val scaled = v.n.multiply(HUNDRED)
            var cents = scaled.divide(v.d)
            val r = scaled.remainder(v.d)
            if (r.signum() != 0 && r.shiftLeft(1) >= v.d) cents = cents.add(BigInteger.ONE)
            if (cents.signum() < 0) return Eval.Bad(ErrorKind.NEGATIVE)
            if (cents > BIG_MAX_CENTS) return Eval.Bad(ErrorKind.RANGE)
            Eval.Ok(cents.toLong())
        } catch (e: ParseError) {
            Eval.Bad(e.kind)
        } catch (_: ArithmeticException) {
            Eval.Bad(ErrorKind.DIV_ZERO)
        } catch (_: Exception) {
            Eval.Bad(ErrorKind.SYNTAX)
        }
    }

    /** 算式 → 分。用精确有理数算完，只在最后一步舍入到分。失败（含原因）用 [evaluate]。 */
    fun evalToCents(text: String): Long? = (evaluate(text) as? Eval.Ok)?.cents

    /**
     * 递归下降：expr := term (('+'|'-') term)* ； term := unary (('*'|'/') unary)* ；
     * unary := '-' unary | factor ； factor := number | '(' expr ')'。
     *
     * 值用 [Frac]（精确分数）而不是 BigDecimal/Double：`0.1+0.2` 在 Double 下是
     * 0.30000000000000004；而 BigDecimal 的除法必须指定精度（旧版就是这里丢的 1 分）。
     * 分数在 `+ - * /` 下永远精确，最后一次性舍入到分。
     */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseExpr(): Frac {
            var v = parseTerm()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = parseTerm()
                v = if (op == '+') v.add(r) else v.sub(r)
            }
            return v
        }

        private fun parseTerm(): Frac {
            var v = parseUnary()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = parseUnary()
                v = if (op == '*') v.mul(r) else v.div(r)
            }
            return v
        }

        private fun parseUnary(): Frac {
            if (i < s.length && s[i] == '-') { i++; return parseUnary().negate() }
            if (i < s.length && s[i] == '+') { i++; return parseUnary() }
            return parseFactor()
        }

        private fun parseFactor(): Frac {
            if (i >= s.length) throw ParseError(ErrorKind.INCOMPLETE)
            if (s[i] == '(') {
                i++
                val v = parseExpr()
                // 括号里的算式已经解析完了、缺的只是那个 `)` —— 这是括号问题，不是「还没写完」
                if (i >= s.length || s[i] != ')') throw ParseError(ErrorKind.PAREN)
                i++
                return v
            }
            val start = i
            var dots = 0
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) {
                if (s[i] == '.' && ++dots > 1) throw ParseError(ErrorKind.SYNTAX)
                i++
            }
            if (i == start) throw ParseError(
                if (i >= s.length) ErrorKind.INCOMPLETE else ErrorKind.SYNTAX
            )
            return Frac.ofDecimal(s.substring(start, i))
        }

        fun expectEnd() {
            if (i != s.length) throw ParseError(ErrorKind.SYNTAX)
        }
    }

    private val HUNDRED = BigInteger.valueOf(100)

    /**
     * 精确分数：分子 [n] / 分母 [d]（d > 0，已约分）。
     *
     * 只在 `+ - * /` 上用，四种运算结果仍是分数 → 全程不丢精度。
     * 规模：输入最长 48 字符，就算写满 `1/3/7/11/13/…` 分母也只是若干个小数的乘积，
     * BigInteger 处理起来毫无压力（单次求值微秒级）。
     */
    private class Frac(val n: BigInteger, val d: BigInteger) {

        fun add(o: Frac) = of(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d))
        fun sub(o: Frac) = of(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d))
        fun mul(o: Frac) = of(n.multiply(o.n), d.multiply(o.d))

        fun div(o: Frac): Frac {
            if (o.n.signum() == 0) throw ArithmeticException("div0")
            return of(n.multiply(o.d), d.multiply(o.n))
        }

        fun negate() = Frac(n.negate(), d)

        companion object {
            /** 归一化：分母转正、按 gcd 约分。分母为 0 直接抛（除零）。 */
            fun of(nn: BigInteger, dd: BigInteger): Frac {
                if (dd.signum() == 0) throw ArithmeticException("div0")
                var a = nn
                var b = dd
                if (b.signum() < 0) { a = a.negate(); b = b.negate() }
                val g = a.gcd(b)
                return if (g.signum() == 0 || g == BigInteger.ONE) Frac(a, b)
                else Frac(a.divide(g), b.divide(g))
            }

            /** `"12.34"` → 1234/100（精确，不走 Double） */
            fun ofDecimal(text: String): Frac {
                val dot = text.indexOf('.')
                if (dot < 0) return of(BigInteger(text), BigInteger.ONE)
                val intPart = text.substring(0, dot).ifEmpty { "0" }
                val fracPart = text.substring(dot + 1)
                val digits = intPart + fracPart
                return of(BigInteger(digits), BigInteger.TEN.pow(fracPart.length))
            }
        }
    }
}
