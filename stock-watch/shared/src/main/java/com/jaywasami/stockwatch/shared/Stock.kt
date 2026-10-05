package com.jaywasami.stockwatch.shared

import org.json.JSONArray
import org.json.JSONObject

/** 一檔股票。market 為 "tse"(上市) 或 "otc"(上櫃)。加權指數的代號固定為 t00。 */
data class Stock(val code: String, val name: String, val market: String) {
    val isIndex: Boolean get() = code == INDEX_CODE

    /** 證交所即時報價用的代號,例如 tse_2330.tw */
    val misKey: String get() = "${market}_${code}.tw"

    /** Yahoo 走勢圖用的代號 */
    val yahooSymbol: String
        get() = when {
            isIndex -> "^TWII"
            market == "otc" -> "$code.TWO"
            else -> "$code.TW"
        }

    /** 手機清單上顯示的代號 */
    val displayCode: String get() = if (isIndex) "大盤" else code

    companion object {
        const val INDEX_CODE = "t00"
        val INDEX = Stock(INDEX_CODE, "加權指數", "tse")
        val DEFAULT_LIST = listOf(
            INDEX,
            Stock("2330", "台積電", "tse"),
            Stock("0050", "元大台灣50", "tse"),
        )
    }
}

object StockListCodec {
    fun encode(list: List<Stock>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("c", it.code).put("n", it.name).put("m", it.market))
        }
        return arr.toString()
    }

    fun decode(text: String?): List<Stock>? {
        if (text.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Stock(o.getString("c"), o.getString("n"), o.optString("m", "tse"))
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** 手機與手錶之間傳送清單用的路徑與欄位名稱 */
object SyncPaths {
    const val WATCHLIST = "/watchlist"
    const val KEY_LIST = "list"
    const val KEY_TIME = "time"
}
