package com.jaywasami.stockwatch.shared

import org.json.JSONObject

/** 走勢圖上的一個點:時間(秒)與價格 */
data class ChartPoint(val epochSec: Long, val price: Double)

data class Intraday(val points: List<ChartPoint>, val prevClose: Double?)

/** Yahoo 當日走勢資料 */
object YahooApi {
    suspend fun intraday(stock: Stock): Intraday {
        val symbol = java.net.URLEncoder.encode(stock.yahooSymbol, "UTF-8")
        val text = Http.get("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1m&range=1d")
        val result = try {
            JSONObject(text).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        } catch (e: Exception) {
            throw NetworkException("Yahoo 回傳的資料看不懂", e)
        }
        val meta = result.optJSONObject("meta")
        val prev = meta?.optDouble("chartPreviousClose")?.takeIf { !it.isNaN() }
            ?: meta?.optDouble("previousClose")?.takeIf { !it.isNaN() }
        val times = result.optJSONArray("timestamp")
        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
        val points = ArrayList<ChartPoint>()
        if (times != null && closes != null) {
            val n = minOf(times.length(), closes.length())
            for (i in 0 until n) {
                if (closes.isNull(i)) continue
                val p = closes.optDouble(i)
                if (p.isNaN() || p <= 0) continue
                points += ChartPoint(times.getLong(i), p)
            }
        }
        return Intraday(points, prev)
    }
}
