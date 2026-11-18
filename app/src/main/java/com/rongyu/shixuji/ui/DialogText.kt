package com.rongyu.shixuji.ui

import androidx.compose.ui.unit.sp

/**
 * 弹窗里唯一的一套字号刻度（2026-11-08 用户：几种弹窗里的字体大小不统一，统一一下）。
 *
 * 之前每个弹窗各写各的：标题 16 或 17、行标签 12 / 13 / 14 混着来（同一个「记一笔」里
 * 「标签」12sp 而「日期」14sp）、错误提示 11sp 小到看不清。现在收成六档，一处改全 App 跟着变。
 *
 *  | 档位   | 值    | 用在哪                                             |
 *  |--------|-------|----------------------------------------------------|
 *  | Title  | 17sp  | 弹窗标题（粗体由调用处给）                          |
 *  | Main   | 16sp  | 详情弹窗的主项：账目名 / 待办名 / 大号日期          |
 *  | Value  | 15sp  | 行里可点的值：日期、金额                            |
 *  | Button | 14sp  | 弹窗按钮                                            |
 *  | Body   | 13sp  | 正文、备注、行标签、chip 文字                        |
 *  | Hint   | 12sp  | 错误红字、算式结果、「间隔 N 天」这类次要提示        |
 *
 * 注意：这些只是字号，字族仍由 songtiFamily（宋体 / Times New Roman）提供；
 * BasicTextField 与 ProvideTextStyle 里必须显式写 fontFamily，否则会掉回系统无衬线。
 */
object DialogText {
    val Title = 17.sp
    val Main = 16.sp
    val Value = 15.sp
    val Button = 14.sp
    val Body = 13.sp
    val Hint = 12.sp
}
