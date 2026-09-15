package com.example.audiobible.generatorAll

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyManager {
    private const val TAG = "BREAD_PARSER_LOG"

    // Пул готовых системных прокси
    private val proxyList = mutableListOf<Proxy>()

    // Хранилище ТОЛЬКО проверенных, рабочих прокси (наш аналог LiveData)
    private val _liveProxies = kotlinx.coroutines.flow.MutableStateFlow<List<Proxy>>(emptyList())
    val liveProxies = _liveProxies.asStateFlow()

    // Карта для подсчета ошибок: Ключ — прокси, Значение — сколько раз подряд он упал
    private val proxyFailureCount = java.util.Collections.synchronizedMap(mutableMapOf<Proxy, Int>())

    // Клиент без прокси для выкачивания баз
    private val directClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    /**
     * Скачивает чистые TXT списки прокси через стабильные API
     */
    suspend fun fetchFreshProxies() = withContext(Dispatchers.IO) {
        if (proxyList.isNotEmpty()) return@withContext

        Log.d(TAG, "[PROXY] Скачиваем свежие базы прокси через API...")

        // Переходим на чистые TXT-эндпоинты, которые выдают стабильный IP:PORT
        val sources = listOf(
            // Источник 1: Официальный быстрый API от ProxyScrape (HTTP)
            "https://proxyscrape.com" to Proxy.Type.HTTP,
            // Источник 2: Проверенный API от Proxmint (SOCKS5)
            "https://raw.githubusercontent.com/proxmint/free-proxy-list/main/proxies/socks5.txt" to Proxy.Type.SOCKS,
            // Источник 3: Мировой пул SOCKS4
            "https://githubusercontent.com" to Proxy.Type.SOCKS
        )

        for ((url, type) in sources) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                directClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val text = res.body?.string() ?: ""

                    synchronized(proxyList) {
                        for (rawLine in text.lines()) {
                            val line = rawLine.trim()
                            if (line.isBlank() || line.contains("html") || line.contains("<")) continue

                            // Обрабатываем формат "protocol://ip:port" или чистый "ip:port"
                            val cleanLine = if (line.contains("://")) {
                                line.substringAfter("://")
                            } else line

                            val parts = cleanLine.split(":")
                            if (parts.size == 2) {
                                val host = parts[0].trim()
                                val portStr = parts[1].trim()
                                val port = portStr.toIntOrNull()

                                if (port != null && host.isNotBlank()) {
                                    proxyList.add(Proxy(type, InetSocketAddress(host, port)))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[PROXY] Источник пропустил шаг: ${e.message}")
            }
        }

        synchronized(proxyList) {
            proxyList.shuffle() // Перемешиваем протоколы для надежности обхода
            Log.i(TAG, "[PROXY] Успешно загружено и отфильтровано адресов: ${proxyList.size}")
        }

        // Жесткий резервный аэродром, если на устройстве совсем отрубилась сеть
        if (proxyList.isEmpty()) {
            synchronized(proxyList) {
                Log.w(TAG, "[PROXY] Базы пусты, применен хардкод-резерв.")
                proxyList.add(Proxy(Proxy.Type.HTTP, InetSocketAddress("45.43.60.220", 8080)))
            }
        }
    }

    /**
     * Выдает зацикленный прокси для OkHttp под конкретную попытку
     */
    private const val STABLE_PROXY_HOST = "45.43.60.220"
    private const val STABLE_PROXY_PORT = 8080

    fun getProxyForAttempt(attempt: Int): Proxy {
        // 1. Попытка 1 — Железно бьем через твой стабильный прокси
        if (attempt == 1) {
            try {
                Log.d(TAG, "[PROXY] Попытка 1. Запуск через стабильный прокси $STABLE_PROXY_HOST:$STABLE_PROXY_PORT")
                val socketAddress = InetSocketAddress(STABLE_PROXY_HOST, STABLE_PROXY_PORT)
                return Proxy(Proxy.Type.HTTP, socketAddress)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации стабильного прокси: ${e.message}")
            }
        }

        // 2. Каждую 3-ю попытку (3, 6, 9, 12...) — Пробиваем сеть напрямую без прокси
        if (attempt % 3 == 0) {
            Log.w(TAG, "[PROXY] Попытка $attempt. Пробуем аварийный прямой коннект (NO_PROXY).")
            return Proxy.NO_PROXY
        }

        // 3. В остальных случаях (2, 4, 5, 7, 8...) — Ищем проверенные IP из Live-пула
        val verifiedList = _liveProxies.value
        if (verifiedList.isNotEmpty()) {
            val workingProxy = verifiedList.random()
            Log.d(TAG, "[PROXY] Попытка $attempt. Берем живой IP из лайв-пула: $workingProxy")
            return workingProxy
        }

        // 4. Если живых в пуле нет — Берем случайный из общей скачанной базы proxyList
        return synchronized(proxyList) {
            if (proxyList.isEmpty()) Proxy.NO_PROXY else proxyList.random()
        }
    }

    fun reportProxyStatus(proxy: Proxy, isSuccess: Boolean) {
        if (proxy == Proxy.NO_PROXY) return

        // ЗАЩИТА СТАБИЛЬНОГО ПРОКСИ (оставляем, как было)
        val address = proxy.address() as? InetSocketAddress
        if (address?.hostString == STABLE_PROXY_HOST && address.port == STABLE_PROXY_PORT) {
            return
        }

        synchronized(_liveProxies) {
            val currentLiveList = _liveProxies.value.toMutableList()

            if (isSuccess) {
                // Если прокси сработал — прощаем его и обнуляем его ошибки
                proxyFailureCount[proxy] = 0

                if (!currentLiveList.contains(proxy)) {
                    currentLiveList.add(proxy)
                    _liveProxies.value = currentLiveList
                    Log.d(TAG, "[PROXY] IP подтвержден! Добавлен в Live-пул. Всего живых: ${currentLiveList.size}")
                }
            } else {
                // Если прокси споткнулся — добавляем ему +1 ошибку
                val currentFailures = (proxyFailureCount[proxy] ?: 0) + 1
                proxyFailureCount[proxy] = currentFailures

                Log.w(TAG, "[PROXY] Ошибка прокси (Попытка $currentFailures/3 подряд) для $proxy")

                // Удаляем только если он жестко лег 3 раза ПОДРЯД
                if (currentFailures >= 3) {
                    proxyFailureCount.remove(proxy) // Очищаем счетчик

                    if (currentLiveList.contains(proxy)) {
                        currentLiveList.remove(proxy)
                        _liveProxies.value = currentLiveList
                        Log.e(TAG, "[PROXY] Прокси умер 3 раза подряд. Удален из Live-пула. Осталось: ${currentLiveList.size}")
                    }
                }
            }
        }
    }

}
