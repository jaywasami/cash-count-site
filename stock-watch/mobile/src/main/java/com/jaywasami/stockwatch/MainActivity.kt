package com.jaywasami.stockwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jaywasami.stockwatch.shared.Stock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: PhoneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // App 開著的時候,每 10 秒確認一次手錶是否連著
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.refreshConnection()
                    delay(10_000)
                }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF111114)) {
                    PhoneScreen(vm)
                }
            }
        }
    }
}

private val Red = Color(0xFFFF5252)
private val Green = Color(0xFF34C759)

@Composable
fun PhoneScreen(vm: PhoneViewModel) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("上班偷看盤", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

        val (syncText, syncColor) = when (sync) {
            SyncState.SYNCING -> "傳送到手錶中…" to Color.LightGray
            SyncState.SYNCED -> "✓ 已同步到手錶" to Green
            SyncState.WATCH_OFFLINE -> "手錶未連線,稍後會自動補傳" to Color(0xFFFFCC00)
            SyncState.FAILED -> "傳送失敗,點這裡重試" to Red
        }
        Text(
            syncText,
            color = syncColor,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E22), RoundedCornerShape(10.dp))
                .clickable { vm.syncToWatch() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("代號或名稱,例如 2330 或 台積") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addFromQuery() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = vm::addFromQuery, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("新增")
                }
            }
        }

        message?.let { (text, isError) ->
            Text(text, color = if (isError) Red else Green, fontSize = 14.sp)
        }

        if (suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    itemsIndexed(suggestions, key = { _, s -> "sug_" + s.code }) { _, s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.add(s) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(s.name, Modifier.weight(1f), fontSize = 16.sp)
                            Text(s.displayCode, color = Color.Gray, fontSize = 16.sp)
                            Text("  ＋", color = Green, fontSize = 16.sp)
                        }
                        HorizontalDivider(color = Color(0xFF2A2A2E))
                    }
                }
            }
        }

        Text("我的清單(${list.size} 檔)— 手錶會照這個順序顯示", color = Color.Gray, fontSize = 13.sp)

        if (list.isEmpty()) {
            Text("清單是空的,請在上面輸入股票新增", color = Color.LightGray)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(list, key = { _, s -> s.code }) { index, s ->
                StockRow(
                    stock = s,
                    canUp = index > 0,
                    canDown = index < list.size - 1,
                    onUp = { vm.move(s, -1) },
                    onDown = { vm.move(s, 1) },
                    onDelete = { vm.remove(s) },
                )
            }
        }
    }
}

@Composable
private fun StockRow(
    stock: Stock,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E22), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stock.name, fontSize = 17.sp, color = Color.White)
            Text(stock.displayCode, fontSize = 13.sp, color = Color.Gray)
        }
        TextButton(onClick = onUp, enabled = canUp) { Text("▲", fontSize = 18.sp) }
        TextButton(onClick = onDown, enabled = canDown) { Text("▼", fontSize = 18.sp) }
        TextButton(onClick = onDelete) { Text("刪除", color = Red) }
    }
}
