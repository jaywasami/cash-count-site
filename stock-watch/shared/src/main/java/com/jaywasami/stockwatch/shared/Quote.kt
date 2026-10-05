package com.jaywasami.stockwatch.shared

import java.text.DecimalFormat
import java.util.Locale

/** 一檔股票的即時報價 */
data class Quote(
    val code: String,
    val price: Double?,
    val prevClose: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    /** 累積成交量(張) */
    val volume: Long?,
    /** 資料時間,例如 13:30:00 */
    val time: String,
    /** 資料日期,例如 20261005 */
    val date: String,
) {
    val change: Double?
        get() = if (price != null && prevClose != null) price - prevClose else null

    val percent: Double?
        get() {
            val c = change ?: return null
            val p = prevClose ?: return null
            return if (p == 0.0) null else c / p * 100.0
        }

    /** 1 = 漲, -1 = 跌, 0 = 平盤或沒有資料 */
    val direction: Int
        get() {
            val c = change ?: return 0
            return when {
                c > 0.00001 -> 1
                c < -0.00001 -> -1
                else -> 0
            }
        }
}

object Fmt {
    private val volumeFormat = DecimalFormat("#,###")

    fun price(v: Double?): String = if (v == null) "--" else String.format(Locale.US, "%.2f", v)

    fun change(v: Double?): String = when {
        v == null -> "--"
        v > 0.00001 -> String.format(Locale.US, "▲%.2f", v)
        v < -0.00001 -> String.format(Locale.US, "▼%.2f", -v)
        else -> "0.00"
    }

    fun percent(v: Double?): String = when {
        v == null -> "(--)"
        v > 0.00001 -> String.format(Locale.US, "(+%.2f%%)", v)
        v < -0.00001 -> String.format(Locale.US, "(%.2f%%)", v)
        else -> "(0.00%)"
    }

    fun volume(v: Long?): String = if (v == null) "--" else volumeFormat.format(v) + " 張"
}
