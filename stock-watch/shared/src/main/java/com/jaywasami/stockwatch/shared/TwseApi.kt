package com.jaywasami.stockwatch.shared

import org.json.JSONArray
import org.json.JSONObject

/** 證交所公開資料:即時報價、股票名稱清單 */
object TwseApi {
    private const val MIS_HTTPS = "https://mis.twse.com.tw"
    private const val MIS_HTTP = "http://mis.twse.com.tw"
    private var misBase = MIS_HTTPS
    private var warmedUp = false

    /** 取得多檔即時報價,回傳 代號 → 報價 */
    suspend fun quotes(stocks: List<Stock>): Map<String, Quote> {
        val result = HashMap<String, Quote>()
        for (chunk in stocks.distinctBy { it.misKey }.chunked(20)) {
            val arr = fetchMis(chunk.joinToString("|") { it.misKey })
            for (i in 0 until arr.length()) {
                val q = parseQuote(arr.getJSONObject(i)) ?: continue
                result[q.code] = q
            }
        }
        return result
    }

    /**
     * 用代號找股票(上市、上櫃、ETF 都可以)。找不到回傳 null;沒網路會丟出 NetworkException。
     */
    suspend fun lookupCode(rawCode: String): Stock? {
        val code = rawCode.trim().uppercase()
        if (code == Stock.INDEX_CODE.uppercase()) return Stock.INDEX
        if (!Regex("^[0-9A-Z]{4,6}$").matches(code)) return null
        val arr = fetchMis("tse_$code.tw|otc_$code.tw")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("n").trim()
            val c = o.optString("c").trim()
            if (name.isEmpty() || !c.equals(code, ignoreCase = true)) continue
            val market = if (o.optString("ex") == "otc") "otc" else "tse"
            return Stock(c, name, market)
        }
        return null
    }

    /** 下載全部上市+上櫃股票(含 ETF)名稱清單,給中文搜尋用 */
    suspend fun downloadSymbols(): List<Stock> {
        val list = ArrayList<Stock>()
        var lastError: Exception? = null
        try {
            val arr = JSONArray(Http.get("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL"))
            list += parseSymbolArray(arr, "tse")
        } catch (e: Exception) {
            lastError = e
        }
        try {
            val arr = JSONArray(Http.get("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes"))
            list += parseSymbolArray(arr, "otc")
        } catch (e: Exception) {
            lastError = e
        }
        if (list.isEmpty()) throw NetworkException("下載股票名稱清單失敗", lastError)
        return listOf(Stock.INDEX) + list.distinctBy { it.code }
    }

    private fun parseSymbolArray(arr: JSONArray, market: String): List<Stock> {
        val out = ArrayList<Stock>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = firstOf(o, "Code", "SecuritiesCompanyCode", "SecuritiesCompanyID", "代號")
            val name = firstOf(o, "Name", "CompanyName", "CompanyAbbreviation", "名稱")
            if (code.isNotEmpty() && name.isNotEmpty()) out += Stock(code, name, market)
        }
        return out
    }

    private fun firstOf(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k).trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private suspend fun fetchMis(exCh: String): JSONArray {
        if (!warmedUp) warmUp()
        val path = "/stock/api/getStockInfo.jsp?ex_ch=" +
            java.net.URLEncoder.encode(exCh, "UTF-8") +
            "&json=1&delay=0&_=" + System.currentTimeMillis()
        val text = try {
            Http.get(misBase + path, "$misBase/stock/index.jsp")
        } catch (e: NetworkException) {
            // 少數裝置不認得證交所的安全憑證,改用一般連線再試一次
            if (misBase == MIS_HTTPS && e.cause is javax.net.ssl.SSLException) {
                misBase = MIS_HTTP
                warmedUp = false
                warmUp()
                Http.get(misBase + path, "$misBase/stock/index.jsp")
            } else {
                throw e
            }
        }
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw NetworkException("證交所回傳的資料看不懂", e)
        }
        return json.optJSONArray("msgArray") ?: JSONArray()
    }

    private suspend fun warmUp() {
        try {
            Http.get("$misBase/stock/index.jsp")
        } catch (_: Exception) {
            // 只是為了拿 cookie,失敗也沒關係
        }
        warmedUp = true
    }

    private fun num(o: JSONObject, key: String): Double? {
        val s = o.optString(key).trim()
        if (s.isEmpty() || s == "-") return null
        return s.toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun firstOfList(o: JSONObject, key: String): Double? =
        o.optString(key).split("_").firstNotNullOfOrNull { it.trim().toDoubleOrNull()?.takeIf { v -> v > 0 } }

    private fun parseQuote(o: JSONObject): Quote? {
        val code = o.optString("c").trim()
        if (code.isEmpty()) return null
        val prev = num(o, "y")
        // z = 最近成交價;剛開盤還沒成交時用最佳買價,再沒有就用昨收
        val price = num(o, "z") ?: num(o, "pz") ?: firstOfList(o, "b") ?: prev
        return Quote(
            code = code,
            price = price,
            prevClose = prev,
            open = num(o, "o"),
            high = num(o, "h"),
            low = num(o, "l"),
            volume = o.optString("v").trim().toLongOrNull(),
            time = o.optString("t"),
            date = o.optString("d"),
        )
    }
}
