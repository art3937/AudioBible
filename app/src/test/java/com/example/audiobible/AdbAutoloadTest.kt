package com.example.audiobible // Проверь свой пакет приложения

import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class AdbAutoloadTest {

    @Test
    fun autoPullUnzipAndCleanAllBooks() {
        // === НАСТРОЙКИ СВЯЗИ ===
        val deviceIp = "192.168.0.193:5555"
        val downloadDirOnPhone = "/sdcard/Download"

        // Автоматически находим путь к официальному ADB в системе Windows
        val localAppData = System.getenv("LOCALAPPDATA")
        val defaultAdb = File(localAppData, "Android\\Sdk\\platform-tools\\adb.exe")
        val scrcpyAdb = File("C:\\Users\\Admin\\Downloads\\scrcpy-win64-v4.1\\scrcpy-win64-v4.1\\adb.exe")

        val adbPath = when {
            defaultAdb.exists() -> defaultAdb.absolutePath
            scrcpyAdb.exists() -> scrcpyAdb.absolutePath
            File("C:\\Users\\Admin\\Downloads\\scrcpy-win64-v4.1\\adb.exe").exists() ->
                File("C:\\Users\\Admin\\Downloads\\scrcpy-win64-v4.1\\adb.exe").absolutePath
            else -> {
                println("❌ Ошибка: Не удалось найти adb.exe ни в Android SDK, ни в папке Downloads.")
                return
            }
        }

        println("ℹ️ Используем ADB по пути: $adbPath")

        // 1. ТВОЯ КАНOHИЧЕСКАЯ КАРТА КНИГ (ID -> Имя папки в assets)
        val bookFoldersMap = mapOf(
            1 to "genesis",       2 to "exodus",       3 to "leviticus",
            4 to "numbers",       5 to "deuteronomy",  6 to "joshua",
            7 to "judges",        8 to "ruth",         9 to "samuel1",
            10 to "samuel2",      11 to "kings1",      12 to "kings2",
            13 to "chronicles1",  14 to "chronicles2", 15 to "ezra",
            16 to "nehemiah",     17 to "esther",      18 to "job",
            19 to "psalms",       20 to "proverbs",    21 to "ecclesiastes",
            22 to "song",         23 to "isaiah",      24 to "jeremiah",
            25 to "lamentations", 26 to "ezekiel",     27 to "daniel",
            28 to "hosea",        29 to "joel",        30 to "amos",
            31 to "obadiah",      32 to "jonah",       33 to "micah",
            34 to "nahum",        35 to "habakkuk",    36 to "zephaniah",
            37 to "haggai",       38 to "zechariah",   39 to "malachi"
        )

        println("🔍 Сканируем папку Download на смартфоне...")

        val lsProcess = ProcessBuilder(adbPath, "-s", deviceIp, "shell", "ls", downloadDirOnPhone)
            .redirectErrorStream(true)
            .start()

        val phoneFiles = lsProcess.inputStream.bufferedReader().readLines()
        lsProcess.waitFor()

        val targetArchives = phoneFiles.filter { fileName ->
            fileName.startsWith("8_") && fileName.endsWith(".zip")
        }

        if (targetArchives.isEmpty()) {
            println("\n☕ На телефоне пока нет новых архивов вида 8_X.zip для скачивания.")
            return
        }

        println("📚 Найдено новых архивов для обработки: ${targetArchives.size}\n")

        for (archiveName in targetArchives) {
            val bookIdString = archiveName.substringAfter("8_").substringBefore(".zip")
            val bookId = bookIdString.toIntOrNull()

            if (bookId == null || !bookFoldersMap.containsKey(bookId)) {
                println("⚠️ Пропущен файл $archiveName: неверный ID книги.")
                continue
            }

            val targetFolderName = bookFoldersMap[bookId]!!
            val remoteZipPath = "$downloadDirOnPhone/$archiveName"
            val localZipFile = File("src/main/assets/temp_download.zip")
            val assetsOutputDir = File("src/main/assets/$targetFolderName")

            println("======== 📦 ОБРАБОТКА: Книга ID $bookId -> assets/$targetFolderName ========")
            println("📥 Шаг 1: Скачиваем $archiveName через ADB...")

            val pullProcess = ProcessBuilder(adbPath, "-s", deviceIp, "pull", remoteZipPath, localZipFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            pullProcess.inputStream.bufferedReader().use { println(it.readText().trim()) }

            if (pullProcess.waitFor() != 0 || !localZipFile.exists()) {
                println("❌ Ошибка скачивания файла $archiveName. Пропускаем его.")
                continue
            }

            println("📂 Шаг 2: Распаковываем треки напрямую в assets/$targetFolderName...")
            if (!assetsOutputDir.exists()) assetsOutputDir.mkdirs()

            try {
                ZipInputStream(localZipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.name.contains("__MACOSX") && !entry.isDirectory) {
                            val fileName = File(entry.name).name
                            val newFile = File(assetsOutputDir, fileName)

                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            println("   -> assets/$targetFolderName/$fileName")
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                println("✅ Книга распакована успешно!")
            } catch (e: Exception) {
                println("❌ Ошибка при распаковке архива $archiveName: ${e.message}")
                localZipFile.delete()
                continue
            }

            println("🧹 Шаг 3: Удаление временного архива на ПК...")
            if (localZipFile.exists() && localZipFile.delete()) {
                println("🗑️ Временный zip-файл на компьютере успешно удален.")
            }

            // 🔥 СТРОКА С УДАЛЕНИЕМ С ТЕЛЕФОНА (adb shell rm) ПОЛНОСТЬЮ ВЫРЕЗАНА!
            println("📱 Оригинальный архив $archiveName оставлен на смартфоне.")
            println("==================================================================\n")
        }

        println("🎉 ВСЕ НАЙДЕННЫЕ КНИГИ ОБРАБОТАНЫ!")
    }
}
