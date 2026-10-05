package com.jaywasami.stockwatch.shared

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object MarketHours {
    val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
    val OPEN: LocalTime = LocalTime.of(9, 0)
    val CLOSE: LocalTime = LocalTime.of(13, 30)

    /** 週一到週五 9:00–13:30(多留 2 分鐘抓最後收盤價) */
    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(TAIPEI)): Boolean {
        val t = now.withZoneSameInstant(TAIPEI)
        if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) return false
        val time = t.toLocalTime()
        return !time.isBefore(OPEN) && time.isBefore(CLOSE.plusMinutes(2))
    }
}
