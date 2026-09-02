package com.example.audiobible // Твой пакет приложения

import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.min

class AdbAutoloadTest {

    @Test
    fun autoPullUnzipAndCleanAllBooks() {
        // === НАСТРОЙКИ СВЯЗИ ===
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

        // 1. ТВОЯ КАНОНИЧЕСКАЯ КАРТА КНИГ (Строго по твоему скриншоту папок в assets)
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

        // Проверяем, какие вообще устройства сейчас видит ADB
        println("🔍 Проверяем активные подключения...")
        val devicesProcess = ProcessBuilder(adbPath, "devices")
            .redirectErrorStream(true)
            .start()
        val activeDevices = devicesProcess.inputStream.bufferedReader().readLines()
        devicesProcess.waitFor()

        println("📱 Статус подключений в системе:")
        activeDevices.forEach { println("   $it") }

        println("🔍 Сканируем папку $downloadDirOnPhone на активном смартфоне...")

        val lsProcess = ProcessBuilder(adbPath, "shell", "ls", downloadDirOnPhone)
            .redirectErrorStream(true)
            .start()

        val phoneFiles = lsProcess.inputStream.bufferedReader().readLines()
        lsProcess.waitFor()

        println("\n📁 [ОТЛАДКА] Найдено файлов в папке на телефоне: ${phoneFiles.size}")

        // Фильтруем только ZIP-архивы, которые начинаются на 8_ (твои книги)
        val targetArchives = phoneFiles
            .map { it.trim() }
            .filter { it.startsWith("8_") && it.endsWith(".zip", ignoreCase = true) && it.isNotEmpty() }

        if (targetArchives.isEmpty()) {
            println("\n☕ В папке Загрузки на телефоне нет подходящих ZIP-архивов книг (8_X.zip).")
            return
        }

        println("\n📚 Найдено ZIP-архивов книг для обработки: ${targetArchives.size}\n")

        for (archiveName in targetArchives) {
            // Четко вытаскиваем числовой ID из маски файла
            val bookIdString = archiveName.substringAfter("8_").substringBefore(".zip")
            val bookId = bookIdString.toIntOrNull()

            // Если ID некорректный или его нет в нашей карте — пропускаем
            if (bookId == null || !bookFoldersMap.containsKey(bookId)) {
                continue
            }

            val targetFolderName = bookFoldersMap[bookId]!!
            val localZipFile = File("src/main/assets/temp_download.zip")
            val assetsOutputDir = File("src/main/assets/$targetFolderName")

            // 🔥 УМНАЯ ПРОВЕРКА НА ДУБЛИКАТЫ (Смотрит в твои английские папки)
            if (assetsOutputDir.exists() && assetsOutputDir.isDirectory) {
                val existingFiles = assetsOutputDir.listFiles { _, name -> name.endsWith(".mp3", ignoreCase = true) }
                if (existingFiles != null && existingFiles.isNotEmpty()) {
                    println("⏩ [ПРОПУСК] Книга '$archiveName' -> assets/$targetFolderName уже полностью заполнена (${existingFiles.size} треков).")
                    println("==================================================================\n")
                    continue
                }
            }

            println("======== 📦 ОБРАБОТКА: Книга ID $bookId -> assets/$targetFolderName ========")
            println("📥 Шаг 1: Скачиваем архив через ADB...")

            val pullProcess = ProcessBuilder(adbPath, "pull", "$downloadDirOnPhone/$archiveName", localZipFile.absolutePath)
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

            println("📱 Оригинальный архив оставлен на смартфоне.")
            println("==================================================================\n")
        }

        println("🎉 ВСЕ НАЙДЕННЫЕ КНИГИ ОБРАБОТАНЫ!")
    }
}
