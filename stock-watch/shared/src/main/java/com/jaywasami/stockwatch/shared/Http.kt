package com.jaywasami.stockwatch.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL

/** 網路連線失敗(沒網路、對方沒回應) */
class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    init {
        // 證交所網站有時需要先拿到 cookie 才會回資料
        if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager())
    }

    suspend fun get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (referer != null) conn.setRequestProperty("Referer", referer)
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw NetworkException("HTTP $code")
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e.message ?: "連線失敗", e)
        }
    }
}
