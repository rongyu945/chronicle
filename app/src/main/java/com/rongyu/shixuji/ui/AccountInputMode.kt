package com.rongyu.shixuji.ui

/**
 * 记账弹窗当前的**输入工具**（7.7.9 引入）。
 *
 * 为什么要有这个东西：7.7.3 起用 `keypadVisible` + `noteFocused` 两个布尔值表达键盘状态，
 * 可它们描述的是「面板显不显示」和「焦点在哪」，都不是「用户此刻想用哪种输入」。
 * 于是「备注失焦」被当成「用户要输金额」—— 点标签、点日期、按系统返回键收起 IME，
 * 都会把自绘数字键盘弹回来，用户从没表达过那个意图。
 *
 * 现在键盘只由本枚举决定，焦点状态（`noteFocused`）只管视觉：
 *
 * | 模式 | 自绘键盘 | 系统 IME | 弹窗让位量 |
 * |---|---|---|---|
 * | [Amount] | 显示 | 关闭 | 面板高 + 导航条 + 12dp |
 * | [Note] | 隐藏 | 由 BasicTextField 自然唤起 | 真实 IME inset |
 * | [SwitchingToAmount] | 隐藏（等 IME 真的收起） | 正在收起 | 真实 IME inset |
 * | [None] | 隐藏 | 关闭 | 居中（无键盘时的常规位置） |
 *
 * 允许的转移：
 * - 打开弹窗 → [Amount]
 * - 备注获焦 → [Note]
 * - 点金额框（不在 [Amount] 时）→ [SwitchingToAmount] →（IME inset 归零）→ [Amount]
 * - 点「收起」（备注折叠）→ [Amount]（明确动作，不等 inset）
 * - 系统返回键收 IME → 仍 [Note]（不自动变 [Amount]）
 *
 * **禁止**的转移：备注失焦 → [Amount]；IME 还在显示时 [Amount] 已经可见；
 * 弹窗关闭后旧协程把键盘重新弹出来。
 */
enum class AccountInputMode {
    /** 自绘数字键盘（含算式键位），打开弹窗时的默认态 */
    Amount,

    /** 备注在用系统输入法 */
    Note,

    /** 已请求收起系统 IME，正在等 `WindowInsets.ime` 归零，期间两个键盘都不显示 */
    SwitchingToAmount,

    /** 当前没有任何键盘（弹窗回到垂直居中）；只在交接空档短暂出现 */
    None,
}
