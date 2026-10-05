package com.jaywasami.stockwatch

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.jaywasami.stockwatch.shared.Stock
import com.jaywasami.stockwatch.shared.StockListCodec
import com.jaywasami.stockwatch.shared.SyncPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 手錶上記住的股票清單(存在手錶裡,手機不在也能用) */
object WatchStore {
    private val _list = MutableStateFlow<List<Stock>>(Stock.DEFAULT_LIST)
    val list: StateFlow<List<Stock>> = _list
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences("stocks", Context.MODE_PRIVATE)
        StockListCodec.decode(prefs.getString("list", null))?.let { _list.value = it }
    }

    fun saveFromDataMap(context: Context, map: DataMap) {
        init(context)
        val text = map.getString(SyncPaths.KEY_LIST) ?: return
        val list = StockListCodec.decode(text) ?: return
        context.getSharedPreferences("stocks", Context.MODE_PRIVATE)
            .edit().putString("list", text).apply()
        _list.value = list
    }
}
