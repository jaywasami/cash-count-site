package com.jaywasami.stockwatch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.jaywasami.stockwatch.shared.NetworkException
import com.jaywasami.stockwatch.shared.Stock
import com.jaywasami.stockwatch.shared.StockListCodec
import com.jaywasami.stockwatch.shared.SyncPaths
import com.jaywasami.stockwatch.shared.TwseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

enum class SyncState { SYNCING, SYNCED, WATCH_OFFLINE, FAILED }

class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("stocks", Context.MODE_PRIVATE)
    private val symbolsFile = File(app.filesDir, "symbols.json")

    private val _list = MutableStateFlow(StockListCodec.decode(prefs.getString("list", null)) ?: Stock.DEFAULT_LIST)
    val list: StateFlow<List<Stock>> = _list

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _suggestions = MutableStateFlow<List<Stock>>(emptyList())
    val suggestions: StateFlow<List<Stock>> = _suggestions

    /** 給使用者看的提示文字,第二個值 true 代表是錯誤 */
    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message: StateFlow<Pair<String, Boolean>?> = _message

    private val _sync = MutableStateFlow(SyncState.SYNCING)
    val sync: StateFlow<SyncState> = _sync

    private val _symbolsReady = MutableStateFlow(false)
    val symbolsReady: StateFlow<Boolean> = _symbolsReady

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private var symbols: List<Stock> = emptyList()

    init {
        viewModelScope.launch { loadSymbols() }
        syncToWatch()
    }

    private suspend fun loadSymbols() {
        val cached = withContext(Dispatchers.IO) {
            if (symbolsFile.exists()) StockListCodec.decode(symbolsFile.readText()) else null
        }
        if (cached != null) {
            symbols = cached
            _symbolsReady.value = true
        }
        val age = System.currentTimeMillis() - prefs.getLong("symbolsTime", 0)
        if (cached == null || age > 7L * 24 * 3600 * 1000) {
            try {
                val fresh = TwseApi.downloadSymbols()
                symbols = fresh
                withContext(Dispatchers.IO) { symbolsFile.writeText(StockListCodec.encode(fresh)) }
                prefs.edit().putLong("symbolsTime", System.currentTimeMillis()).apply()
                _symbolsReady.value = true
            } catch (e: Exception) {
                if (cached == null) {
                    _message.value = "股票名稱清單下載失敗(可能沒網路),目前只能用代號新增" to true
                }
            }
        }
        updateSuggestions()
    }

    fun setQuery(text: String) {
        _query.value = text
        _message.value = null
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        val upper = q.uppercase()
        _suggestions.value = symbols.asSequence()
            .filter { it.name.contains(q, ignoreCase = true) || it.code.startsWith(upper) }
            .sortedWith(compareBy({ !it.code.startsWith(upper) }, { it.code.length }, { it.code }))
            .take(30)
            .toList()
    }

    /** 按下「新增」:先找名稱清單,沒有的話直接問證交所 */
    fun addFromQuery() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _message.value = "請先輸入股票代號或名稱" to true
            return
        }
        val upper = q.uppercase()
        val exact = symbols.firstOrNull { it.code == upper }
            ?: symbols.firstOrNull { it.name == q }
            ?: _suggestions.value.singleOrNull()
        if (exact != null) {
            add(exact)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val found = TwseApi.lookupCode(q)
                if (found == null) {
                    _message.value = "找不到這檔股票" to true
                } else {
                    add(found)
                }
            } catch (e: NetworkException) {
                _message.value = "連不上網路,請稍後再試" to true
            } finally {
                _busy.value = false
            }
        }
    }

    fun add(stock: Stock) {
        if (_list.value.any { it.code == stock.code }) {
            _message.value = "「${stock.name}」已經在清單裡了" to true
            return
        }
        update(_list.value + stock)
        _query.value = ""
        _suggestions.value = emptyList()
        _message.value = "已新增「${stock.name}」" to false
    }

    fun remove(stock: Stock) {
        update(_list.value.filterNot { it.code == stock.code })
        _message.value = "已刪除「${stock.name}」" to false
    }

    fun move(stock: Stock, delta: Int) {
        val l = _list.value.toMutableList()
        val i = l.indexOfFirst { it.code == stock.code }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        update(l)
    }

    private fun update(newList: List<Stock>) {
        _list.value = newList
        prefs.edit().putString("list", StockListCodec.encode(newList)).apply()
        syncToWatch()
    }

    /** 把清單交給系統傳到手錶。手錶沒連線時,系統會在重新連上後自動補傳。 */
    fun syncToWatch() {
        viewModelScope.launch {
            _sync.value = SyncState.SYNCING
            try {
                val req = PutDataMapRequest.create(SyncPaths.WATCHLIST).apply {
                    dataMap.putString(SyncPaths.KEY_LIST, StockListCodec.encode(_list.value))
                    dataMap.putLong(SyncPaths.KEY_TIME, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(getApplication<Application>()).putDataItem(req).await()
                refreshConnection()
            } catch (e: Exception) {
                _sync.value = SyncState.FAILED
            }
        }
    }

    /** 檢查手錶是否連著 */
    fun refreshConnection() {
        viewModelScope.launch {
            if (_sync.value == SyncState.FAILED) return@launch
            _sync.value = try {
                val nodes = Wearable.getNodeClient(getApplication<Application>()).connectedNodes.await()
                if (nodes.isEmpty()) SyncState.WATCH_OFFLINE else SyncState.SYNCED
            } catch (e: Exception) {
                SyncState.WATCH_OFFLINE
            }
        }
    }
}
