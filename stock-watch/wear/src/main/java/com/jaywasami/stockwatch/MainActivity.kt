package com.jaywasami.stockwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.jaywasami.stockwatch.shared.SyncPaths
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        loadLatestFromPhone()
        setContent { WatchApp() }
    }

    /** 打開時主動看一下手機最後傳來的清單(避免錯過通知) */
    private fun loadLatestFromPhone() {
        lifecycleScope.launch {
            try {
                val buffer = Wearable.getDataClient(this@MainActivity).dataItems.await()
                try {
                    val item = buffer.filter { it.uri.path == SyncPaths.WATCHLIST }
                        .maxByOrNull { DataMapItem.fromDataItem(it).dataMap.getLong(SyncPaths.KEY_TIME) }
                    if (item != null) {
                        WatchStore.saveFromDataMap(this@MainActivity, DataMapItem.fromDataItem(item).dataMap)
                    }
                } finally {
                    buffer.release()
                }
            } catch (_: Exception) {
                // 沒有手機也沒關係,用手錶上記住的清單
            }
        }
    }
}
