package com.jaywasami.stockwatch

import com.jaywasami.stockwatch.shared.ChartPoint
import com.jaywasami.stockwatch.shared.MarketHours
import com.jaywasami.stockwatch.shared.Quote
import com.jaywasami.stockwatch.shared.Stock
import com.jaywasami.stockwatch.shared.TwseApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 手錶上抓報價、記住最近報價,並在開盤時順便記錄簡易走勢(Yahoo 失敗時用) */
object QuoteRepository {
    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes

    /** null = 正常;否則是要給使用者看的錯誤提示 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _updatedAt = MutableStateFlow<String?>(null)
    val updatedAt: StateFlow<String?> = _updatedAt

    private val recorded = HashMap<String, MutableList<ChartPoint>>()
    private var recordedDate: String? = null

    suspend fun refresh(stocks: List<Stock>) {
        if (stocks.isEmpty()) return
        try {
            val fresh = TwseApi.quotes(stocks)
            if (fresh.isEmpty()) {
                _error.value = "暫時抓不到報價"
                return
            }
            _quotes.value = _quotes.value + fresh
            _error.value = null
            _updatedAt.value = ZonedDateTime.now(MarketHours.TAIPEI)
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            record(fresh.values)
        } catch (e: Exception) {
            _error.value = "連不上網路"
        }
    }

    private fun record(quotes: Collection<Quote>) {
        if (!MarketHours.isOpen()) return
        val now = ZonedDateTime.now(MarketHours.TAIPEI)
        val today = now.toLocalDate().toString()
        if (recordedDate != today) {
            recorded.clear()
            recordedDate = today
        }
        val sec = now.toEpochSecond()
        for (q in quotes) {
            val p = q.price ?: continue
            val list = recorded.getOrPut(q.code) { ArrayList() }
            if (list.isEmpty() || sec - list.last().epochSec >= 5) list += ChartPoint(sec, p)
        }
    }

    fun recordedPoints(code: String): List<ChartPoint> = recorded[code]?.toList() ?: emptyList()
}
