import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class DownloadBibleAudioTest {

    @Test
    fun downloadGenesisFullyAutomated() {
        val targetFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data_audio")
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        // Отключаем встроенные проверки SSL, так как при запросе по IP-адресу сертификаты всегда ругаются
        configureUnsafeSsl()

        println("=== ЗАПУСК ТОЧЕЧНОГО СКАЧИВАНИЯ ПО IP-АДРЕСУ (3 ГЛАВЫ) ===")

        var totalDownloaded = 0

        for (chapterId in 1..3) {
            val outputFile = File(targetFolder, "$chapterId.mp3")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val formattedChapter = if (chapterId < 10) "0$chapterId" else "$chapterId"

            // Прямой IP-адрес сервера Института Перевода Библии
            val audioUrlString = "https://91.218.228"

            try {
                println("⏳ Прямой IP-запрос главы $chapterId из 3... ($audioUrlString)")

                val url = URL(audioUrlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000

                // Маскируемся под стандартный ExoPlayer
                connection.setRequestProperty("User-Agent", "AndroidExoPlayer/2.19.1 (Linux;Android 11)")

                // Обязательно передаем Host-заголовок, чтобы веб-сервер внутри IP понял, какой файл мы ищем
                connection.setRequestProperty("Host", "bible.ru")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedInputStream(connection.inputStream).use { input ->
                        // ИСПРАВЛЕНО: Убрали именованный параметр targetFile =, оставили чистый файл
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val sizeKb = outputFile.length() / 1024
                    if (sizeKb > 300) {
                        println("✅ КРУТО! Глава $chapterId успешно скачана по IP! Размер: $sizeKb КБ")
                        totalDownloaded++
                    } else {
                        println("❌ Ошибка: скачался некорректный блок размером $sizeKb КБ.")
                        outputFile.delete()
                    }
                } else {
                    println("❌ Сервер вернул код ответа: ${connection.responseCode}")
                }

                Thread.sleep(300)

            } catch (e: Exception) {
                println("❌ ОШИБКА НА ГЛАВЕ $chapterId: ${e.message}")
            }
        }

        println("=== СКАЧИВАНИЕ И СБОРКА ПОЛНОСТЬЮ ЗАВЕРШЕНЫ ===")
        println("📊 Финальный статус: В папке assets успешно подготовлено: $totalDownloaded из 3 глав Бытия!")
    }

    private fun configureUnsafeSsl() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
