package com.example.audiobible.generatorAll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ImageGenerator {

    private const val TAG = "BREAD_PARSER_LOG"

    // Базовый клиент под динамические прокси
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    // Прямой клиент для аварийного режима без прокси
    private val directClient = OkHttpClient.Builder()
        .connectTimeout(40, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    private val mutexMap = mutableMapOf<String, Mutex>()

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getCachedImage(context: Context, bookName: String): Bitmap? {
        try {
            val cacheDir = File(context.cacheDir, "image_cache")
            val filename = sha256(bookName) + ".png" // Кэш железно разделен по имени книги
            val cacheFile = File(cacheDir, filename)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return BitmapFactory.decodeFile(cacheFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCachedImage error: ${e.localizedMessage}")
        }
        return null
    }

    // ============================================================
    //  ПРОМПТЫ КНИГ (без упоминаний религии и имен)
    // ============================================================
    private fun buildPrompt(bookName: String): String {
        val name = bookName.lowercase()

        return when {
            // === ВЕТХИЙ ЗАВЕТ ===
            name.contains("бытие") -> "Человек на холме. Внизу палаточный лагерь. Верблюды идут в пустыню"
            name.contains("исход") -> "Люди с детьми идут по дороге. Рядом большая гора. Камни"
            name.contains("левит") -> "Человек в белой одежде делает костер. Пустыня. Лагерь"
            name.contains("числ") -> "Древние воины осматривают вход в темную пещеру. Скалы"
            name.contains("второзакон") -> "Человек говорит перед большой толпой людей. Пустынный пейзаж"
            name.contains("навин") -> "Воин в доспехах стоит перед большими воротами каменной крепости"
            name.contains("судей") -> "Ближневосточная женщина сидит под деревом. Рядом старые глиняные кувшины и корзины"
            name.contains("руф") -> "Ближневосточная женщина собирает пшеницу в поле. Желтая трава. Снопы"
            name.contains("1 царств") || name.contains("первая книга царств") -> "Старый человек с деревянным посохом. Древний каменный город"
            name.contains("2 царств") || name.contains("вторая книга царств") -> "Молодой парень играет на музыкальном инструменте во дворе дворца"
            name.contains("3 царств") || name.contains("третья книга царств") -> "Король сидит на троне в большом зале. Колонны. Слуги"
            name.contains("4 царств") || name.contains("четвертая книга царств") -> "Человек в грубой одежде стоит у входа в пещеру в горах"
            name.contains("1 паралипоменон") -> "Писатель пишет текст на свитке. Масляная лампа. Древний зал"
            name.contains("2 паралипоменон") -> "Богатый правитель стоит у колонн большого красивого храма"
            name.contains("ездр") -> "Мудрец со свитком разговаривает с людьми в каменном дворце"
            name.contains("нееми") -> "Строители восстанавливают старую городскую стену. Камни и инструменты"
            name.contains("есфир") -> "Красивая ближневосточная королева в шелковом платье идет по залу дворца. Колонны"
            name.contains("иов") -> "Грустный человек сидит на земле среди старых руин и камней"
            name.contains("псал") -> "Пастух играет на маленькой арфе на зеленом холме. Рядом овцы"
            name.contains("притч") -> "Учитель говорит с молодыми учениками во дворе дома"
            name.contains("екклесиаст") -> "Человек смотрит на закат солнца с балкона дворца. Тени"
            name.contains("песн") -> "Ближневосточный мужчина и женщина идут по зеленому саду. Виноградники. Цветы"
            name.contains("исаи") -> "Оратор говорит речь перед людьми у ворот города"
            name.contains("иереми") -> "Человек в железных цепях. На фоне дым и старый город"
            name.contains("плач") -> "Ближневосточная женщина плачет у разрушенной стены. Разбитая посуда на земле"
            name.contains("иезекиил") -> "Человек сидит на берегу реки. Вода и зеленая трава"
            name.contains("даниил") -> "Молодой человек в красивой одежде стоит в огромном зале дворца"
            name.contains("осии") -> "Мужчина стоит у старого каменного памятника. Сухая трава"
            name.contains("иоил") -> "Фермер смотрит на сухое пустое поле. Плохая погода"
            name.contains("амос") -> "Пастух с палкой стоит под зеленым деревом. Рядом овцы"
            name.contains("авдий") -> "Бегун бежит по горной дороге. Вокруг большие скалы"
            name.contains("ион") -> "Моряк в маленькой лодке. Большие волны. Шторм в море"
            name.contains("михе") -> "Простой человек в бедной одежде стоит возле глиняных домов"
            name.contains("наум") -> "Человек смотрит на далекий город. Огонь и дым в небе"
            name.contains("аввакум") -> "Стражник на каменной башне смотрит вдаль. Песок и пыль"
            name.contains("софони") -> "Человек разговаривает с толпой на торговой площади города"
            name.contains("агге") -> "Рабочий с деревянной доской строит стену дома. Инструменты"
            name.contains("захари") -> "Человек держит золотой подсвечник в темной комнате храма"
            name.contains("малахи") -> "Человек в простой одежде стоит у каменного стола для костра"

            // === НОВЫЙ ЗАВЕТ ===
            // Евангелия и Деяния
            name.contains("матфе") -> "Человек за деревянным столом считает золотые монеты"
            name.contains("марк") -> "Молодой парень пишет текст на бумаге. Горит свеча"
            name.contains("лук") -> "Старый врач осматривает больного человека в комнате"
            name.contains("иоанн") && !name.contains("послание") -> "Старик на каменном берегу смотрит на синее море"
            name.contains("деяни") -> "Группа людей эмоционально разговаривает на городской площади"

            // Соборные послания
            name.contains("иаков") -> "Рыбак делает рыболовную сеть на песке у озера"
            name.contains("1 соборное") && name.contains("петр") -> "Пастух ведет группу белых овец по горной тропинке"
            name.contains("2 соборное") && name.contains("петр") -> "Древний ближневосточный учитель останавливает путников на дороге"
            name.contains("1 соборное") && name.contains("иоанн") -> "Мужчина нежно обнимает ребенка возле теплого домашнего костра"
            name.contains("2 соборное") && name.contains("иоанн") -> "Ближневосточная женщина открывает деревянную дверь входящему гостю"
            name.contains("3 соборное") && name.contains("иоанн") -> "Добрый хозяин дома угощает гостя за большим столом с едой"
            name.contains("иуд") -> "Древний воин с мечом стоит на посту ночью. Темное небо"

            // Послания Павла
            name.contains("римлян") -> "Воин пишет письмо за столом внутри военной палатки"
            name.contains("1 послание к коринфянам") -> "Мастер делает глиняный горшок на специальном круге"
            name.contains("2 послание к коринфянам") -> "Старый моряк чинит парус на деревянном причале"
            name.contains("галатам") -> "Фермер работает в поле. Большие быки тянут старый плуг"
            name.contains("ефесянам") -> "Продавец показывает красивые цветные ткани на рынке города"
            name.contains("филиппий") -> "Римский солдат в железных доспехах стоит на посту у каменного здания"
            name.contains("колоссянам") -> "Человек делает ткань на старом деревянном ткацком станке"
            name.contains("1 послание к фессалоникий") -> "Кузнец работает с горячим металлом и тяжелым молотом в мастерской"
            name.contains("2 послание к фессалоникий") -> "Плотник строгает деревянную доску инструментами в мастерской"
            name.contains("1 послание к тимофе") -> "Молодой парень внимательно слушает старого мудрого мастера"
            name.contains("2 послание к тимофе") -> "Человек сидит на каменной скамье внутри темной тюремной камеры"
            name.contains("титу") -> "Человек за столом подписывает важные документы старым пером"
            name.contains("филимон") -> "Хозяин дома спокойно разговаривает с рабочим в светлой комнате"
            name.contains("евре") -> "Путник со старой деревянной палкой идет по сухой пыльной дороге"

            // Откровение
            name.contains("откровение") || name.contains("апокалипсис") -> "Человек на высокой скале смотрит на черное грозовое небо"

            else -> "Древний библейский сюжет, исторический стиль, реализм"
        }
    }



    suspend fun generateImageForBook(
        context: Context,
        bookName: String
    ): Bitmap? {
        val prompt = buildPrompt(bookName)
        return generateImage(context, prompt, bookName)
    }

    suspend fun generateImage(
        context: Context,
        russianPrompt: String,
        bookName: String
    ): Bitmap? = withContext(Dispatchers.IO) {

        // 1. Проверка кэша на самом старте
        val cachedBitmap = getCachedImage(context, bookName)
        if (cachedBitmap != null) {
            Log.d(TAG, "[IMAGE] Изображение '$bookName' выдано из кэша.")
            return@withContext cachedBitmap
        }


        Log.d(TAG, "[IMAGE] ---> СТАРТ ПЕРЕВОДА для '$bookName'.")

        val englishPrompt = try {
            TextTranslator.translateRuToEn(russianPrompt)

        } catch (e: Exception) {
            Log.e(TAG, "[IMAGE] Ошибка перевода: ${e.localizedMessage}")
            russianPrompt
        }
        Log.e(TAG, "[IMAGE] переведено: $englishPrompt")
        // val suffix = ". High quality, clear details, rich colors."
        val enhancedPrompt = "$englishPrompt"//$suffix"
        val requestKey = sha256(enhancedPrompt)

        val mutex = synchronized(mutexMap) {
            mutexMap.getOrPut(requestKey) { Mutex() }
        }

        try {
            mutex.withLock {
                val cacheDir = File(context.cacheDir, "image_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val filename = sha256(bookName) + ".png"
                val cacheFile = File(cacheDir, filename)

                // Повторная проверка кэша в локе
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    try {
                        return@withContext BitmapFactory
                            .decodeFile(cacheFile.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Ошибка чтения кэша в локе")
                    }
                }

                // БЕСКОНЕЧНЫЙ ЦИКЛ ПОПЫТОК
                var attempt = 1
                while (true) {
                    // ПРОВЕРКА КЭША НА КАЖДОЙ ИТЕРАЦИИ ЦИКЛА
                    if (cacheFile.exists() && cacheFile.length() > 1024) { // Больше 1 КБ, чтобы отсечь пустые файлы
                        try {
                            val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                            if (bitmap != null) {
                                Log.d(
                                    TAG,
                                    "[IMAGE] '$bookName' обнаружен в кэше внутри цикла! Успешный выход."
                                )
                                return@withContext bitmap
                            }
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Ошибка чтения кэша на попытке $attempt, продолжаем качать..."
                            )
                        }
                    }
                    try {
                        val randomSeed = (1..100_000).random()
                        val targetUrl = HttpUrl.Builder()
                            .scheme("https")
                            .host("image.pollinations.ai")
                            .addPathSegment("p")
                            .addPathSegment(enhancedPrompt)
                            .addQueryParameter("width", "512")
                            .addQueryParameter("height", "512")
                            .addQueryParameter("model", "flux")
                            .addQueryParameter("seed", randomSeed.toString())
                            .addQueryParameter("nologo", "true")
                            // ЖЕСТКИЙ ФИЛЬТР: запрещаем китайцев, аниме, современную одежду и корейцев
                            .addQueryParameter("negative", "asian, chinese, korean, japanese, anime, 3d render, modern clothes, makeup")
                            .build()

                        val currentProxy = ProxyManager
                            .getProxyForAttempt(attempt)

                        val dynamicClient = baseClient.newBuilder()
                            .proxy(currentProxy)
                            .build()

                        Log.d(TAG, "[IMAGE] '$bookName'. Попытка $attempt")

                        var bytes: ByteArray? = null

                        // 1. Попытка через динамический ПРОКСИ
                        try {
                            val req = generateRequest(targetUrl, attempt)
                            bytes = dynamicClient.newCall(req).execute().use { response ->
                                if (!response.isSuccessful) {
                                    // Прокси ответил ошибкой (например 403, 502)
                                    ProxyManager.reportProxyStatus(currentProxy, isSuccess = false)
                                    return@use null
                                }
                                val body = response.body ?: return@use null
                                val rawBytes = body.bytes()
                                if (rawBytes.isEmpty()) return@use null

                                // Проверка Cloudflare
                                if (rawBytes.size < 500_000) {
                                    val str = String(rawBytes, Charsets.UTF_8)
                                    if (str.trim()
                                            .startsWith("<!DOCTYPE") || str.contains("<html")
                                    ) {
                                        // Это заглушка Cloudflare, прокси плохой
                                        ProxyManager.reportProxyStatus(
                                            currentProxy,
                                            isSuccess = false
                                        )
                                        return@use null
                                    }
                                }

                                // ЕСЛИ ДОШЛИ СЮДА — ВСЕ СУПЕР! Прокси живой и отдал картинку
                                ProxyManager.reportProxyStatus(currentProxy, isSuccess = true)
                                rawBytes
                            }
                        } catch (proxyException: Exception) {
                            Log.w(TAG, "[IMAGE] Сбой прокси: ${proxyException.message}")
                            // Сетевой сбой или таймаут — удаляем прокси
                            ProxyManager.reportProxyStatus(currentProxy, isSuccess = false)
                        }


                        // 2. АВАРИЙНЫЙ ОБХОД НАПРЯМУЮ
                        if (bytes == null) {
                            try {
                                val req = generateRequest(targetUrl, attempt)
                                bytes = directClient.newCall(req)
                                    .execute().use { response ->
                                        if (!response.isSuccessful) return@use null
                                        val body = response.body ?: return@use null
                                        val rawBytes = body.bytes()
                                        if (rawBytes.isNotEmpty()) rawBytes else null
                                    }
                            } catch (directException: Exception) {
                                Log.e(
                                    TAG,
                                    "[IMAGE] Крах прямого подключения ${directException.message}"
                                )
                            }
                        }

                        // 3. Сохранение в кэш и возврат
                        if (bytes != null) {
                            try {
                                cacheFile.outputStream().use { fos -> fos.write(bytes) }
                                Log.d(TAG, "[IMAGE] Успешно сохранено в кэш.")
                            } catch (cacheEx: Exception) {
                                Log.w(TAG, "Ошибка записи кэша")
                            }

                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) return@withContext bitmap
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "[IMAGE] Сбой итерации $attempt для $bookName: ${e.message}")
                    }

                    // === ЖЕСТКАЯ ЗАДЕРЖКА ВНЕ TRY-CATCH ===
                    // Теперь, даже если всё взорвалось, дятел ОСТАНОВИТСЯ и подождет
                    attempt++

                    val delayTime = when (attempt) {
                        2 -> 4000L
                        3 -> 8000L
                        else -> 15000L // Не даем спамить лог чаще чем раз в 15 секунд
                    }
                    Log.w(TAG, "[IMAGE] Засыпаем на $delayTime мс перед попыткой $attempt")
                    delay(delayTime)
                } // Конец while
            } // Конец mutex
        } catch (e: Exception) {
            Log.e(TAG, "[IMAGE] Крах блокировки: ${e.localizedMessage}")
        } finally {
            synchronized(mutexMap) { mutexMap.remove(bookName) } // Проверь, что тут bookName, а не requestKey!
        }

        return@withContext null
    }


    private fun generateRequest(targetUrl: okhttp3.HttpUrl, attempt: Int): Request {
        // Список разных живых браузеров, чтобы сайт думал, что заходят разные люди
        val userAgents = listOf(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36"
        )
        // Выбираем заголовок на основе номера попытки
        val selectedAgent = userAgents[attempt % userAgents.size]

        return Request.Builder()
            .url(targetUrl)
            .addHeader("User-Agent", selectedAgent)
            .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
    }
}
