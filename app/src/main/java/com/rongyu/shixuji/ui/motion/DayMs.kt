package com.rongyu.shixuji.ui.motion

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「天毫秒」与 [LocalDate] 的互转。
 *
 * 应用其余部分（DateUtils、Room 的日期列）都以「本机时区的当天 00:00 毫秒」为一天的身份，
 * 这里必须用同一个时区换算，否则周条上的格子和列表里的数据会差一天。
 */
private val zone: ZoneId get() = ZoneId.systemDefault()

/** 这一天在本机时区的 00:00 毫秒 */
fun LocalDate.toStartMs(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** 这个毫秒时刻在本机时区是哪一天 */
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
