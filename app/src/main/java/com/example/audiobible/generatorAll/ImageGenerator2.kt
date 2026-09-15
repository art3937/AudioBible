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
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ImageGenerator2 {

    private const val TAG = "BREAD_PARSER_LOG"
    private const val MAX_RETRIES = 3

    private const val STABLE_PROXY_HOST = "45.43.60.220"
    private const val STABLE_PROXY_PORT = 8080
    private val proxyAddress = InetSocketAddress(STABLE_PROXY_HOST, STABLE_PROXY_PORT)
    private val appProxy = Proxy(Proxy.Type.HTTP, proxyAddress)

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .proxy(appProxy)
        .build()

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    private val mutexMap = mutableMapOf<String, Mutex>()

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ============================================================
    //  ПРОМПТЫ КНИГ
    // ============================================================
    private fun buildPrompt(bookName: String): String {
        return when (bookName) {
            // Ветхий Завет
            "Бытие" -> "Человек стоит на холме, внизу раскинулся лагерь с шатрами, караван верблюдов уходит в пустыню"
            "Исход" -> "Толпа путников с детьми и поклажей идёт по каменистой тропе у подножия горы"
            "Левит" -> "Человек в льняных одеждах разжигает костёр в центре пустынного лагеря"
            "Числа" -> "Вооружённые разведчики осматривают вход в пещеру среди скал"
            "Второзаконие" -> "Человек на возвышении обращается к собравшейся толпе, вокруг пустынный пейзаж"
            "Иисус Навин" -> "Воин в кожаных доспехах стоит перед древней крепостью, на фоне стены и башни"
            "Судьи" -> "Женщина в льняном платье сидит под пальмой, рядом глиняные сосуды и корзина"
            "Руфь" -> "Девушка собирает колосья на пшеничном поле, снопы лежат на земле"
            "1 Царств" -> "Седой старец с посохом у каменных домов древнего города"
            "2 Царств" -> "Молодой человек с арфой во дворе каменного дворца"
            "3 Царств" -> "Правитель на троне в зале с кедровыми колоннами, рядом слуги"
            "4 Царств" -> "Дикий человек в верблюжьей шкуре стоит у входа в горную пещеру"
            "1 Паралипоменон" -> "Писец со свитком пишет при свете масляной лампы в тронном зале"
            "2 Паралипоменон" -> "Правитель в парадных одеждах стоит у колонн храма"
            "Ездра" -> "Учёный со свитком беседует с людьми в зале персидского дворца"
            "Неемия" -> "Мужчина руководит рабочими, восстанавливающими городскую стену, лежат камни и инструменты"
            "Есфирь" -> "Молодая женщина в шёлковых одеждах идёт по залу дворца, вокруг мраморные колонны"
            "Иов" -> "Человек сидит среди руин, опустив голову, вокруг пыль и камни"
            "Псалтирь" -> "Пастух играет на лире на склоне холма, рядом пасутся овцы"
            "Притчи" -> "Учитель объясняет что-то группе молодых людей во дворе дома"
            "Екклесиаст" -> "Человек смотрит на закат с террасы дворца, тени удлиняются"
            "Песнь Песней" -> "Пара идёт по саду среди виноградников и цветущих кустов"
            "Исаия" -> "Оратор в богатых одеждах говорит у городских ворот, вокруг толпа"
            "Иеремия" -> "Человек в цепях стоит на фоне горящего города, дым поднимается в небо"
            "Плач Иеремии" -> "Женщина плачет среди обломков стены, рядом разбитые кувшины"
            "Иезекииль" -> "Человек сидит у реки, в воде тростник, на другом берегу люди"
            "Даниил" -> "Юноша в роскошных одеждах стоит в зале вавилонского дворца"
            "Осия" -> "Мужчина стоит у разрушенного каменного алтаря, вокруг сухая трава"
            "Иоиль" -> "Фермер смотрит на поле, где погибла вся растительность"
            "Амос" -> "Пастух с посохом стоит под смоковницей, рядом пасутся овцы"
            "Авдий" -> "Гонец бежит по горной тропе, за спиной сумка, вокруг скалы"
            "Иона" -> "Моряк держится за борт лодки в штормовом море, волны накрывают палубу"
            "Михей" -> "Простой человек в грубой одежде стоит у глиняных домов деревни"
            "Наум" -> "Человек издалека смотрит на город, объятый пламенем"
            "Аввакум" -> "Стражник на башне смотрит вдаль, горизонт затянут пылью"
            "Софония" -> "Человек жестикулирует, обращаясь к толпе на рыночной площади"
            "Аггей" -> "Строитель с молотком и доской стоит у недостроенной стены"
            "Захария" -> "Человек держит золотой светильник в полутёмном храме"
            "Малахия" -> "Священнослужитель в простых одеждах стоит у каменного алтаря"

            // Новый Завет
            "Матфей" -> "Мытарь за столом пересчитывает монеты, на столе лежат таблички и мешочки"
            "Марк" -> "Юноша пишет на свитке при свете лампы, вокруг стопки пергаментов"
            "Лука" -> "Врач осматривает больного в комнате, на полке склянки и бинты"
            "Иоанн" -> "Старец на скалистом берегу смотрит на море на закате"
            "Деяния" -> "Группа людей разных национальностей разговаривает на площади портового города"
            "Римлянам" -> "Солдат пишет письмо за столом в палатке, вокруг военное снаряжение"
            "1 Коринфянам" -> "Гончар за гончарным кругом формирует сосуд, на полках готовые изделия"
            "2 Коринфянам" -> "Мореплаватель чинит парус на причале, рядом лодки и канаты"
            "Галатам" -> "Крестьянин пашет поле на волах, на горизонте горы"
            "Ефесянам" -> "Купец показывает товары на рынке, вокруг мраморные колонны и прилавки"
            "Филиппийцам" -> "Римский центурион стоит у казармы, на фоне закатное небо"
            "Колоссянам" -> "Ткач работает за станком в мастерской, на стенах рулоны ткани"
            "1 Фессалоникийцам" -> "Кузнец куёт подкову у горна, летят искры"
            "2 Фессалоникийцам" -> "Столяр строгает доску в светлой мастерской"
            "1 Тимофею" -> "Молодой ученик слушает наставления старшего мастера"
            "2 Тимофею" -> "Человек в тёмной камере сидит на скамье, сквозь решётку пробивается свет"
            "Титу" -> "Чиновник за столом подписывает документы, рядом свитки и печати"
            "Филимону" -> "Хозяин дома разговаривает с рабом у двери"
            "Евреям" -> "Путник с посохом идёт по пыльной дороге, вдали караван"
            "Иакова" -> "Рыбак чинит сеть на берегу озера, рядом лодка"
            "1 Петра" -> "Пастух гонит овец по горной тропе"
            "2 Петра" -> "Человек останавливает путников на перекрёстке, что-то объясняет"
            "1 Иоанна" -> "Мужчина обнимает ребёнка у очага, в комнате тепло и уютно"
            "2 Иоанна" -> "Женщина открывает дверь входящему гостю"
            "3 Иоанна" -> "Хозяин угощает гостя за накрытым столом"
            "Иуды" -> "Воин в доспехах стоит на посту у городских ворот ночью"
            "Откровение" -> "Человек на скале смотрит на грозовое небо, молнии разрывают тучи"
            else -> "На тему библия"
        }
    }
    suspend fun generateImageForBook(context: Context, bookName: String): Bitmap? {
        val prompt = buildPrompt(bookName)
        // Передаем bookName для точной уникальности кэша каждой книги
        return generateImage(context, prompt, bookName)
    }

    fun getCachedImage(context: Context, bookName: String): Bitmap? {
        try {
            val cacheDir = File(context.cacheDir, "image_cache")
            val filename = sha256(bookName) + ".png" // Кэш строго по названию книги
            val cacheFile = File(cacheDir, filename)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return BitmapFactory.decodeFile(cacheFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCachedImage error: ${e.localizedMessage}")
        }
        return null
    }

    suspend fun generateImage(context: Context, russianPrompt: String, bookName: String): Bitmap? =
        withContext(Dispatchers.IO) {

            // 1. Проверяем кэш по названию книги перед сетевыми запросами
            val cachedBitmap = getCachedImage(context, bookName)
            if (cachedBitmap != null) {
                Log.d(TAG, "[IMAGE] Изображение для книги '$bookName' найдено в кэше. Сеть не трогаем.")
                return@withContext cachedBitmap
            }

            Log.d(TAG, "[IMAGE] ---> СТАРТ ПЕРЕВОДА для книги '$bookName'. Текст: $russianPrompt")

            // Прямой перевод без TextAutoCorrector
            val englishPrompt = try {
                Log.d(TAG, "[IMAGE] Перевод промпта...")
                TextTranslator.translateRuToEn(russianPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "[IMAGE] Ошибка перевода: ${e.localizedMessage}. Используем исходный русский.")
                russianPrompt
            }

            val enhancedPrompt = "$englishPrompt. High quality, clear details, well-defined shapes, rich colors."

            // Ключ для Mutex-синхронизации параллельных запросов
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

                    // Повторная проверка кэша внутри лока
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        try {
                            return@withContext BitmapFactory.decodeFile(cacheFile.absolutePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Ошибка чтения кэша в локе: ${e.localizedMessage}")
                        }
                    }

                    // БЕСКОНЕЧНЫЙ ЦИКЛ ПОПЫТОК
                    var attempt = 1
                    while (true) {
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
                                .build()

                            Log.d(TAG, "[IMAGE] Книга '$bookName'. Попытка $attempt через ПРОКСИ ($STABLE_PROXY_HOST). URL: $targetUrl")

                            var bytes: ByteArray? = null

                            // 1. Попытка запроса через основной клиент с ПРОКСИ
                            try {
                                bytes = client.newCall(generateRequest(targetUrl)).execute().use { response ->
                                    if (!response.isSuccessful) {
                                        Log.e(TAG, "[IMAGE] ОШИБКА ПРОКСИ: HTTP ${response.code} (попытка $attempt)")
                                        return@use null
                                    }
                                    val body = response.body ?: return@use null
                                    val rawBytes = body.bytes()
                                    if (rawBytes.isEmpty()) return@use null

                                    // Проверка на Cloudflare HTML заглушки
                                    if (rawBytes.size < 500_000) {
                                        val textCheck = String(rawBytes, Charsets.UTF_8)
                                        if (textCheck.trim().startsWith("<!DOCTYPE") || textCheck.contains("<html")) {
                                            Log.e(TAG, "[IMAGE] ОШИБКА ПРОКСИ: Скачался HTML вместо картинки.")
                                            return@use null
                                        }
                                    }
                                    Log.i(TAG, "[IMAGE] УСПЕШНО СКАЧАНО ЧЕРЕЗ ПРОКСИ! Размер: ${rawBytes.size} байт.")
                                    rawBytes
                                }
                            } catch (proxyException: Exception) {
                                Log.w(TAG, "[IMAGE] СБОЙ СЕТИ ПРОКСИ на попытке $attempt: ${proxyException.localizedMessage}")
                            }

                            // 2. АВАРИЙНЫЙ ОБХОД НАПРЯМУЮ БЕЗ ПРОКСИ
                            if (bytes == null) {
                                Log.w(TAG, "[АВАРИЙНЫЙ РЕЖИМ] Прокси подвёл. Пробуем скачать НАПРЯМУЮ без прокси...")
                                try {
                                    bytes = directClient.newCall(generateRequest(targetUrl)).execute().use { response ->
                                        if (!response.isSuccessful) {
                                            Log.e(TAG, "[IMAGE] ОШИБКА НАПРЯМУЮ: HTTP ${response.code}")
                                            return@use null
                                        }
                                        val body = response.body ?: return@use null
                                        val rawBytes = body.bytes()
                                        if (rawBytes.isNotEmpty()) {
                                            Log.i(TAG, "[IMAGE] УСПЕШНО СКАЧАНО НАПРЯМУЮ БЕЗ ПРОКСИ! Размер: ${rawBytes.size} байт.")
                                            rawBytes
                                        } else null
                                    }
                                } catch (directException: Exception) {
                                    Log.e(TAG, "[IMAGE] Крах прямого подключения: ${directException.localizedMessage}")
                                }
                            }

                            // Если байты успешно получены — сохраняем в кэш и отдаем Bitmap
                            if (bytes != null) {
                                try {
                                    cacheFile.outputStream().use { fos ->
                                        fos.write(bytes)
                                    }
                                    Log.d(TAG, "[IMAGE] Изображение для '$bookName' успешно сохранено в кэш на диск.")
                                } catch (cacheEx: Exception) {
                                    Log.w(TAG, "[IMAGE] Ошибка сохранения кэша: ${cacheEx.localizedMessage}")
                                }

                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    Log.d(TAG, "[IMAGE] Изображение для '$bookName' успешно декодировано в Bitmap.")
                                    return@withContext bitmap
                                }
                            }

                            // Если скачать не получилось, увеличиваем счетчик и делаем паузу
                            attempt++
                            delay(3000L)
                        } catch (e: Exception) {
                            Log.e(TAG, "[IMAGE] Сбой итерации $attempt: ${e.localizedMessage}")
                            attempt++
                            delay(3000L)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[IMAGE] Крах внутри блокировки: ${e.localizedMessage}", e)
            } finally {
                synchronized(mutexMap) { mutexMap.remove(requestKey) }
            }

            return@withContext null
        }

    private fun generateRequest(targetUrl: HttpUrl): Request =
        Request.Builder()
            .url(targetUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .get()
            .build()
}
