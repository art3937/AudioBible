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

        // Отключаем встроенные проверки SSL, если нужно
        configureUnsafeSsl()

        // Ожидается, что пользователь задаст шаблон URL через системное свойство или переменную окружения.
        // Примеры поддерживаемых шаблонов:
        //  - https://91.218.228.218/files/genesis_{n}.mp3
        //  - https://example.com/path/%s.mp3
        //  - https://example.com/path/genesis_
        val rawBase = System.getProperty("audio.base.url") ?: System.getenv("AUDIO_BASE_URL")
        require(!rawBase.isNullOrBlank()) {
            "Set system property -Daudio.base.url or env AUDIO_BASE_URL to a URL template, e.g. https://91.218.228.218/files/genesis_{n}.mp3"
        }

        val hostHeader = System.getProperty("audio.host.header") ?: System.getenv("AUDIO_HOST_HEADER")

        println("=== START DOWNLOAD USING BASE: $rawBase ===")

        var totalDownloaded = 0

        for (chapterId in 1..3) {
            val formattedChapter = if (chapterId < 10) "0$chapterId" else "$chapterId"

            val audioUrlString = when {
                rawBase.contains("{n}") -> rawBase.replace("{n}", formattedChapter)
                rawBase.contains("%s") -> String.format(rawBase, formattedChapter)
                rawBase.endsWith("_") -> rawBase + formattedChapter + ".mp3"
                rawBase.endsWith("/") -> rawBase + formattedChapter + ".mp3"
                else -> rawBase + formattedChapter + ".mp3"
            }

            println("⏳ Attempting download chapter $chapterId from: $audioUrlString")

            val outputFile = File(targetFolder, "${formattedChapter}.mp3")
            if (outputFile.exists()) outputFile.delete()

            try {
                val url = URL(audioUrlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true

                // заголовки
                conn.setRequestProperty("User-Agent", "AndroidExoPlayer/2.19.1 (Linux;Android 11)")
                if (!hostHeader.isNullOrBlank()) conn.setRequestProperty("Host", hostHeader)

                conn.connect()

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedInputStream(conn.inputStream).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val sizeKb = outputFile.length() / 1024
                    if (sizeKb > 50) { // порог маленький для теста; подправьте при необходимости
                        println("✅ Chapter $chapterId downloaded: ${outputFile.absolutePath} (${sizeKb} KB)")
                        totalDownloaded++
                    } else {
                        println("❌ Downloaded file seems too small: ${sizeKb} KB; deleting")
                        outputFile.delete()
                    }
                } else {
                    println("❌ Server returned HTTP $code for $audioUrlString")
                }

                conn.disconnect()

            } catch (e: Exception) {
                println("❌ Error downloading chapter $chapterId: ${e.message}")
            }

            Thread.sleep(300)
        }

        println("=== FINISHED. Downloaded $totalDownloaded of 3 chapters.")
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
