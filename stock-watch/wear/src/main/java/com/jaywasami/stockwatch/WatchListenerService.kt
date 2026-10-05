package com.jaywasami.stockwatch

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.jaywasami.stockwatch.shared.SyncPaths

class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != SyncPaths.WATCHLIST) continue
            WatchStore.saveFromDataMap(this, DataMapItem.fromDataItem(item).dataMap)
        }
    }
}
