package com.jaywasami.stockwatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jaywasami.stockwatch.shared.ChartPoint
import com.jaywasami.stockwatch.shared.Fmt
import com.jaywasami.stockwatch.shared.MarketHours
import com.jaywasami.stockwatch.shared.Quote
import com.jaywasami.stockwatch.shared.Stock
import com.jaywasami.stockwatch.shared.YahooApi
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime

// 台股習慣:紅漲、綠跌、白平盤
private val Up = Color(0xFFFF4B4B)
private val Down = Color(0xFF2ECC71)
private val Flat = Color.White
private val Card = Color(0xFF1C1C1E)
private val Warn = Color(0xFFFFCC00)

private fun colorOf(q: Quote?): Color = when (q?.direction) {
    1 -> Up
    -1 -> Down
    else -> Flat
}

/** 開盤中 5 秒更新一次;其他時間 5 分鐘一次 */
private fun refreshDelay(): Long = if (MarketHours.isOpen()) 5_000L else 300_000L

@Composable
fun WatchApp() {
    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = "list") {
            composable("list") {
                ListScreen(onOpen = { nav.navigate("detail/${it.code}") })
            }
            composable("detail/{code}") { entry ->
                val code = entry.arguments?.getString("code") ?: ""
                DetailScreen(code)
            }
        }
    }
}

@Composable
fun ListScreen(onOpen: (Stock) -> Unit) {
    val stocks by WatchStore.list.collectAsStateWithLifecycle()
    val quotes by QuoteRepository.quotes.collectAsStateWithLifecycle()
    val error by QuoteRepository.error.collectAsStateWithLifecycle()
    val updatedAt by QuoteRepository.updatedAt.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 只有螢幕亮著、App 在前面時才更新;螢幕熄滅就自動停止
    LaunchedEffect(stocks) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                QuoteRepository.refresh(stocks)
                delay(refreshDelay())
            }
        }
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("上班偷看盤", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            error?.let { msg ->
                item {
                    Text(
                        if (quotes.isEmpty()) "$msg\n恢復後會自動更新" else "$msg(顯示的是上次的價格)",
                        color = Warn,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (stocks.isEmpty()) {
                item {
                    Text(
                        "清單是空的\n請在手機 App 新增股票",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(stocks, key = { it.code }) { s ->
                StockRow(s, quotes[s.code]) { onOpen(s) }
            }
            item {
                val note = when {
                    updatedAt == null -> "讀取中…"
                    MarketHours.isOpen() -> "更新於 $updatedAt"
                    else -> "非交易時間・收盤價"
                }
                Text(note, color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StockRow(stock: Stock, q: Quote?, onClick: () -> Unit) {
    val c = colorOf(q)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stock.name,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(Fmt.change(q?.change), color = c, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.price(q?.price),
                color = c,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(Fmt.percent(q?.percent), color = c, fontSize = 13.sp)
        }
    }
}

@Composable
fun DetailScreen(code: String) {
    val stocks by WatchStore.list.collectAsStateWithLifecycle()
    val quotes by QuoteRepository.quotes.collectAsStateWithLifecycle()
    val error by QuoteRepository.error.collectAsStateWithLifecycle()
    val stock = stocks.firstOrNull { it.code == code } ?: Stock(code, code, "tse")
    val q = quotes[code]
    val lifecycleOwner = LocalLifecycleOwner.current

    var yahooPoints by remember(code) { mutableStateOf<List<ChartPoint>?>(null) }
    var yahooFailed by remember(code) { mutableStateOf(false) }

    LaunchedEffect(code) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                QuoteRepository.refresh(listOf(stock))
                delay(refreshDelay())
            }
        }
    }
    LaunchedEffect(code) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val d = YahooApi.intraday(stock)
                    yahooPoints = d.points
                    yahooFailed = d.points.isEmpty()
                } catch (e: Exception) {
                    yahooFailed = true
                }
                delay(if (MarketHours.isOpen()) 60_000L else 600_000L)
            }
        }
    }

    // 走勢圖:優先用 Yahoo,最新一點用證交所即時價補上;Yahoo 失敗就用手錶自己記錄的
    val nowSec = Instant.now().epochSecond
    val yp = yahooPoints
    val (points, simple) = if (!yahooFailed && !yp.isNullOrEmpty()) {
        val price = q?.price
        val merged = if (price != null && MarketHours.isOpen() && nowSec > yp.last().epochSec) {
            yp + ChartPoint(nowSec, price)
        } else yp
        merged to false
    } else {
        QuoteRepository.recordedPoints(code) to true
    }

    val c = colorOf(q)
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(stock.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (q == null) {
                item {
                    Text(
                        if (error != null) "無法取得資料\n恢復網路後會自動更新" else "讀取中…",
                        color = if (error != null) Warn else Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                item {
                    Text(Fmt.price(q.price), color = c, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    Text("${Fmt.change(q.change)}  ${Fmt.percent(q.percent)}", color = c, fontSize = 13.sp)
                }
                if (error != null) {
                    item { Text("$error(顯示的是上次的價格)", color = Warn, fontSize = 11.sp) }
                }
            }
            item {
                ChartBox(points, q?.prevClose ?: q?.price, c, simple, hasError = error != null || yahooFailed)
            }
            if (q != null) {
                item { InfoRow("開盤", Fmt.price(q.open)) }
                item { InfoRow("最高", Fmt.price(q.high), Up) }
                item { InfoRow("最低", Fmt.price(q.low), Down) }
                item { InfoRow("昨收", Fmt.price(q.prevClose)) }
                if (!stock.isIndex) item { InfoRow("成交量", Fmt.volume(q.volume)) }
                item {
                    Text(
                        if (MarketHours.isOpen()) "資料時間 ${q.time}" else "非交易時間・收盤價",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp)
    }
}

@Composable
private fun ChartBox(points: List<ChartPoint>, baseline: Double?, color: Color, simple: Boolean, hasError: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (points.size < 2) {
            Text(
                if (hasError && simple) "無法取得走勢資料" else "目前沒有走勢資料",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }
        PriceChart(points, baseline, color)
        if (simple) {
            Spacer(Modifier.height(2.dp))
            Text("簡易走勢(打開 App 後才開始記錄)", color = Warn, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PriceChart(points: List<ChartPoint>, baseline: Double?, color: Color) {
    val first = Instant.ofEpochSecond(points.first().epochSec).atZone(MarketHours.TAIPEI)
    val day: ZonedDateTime = first.toLocalDate().atStartOfDay(MarketHours.TAIPEI)
    val start = day.with(MarketHours.OPEN).toEpochSecond()
    val end = day.with(MarketHours.CLOSE).toEpochSecond()
    var minP = points.minOf { it.price }
    var maxP = points.maxOf { it.price }
    if (baseline != null) {
        minP = minOf(minP, baseline)
        maxP = maxOf(maxP, baseline)
    }
    if (maxP - minP < 0.0001) {
        maxP += 1
        minP -= 1
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text("H ${Fmt.price(points.maxOf { it.price })}", color = Up, fontSize = 10.sp)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val w = size.width
            val h = size.height
            fun x(t: Long) = ((t - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f) * w
            fun y(p: Double) = (h - ((p - minP) / (maxP - minP)).toFloat() * h)

            // 平盤(昨收)虛線
            if (baseline != null) {
                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, y(baseline)),
                    end = Offset(w, y(baseline)),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p.epochSec), y(p.price)) else path.lineTo(x(p.epochSec), y(p.price))
            }
            drawPath(path, color = color, style = Stroke(width = 2.5f))
        }
        Text("L ${Fmt.price(points.minOf { it.price })}", color = Down, fontSize = 10.sp)
    }
}
