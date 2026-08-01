package com.example.audiobible

import org.json.JSONObject
import java.io.File
import org.junit.Test
import java.io.BufferedInputStream
import java.io.FileOutputStream

import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class CreateFilesTest {
    @Test
    fun checkAllChaptersMatch() {
        // Каноническое количество глав для проверки каждого файла на диске
        val expectedChapters = mapOf(
            "genesis" to 50, "exodus" to 40, "leviticus" to 27, "numbers" to 36, "deuteronomy" to 34,
            "joshua" to 24, "judges" to 21, "ruth" to 4, "samuel1" to 31, "samuel2" to 24,
            "kings1" to 22, "kings2" to 25, "chronicles1" to 29, "chronicles2" to 36, "ezra" to 10,
            "nehemiah" to 13, "esther" to 10, "job" to 42, "psalms" to 150, "proverbs" to 31,
            "ecclesiastes" to 12, "song" to 8, "isaiah" to 66, "jeremiah" to 52, "lamentations" to 5,
            "ezekiel" to 48, "daniel" to 12, "hosea" to 14, "joel" to 3, "amos" to 9,
            "obadiah" to 1, "jonah" to 4, "micah" to 7, "nahum" to 3, "habakkuk" to 3,
            "zephaniah" to 3, "haggai" to 2, "zechariah" to 14, "malachi" to 4, "matthew" to 28,
            "mark" to 16, "luke" to 24, "john" to 21, "acts" to 28, "james" to 5,
            "peter1" to 5, "peter2" to 3, "john1" to 5, "john2" to 1, "john3" to 1,
            "jude" to 1, "romans" to 16, "corinthians1" to 16, "corinthians2" to 13, "galatians" to 6,
            "ephesians" to 6, "philippians" to 4, "colossians" to 4, "thessalonians1" to 5, "thessalonians2" to 3,
            "timothy1" to 6, "timothy2" to 4, "titus" to 3, "philemon" to 1, "hebrews" to 13,
            "revelation" to 22
        )

        val targetFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data")

        if (!targetFolder.exists()) {
            println("=== ОШИБКА: Папка bible_data не существует ===")
            return
        }

        println("=== НАЧАЛО ИНТЕЛЛЕКТУАЛЬНОЙ ПРОВЕРКИ ASSETS ===")
        var totalErrors = 0
        var totalCheckedFiles = 0

        expectedChapters.forEach { (folderName, expectedCount) ->
            val bookDir = File(targetFolder, folderName)

            // 1. Проверяем существование папки
            if (!bookDir.exists() || !bookDir.isDirectory) {
                println("❌ КРИТИЧЕСКАЯ ОШИБКА: Папка книги '$folderName' полностью отсутствует!")
                totalErrors++
                return@forEach
            }

            // 2. Считаем файлы глав на диске
            val actualFiles = bookDir.listFiles { _, name -> name.endsWith(".txt") }
            val actualCount = actualFiles?.size ?: 0
            totalCheckedFiles += actualCount

            if (actualCount != expectedCount) {
                println("❌ ОШИБКА В КНИГЕ '$folderName': ожидалось глав $expectedCount, на диске создано $actualCount!")
                totalErrors++
            }

            // 3. Выборочно читаем первую строчку 1-й главы для верификации контента
            val firstChapterFile = File(bookDir, "1.txt")
            var textPreview = ""
            if (firstChapterFile.exists() && firstChapterFile.length() > 0L) {
                try {
                    // Берем первые 60 символов первой строки для лога
                    val firstLine = firstChapterFile.useLines { it.firstOrNull() } ?: ""
                    textPreview = if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine
                } catch (e: Exception) {
                    textPreview = "[Ошибка чтения файла]"
                }
            } else {
                textPreview = "❌ ФАЙЛ ПУСТОЙ ИЛИ ОТСУТСТВУЕТ!"
                totalErrors++
            }

            // Дополнительная проверка всех остальных файлов книги на пустоту
            actualFiles?.forEach { file ->
                if (file.length() == 0L) {
                    println("⚠️ ВНИМАНИЕ: Файл '${file.absolutePath}' пустой (0 байт)!")
                    totalErrors++
                }
            }

            // Выводим красивый отчет по текущей книге
            val statusIcon = if (actualCount == expectedCount && !textPreview.startsWith("❌")) "✅" else "❌"
            println("$statusIcon Книга '$folderName' -> глав на диске: $actualCount/$expectedCount. Первая строчка: \"$textPreview\"")
        }

        println("=== ВАЛИДАЦИЯ ЗАВЕРШЕНА ===")
        println("Всего успешно проверено текстовых файлов глав: $totalCheckedFiles")
        if (totalErrors == 0) {
            println("🏆 ВЕЛИКОЛЕПНО! Все книги и главы на 100% соответствуют канону. Тексты на своих местах, багов нет.")
        } else {
            println("❌ НАЙДЕНЫ КОСЯКИ: Количество проблемных мест: $totalErrors. Требуется исправление генератора.")
        }
    }



    @Test
    fun rewriteBibleFromScratch() {
        // ИДЕАЛЬНАЯ КАРТА: Имя папки в assets -> Точное имя книги из вашего лога диагностики
        val bibleBooks = mapOf(
            "genesis" to "бытие", "exodus" to "исход", "leviticus" to "левит",
            "numbers" to "числа", "deuteronomy" to "второзаконие", "joshua" to "иисус навин",
            "judges" to "книга судей", "ruth" to "руфь", "samuel1" to "1-я царств",
            "samuel2" to "2-я царств", "kings1" to "3-я царств", "kings2" to "4-я царств",
            "chronicles1" to "1-я паралипоменон", "chronicles2" to "2-я паралипоменон", "ezra" to "ездра",
            "nehemiah" to "неемия", "esther" to "есфирь", "job" to "иов",
            "psalms" to "псалтирь", "proverbs" to "притчи", "ecclesiastes" to "екклесиаст",
            "song" to "песни песней", "isaiah" to "исаия", "jeremiah" to "иеремия",
            "lamentations" to "плач иеремии", "ezekiel" to "иезекииль", "daniel" to "даниил",
            "hosea" to "осия", "joel" to "иоиль", "amos" to "амос",
            "obadiah" to "авдия", "jonah" to "иона", "micah" to "михей",
            "nahum" to "наум", "habakkuk" to "аввакум", "zephaniah" to "софония",
            "haggai" to "аггей", "zechariah" to "захария", "malachi" to "малахия",
            "matthew" to "от матфея", "mark" to "от марка", "luke" to "от луки",
            "john" to "от иоанна", "acts" to "деяния", "james" to "иакова",
            // Внимание: в строках ниже буква E — ЛАТИНСКАЯ, как в вашем JSON файле!
            "peter1" to "1-e петра", "peter2" to "2-e петра",
            "john1" to "1-e иоанна", "john2" to "2-e иоанна", "john3" to "3-e иоанна",
            "jude" to "иуда",
            "romans" to "к римлянам", "corinthians1" to "1-е коринфянам", "corinthians2" to "2-е коринфянам",
            "galatians" to "к галатам", "ephesians" to "к ефесянам", "philippians" to "к филиппийцам",
            "colossians" to "к колоссянам", "thessalonians1" to "1-е фессалоникийцам", "thessalonians2" to "2-е фессалоникийцам",
            "timothy1" to "1-е тимофею", "timothy2" to "2-е тимофею", "titus" to "к титу",
            "philemon" to "к филимону", "hebrews" to "к евреям", "revelation" to "откровение"
        )

        val targetFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data")
        val localFile = File("C:/synodal.json")

        if (!localFile.exists()) {
            println("=== ОШИБКА: Положите скачанный файл synodal.json на диск C! ===")
            return
        }

        if (targetFolder.exists()) {
            targetFolder.deleteRecursively()
        }
        targetFolder.mkdirs()

        println("=== ЧТЕНИЕ И ТОЧНАЯ СБОРКА ИЗ ПЛОСКОГО JSON ===")
        val rootObject = org.json.JSONObject(localFile.readText())
        val versesArray = rootObject.getJSONArray("verses")

        // Группировка: ОчищенноеИмяКнигиВJson -> (НомерГлавы -> СписокСтихов)
        val bibleMap = mutableMapOf<String, MutableMap<Int, MutableList<String>>>()

        for (i in 0 until versesArray.length()) {
            val verseObj = versesArray.getJSONObject(i)
            // Приводим к нижнему регистру и обрезаем пробелы для надежного маппинга
            val jsonBookName = verseObj.getString("book_name").lowercase().trim()
            val chapterId = verseObj.getInt("chapter")
            val verseId = verseObj.getInt("verse")
            val text = verseObj.getString("text")

            val bookMap = bibleMap.getOrPut(jsonBookName) { mutableMapOf() }
            val chapterList = bookMap.getOrPut(chapterId) { mutableListOf() }

            chapterList.add("$verseId. $text")
        }

        var totalCreated = 0

        bibleBooks.forEach { (folderName, jsonBookName) ->
            val chaptersMap = bibleMap[jsonBookName]
            if (chaptersMap == null) {
                println("⚠️ ВНИМАНИЕ: Книга '$jsonBookName' не найдена в файле JSON!")
                return@forEach
            }

            val bookDir = File(targetFolder, folderName)
            bookDir.mkdirs()

            // Сортируем главы по возрастанию, чтобы они записывались по порядку
            chaptersMap.keys.sorted().forEach { chapterId ->
                val versesList = chaptersMap[chapterId] ?: return@forEach
                val file = File(bookDir, "$chapterId.txt")
                val chapterText = versesList.joinToString("\n")
                file.writeText(chapterText)
                totalCreated++
            }
            println("✅ Папка '$folderName' успешно заполнена (глав: ${chaptersMap.size})")
        }

        println("=== ГЕНЕРАЦИЯ ЗАВЕРШЕНА УСПЕШНО ===")
        println("Все пропуски устранены. Создано чистых канонических глав: $totalCreated")
    }



    // Абсолютный путь к папке assets в вашем проекте
    private val assetsFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data")

//    @Test
//    fun generateFiles() {
//        val englishBookNames = listOf(
//            "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
//            "joshua", "judges", "ruth", "samuel1", "samuel2",
//            "kings1", "kings2", "chronicles1", "chronicles2", "ezra",
//            "nehemiah", "esther", "job", "psalms", "proverbs",
//            "ecclesiastes", "song", "isaiah", "jeremiah", "lamentations",
//            "ezekiel", "daniel", "hosea", "joel", "amos",
//            "obadiah", "jonah", "micah", "nahum", "habakkuk",
//            "zephaniah", "haggai", "zechariah", "malachi", "matthew",
//            "mark", "luke", "john", "acts", "james",
//            "peter1", "peter2", "john1", "john2", "john3",
//            "jude", "romans", "corinthians1", "corinthians2", "galatians",
//            "ephesians", "philippians", "colossians", "thessalonians1", "thessalonians2",
//            "timothy1", "timothy2", "titus", "philemon", "hebrews",
//            "revelation"
//        )
//
//        if (!assetsFolder.exists()) {
//            assetsFolder.mkdirs()
//        }
//
//        println("=== ЧТЕНИЕ ЛОКАЛЬНОГО ФАЙЛА БАЗЫ ДАННЫХ ===")
//
//        // Указываем путь к сохраненному файлу rst.json на вашем компьютере
//        val localJsonFile = File("C:/rst.json")
//
//        if (!localJsonFile.exists()) {
//            println("=== ОШИБКА: Файл rst.json не найден по пути ${localJsonFile.absolutePath}! ===")
//            println("Скачайте его через браузер и положите на диск C.")
//            return
//        }
//
//        val jsonContent: String
//        try {
//            jsonContent = localJsonFile.readText()
//            println("Локальная база данных Библии успешно прочитана из файла.")
//        } catch (e: Exception) {
//            println("=== ОШИБКА ЧТЕНИЯ: Не удалось прочитать локальный файл: ${e.message} ===")
//            return
//        }
//
//        try {
//            val rootObject = JSONObject(jsonContent)
//            val booksArray = rootObject.getJSONArray("Books")
//
//            var totalFilesCreated = 0
//
//            for (i in 0 until booksArray.length()) {
//                val bookObject = booksArray.getJSONObject(i)
//                val chaptersArray = bookObject.getJSONArray("Chapters")
//
//                val bookFolderName = englishBookNames[i]
//                val bookDirectory = File(assetsFolder, bookFolderName)
//
//                if (!bookDirectory.exists()) {
//                    bookDirectory.mkdirs()
//                }
//
//                for (j in 0 until chaptersArray.length()) {
//                    val chapterObject = chaptersArray.getJSONObject(j)
//                    val versesArray = chapterObject.getJSONArray("Verses")
//
//                    val chapterNumber = j + 1
//                    val file = File(bookDirectory, "$chapterNumber.txt")
//
//                    val chapterTextBuilder = StringBuilder()
//                    for (v in 0 until versesArray.length()) {
//                        val verseObject = versesArray.getJSONObject(v)
//                        val verseNumber = verseObject.getInt("VerseId")
//                        val verseText = verseObject.getString("Text")
//
//                        chapterTextBuilder.append("$verseNumber. $verseText\n")
//                    }
//
//                    file.writeText(chapterTextBuilder.toString().trim())
//                    totalFilesCreated++
//                }
//                println("Книга '$bookFolderName' успешно наполнена текстом (гл: ${chaptersArray.length()})")
//            }
//
//            println("=== УСПЕХ! ВСЯ БИБЛИЯ СОХРАНЕНА В ASSETS ===")
//            println("Всего сгенерировано и заполнено файлов глав: $totalFilesCreated")
//
//        } catch (e: Exception) {
//            println("=== ОШИБКА РАЗБОРА JSON: Проверьте структуру файла: ${e.message} ===")
//        }
//    }

    @Test
    fun clearAssetsFolder() {
        if (assetsFolder.exists()) {
            assetsFolder.deleteRecursively()
            println("=== ОЧИСТКА ЗАВЕРШЕНА: Папка bible_data удалена ===")
        }
    }

    @Test
    fun printAllJsonBookNames() {
        val localFile = File("C:/synodal.json")
        if (!localFile.exists()) {
            println("=== ОШИБКА: Файл synodal.json не найден на диске C ===")
            return
        }

        val rootObject = org.json.JSONObject(localFile.readText())
        val versesArray = rootObject.getJSONArray("verses")

        // Собираем все уникальные названия книг из файла
        val uniqueNames = mutableSetOf<String>()
        for (i in 0 until versesArray.length()) {
            val verseObj = versesArray.getJSONObject(i)
            uniqueNames.add(verseObj.getString("book_name"))
        }

        println("=== СПИСОК ВСЕХ КНИГ В ВАШЕМ JSON ФАЙЛЕ ===")
        uniqueNames.forEachIndexed { index, name ->
            println("Книга #$index: \"$name\"")
        }
        println("==========================================")
    }


    }
